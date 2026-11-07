#!/usr/bin/env bash
# Installs the Parcel Tracker backend (and web portal) natively on Linux or macOS.
# Requires JDK 17+, Maven and a running PostgreSQL 16+. No Docker needed.
# Usage: installer/install.sh [install-dir]   (default: ~/.parcel-tracker)
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_DIR="${1:-$HOME/.parcel-tracker}"
DB_NAME="${PARCEL_DB_NAME:-parceltracker}"
DB_USER="${PARCEL_DB_USER:-parceltracker}"
PG_ADMIN="${PARCEL_PG_ADMIN:-postgres}"
PG_HOST="${PARCEL_PG_HOST:-localhost}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required tool: $1" >&2; exit 1; }; }
need java; need mvn; need psql; need openssl

read_username() {
  local prompt="$1" value LC_ALL=C
  while true; do
    read -r -p "$prompt: " value || { echo "Unable to read username." >&2; exit 1; }
    if [[ "$value" =~ ^[a-zA-Z0-9._-]{1,255}$ ]]; then
      REPLY="$value"
      return
    fi
    echo "Use 1-255 letters, digits, dots, underscores or hyphens." >&2
  done
}

read_password() {
  local prompt="$1" value LC_ALL=C
  while true; do
    read -r -s -p "$prompt: " value || { echo "Unable to read password." >&2; exit 1; }
    echo
    if [[ "$value" =~ ^[[:print:]]{12,72}$ && "$value" =~ [^[:space:]] ]]; then
      REPLY="$value"
      return
    fi
    echo "Use 12-72 printable ASCII characters, including at least one non-space character." >&2
  done
}

JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
if [ "${JAVA_MAJOR:-0}" -lt 17 ]; then echo "JDK 17 or newer is required (found ${JAVA_MAJOR:-unknown})." >&2; exit 1; fi

mkdir -p "$INSTALL_DIR"
chmod 700 "$INSTALL_DIR"

if [ -e "$INSTALL_DIR/parcel-tracker.env" ]; then
  echo "Existing install found in $INSTALL_DIR; keeping its configuration and rebuilding the jar."
else
  read_username "Admin username"; ADMIN_USERNAME="$REPLY"
  read_password "Admin password"; ADMIN_PASSWORD="$REPLY"
  while true; do
    read_username "Courier username (courier ID)"; COURIER_USERNAME="$REPLY"
    if [ "$COURIER_USERNAME" != "$ADMIN_USERNAME" ]; then break; fi
    echo "Courier username must differ from the admin username." >&2
  done
  read_password "Courier password"; COURIER_PASSWORD="$REPLY"
  DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"

  # Fall back to the postgres OS account when local admin login requires it (typical on Linux).
  PSQL=(psql -h "$PG_HOST" -U "$PG_ADMIN" -v ON_ERROR_STOP=1 -d postgres)
  if ! "${PSQL[@]}" -tAc 'select 1' >/dev/null 2>&1 && command -v sudo >/dev/null 2>&1; then
    PSQL=(sudo -u postgres psql -v ON_ERROR_STOP=1 -d postgres)
  fi
  if ! "${PSQL[@]}" -tAc 'select 1' >/dev/null 2>&1; then
    echo "Cannot connect to PostgreSQL as '$PG_ADMIN'. Start it and set PARCEL_PG_ADMIN/PARCEL_PG_HOST if needed." >&2
    exit 1
  fi
  if [ "$("${PSQL[@]}" -tAc "select 1 from pg_roles where rolname='$DB_USER'")" = "1" ]; then
    echo "Database role '$DB_USER' already exists; set PARCEL_DB_USER to another name or drop it first." >&2
    exit 1
  fi
  "${PSQL[@]}" -c "create role \"$DB_USER\" login password '$DB_PASSWORD'"
  "${PSQL[@]}" -c "create database \"$DB_NAME\" owner \"$DB_USER\""

  (umask 077
   {
     printf 'DATABASE_URL=%q\nDATABASE_USERNAME=%q\nDATABASE_PASSWORD=%q\n' \
       "jdbc:postgresql://$PG_HOST:5432/$DB_NAME" "$DB_USER" "$DB_PASSWORD"
     printf 'PARCEL_ADMIN_USERNAME=%q\nPARCEL_ADMIN_PASSWORD=%q\n' "$ADMIN_USERNAME" "$ADMIN_PASSWORD"
     printf 'PARCEL_BOOTSTRAP_COURIER_ID=%q\nPARCEL_BOOTSTRAP_PASSWORD=%q\n' "$COURIER_USERNAME" "$COURIER_PASSWORD"
   } > "$INSTALL_DIR/parcel-tracker.env"
  )
  echo "Credentials saved to $INSTALL_DIR/parcel-tracker.env (owner-readable only)."
fi

echo "Building backend..."
(cd "$REPO_DIR/backend" && mvn -q -DskipTests package)
cp "$REPO_DIR/backend/target/parcel-tracker-backend-1.0.0.jar" "$INSTALL_DIR/parcel-tracker-backend.jar"

cat > "$INSTALL_DIR/start.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
set -a; . "$DIR/parcel-tracker.env"; set +a
exec java -jar "$DIR/parcel-tracker-backend.jar"
EOF
chmod 755 "$INSTALL_DIR/start.sh"

cat <<EOF

Installed to $INSTALL_DIR
Start:   $INSTALL_DIR/start.sh
Website: http://localhost:8080
Admin login and courier credentials: see $INSTALL_DIR/parcel-tracker.env
EOF
