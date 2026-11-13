#!/usr/bin/env bash
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/repo/installer" "$tmp/repo/backend/target" "$tmp/bin"
cp "$repo/installer/install.sh" "$tmp/repo/installer/"
touch "$tmp/repo/backend/target/parcel-tracker-backend-1.0.0.jar"

cat > "$tmp/bin/java" <<'EOF'
#!/usr/bin/env bash
echo 'openjdk version "17.0.1"' >&2
EOF
cat > "$tmp/bin/mvn" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MVN_LOG"
exit 0
EOF
cat > "$tmp/bin/psql" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$PSQL_LOG"
EOF
chmod +x "$tmp/bin/java" "$tmp/bin/mvn" "$tmp/bin/psql"
export PATH="$tmp/bin:$PATH" PSQL_LOG="$tmp/psql.log" MVN_LOG="$tmp/mvn.log"

admin_password='admin $HOME '"'"' " `test`'
courier_password='courier $(touch injected) \ !'
printf '%s\n' 'bad:name' 'admin-1' 'short' '            ' "$admin_password" 'admin-1' 'courier-1' "$courier_password" '' |
  bash "$tmp/repo/installer/install.sh" "$tmp/install" > "$tmp/output" 2>&1

grep -q 'Use 1-255' "$tmp/output"
grep -q 'Use 12-72' "$tmp/output"
grep -q 'must differ' "$tmp/output"
test "$(ls -l "$tmp/install/parcel-tracker.env" | cut -c 1-10)" = '-rw-------'
test ! -e injected
(
  . "$tmp/install/parcel-tracker.env"
  test "$PARCEL_ADMIN_USERNAME" = 'admin-1'
  test "$PARCEL_ADMIN_PASSWORD" = "$admin_password"
  test "$PARCEL_BOOTSTRAP_COURIER_ID" = 'courier-1'
  test "$PARCEL_BOOTSTRAP_PASSWORD" = "$courier_password"
)
cp "$tmp/install/parcel-tracker.env" "$tmp/original.env"
cp "$PSQL_LOG" "$tmp/original-psql.log"
printf '\n' | bash "$tmp/repo/installer/install.sh" "$tmp/install" > "$tmp/reinstall-output"
cmp "$tmp/original.env" "$tmp/install/parcel-tracker.env"
cmp "$tmp/original-psql.log" "$PSQL_LOG"
grep -q 'keeping its configuration' "$tmp/reinstall-output"
test "$(grep -c -- '-q -DskipTests clean package' "$MVN_LOG")" -eq 2

echo 'Installer credential tests passed.'
