import http.client
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest import mock


spec = importlib.util.spec_from_file_location("parcel_setup", Path(__file__).with_name("setup.py"))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


def form(directory):
    return {
        "install_dir": str(directory),
        "postgres_install": "existing",
        "pg_host": "localhost",
        "pg_admin": "postgres",
        "pg_password": "secret-password",
        "db_name": "parceltracker",
        "db_user": "parceltracker",
        "admin_username": "admin",
        "admin_password": "admin password 123",
        "courier_username": "courier",
        "courier_password": "courier password 123",
        "tomtom_key": "test-key",
    }


class ValidationTest(unittest.TestCase):
    def test_valid_and_invalid_values(self):
        data = form(Path.home() / "parcel-test-install")
        setup.validate(data)
        for field, value in (("db_name", "name'; DROP DATABASE x;--"),
                             ("pg_host", "host/other"),
                             ("admin_password", "short"),
                             ("courier_username", "admin"),
                             ("postgres_install", "invalid"),
                             ("install_dir", "relative/path"),
                             ("install_dir", str(setup.ROOT / "credentials"))):
            with self.subTest(field=field), self.assertRaises(setup.SetupError):
                setup.validate({**data, field: value})

    def test_new_postgres_requires_local_default_admin_and_strong_password(self):
        data = {**form(Path.home() / "parcel-test-install"), "postgres_install": "install"}
        setup.validate(data)
        for field, value in (("pg_host", "db.example.com"), ("pg_admin", "other"),
                             ("pg_password", "short"), ("pg_password", "bad\npassword123")):
            with self.subTest(field=field), self.assertRaises(setup.SetupError):
                setup.validate({**data, field: value})

    def test_existing_directory_is_not_modified(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            marker = path / "marker"
            marker.write_text("untouched")
            with mock.patch.object(setup.shutil, "which", return_value="/usr/bin/test"), \
                    mock.patch.object(setup, "command", return_value='openjdk version "17"'):
                with self.assertRaisesRegex(setup.SetupError, "already exists"):
                    setup.install(form(path))
            self.assertEqual("untouched", marker.read_text())


class InstallTest(unittest.TestCase):
    def test_installs_and_starts_without_exposing_postgres_password(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "source"
            target = root / "backend" / "target"
            target.mkdir(parents=True)
            (target / "parcel-tracker-backend-1.0.0.jar").write_bytes(b"jar")
            directory = Path(temp) / "installed"
            calls = []

            def command(args, **kwargs):
                calls.append((args, kwargs))
                sql = kwargs.get("input_text", "")
                if args[0] == "java":
                    return 'openjdk version "17"'
                if sql.startswith("SHOW"):
                    return "160000"
                if sql.startswith("SELECT 1"):
                    return "1"
                return ""

            process = mock.Mock()
            process.poll.return_value = None

            def launch(args, **kwargs):
                kwargs["stdout"].write("Started ParcelTrackerApplication\n")
                kwargs["stdout"].flush()
                return process

            response = mock.MagicMock()
            response.__enter__.return_value.status = 200
            data = form(directory)
            data["pg_host"] = "database.example.com"
            with mock.patch.object(setup, "ROOT", root), \
                    mock.patch.object(setup.shutil, "which", return_value="/usr/bin/test"), \
                    mock.patch.object(setup, "command", side_effect=command), \
                    mock.patch.object(setup.socket, "socket"), \
                    mock.patch.object(setup.subprocess, "check_output",
                                       return_value='"runner","S-1-5-21-1"'), \
                    mock.patch.object(setup.subprocess, "Popen", side_effect=launch), \
                    mock.patch.object(setup.urllib.request, "urlopen", return_value=response):
                self.assertIn("Installation complete", setup.install(data))

            config = json.loads((directory / "config.json").read_text())
            self.assertEqual("admin password 123", config["PARCEL_ADMIN_PASSWORD"])
            self.assertNotIn("secret-password", (directory / "config.json").read_text())
            self.assertTrue(config["DATABASE_URL"].endswith("?sslmode=verify-full"))
            self.assertTrue((directory / "parcel-tracker-backend.jar").is_file())
            self.assertTrue((directory / "run-installed.py").is_file())
            if os.name != "nt":
                self.assertEqual(0o700, directory.stat().st_mode & 0o777)
                self.assertEqual(0o600, (directory / "config.json").stat().st_mode & 0o777)
            for args, kwargs in calls:
                self.assertNotIn("secret-password", str(args))
                if args[0] == "psql":
                    self.assertEqual("secret-password", kwargs["env"]["PGPASSWORD"])
                    self.assertEqual("verify-full", kwargs["env"]["PGSSLMODE"])

    def test_provisioning_failure_preserves_configuration(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "source"
            target = root / "backend" / "target"
            target.mkdir(parents=True)
            (target / "parcel-tracker-backend-1.0.0.jar").write_bytes(b"jar")
            directory = Path(temp) / "installed"

            def command(args, **kwargs):
                sql = kwargs.get("input_text", "")
                if args[0] == "java":
                    return 'openjdk version "17"'
                if sql.startswith("SHOW"):
                    return "160000"
                if sql.startswith("CREATE DATABASE"):
                    raise setup.SetupError("database creation failed")
                return ""

            with mock.patch.object(setup, "ROOT", root), \
                    mock.patch.object(setup.shutil, "which", return_value="/usr/bin/test"), \
                    mock.patch.object(setup, "command", side_effect=command), \
                    mock.patch.object(setup.socket, "socket"):
                with self.assertRaisesRegex(setup.SetupError, "may have been created"):
                    setup.install(form(directory))
            self.assertTrue((directory / "config.json").is_file())


class PostgresInstallTest(unittest.TestCase):
    def run_installer(self, packages="", directories=False, clusters="16 main 5432 online postgres /data /log"):
        calls = []

        def fake_command(args, **kwargs):
            calls.append((args, kwargs))
            if args[0] == "dpkg-query":
                return packages
            if args[0] == "apt-cache":
                return "postgresql-16:\n  Candidate: 16.4-1\n"
            if args[0] == "pg_lsclusters":
                return clusters
            return ""

        with mock.patch.object(setup.sys, "platform", "linux"), \
                mock.patch.object(setup.shutil, "which", side_effect=lambda name: None if name == "postgres" else "/usr/bin/" + name), \
                mock.patch.object(setup.Path, "exists", return_value=directories), \
                mock.patch.object(setup.socket, "socket"), \
                mock.patch.object(setup, "command", side_effect=fake_command):
            setup.install_postgresql("safe'password123")
        return calls

    def test_installs_and_sets_password_over_stdin(self):
        calls = self.run_installer()
        self.assertIn(["sudo", "-n", "apt-get", "-y", "install", "postgresql-16", "postgresql-client-16"],
                      [args for args, _ in calls])
        args, kwargs = calls[-1]
        self.assertEqual(["sudo", "-n", "-u", "postgres", "psql"], args[:5])
        self.assertNotIn("safe'password123", str(args))
        self.assertIn("safe''password123", kwargs["input_text"])

    def test_does_not_change_existing_installations(self):
        for packages, directories in (("postgresql-16 ii ", False),
                                      ("postgresql-16 iF ", False),
                                      ("postgresql-16 rc ", False),
                                      ("", True)):
            with self.subTest(packages=packages, directories=directories), \
                    self.assertRaisesRegex(setup.SetupError, "existing PostgreSQL"):
                self.run_installer(packages, directories)

    def test_unexpected_cluster_does_not_set_password(self):
        with self.assertRaisesRegex(setup.SetupError, "expected new PostgreSQL"):
            self.run_installer(clusters="16 other 5433 online postgres /data /log")

    def test_sudo_approval_is_required_before_installing(self):
        calls = []

        def fake_command(args, **kwargs):
            calls.append(args)
            raise setup.SetupError("sudo denied")

        with mock.patch.object(setup.sys, "platform", "linux"), \
                mock.patch.object(setup.shutil, "which", return_value="/usr/bin/test"), \
                mock.patch.object(setup, "command", side_effect=fake_command):
            with self.assertRaisesRegex(setup.SetupError, "sudo -v"):
                setup.install_postgresql("safe-password123")
        self.assertEqual([["sudo", "-n", "true"]], calls)


class ServerTest(unittest.TestCase):
    def setUp(self):
        self.server = setup.ThreadingHTTPServer(("127.0.0.1", 0), setup.make_handler("private-token"))
        self.server.install_lock = threading.Lock()
        self.thread = threading.Thread(target=self.server.serve_forever)
        self.thread.start()
        self.host = "127.0.0.1:{}".format(self.server.server_port)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def request(self, method, path, headers=None, body=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        try:
            connection.request(method, path, body=body, headers=headers or {})
            response = connection.getresponse()
            return response.status, response.read()
        finally:
            connection.close()

    def test_rejects_dns_rebinding_and_cross_origin_or_missing_token(self):
        self.assertEqual(404, self.request("GET", "/", {"Host": "attacker.example"})[0])
        self.assertEqual(200, self.request("GET", "/")[0])
        headers = {"Host": self.host, "Content-Type": "application/json",
                   "Origin": "http://attacker.example", "X-Setup-Token": "private-token"}
        with mock.patch.object(setup, "install") as install:
            self.assertEqual(403, self.request("POST", "/install", headers, "{}")[0])
            headers["Origin"] = "http://" + self.host
            headers["X-Setup-Token"] = "wrong"
            self.assertEqual(403, self.request("POST", "/install", headers, "{}")[0])
            install.assert_not_called()

    def test_success_shuts_down_setup_server(self):
        headers = {"Host": self.host, "Content-Type": "application/json",
                   "Origin": "http://" + self.host, "X-Setup-Token": "private-token"}
        with mock.patch.object(setup, "install", return_value="complete") as install:
            status, body = self.request("POST", "/install", headers, json.dumps({"a": 1}))
            self.assertEqual(200, status)
            self.assertEqual("complete", json.loads(body)["message"])
            install.assert_called_once_with({"a": 1})
            self.thread.join(timeout=3)
            self.assertFalse(self.thread.is_alive())

    def test_failure_does_not_stop_server_or_echo_form(self):
        headers = {"Host": self.host, "Content-Type": "application/json",
                   "Origin": "http://" + self.host, "X-Setup-Token": "private-token"}
        with mock.patch.object(setup, "install", side_effect=setup.SetupError("invalid input")):
            status, body = self.request("POST", "/install", headers, '{"password":"secret"}')
            self.assertEqual(400, status)
            self.assertNotIn(b"secret", body)
            self.assertTrue(self.thread.is_alive())


if __name__ == "__main__":
    unittest.main()
