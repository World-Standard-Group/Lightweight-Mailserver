#!/bin/bash
# ===========================================
# mailctl - Update Script
# git pull -> rebuild image -> restart containers
#
# Honours MAILCTL_DB_MODE from .env: the bundled postgres container is
# started only in local mode.
#
# USAGE:
#   bash scripts/update.sh              # update from current branch
#   bash scripts/update.sh develop      # update from a specific branch
# ===========================================

set -e

cd "$(dirname "$0")/.."

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   mailctl - Update                     ${NC}"
echo -e "${BLUE}=========================================${NC}"

if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed.${NC}"
    exit 1
fi

if ! command -v git &> /dev/null; then
    echo -e "${RED}Error: Git is not installed.${NC}"
    exit 1
fi

# ===========================================
# Database mode determines whether the local profile is used
# ===========================================
DB_MODE="local"
if [ -f .env ]; then
    DB_MODE=$(grep "^MAILCTL_DB_MODE=" .env | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr -d '\r' | xargs)
    DB_MODE="${DB_MODE:-local}"
fi

case "$(printf '%s' "$DB_MODE" | tr '[:upper:]' '[:lower:]')" in
    local)     COMPOSE="docker compose --profile local" ;;
    external)  COMPOSE="docker compose" ;;
    *)
        echo -e "${RED}Error: MAILCTL_DB_MODE must be 'local' or 'external' (got '$DB_MODE').${NC}"
        exit 1
        ;;
esac

echo -e "${BLUE}Database mode: ${DB_MODE}${NC}"

# ===========================================
# [1/4] Pull latest code
# ===========================================
echo -e "\n${YELLOW}[1/4] Pulling changes (branch: ${BRANCH})...${NC}"
git fetch origin "${BRANCH}" 2>/dev/null || echo -e "${YELLOW}No remote '${BRANCH}'; using local tree.${NC}"

LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/${BRANCH}" 2>/dev/null || echo "")

if [ -n "$REMOTE" ] && [ "$LOCAL" = "$REMOTE" ]; then
    echo -e "${GREEN}Already up to date.${NC}"
else
    git pull origin "${BRANCH}" 2>/dev/null || echo -e "${YELLOW}Pull skipped; building current tree.${NC}"
fi

# ===========================================
# [2/4] Rebuild mailctl image
#
# The jar lives inside the image, so the rebuild is the deploy step. Database
# and mail volumes are untouched, so mail state survives every update.
# ===========================================
echo -e "\n${YELLOW}[2/4] Rebuilding mailctl image...${NC}"
docker compose build mailctl

# ===========================================
# [3/4] Restart mailctl (the entrypoint re-applies migrations)
# ===========================================
echo -e "\n${YELLOW}[3/4] Restarting mailctl...${NC}"
$COMPOSE up -d mailctl

echo -e "${BLUE}Waiting for migrations to apply..."
sleep 8

# ===========================================
# [4/4] Verify
# ===========================================
echo -e "\n${YELLOW}[4/4] Verifying...${NC}"
if ! docker compose exec -T mailctl mailctl --version > /dev/null 2>&1; then
    echo -e "${RED}mailctl failed to start. Last logs:${NC}"
    docker compose logs --tail=40 mailctl
    exit 1
fi
docker compose exec -T mailctl mailctl --version
echo -e "${GREEN}mailctl is responding.${NC}"

if ! docker compose exec -T mailctl mailctl health > /dev/null 2>&1; then
    echo -e "${YELLOW}Note: the database check failed. Inspect with: docker compose logs mailctl${NC}"
fi

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Update complete                     ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Container status:${NC}"
$COMPOSE ps

echo -e "\n${BLUE}If you changed domains, users, aliases or DKIM keys,${NC}"
echo -e "${BLUE}re-apply the generated config:${NC}"
echo -e "  ${YELLOW}bash scripts/apply-config.sh${NC}"
