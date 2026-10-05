#!/bin/bash
# ===========================================
# mailctl - Quick Start Script
# One-command first-time setup for the containerised mail server
#
# Database mode is read from MAILCTL_DB_MODE in .env:
#   local     -> bundled postgres, no TLS, unpublished port
#   external  -> remote postgres, sslmode=verify-full + channelBinding=require
#
# USAGE:
#   bash scripts/start.sh
# ===========================================

set -e

cd "$(dirname "$0")/.."

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   mailctl - Quick Start                ${NC}"
echo -e "${BLUE}=========================================${NC}"

# ===========================================
# Prerequisites
# ===========================================
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed.${NC}"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo -e "${RED}Error: Docker Compose v2 is not available.${NC}"
    exit 1
fi

# ===========================================
# .env
# ===========================================
if [ ! -f .env ]; then
    echo -e "\n${YELLOW}Creating .env from .env.example...${NC}"
    cp .env.example .env
    echo -e "${YELLOW}Edit .env: set MAIL_DB_MODE, MAIL_DOMAIN and POSTGRES_PASSWORD.${NC}"
    echo -e "${YELLOW}Then run: bash scripts/start.sh${NC}"
    exit 0
fi

if grep -q "changeme_secure_password" .env; then
    echo -e "${RED}Error: change POSTGRES_PASSWORD in .env before starting.${NC}"
    exit 1
fi

# ===========================================
# Database mode
# ===========================================
DB_MODE=$(grep "^MAILCTL_DB_MODE=" .env | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr -d '\r' | xargs)
DB_MODE="${DB_MODE:-local}"
DB_MODE_LC=$(printf '%s' "$DB_MODE" | tr '[:upper:]' '[:lower:]')

case "$DB_MODE_LC" in
    local)
        DB_MODE_DESC="LOCAL (bundled postgres container, no TLS, port not published)"
        COMPOSE="docker compose --profile local"
        ;;
    external)
        DB_MODE_DESC="EXTERNAL (verify-full + channelBinding=require)"
        COMPOSE="docker compose"
        ;;
    *)
        echo -e "${RED}Error: MAILCTL_DB_MODE must be 'local' or 'external' (got '$DB_MODE').${NC}"
        exit 1
        ;;
esac

echo -e "\n${BLUE}Database mode: ${DB_MODE_DESC}${NC}"

# ===========================================
# External mode validation (fail before building anything)
# ===========================================
if [ "$DB_MODE_LC" = "external" ]; then
    SSL_MODE=$(grep "^MAILCTL_DB_SSL_MODE=" .env | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr -d '\r' | xargs)
    DB_HOST=$(grep "^MAILCTL_DB_HOST=" .env | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr -d '\r' | xargs)
    ROOT_CERT=$(grep "^MAILCTL_DB_SSL_ROOT_CERT=" .env | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr -d '\r' | xargs)

    if [ "$SSL_MODE" != "verify-full" ]; then
        echo -e "${RED}Error: external mode requires MAILCTL_DB_SSL_MODE=verify-full${NC}"
        echo -e "${RED}       (got '${SSL_MODE:-unset}'). Weaker modes do not verify the${NC}"
        echo -e "${RED}       server identity and are refused.${NC}"
        exit 1
    fi

    if [ -z "$DB_HOST" ] || [ "$DB_HOST" = "localhost" ] || [ "$DB_HOST" = "postgres" ]; then
        echo -e "${RED}Error: set MAILCTL_DB_HOST to your external database host.${NC}"
        exit 1
    fi

    if [ -z "$ROOT_CERT" ]; then
        echo -e "${RED}Error: external mode requires MAILCTL_DB_SSL_ROOT_CERT.${NC}"
        exit 1
    fi

    # The container mounts ./keys at /etc/mailctl/keys, so map the path back.
    missing=""
    for entry in MAILCTL_DB_SSL_ROOT_CERT MAILCTL_DB_SSL_CERT MAILCTL_DB_SSL_KEY; do
        path=$(grep "^${entry}=" .env | cut -d'=' -f2- | tr -d '"' | tr -d "'" | tr -d '\r' | xargs)
        [ -z "$path" ] && continue
        host_path="${path/#\/etc\/mailctl\/keys/keys}"
        [ -f "$host_path" ] || missing="$missing $host_path ($entry)"
    done
    if [ -n "$missing" ]; then
        echo -e "${RED}Error: TLS material not found in keys/:${NC}"
        for m in $missing; do echo -e "  ${RED}- ${m}${NC}"; done
        echo -e "${YELLOW}See keys/README.md for how to generate the material.${NC}"
        exit 1
    fi
    echo -e "${GREEN}TLS material present - the bundled postgres will be skipped.${NC}"
fi

# ===========================================
# Directories that Compose bind-mounts
#
# Compose creates missing bind-mount paths as root-owned directories, which then
# break the services that need to write there. Create them up front.
# ===========================================
echo -e "\n${YELLOW}Preparing directories...${NC}"
mkdir -p config/postfix config/dovecot config/rspamd \
         config/generated/postfix config/generated/dovecot \
         config/generated/rspamd config/generated/dkim config/tls \
         dkim backups keys
echo -e "${GREEN}Directories ready.${NC}"

# ===========================================
# [1/4] Pull images
# ===========================================
echo -e "\n${YELLOW}[1/4] Pulling Docker images...${NC}"
if [ "$DB_MODE_LC" = "local" ]; then
    $COMPOSE pull postgres redis rspamd postfix dovecot
else
    echo -e "${BLUE}External DB mode - skipping the postgres image.${NC}"
    docker compose pull redis rspamd postfix dovecot
fi

# ===========================================
# [2/4] Build mailctl
# ===========================================
echo -e "\n${YELLOW}[2/4] Building mailctl image...${NC}"
docker compose build mailctl

# ===========================================
# [3/4] Start everything
# ===========================================
echo -e "\n${YELLOW}[3/4] Starting services...${NC}"
$COMPOSE up -d

if [ "$DB_MODE_LC" = "local" ]; then
    echo -e "${BLUE}Waiting for PostgreSQL to become healthy..."
    status="starting"
    for i in $(seq 1 30); do
        status=$(docker inspect --format='{{.State.Health.Status}}' mailctl-postgres 2>/dev/null || echo "starting")
        [ "$status" = "healthy" ] && break
        sleep 2
    done

    if [ "$status" != "healthy" ]; then
        echo -e "${RED}PostgreSQL did not become healthy. Last logs:${NC}"
        $COMPOSE logs --tail=30 postgres
        exit 1
    fi
    echo -e "${GREEN}PostgreSQL healthy.${NC}"
else
    echo -e "${BLUE}External DB mode - mailctl connects to ${DB_HOST} over TLS.${NC}"
fi

# Migrations are applied by the container entrypoint on start.
sleep 4

# ===========================================
# [4/4] Verify
# ===========================================
echo -e "\n${YELLOW}[4/4] Verifying mailctl...${NC}"
docker compose exec -T mailctl mailctl --version

if ! docker compose exec -T mailctl mailctl health > /dev/null 2>&1; then
    echo -e "${RED}Database check failed. mailctl logs:${NC}"
    docker compose logs --tail=30 mailctl
    exit 1
fi

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Setup complete                      ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Container status:${NC}"
$COMPOSE ps

cat <<'EOF'

Next steps:
  1. Add your mail domain:
       docker compose exec mailctl mailctl domain add example.com

  2. Generate DKIM and note the DNS record it prints:
       docker compose exec mailctl mailctl dkim generate example.com

  3. Add a mailbox user (password inline for non-interactive use):
       docker compose exec -T mailctl mailctl user add alice@example.com --password 'secret'

  4. Push the generated configuration to the mail services:
       bash scripts/apply-config.sh

  5. Full diagnostic report:
       docker compose exec mailctl mailctl doctor run

Diagnostics:
  health      docker compose exec mailctl mailctl health check
  logs        docker compose logs -f mailctl
  status      docker compose ps
  stop        docker compose down
  update      bash scripts/update.sh

EOF
