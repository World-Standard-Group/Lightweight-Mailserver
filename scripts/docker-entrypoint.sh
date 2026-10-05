#!/bin/bash
# ===========================================
# mailctl - Docker entrypoint
#
# Two database modes:
#   local     - bundled postgres container, no TLS, unpublished port
#   external  - remote postgres over sslmode=verify-full + channelBinding=require
#
# Applies Flyway migrations on start, then runs CMD (Compose sets
# `sleep infinity` so you can `docker compose exec mailctl mailctl ...`).
# ===========================================

set -e

echo "========================================="
echo "   mailctl - starting                  "
echo "========================================="

# Strip Windows carriage returns from .env values edited on Windows
for var in MAILCTL_DB_MODE MAILCTL_DB_HOST MAILCTL_DB_PORT MAILCTL_DB_NAME \
           MAILCTL_DB_USER MAILCTL_DB_PASSWORD MAILCTL_DB_URL MAILCTL_DB_SSL_MODE \
           MAILCTL_DB_SSL_ROOT_CERT MAILCTL_DB_SSL_CERT MAILCTL_DB_SSL_KEY \
           MAILCTL_MAIL_HOSTNAME MAILCTL_MAIL_DOMAIN MAILCTL_MAIL_BASE \
           JAVA_OPTS; do
    value="$(printenv "$var" 2>/dev/null || true)"
    if [ -n "$value" ]; then
        clean="$(printf '%s' "$value" | tr -d '\r' | tr -d '"')"
        export "$var"="$clean"
    fi
done

DB_MODE="${MAILCTL_DB_MODE:-local}"

# ===========================================
# Mode validation
# ===========================================
case "$DB_MODE" in
    local)
        echo "Database mode: LOCAL (bundled postgres, no TLS, port not published)"
        # A local connection must not claim TLS.
        if [ -n "${MAILCTL_DB_SSL_MODE:-}" ] && [ "$MAILCTL_DB_SSL_MODE" != "disable" ]; then
            echo "ERROR: local mode does not use TLS, but MAILCTL_DB_SSL_MODE=${MAILCTL_DB_SSL_MODE}."
            echo "       Set MAILCTL_DB_MODE=external to use a TLS connection."
            exit 1
        fi
        export MAILCTL_DB_SSL_MODE=disable
        ;;

    external)
        echo "Database mode: EXTERNAL (sslmode=verify-full, channelBinding=require)"

        # TLS is not optional in this mode and cannot be weakened.
        MODE_LC="$(printf '%s' "${MAILCTL_DB_SSL_MODE:-}" | tr '[:upper:]' '[:lower:]')"
        if [ "$MODE_LC" != "verify-full" ]; then
            echo "ERROR: external mode requires MAILCTL_DB_SSL_MODE=verify-full"
            echo "       (got '${MAILCTL_DB_SSL_MODE:-unset}'). Weaker modes do not"
            echo "       verify the server identity and are refused."
            exit 1
        fi

        if [ -z "${MAILCTL_DB_HOST:-}" ] || [ "$MAILCTL_DB_HOST" = "localhost" ] || [ "$MAILCTL_DB_HOST" = "postgres" ]; then
            echo "ERROR: set MAILCTL_DB_HOST to your external database host"
            echo "       (got '${MAILCTL_DB_HOST:-unset}')."
            exit 1
        fi

        if [ -z "${MAILCTL_DB_SSL_ROOT_CERT:-}" ]; then
            echo "ERROR: external mode requires MAILCTL_DB_SSL_ROOT_CERT"
            echo "       (the CA that signed the server certificate)."
            exit 1
        fi

        # Map container paths back to the host so a missing file is reported
        # before the JVM starts.
        missing=""
        for var in MAILCTL_DB_SSL_ROOT_CERT MAILCTL_DB_SSL_CERT MAILCTL_DB_SSL_KEY; do
            path="${!var:-}"
            [ -z "$path" ] && continue
            host_path="${path/#\/etc\/mailctl\/pki/keys}"
            [ -f "$host_path" ] || missing="$missing $host_path ($var)"
        done
        if [ -n "$missing" ]; then
            echo "ERROR: TLS material not found on the host:"
            for m in $missing; do echo "  - $m"; done
            echo "See keys/README.md for how to generate and place the material."
            exit 1
        fi
        echo "TLS material present - the postgres container will be skipped."
        ;;

    *)
        echo "ERROR: MAILCTL_DB_MODE must be 'local' or 'external' (got '$DB_MODE')."
        exit 1
        ;;
esac

# ===========================================
# Client key permissions
#
# PgJDBC's PEM loader refuses a private key that is group/world readable. The
# keys volume is mounted read-only (0444), so stage a 0600 copy in the
# container-owned PKI directory and point the driver at that.
# ===========================================
PKI=/etc/mailctl/pki
mkdir -p "$PKI"

if [ -n "${MAILCTL_DB_SSL_KEY:-}" ]; then
    if cp "$MAILCTL_DB_SSL_KEY" "$PKI/client.key" 2>/dev/null; then
        chmod 600 "$PKI/client.key"
        export MAILCTL_DB_SSL_KEY="$PKI/client.key"
        echo "Staged client key at $MAILCTL_DB_SSL_KEY (mode 0600)"
    else
        echo "ERROR: could not stage client key from $MAILCTL_DB_SSL_KEY"
        exit 1
    fi
fi

# Verify-full also needs the CA readable by the JVM.
if [ -n "${MAILCTL_DB_SSL_ROOT_CERT:-}" ] && [ ! -r "$MAILCTL_DB_SSL_ROOT_CERT" ]; then
    echo "ERROR: CA certificate not readable: $MAILCTL_DB_SSL_ROOT_CERT"
    exit 1
fi

echo "JAVA_OPTS: ${JAVA_OPTS}"
echo "Config:    ${MAILCTL_CONFIG:-/etc/mailctl/config.yaml}"

# ===========================================
# Migrations
#
# The target differs by mode: local waits on the bundled container, external
# waits on the remote host. Either way the loop is bounded so a bad host or bad
# credentials surface as a clear message instead of an idle container.
# ===========================================
echo "Applying database migrations..."
attempt=1
max_attempts=30
until java ${JAVA_OPTS} -jar /app/mailctl.jar health > /dev/null 2>&1; do
    if [ "$attempt" -ge "$max_attempts" ]; then
        echo "ERROR: database not reachable after ${max_attempts} attempts (mode: ${DB_MODE})."
        if [ "$DB_MODE" = "external" ]; then
            echo "Check MAILCTL_DB_HOST, the CA bundle, and that the server"
            echo "supports channelBinding=require (PostgreSQL 12+ with SCRAM)."
        else
            echo "Check: docker compose logs postgres"
        fi
        echo "The container will idle so you can inspect it. Re-run migrations with:"
        echo "  docker compose exec mailctl mailctl health"
        break
    fi
    echo "  database not ready (attempt ${attempt}/${max_attempts}), retrying in 2s..."
    attempt=$((attempt + 1))
    sleep 2
done

echo "========================================="
echo "   mailctl - ready                     "
echo "========================================="
echo "Run commands with:"
echo "  docker compose exec mailctl mailctl --help"
echo "  docker compose exec mailctl mailctl domain list"

exec "$@"
