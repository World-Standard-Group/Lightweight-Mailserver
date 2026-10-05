#!/bin/bash
# ===========================================
# mailctl - Apply generated configuration
#
# mailctl renders Postfix/Dovecot/Rspamd config into
# config/generated/. The mail service containers mount ./config/postfix,
# ./config/dovecot and ./config/rspamd, so this script copies the rendered
# files into those mount points and restarts the affected containers.
#
# USAGE:
#   bash scripts/apply-config.sh
# ===========================================

set -e

cd "$(dirname "$0")/.."

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   mailctl - Apply Config               ${NC}"
echo -e "${BLUE}=========================================${NC}"

if [ ! -d config/generated ]; then
    echo -e "${RED}Error: config/generated not found.${NC}"
    echo -e "Generate it first:"
    echo -e "  ${YELLOW}docker compose exec mailctl mailctl config generate --output-dir /etc/mailctl/generated${NC}"
    exit 1
fi

echo -e "\n${YELLOW}[1/3] Generating configuration...${NC}"
docker compose exec -T mailctl mailctl \
    config generate --output-dir /etc/mailctl/generated

echo -e "\n${YELLOW}[2/3] Copying into service mount points...${NC}"
if [ -d config/generated/postfix ]; then
    cp -v config/generated/postfix/* config/postfix/ 2>/dev/null || true
fi
if [ -d config/generated/dovecot ]; then
    cp -v config/generated/dovecot/* config/dovecot/ 2>/dev/null || true
fi
if [ -d config/generated/rspamd ]; then
    cp -v config/generated/rspamd/* config/rspamd/ 2>/dev/null || true
fi
if [ -d config/generated/dkim ]; then
    cp -v config/generated/dkim/* dkim/ 2>/dev/null || true
fi

# postmap compiles the hash: map files that main.cf references.
# Run it inside the postfix container, which has the postfix binaries.
echo -e "${BLUE}Compiling Postfix maps...${NC}"
docker compose exec -T postfix postmap /etc/postfix/virtual_alias_maps 2>/dev/null || \
    echo -e "${YELLOW}postmap skipped (map files may not exist yet).${NC}"
docker compose exec -T postfix postmap /etc/postfix/virtual_mailbox_maps 2>/dev/null || true
docker compose exec -T postfix postmap /etc/postfix/virtual_mailbox_domains 2>/dev/null || true
docker compose exec -T postfix postmap /etc/postfix/sender_login_maps 2>/dev/null || true

# Rspamd signing keys come from mailctl, NOT from rspamadm.
#
# The private key Rspamd signs with MUST be the counterpart of the public key in
# the DNS record mailctl already published. Generating a fresh pair here would
# publish one key in DNS and sign with another, and every message would fail DKIM
# alignment. mailctl writes the PEM from the database to config/generated/dkim/,
# and apply-config.sh copies it below.
echo -e "${BLUE}Rspamd DKIM keys come from mailctl (config/generated/dkim).${NC}"

echo -e "\n${YELLOW}[3/3] Restarting mail services...${NC}"
docker compose restart postfix dovecot rspamd

sleep 5

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Config applied                      ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Check service status:${NC}"
docker compose ps
echo -e "\n${BLUE}Run the doctor for a full report:${NC}"
echo -e "  ${YELLOW}docker compose exec mailctl mailctl doctor run${NC}"
