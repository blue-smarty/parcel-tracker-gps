#!/usr/bin/env python3
"""Local, single-use browser setup for the native backend."""

import contextlib
import csv
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


ROOT = Path(__file__).resolve().parent.parent
INSTALLER = Path(__file__).resolve().parent
IDENTIFIER = re.compile(r"[A-Za-z_][A-Za-z0-9_]{0,62}\Z", re.ASCII)
USERNAME = re.compile(r"[A-Za-z0-9._-]{1,255}\Z", re.ASCII)
MAX_BODY = 16384


class SetupError(Exception):
    pass


def validate(data):
    fields = ("install_dir", "postgres_install", "pg_host", "pg_admin", "pg_password", "db_name",
              "db_user", "admin_username", "admin_password", "courier_username",
              "courier_password", "tomtom_key")
    if not isinstance(data, dict) or set(data) != set(fields) or any(
            not isinstance(data[field], str) for field in fields):
        raise SetupError("Invalid setup form.")
    if not data["install_dir"].strip() or not Path(data["install_dir"]).expanduser().is_absolute():
        raise SetupError("Install directory must be an absolute path.")
    resolved = Path(data["install_dir"]).expanduser().resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise SetupError("Choose an install directory outside the source repository.")
    if not re.fullmatch(r"[A-Za-z0-9.-]+", data["pg_host"], re.ASCII):
        raise SetupError("PostgreSQL host must be a hostname or IPv4 address.")
    for field in ("pg_admin", "db_name", "db_user"):
        if not IDENTIFIER.fullmatch(data[field]):
            raise SetupError("PostgreSQL names must begin with a letter or underscore and contain only letters, digits and underscores (maximum 63 characters).")
    for field in ("admin_username", "courier_username"):
        if not USERNAME.fullmatch(data[field]):
            raise SetupError("Usernames must contain 1-255 letters, digits, dots, underscores or hyphens.")
    if data["admin_username"] == data["courier_username"]:
        raise SetupError("Admin and courier usernames must differ.")
    for field in ("admin_password", "courier_password"):
        value = data[field]
        if not 12 <= len(value) <= 72 or not all(" " <= c <= "~" for c in value) or not value.strip():
            raise SetupError("Passwords must contain 12-72 printable ASCII characters, including a non-space character.")
    if "\x00" in data["pg_password"] or "\x00" in data["tomtom_key"]:
        raise SetupError("Secrets cannot contain NUL characters.")
    if data["postgres_install"] not in ("existing", "install"):
        raise SetupError("Choose a valid PostgreSQL setup option.")
    if data["postgres_install"] == "install":
        if data["pg_host"] not in ("localhost", "127.0.0.1") or data["pg_admin"] != "postgres":
            raise SetupError("Automatic PostgreSQL installation requires localhost and the postgres administrator.")
        password = data["pg_password"]
        if not 12 <= len(password) <= 72 or not all(" " <= c <= "~" for c in password) or not password.strip():
            raise SetupError("The new PostgreSQL administrator password must contain 12-72 printable ASCII characters, including a non-space character.")


def command(args, *, env=None, input_text=None, cwd=None, timeout=600, capture_to_file=False):
    try:
        with tempfile.TemporaryFile(mode="w+t", encoding="utf-8") if capture_to_file else contextlib.nullcontext() as output:
            result = subprocess.run(args, input=input_text,
                                    stdin=subprocess.DEVNULL if input_text is None else None,
                                    text=True, env=env, cwd=cwd,
                                    stdout=output if capture_to_file else subprocess.PIPE,
                                    stderr=subprocess.STDOUT, timeout=timeout)
            if capture_to_file:
                output.seek(0)
                result.stdout = output.read()
    except (OSError, subprocess.TimeoutExpired) as exc:
        if isinstance(exc, subprocess.TimeoutExpired) and capture_to_file:
            raise SetupError("PostgreSQL package installation timed out. Package processes may still be running; inspect them and the package state before retrying.") from exc
        raise SetupError("Could not run {}: {}".format(args[0], exc)) from exc
    if result.returncode:
        if args[0] == "mvn":
            print("Maven build failed:\n{}".format(result.stdout), file=sys.stderr, flush=True)
            raise SetupError("Maven build failed. Check the setup terminal for details.")
        if args[0] == "psql" and not (input_text or "").startswith("CREATE ROLE"):
            diagnostic = result.stdout
            password = (env or {}).get("PGPASSWORD")
            if password:
                diagnostic = diagnostic.replace(password, "[redacted]")
            print("PostgreSQL command failed:\n{}".format(diagnostic), file=sys.stderr, flush=True)
            raise SetupError("PostgreSQL command failed. Check the setup terminal for details.")
        if args[0] == "sudo" and "apt-get" in args:
            print("PostgreSQL package installation failed:\n{}".format(result.stdout), file=sys.stderr, flush=True)
            raise SetupError("PostgreSQL package installation failed. Check the setup terminal for details.")
        raise SetupError("{} failed. Check PostgreSQL connectivity and permissions, then retry.".format(args[0]))
    return result.stdout.strip()


def private_directory(path):
    if path.exists():
        raise SetupError("Install directory already exists. Choose a new directory; existing installations are never overwritten.")
    path.mkdir(mode=0o700, parents=True)
    if os.name == "nt":
        try:
            output = subprocess.check_output(["whoami", "/user", "/fo", "csv", "/nh"], text=True)
            sid = next(csv.reader(output.splitlines()))[1]
            command(["icacls", str(path), "/inheritance:r", "/grant:r", "*{}:(OI)(CI)F".format(sid)])
        except (OSError, subprocess.CalledProcessError, IndexError, StopIteration, SetupError) as exc:
            path.rmdir()
            raise SetupError("Could not restrict the install directory to the current Windows user.") from exc
    else:
        path.chmod(0o700)


def install_postgresql(password, progress=lambda message: None):
    if not sys.platform.startswith("linux") or not all(
            shutil.which(tool) for tool in ("sudo", "apt-get", "apt-cache", "dpkg-query")):
        raise SetupError("Automatic PostgreSQL installation is supported only on Ubuntu/Debian with apt and sudo.")
    try:
        command(["sudo", "-n", "true"])
    except SetupError as exc:
        raise SetupError("Administrator approval is required: run sudo -v in the setup terminal, then retry.") from exc

    packages = command(["dpkg-query", "-W", "-f=${binary:Package} ${db:Status-Abbrev}\\n"])
    installed = any(re.match(r"^postgresql(?:-[0-9]+)?(?::[A-Za-z0-9_-]+)?\s+(?:[ih][A-Za-z?]|rc)", line)
                    for line in packages.splitlines())
    if (installed or Path("/etc/postgresql").exists()
            or Path("/var/lib/postgresql").exists() or shutil.which("postgres")):
        raise SetupError("An existing PostgreSQL installation or data directory was detected. Use the existing-server option; no password was changed.")
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.bind(("127.0.0.1", 5432))
    except OSError as exc:
        raise SetupError("Port 5432 is already in use; PostgreSQL was not installed.") from exc

    policy = command(["apt-cache", "policy", "postgresql-16"])
    if not re.search(r"^\s*Candidate:\s+(?!\(none\))\S+", policy, re.MULTILINE):
        raise SetupError("PostgreSQL 16 is not available in the configured apt repositories. Install it separately, then use the existing-server option.")
    try:
        progress("Installing PostgreSQL packages. This can take up to 30 minutes; keep this page open.")
        # A newly started service can inherit output descriptors and keep a pipe or dpkg PTY open.
        command(["sudo", "-n", "apt-get", "-o", "Dpkg::Use-Pty=0", "-y", "install",
                 "postgresql-16", "postgresql-client-16"], timeout=1800, capture_to_file=True)
        progress("Checking the PostgreSQL cluster and configuring its administrator.")
        clusters = command(["pg_lsclusters", "--no-header"])
        rows = [line.split() for line in clusters.splitlines() if line.strip()]
        if len(rows) != 1 or len(rows[0]) < 4 or rows[0][:3] != ["16", "main", "5432"]:
            raise SetupError("The expected new PostgreSQL 16 main cluster on port 5432 was not found.")
        if rows[0][3] == "down":
            command(["sudo", "-n", "pg_ctlcluster", "16", "main", "start"])
        elif rows[0][3] != "online":
            raise SetupError("The new PostgreSQL cluster is not online.")
        # Send the password over stdin, never through a command-line argument.
        escaped_password = password.replace("'", "''")
        command(["sudo", "-n", "-u", "postgres", "psql", "-X", "-w", "-d", "postgres",
                 "-v", "ON_ERROR_STOP=1"],
                input_text="SET standard_conforming_strings = on;\nALTER ROLE postgres PASSWORD '{}';\n".format(escaped_password))
    except (SetupError, OSError) as exc:
        raise SetupError("PostgreSQL installation or password setup failed. Packages or a cluster may now exist; inspect them and use the existing-server option after fixing the problem. {}".format(exc)) from exc


def install(data, progress=lambda message: None):
    progress("Checking installation prerequisites.")
    validate(data)
    for tool in ("java", "mvn"):
        if not shutil.which(tool):
            raise SetupError("Missing required tool: {}".format(tool))
    version = command(["java", "-version"])
    match = re.search(r'version "(\d+)', version)
    if not match or int(match.group(1)) < 17:
        raise SetupError("JDK 17 or newer is required.")

    directory = Path(data["install_dir"]).expanduser()
    if directory.exists():
        raise SetupError("Install directory already exists. Choose a new directory; existing installations are never overwritten.")

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.bind(("127.0.0.1", 8080))
    except OSError as exc:
        raise SetupError("Port 8080 is unavailable. Stop the existing service before installing.") from exc

    if data["postgres_install"] == "install":
        install_postgresql(data["pg_password"], progress)
    if not shutil.which("psql"):
        raise SetupError("Missing required tool: psql")

    progress("Checking the PostgreSQL connection and database names.")
    env = os.environ.copy()
    if data["pg_password"]:
        env["PGPASSWORD"] = data["pg_password"]
    else:
        env.pop("PGPASSWORD", None)
    remote_db = data["pg_host"].lower() not in ("localhost", "127.0.0.1")
    if remote_db:
        env["PGSSLMODE"] = "verify-full"
    psql = ["psql", "-X", "-w", "-h", data["pg_host"], "-U", data["pg_admin"],
            "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-tA"]
    command(psql, env=env, input_text="SELECT 1;\n")
    version_number = command(psql, env=env, input_text="SHOW server_version_num;\n")
    if not version_number.isdigit() or int(version_number) < 160000:
        raise SetupError("PostgreSQL 16 or newer is required.")
    db_user, db_name = data["db_user"], data["db_name"]
    existing = command(psql, env=env, input_text=(
        "SELECT 'role' FROM pg_roles WHERE rolname = '{}';\n"
        "SELECT 'database' FROM pg_database WHERE datname = '{}';\n".format(db_user, db_name)))
    if existing:
        raise SetupError("The database role or database already exists. Choose new names; existing resources are never modified.")

    progress("Building the backend with Maven.")
    command(["mvn", "-B", "-q", "-f", str(ROOT / "backend" / "pom.xml"),
             "-DskipTests", "package"], cwd=str(ROOT))
    jar = ROOT / "backend" / "target" / "parcel-tracker-backend-1.0.0.jar"
    if not jar.is_file():
        raise SetupError("The backend build did not produce the expected jar.")
    progress("Creating the installation and database.")
    private_directory(directory)
    db_password = secrets.token_urlsafe(32)
    config = {
        "DATABASE_URL": "jdbc:postgresql://{}:5432/{}{}".format(
            data["pg_host"], db_name, "?sslmode=verify-full" if remote_db else ""),
        "DATABASE_USERNAME": db_user,
        "DATABASE_PASSWORD": db_password,
        "PARCEL_ADMIN_USERNAME": data["admin_username"],
        "PARCEL_ADMIN_PASSWORD": data["admin_password"],
        "PARCEL_BOOTSTRAP_COURIER_ID": data["courier_username"],
        "PARCEL_BOOTSTRAP_PASSWORD": data["courier_password"],
        "TOMTOM_API_KEY": data["tomtom_key"],
    }
    config_path = directory / "config.json"
    fd = os.open(str(config_path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        json.dump(config, output)
    shutil.copyfile(jar, directory / "parcel-tracker-backend.jar")
    shutil.copyfile(INSTALLER / "run-installed.py", directory / "run-installed.py")
    try:
        command(psql, env=env, input_text='CREATE ROLE "{}" LOGIN PASSWORD \'{}\';\n'.format(db_user, db_password))
        command(psql, env=env, input_text='CREATE DATABASE "{}" OWNER "{}";\n'.format(db_name, db_user))
    except SetupError as exc:
        raise SetupError("{} Configuration remains in {}. A role or database may have been created; inspect PostgreSQL before retrying with new names.".format(exc, directory)) from exc

    progress("Starting the backend and waiting for it to become ready.")
    log_path = directory / "backend.log"
    fd = os.open(str(log_path), os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
    with os.fdopen(fd, "a", encoding="utf-8") as log:
        kwargs = {"stdout": log, "stderr": subprocess.STDOUT, "stdin": subprocess.DEVNULL}
        if os.name == "nt":
            kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS
        else:
            kwargs["start_new_session"] = True
        try:
            process = subprocess.Popen([sys.executable, str(directory / "run-installed.py")], **kwargs)
        except OSError as exc:
            raise SetupError("Could not start backend; installation remains in {}: {}".format(directory, exc)) from exc

    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise SetupError("Backend exited during startup. Check {}. Installation and database have been preserved.".format(log_path))
        try:
            with urllib.request.urlopen("http://127.0.0.1:8080/api/csrf", timeout=2) as response:
                started = "Started ParcelTrackerApplication" in log_path.read_text(encoding="utf-8", errors="replace")
                if response.status == 200 and started and process.poll() is None:
                    return "Installation complete. Backend is running. Configuration and logs are in {}.".format(directory)
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
    raise SetupError("Backend did not become ready in time. Check {}; installation and database have been preserved.".format(log_path))


def make_handler(token):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):
            pass

        def allowed_host(self):
            return self.headers.get("Host") == "127.0.0.1:{}".format(self.server.server_port)

        def do_GET(self):
            if not self.allowed_host():
                self.send_error(404)
                return
            if self.path == "/status":
                supplied_token = self.headers.get("X-Setup-Token", "")
                if not supplied_token.isascii() or not hmac.compare_digest(supplied_token, token):
                    self.send_error(403)
                    return
                self.reply(200, self.server.progress)
                return
            if self.path != "/":
                self.send_error(404)
                return
            page = (INSTALLER / "setup.html").read_text(encoding="utf-8")
            default = json.dumps(str(Path.home() / ".parcel-tracker"), ensure_ascii=True).replace("<", "\\u003c")
            content = page.replace("__DEFAULT_INSTALL_DIR__", default).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; form-action 'none'; frame-ancestors 'none'; base-uri 'none'")
            self.end_headers()
            self.wfile.write(content)

        def do_POST(self):
            expected_origin = "http://127.0.0.1:{}".format(self.server.server_port)
            supplied_token = self.headers.get("X-Setup-Token", "")
            if (not self.allowed_host() or self.path != "/install"
                    or self.headers.get("Origin") != expected_origin
                    or not supplied_token.isascii()
                    or not hmac.compare_digest(supplied_token, token)
                    or self.headers.get_content_type() != "application/json"):
                self.send_error(403)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                self.send_error(400)
                return
            if length <= 0 or length > MAX_BODY:
                self.send_error(413)
                return
            if not self.server.install_lock.acquire(blocking=False):
                self.reply(409, "An installation is already in progress.")
                return
            try:
                self.server.progress = "Checking installation prerequisites."
                try:
                    data = json.loads(self.rfile.read(length))
                    message = install(data, lambda message: setattr(self.server, "progress", message))
                except (ValueError, UnicodeDecodeError, SetupError, OSError) as exc:
                    self.reply(400, str(exc))
                    return
                try:
                    self.reply(200, message)
                finally:
                    threading.Thread(target=self.server.shutdown, daemon=True).start()
            finally:
                self.server.install_lock.release()

        def reply(self, status, message):
            content = json.dumps({"message": message}).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(content)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(content)

    return Handler


def main():
    token = secrets.token_urlsafe(32)
    with ThreadingHTTPServer(("127.0.0.1", 0), make_handler(token)) as server:
        server.install_lock = threading.Lock()
        server.progress = "Waiting for installation."
        url = "http://127.0.0.1:{}/#{}".format(server.server_port, token)
        print("Open this private setup link in a browser: {}".format(url), flush=True)
        print("Press Ctrl+C to cancel. Do not share the link.", flush=True)
        webbrowser.open(url)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nSetup cancelled.")


if __name__ == "__main__":
    main()
