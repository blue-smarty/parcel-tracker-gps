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

JAVA_MAJOR="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
if [ "${JAVA_MAJOR:-0}" -lt 17 ]; then echo "JDK 17 or newer is required (found ${JAVA_MAJOR:-unknown})." >&2; exit 1; fi

mkdir -p "$INSTALL_DIR"
chmod 700 "$INSTALL_DIR"

if [ -e "$INSTALL_DIR/parcel-tracker.env" ]; then
  echo "Existing install found in $INSTALL_DIR; keeping its configuration and rebuilding the jar."
else
  DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"
  ADMIN_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"
  COURIER_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"

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

  (umask 077; cat > "$INSTALL_DIR/parcel-tracker.env" <<EOF
DATABASE_URL=jdbc:postgresql://$PG_HOST:5432/$DB_NAME
DATABASE_USERNAME=$DB_USER
DATABASE_PASSWORD=$DB_PASSWORD
PARCEL_ADMIN_USERNAME=admin
PARCEL_ADMIN_PASSWORD=$ADMIN_PASSWORD
PARCEL_BOOTSTRAP_COURIER_ID=courier-001
PARCEL_BOOTSTRAP_PASSWORD=$COURIER_PASSWORD
EOF
)
  echo "Generated credentials saved to $INSTALL_DIR/parcel-tracker.env (owner-readable only)."
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
