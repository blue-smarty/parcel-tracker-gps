#!/usr/bin/env bash
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
ENV_FILE="$ROOT/.env"

if ! command -v docker >/dev/null 2>&1; then
    printf '%s\n' "Docker is required. Install Docker Engine with the Compose plugin, then run this installer again." >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    printf '%s\n' "Docker Compose v2 is required. Install the Compose plugin, then run this installer again." >&2
    exit 1
fi

if [ -e "$ENV_FILE" ]; then
    printf '%s' "An existing .env configuration was found. Replace it? [y/N] "
    read -r replace
    case "$replace" in
        y|Y|yes|YES) ;;
        *) printf '%s\n' "Keeping the existing configuration."; exit 0 ;;
    esac
fi

read_default() {
    prompt="$1"
    default="$2"
    printf '%s [%s]: ' "$prompt" "$default" >&2
    read -r value
    printf '%s' "${value:-$default}"
}

valid_identifier() {
    case "$1" in
        ""|*[!A-Za-z0-9._-]*) return 1 ;;
        *) return 0 ;;
    esac
}

read_password() {
    label="$1"
    while :; do
        printf '%s (12+ characters; letters, digits, . _ ! @ %% - only): ' "$label" >&2
        read -r -s password
        printf '\n' >&2
        if [ "${#password}" -lt 12 ]; then
            printf '%s\n' "Use at least 12 characters." >&2
        elif ! valid_password "$password"; then
            printf '%s\n' "Use only letters, digits, . _ ! @ %%, and -." >&2
        else
            printf '%s' "$password"
            return
        fi
    done
}

valid_password() {
    case "$1" in
        ""|*[!A-Za-z0-9._!@%-]*) return 1 ;;
        *) return 0 ;;
    esac
}

app_port="$(read_default 'Web application port' '8080')"
case "$app_port" in
    ""|*[!0-9]*)
        printf '%s\n' "The port must be a number from 1 to 65535." >&2
        exit 1
        ;;
esac
if [ "${#app_port}" -gt 5 ]; then
    printf '%s\n' "The port must be a number from 1 to 65535." >&2
    exit 1
fi
app_port=$((10#$app_port))
if [ "$app_port" -lt 1 ] || [ "$app_port" -gt 65535 ]; then
    printf '%s\n' "The port must be a number from 1 to 65535." >&2
    exit 1
fi

printf '%s' "Allow access from other machines? [y/N] " >&2
read -r network_access
case "$network_access" in
    y|Y|yes|YES) app_bind_address="0.0.0.0" ;;
    *) app_bind_address="127.0.0.1" ;;
esac

admin_username="$(read_default 'Admin username' 'admin')"
if ! valid_identifier "$admin_username"; then
    printf '%s\n' "Use only letters, digits, period, underscore, and hyphen in the admin username." >&2
    exit 1
fi

admin_password="$(read_password 'Admin password')"
admin_confirmation="$(read_password 'Confirm admin password')"
if [ "$admin_password" != "$admin_confirmation" ]; then
    printf '%s\n' "Passwords did not match." >&2
    exit 1
fi

printf '%s' "Bootstrap a courier account now? [y/N] "
read -r bootstrap
case "$bootstrap" in
    y|Y|yes|YES)
        courier_id="$(read_default 'Courier ID' 'courier1')"
        if ! valid_identifier "$courier_id"; then
            printf '%s\n' "Use only letters, digits, period, underscore, and hyphen in the courier ID." >&2
            exit 1
        fi
        courier_password="$(read_password 'Courier password')"
        courier_confirmation="$(read_password 'Confirm courier password')"
        if [ "$courier_password" != "$courier_confirmation" ]; then
            printf '%s\n' "Passwords did not match." >&2
            exit 1
        fi
        ;;
    *)
        courier_id=""
        courier_password=""
        ;;
esac

allowed_origins="$(read_default 'Allowed browser origins (comma-separated)' 'http://localhost:*,http://127.0.0.1:*')"
case "$allowed_origins" in
    ""|*[!A-Za-z0-9:/.,*_-]*)
        printf '%s\n' "Origins may contain only letters, digits, colon, slash, period, comma, asterisk, underscore, and hyphen." >&2
        exit 1
        ;;
esac

temporary_env="$(mktemp "$ROOT/.env.XXXXXX")"
trap 'rm -f "$temporary_env"' EXIT
umask 077
cat >"$temporary_env" <<EOF
APP_PORT=$app_port
APP_BIND_ADDRESS=$app_bind_address
PARCEL_ADMIN_USERNAME=$admin_username
PARCEL_ADMIN_PASSWORD=$admin_password
PARCEL_BOOTSTRAP_COURIER_ID=$courier_id
PARCEL_BOOTSTRAP_PASSWORD=$courier_password
PARCEL_ALLOWED_ORIGINS=$allowed_origins
EOF
chmod 600 "$temporary_env"
mv "$temporary_env" "$ENV_FILE"
trap - EXIT

printf '%s\n' "Starting Parcel Tracker GPS. This may take a few minutes on the first run."
docker compose --project-directory "$ROOT" --env-file "$ENV_FILE" -f "$ROOT/compose.yaml" up -d --build
printf 'Installation complete. Open http://localhost:%s\n' "$app_port"
printf 'Configuration: %s/.env\n' "$ROOT"
printf '%s\n' "The application is available only on this machine by default. Configure HTTPS and allowed origins before exposing it publicly."
