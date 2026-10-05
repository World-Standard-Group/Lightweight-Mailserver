# mailctl - Lightweight Mail Server Control Plane
## Deployment & Setup Guide

---

## Table of Contents
1. [System Requirements](#system-requirements)
2. [Architecture Overview](#architecture-overview)
3. [Quick Start](#quick-start)
4. [Docker Deployment](#docker-deployment)
5. [Manual Installation](#manual-installation)
6. [Configuration](#configuration)
7. [Database Setup](#database-setup)
8. [Service Integration](#service-integration)
9. [Management Workflow](#management-workflow)
10. [Startup Scripts](#startup-scripts)
11. [Backup & Restore](#backup--restore)
12. [Troubleshooting](#troubleshooting)

---

## System Requirements

### Target Server (Oracle Cloud ARM - VM.Standard.A1.Flex)
- **OS**: Ubuntu 24.04 LTS (Canonical)
- **Architecture**: aarch64
- **Resources**: 1 OCPU, 6 GB RAM
- **External PostgreSQL**: TLS-enabled connection string

### Software Dependencies
| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 25 LTS | Runtime for mailctl |
| PostgreSQL | 16+ | External database (TLS) |
| Postfix | 3.8+ | MTA / SMTP / Queue |
| Dovecot | 2.3+ | IMAP / LMTP / Auth |
| Rspamd | 3.7+ | Spam filtering / DKIM signing |
| Redis | 7+ | Rspamd backend |

---

## Architecture Overview

```
                         INTERNET
                            │
                 ��──────────��──────────��
                 │       Postfix       │  ← mailctl generates config
                 │ SMTP / MTA / Queue  │
                 └──────────��──────────��
                            │
                       ��────��────��
                       │ Rspamd  │  ← mailctl generates DKIM maps
                       └────��────��
                            │
                       ��────��────��
                       │ Maildir │  ← mailctl creates maildirs
                       └────��────��
                            │
                       ��────��────��
                       │ Dovecot │  ← mailctl generates SQL auth config
                       │  IMAP   │
                       └─────────��

                  ��──────────────────��
                  │   Java mailctl   │
                  │                  │
                  │ domain           │
                  │ user             │  → PostgreSQL (state)
                  │ alias            │
                  │ DKIM             │
                  │ config           │
                  │ health           │
                  │ backup           │
                  └────────��─────────��
                           │
                      PostgreSQL
```

**Key Principle**:
- Java owns **state and orchestration**
- Postfix/Dovecot/Rspamd own **mail protocols and filtering**.

---

## Quick Start

### 1. Deploy the Stack (Recommended)
```bash
git clone <repo> && cd lightweight-mailserver
cp .env.example .env
$EDITOR .env          # set MAIL_DOMAIN, MAIL_HOSTNAME, POSTGRES_PASSWORD
bash scripts/start.sh
```

### 2. Verify
```bash
docker compose exec mailctl mailctl --help
docker compose exec mailctl mailctl doctor run
```

### 3. Add a Domain and a User
```bash
docker compose exec mailctl mailctl domain add example.com
docker compose exec mailctl mailctl dkim generate example.com
docker compose exec -T mailctl mailctl user add alice@example.com --password 'secret'
bash scripts/apply-config.sh
```

### 4. Build Locally (no Docker)
```bash
# There is no Maven wrapper in this repo; use a system Maven.
mvn package
java -jar target/lw-mailserver-0.0.1-indev.jar --help
```

See [Docker Deployment](#docker-deployment-containerised) for the full guide.

---

## Docker Deployment (containerised)

Every component runs in Docker: PostgreSQL, Redis, Rspamd, Postfix, Dovecot
and mailctl. mailctl is a CLI, not a daemon, so its container idles and you
drive it with `docker compose exec`.

### One-command deploy

```bash
# first time
bash scripts/start.sh

# after a git pull
bash scripts/update.sh
```

`start.sh` creates `.env` if missing, creates the bind-mount directories,
pulls images, builds mailctl, starts the stack, waits for PostgreSQL to become
healthy and verifies the CLI responds.

`update.sh` pulls the branch, rebuilds the mailctl image and restarts just that
container. Migrations re-apply automatically on start. Data volumes are
untouched, so mail state survives every update.

### Running commands

```bash
docker compose exec mailctl mailctl --help
docker compose exec mailctl mailctl domain add example.com
docker compose exec mailctl mailctl dkim generate example.com
docker compose exec mailctl mailctl user add alice@example.com
docker compose exec mailctl mailctl alias add support@example.com alice@example.com
docker compose exec mailctl mailctl doctor run
```

Add `--` handling for prompts: `user add` and `user passwd` read the password
from stdin when `--password` is omitted, so pass it non-interactively in
automation:

```bash
docker compose exec -T mailctl mailctl user add alice@example.com --password 'secret'
```

### Applying generated configuration

mailctl renders Postfix, Dovecot and Rspamd configuration from the database into
`config/generated/`. The mail service containers mount `./config/postfix`,
`./config/dovecot` and `./config/rspamd`, so a helper copies the rendered files
into those mount points, runs `postmap`, and restarts the services:

```bash
bash scripts/apply-config.sh
```

Re-run this after any change to domains, users, aliases or DKIM keys.

### First-time setup, step by step

```bash
# 1. Environment
cp .env.example .env
$EDITOR .env          # set MAIL_DOMAIN, MAIL_HOSTNAME, POSTGRES_PASSWORD

# 2. TLS material (mailctl and Postfix both read these paths)
cp /path/to/fullchain.pem config/tls/mailcert.pem
cp /path/to/privkey.pem   config/tls/mailkey.pem

# 3. Bring everything up
bash scripts/start.sh

# 4. Add the domain, then read the DKIM DNS record it prints
docker compose exec mailctl mailctl domain add example.com
docker compose exec mailctl mailctl dkim generate example.com

# 5. Publish the DKIM TXT record, SPF and DMARC in DNS, then confirm
docker compose exec mailctl mailctl dkim show example.com --dns-format

# 6. Add a user and push the config to the mail services
docker compose exec -T mailctl mailctl user add alice@example.com --password 'secret'
bash scripts/apply-config.sh

# 7. Full diagnostic report
docker compose exec mailctl mailctl doctor run
```

### DNS and firewall

Required records for `MAIL_DOMAIN`:

| Type | Name | Value |
|------|------|-------|
| A    | `MAIL_HOSTNAME` | the server's public IP |
| MX   | `MAIL_DOMAIN`   | `MAIL_HOSTNAME` |
| TXT  | `MAIL_DOMAIN`   | `v=spf1 mx -all` |
| TXT  | `<selector>._domainkey` | value printed by `dkim generate` |
| TXT  | `_dmarc`        | `v=DMARC1; p=none; rua=mailto:postmaster@...` |

The reverse DNS (PTR) record for the server IP must resolve back to
`MAIL_HOSTNAME`. Most receivers reject mail without it.

Open only: 25 (SMTP), 587 (submission), 993 (IMAPS). Port 143 is plaintext IMAP
and 4190 is sieve; close 143 unless you specifically need it.

### Layout inside the mailctl container

| Path | Purpose | Mounted from host |
|------|---------|-------------------|
| `/app/mailctl.jar` | the shaded CLI jar | baked into the image |
| `/etc/mailctl` | config + `generated/` + `tls/` | `../config` |
| `/var/mail` | maildirs | `mail_data` volume |
| `/var/backups/mailctl` | backups | `../backups` |

### Configuration

`config.yaml` is optional. mailctl reads it when present, then applies
`MAILCTL_*` environment variables on top, so Compose can retarget the container
without editing a file. Supported variables:

| Variable | Meaning |
|----------|---------|
| `MAILCTL_CONFIG` | path to `config.yaml` |
| `MAILCTL_DB_URL` | JDBC URL |
| `MAILCTL_DB_USER` / `MAILCTL_DB_PASSWORD` | database credentials |
| `MAILCTL_MAIL_HOSTNAME` / `MAILCTL_MAIL_DOMAIN` | mail identity |
| `MAILCTL_MAIL_TLS_CERT` / `MAILCTL_MAIL_TLS_KEY` | TLS paths |
| `MAILCTL_MAIL_BASE` | maildir root |
| `MAILCTL_DKIM_DIR` | DKIM key output directory |

### Database: two routes

Pick one with `MAILCTL_DB_MODE` in `.env`. `start.sh` reads it and starts the
matching set of containers.

#### Route 1 - local (default)

```bash
MAILCTL_DB_MODE=local
```

PostgreSQL 18 runs as the `postgres` service with data in the `postgres_data`
volume. It sits behind the Compose `local` profile and has **no published
port** - there is no `ports:` mapping, only `expose:`, so only sibling
containers can reach it at `postgres:5432` on the internal network. No TLS is
used or claimed.

```bash
docker compose --profile local ps
```

Flyway applies `../src/main/resources/db/migration` on every mailctl start, so
schema changes ship with the image and apply on update.

#### Route 2 - external (TLS enforced)

```bash
MAILCTL_DB_MODE=external
MAILCTL_DB_HOST=db.example.com
MAILCTL_DB_PORT=5432
MAILCTL_DB_NAME=mailctl
MAILCTL_DB_USER=mailctl
MAILCTL_DB_PASSWORD=...
MAILCTL_DB_SSL_MODE=verify-full
MAILCTL_DB_SSL_ROOT_CERT=/etc/mailctl/keys/rootCA.crt
```

The bundled container is not started. The connection is built as:

```
jdbc:postgresql://HOST:5432/DB?sslmode=verify-full
    &sslrootcert=/etc/mailctl/keys/rootCA.crt
    &channelBinding=require
```

- `sslmode=verify-full` validates the server certificate against the supplied
  CA **and** checks the hostname matches. `require`/`allow`/`prefer` do not
  verify server identity.
- `channelBinding=require` binds the SCRAM exchange to the TLS channel, so a
  captured authentication cannot be replayed off-channel. This needs
  PostgreSQL 12+ with `password_encryption = scram-sha-256`.

Optional mTLS - add both to also present a client certificate:

```bash
MAILCTL_DB_SSL_CERT=/etc/mailctl/keys/client.crt
MAILCTL_DB_SSL_KEY=/etc/mailctl/keys/client.key
```

Place the CA (and optionally the client cert/key) in `../keys` on the host; it is
mounted read-only at `/etc/mailctl/keys`. PgJDBC refuses a group/world-readable
private key and this mount is `0444`, so the entrypoint stages a `0600` copy at
`/etc/mailctl/pki/client.key` and points the driver at that.

The server must present a certificate whose SAN matches `MAILCTL_DB_HOST`,
signed by the CA in `rootCA.crt`.

#### Enforcement is layered, not advisory

TLS cannot be silently downgraded. All three layers refuse a weaker setting:

| Layer | Refuses |
|-------|---------|
| `../scripts/start.sh` | non-`verify-full` sslmode, missing host, missing CA, missing host-side key files - before building anything |
| `../scripts/docker-entrypoint.sh` | the same checks at container start, before the JVM launches |
| `DbConnection` (Java) | any sslmode other than `verify-full`, and `localhost`/`postgres`/`127.0.0.1`/`::1` as an external host |

So even a bare `java -jar mailctl.jar` cannot end up downgraded, and a failed
connection reports the cause instead of falling back to plaintext.

Switch modes by editing `MAILCTL_DB_MODE` and re-running `bash scripts/start.sh`.

### Troubleshooting

```bash
# Container status and health
docker compose ps

# Logs
docker compose logs -f mailctl
docker compose logs -f postfix

# mailctl's own diagnostics
docker compose exec mailctl mailctl doctor run
docker compose exec mailctl mailctl health check

# Confirm the schema applied
docker compose exec postgres psql -U mailctl -d mailctl -c '\dt'

# Re-apply migrations after a schema change
docker compose restart mailctl
```

Common problems:

| Symptom | Cause and fix |
|---------|---------------|
| `database "mailctl" does not exist` | `POSTGRES_*` in `.env` changed after the volume was created. Either revert it or drop the volume: `docker compose down -v` (destroys all mail state). |
| `relation "domains" does not exist` | Migrations did not apply. Check `docker compose logs mailctl`, then `docker compose restart mailctl`. |
| `Service not initialized` | Old image. Run `bash scripts/update.sh`. |
| Postfix rejects config | Generated files not copied. Run `bash scripts/apply-config.sh` and check `docker compose logs postfix`. |
| DKIM not signing | Confirm the DKIM maps landed in `config/rspamd/` and that the DNS TXT record matches `dkim show --dns-format`. |
| Config directories are root-owned | A bind mount was created by Docker as root. Delete `../config` and re-run `bash scripts/start.sh`, which recreates it. |
| `external mode requires MAILCTL_DB_SSL_MODE=verify-full` | Intentional: weaker modes are refused. Set it to `verify-full` in `.env`, or use `MAILCTL_DB_MODE=local`. |
| `TLS material not found in keys/` | The container path in `.env` did not map back to `../keys` on the host. The value should be `/etc/mailctl/keys/<file>`. |
| `database not reachable` in external mode | Check `MAILCTL_DB_HOST`, that the server certificate's SAN matches it, that it is PostgreSQL 12+ with SCRAM, and `docker compose logs mailctl`. |

### Backup

```bash
# Database
docker compose exec postgres pg_dump -U mailctl mailctl > mailctl-$(date +%F).sql

# Restore
docker compose exec -T postgres psql -U mailctl -d mailctl < mailctl-2026-01-15.sql
```

DKIM private keys live in the database (`dkim_keys.private_key_pem`) and in
`../dkim`. Back up both. Losing them means re-generating DKIM and updating DNS.

---

## Manual Installation

### 1. Install System Packages (Ubuntu 24.04)
```bash
apt-get update && apt-get install -y \
    openjdk-25-jdk-headless \
    postfix \
    dovecot-core dovecot-imapd dovecot-lmtpd dovecot-pgsql \
    rspamd \
    redis-server \
    postgresql-client-18 \
    certbot \
    rsync \
    tar \
    gzip
```

### 2. Create System Users
```bash
# Mail system users
useradd -r -s /bin/false -d /var/mail -m mail
useradd -r -s /bin/false -d /var/lib/rspamd rspamd

# Set permissions
chown -R mail:mail /var/mail
chmod 700 /var/mail
```

### 3. Install mailctl
```bash
# Copy JAR to system location
cp target/lw-mailserver-0.0.1-indev.jar /usr/local/bin/mailctl.jar

# Create wrapper script
cat > /usr/local/bin/mailctl << 'EOF'
#!/bin/bash
exec java -jar /usr/local/bin/mailctl.jar "$@"
EOF
chmod +x /usr/local/bin/mailctl
```

### 4. Create Directory Structure
```bash
mkdir -p /etc/mailctl /etc/rspamd/dkim /var/backups/mailctl /var/mail
chown -R mail:mail /var/mail /var/backups/mailctl
chmod 700 /var/mail /var/backups/mailctl
```

---

## Configuration

### 1. mailctl Config (/etc/mailctl/config.yaml)
```yaml
database:
  url: "jdbc:postgresql://postgres:5432/mailctl?sslmode=require"
  user: "mailctl"
  password: "secure_password"

mail:
  hostname: "mail.example.com"
  domain: "example.com"
  postmaster: "postmaster@example.com"
  tlsCert: "/etc/ssl/certs/mailcert.pem"
  tlsKey: "/etc/ssl/private/mailkey.pem"

security:
  dkimKeyDirectory: "/etc/rspamd/dkim"
  dkimDefaultSelector: "mail"
  dkimDefaultKeySize: 2048
  dkimDefaultAlgorithm: "rsa2048"

paths:
  mailBase: "/var/mail"
  postfixConfigDir: "/etc/postfix"
  dovecotConfigDir: "/etc/dovecot"
  rspamdConfigDir: "/etc/rspamd"
  backupDir: "/var/backups/mailctl"

services:
  postfix:
    name: "postfix"
    enabled: true
  dovecot:
    name: "dovecot"
    enabled: true
  rspamd:
    name: "rspamd"
    enabled: true
  postgresql:
    name: "postgresql"
    enabled: true
```

### 2. Generate Configuration
```bash
mailctl config generate --output-dir /tmp/mailctl-config
```

### 3. Validate Configuration
```bash
mailctl config validate --config-dir /tmp/mailctl-config
```

### 4. Deploy Configuration
```bash
mailctl config deploy --config-dir /tmp/mailctl-config
```

---

## Database Setup

### 1. External PostgreSQL (Production)
```bash
# Create database and user
psql "postgresql://admin:pass@db-host:5432/postgres?sslmode=require" << 'EOF'
CREATE DATABASE mailctl;
CREATE USER mailctl WITH ENCRYPTED PASSWORD 'secure_password';
GRANT ALL PRIVILEGES ON DATABASE mailctl TO mailctl;
\c mailctl
GRANT ALL ON SCHEMA public TO mailctl;
EOF
```

### 2. Run Migrations (Auto on Startup)
```bash
# Migrations run automatically on first mailctl command
mailctl domain list
```

### 3. Manual Migration
```bash
# Using Flyway directly
flyway -url="jdbc:postgresql://host:5432/mailctl?sslmode=require" \
       -user=mailctl -password=secure_password \
       -locations=filesystem:./db/migration \
       migrate
```

### 4. Schema Overview
```sql
-- Domains
domains (id, name, created_at, updated_at, status, dkim_selector, dkim_key_size, dkim_algorithm)

-- Users
users (id, domain_id, email, password_hash, mailbox_path, quota, status, created_at, updated_at, last_login_at)

-- Aliases
aliases (id, domain_id, alias, targets, created_at, updated_at)

-- DKIM Keys
dkim_keys (id, domain_id, selector, algorithm, key_size, private_key_pem, public_key_pem, dns_record, created_at, expires_at, status)
```

---

## Service Integration

### Postfix (main.cf key settings)
```bash
# mailctl generates these automatically
myhostname = mail.example.com
mydomain = example.com
virtual_mailbox_base = /var/mail
virtual_alias_maps = hash:/etc/postfix/virtual_alias_maps
virtual_mailbox_maps = hash:/etc/postfix/virtual_mailbox_maps
virtual_mailbox_domains = hash:/etc/postfix/virtual_mailbox_domains

# TLS
smtpd_tls_cert_file = /etc/ssl/certs/mailcert.pem
smtpd_tls_key_file = /etc/ssl/private/mailkey.pem
smtpd_tls_security_level = may

# SASL (Dovecot)
smtpd_sasl_auth_enable = yes
smtpd_sasl_type = dovecot
smtpd_sasl_path = private/auth

# Rspamd milter
milter_protocol = 6
milter_default_action = accept
smtpd_milters = inet:localhost:11332
non_smtpd_milters = inet:localhost:11332
```

### Dovecot (dovecot.conf key settings)
```bash
# mailctl generates these automatically
mail_location = maildir:/var/mail/%d/%n
mail_uid = mail
mail_gid = mail

# SQL Auth
passdb {
  driver = sql
  args = /etc/dovecot/dovecot-sql.conf.ext
}
userdb {
  driver = sql
  args = /etc/dovecot/dovecot-sql.conf.ext
}

# LMTP for Postfix
service lmtp {
  unix_listener /var/spool/postfix/private/dovecot-lmtp {
    user = postfix
    group = postfix
    mode = 0660
  }
}

# Auth for Postfix
service auth {
  unix_listener /var/spool/postfix/private/auth {
    user = postfix
    group = postfix
    mode = 0660
  }
}
```

### Rspamd (dkim_signing.conf)
```bash
# mailctl generates these automatically
dkim_signing {
  enabled = true;
  selector_map = "/etc/rspamd/dkim_selectors.map";
  domain_map = "/etc/rspamd/dkim_domains.map";
  key_map = "/etc/rspamd/dkim_keys.map";
  arc_signing = true;
}
```

---

## Management Workflow

### Daily Operations

#### 1. Add Domain
```bash
mailctl domain add example.com --dkim-selector mail --dkim-key-size 2048
mailctl dkim generate example.com
mailctl config deploy
```

#### 2. Add User
```bash
mailctl user add alice@example.com --quota 2G
# Enter password when prompted
```

#### 3. Add Alias
```bash
mailctl alias add support@example.com alice@example.com
mailctl alias add sales@example.com alice@example.com,bob@example.com
```

#### 4. Health Check
```bash
mailctl doctor run
mailctl health check
```

#### 4. Service Management
```bash
mailctl service status
mailctl service reload postfix
mailctl service reload dovecot
mailctl service reload rspamd
```

---

## Startup Scripts

### Systemd Service: mailctl-init.service
```ini
[Unit]
Description=mailctl Configuration Deployment
After=network.target postgresql.service
Requires=postgresql.service
Before=postfix.service dovecot.service rspamd.service

[Service]
Type=oneshot
ExecStart=/usr/local/bin/mailctl-config-deploy.sh
RemainAfterExit=yes
StandardOutput=journal

[Install]
WantedBy=multi-user.target
```

### Deploy Script: /usr/local/bin/mailctl-config-deploy.sh
```bash
#!/bin/bash
set -euo pipefail

CONFIG_FILE="/etc/mailctl/config.yaml"
TEMP_DIR="/tmp/mailctl-config-$(date +%s)"
LOG_FILE="/var/log/mailctl-deploy.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

log "Starting mailctl configuration deployment..."

# Check config exists
if [[ ! -f "$CONFIG_FILE" ]]; then
    log "ERROR: Config file not found: $CONFIG_FILE"
    exit 1
fi

# Generate configuration
log "Generating configuration to $TEMP_DIR..."
mkdir -p "$TEMP_DIR"
if ! mailctl --config "$CONFIG_FILE" config generate --output-dir "$TEMP_DIR" >> "$LOG_FILE" 2>&1; then
    log "ERROR: Configuration generation failed"
    exit 1
fi

# Validate configuration
log "Validating configuration..."
if ! mailctl --config "$CONFIG_FILE" config validate --config-dir "$TEMP_DIR" >> "$LOG_FILE" 2>&1; then
    log "ERROR: Configuration validation failed"
    exit 1
fi

# Deploy configuration
log "Deploying configuration..."
if ! mailctl --config "$CONFIG_FILE" config deploy --config-dir "$TEMP_DIR" >> "$LOG_FILE" 2>&1; then
    log "ERROR: Configuration deployment failed"
    exit 1
fi

# Reload services
log "Reloading services..."
mailctl --config "$CONFIG_FILE" service reload all >> "$LOG_FILE" 2>&1

# Cleanup
rm -rf "$TEMP_DIR"

log "Configuration deployment completed successfully"

# Run health check
log "Running health check..."
mailctl --config "$CONFIG_FILE" health check >> "$LOG_FILE" 2>&1 || true

log "Deployment finished"
```

### Management Script: /usr/local/bin/mailctl-manage
```bash
#!/bin/bash
# mailctl management wrapper with common operations

set -euo pipefail

CONFIG_FILE="/etc/mailctl/config.yaml"
MAILCTL="mailctl --config $CONFIG_FILE"

usage() {
    cat << 'EOF'
mailctl-manage - Mail server management wrapper

USAGE:
    mailctl-manage <command> [args...]

COMMANDS:
    setup-domain <domain>       Add domain, generate DKIM, deploy config
    add-user <email> [quota]    Add mailbox user
    add-alias <alias> <targets> Add email alias
    rotate-dkim <domain>        Rotate DKIM keys
    deploy                      Generate, validate, deploy config
    health                      Run full health check
    backup [name]               Create backup
    restore <file>              Restore from backup
    logs                        Show recent logs
    status                      Show service status
    reload [service]            Reload service (default: all)

EXAMPLES:
    mailctl-manage setup-domain example.com
    mailctl-manage add-user alice@example.com 2G
    mailctl-manage add-alias support@example.com alice@example.com
    mailctl-manage deploy
    mailctl-manage health
EOF
}

cmd_setup_domain() {
    local domain="$1"
    local selector="${2:-mail}"
    local key_size="${3:-2048}"
    
    log "Setting up domain: $domain"
    $MAILCTL domain add "$domain" --dkim-selector "$selector" --dkim-key-size "$key_size"
    $MAILCTL dkim generate "$domain" --selector "$selector"
    $MAILCTL config deploy
    log "Domain $domain configured successfully"
}

cmd_add_user() {
    local email="$1"
    local quota="${2:-1G}"
    
    log "Adding user: $email with quota $quota"
    $MAILCTL user add "$email" --quota "$quota"
    $MAILCTL config deploy
}

cmd_add_alias() {
    local alias="$1"
    local targets="$2"
    
    log "Adding alias: $alias -> $targets"
    $MAILCTL alias add "$alias" "$targets"
    $MAILCTL config deploy
}

cmd_rotate_dkim() {
    local domain="$1"
    local selector="${2:-mail}"
    
    log "Rotating DKIM key for $domain:$selector"
    $MAILCTL dkim rotate "$domain" --selector "$selector"
    $MAILCTL config deploy
    log "DKIM rotated. Update DNS with new record:"
    $MAILCTL dkim show "$domain" --selector "$selector" --dns-format
}

cmd_deploy() {
    log "Deploying configuration..."
    $MAILCTL config generate --output-dir /tmp/mailctl-config
    $MAILCTL config validate --config-dir /tmp/mailctl-config
    $MAILCTL config deploy --config-dir /tmp/mailctl-config
    $MAILCTL service reload all
    log "Deployment complete"
}

cmd_health() {
    $MAILCTL doctor run
}

cmd_backup() {
    local name="${1:-backup-$(date +%Y%m%d-%H%M%S)}"
    $MAILCTL backup create --output "/var/backups/mailctl/${name}.tar.gz"
}

cmd_restore() {
    local file="$1"
    $MAILCTL backup restore "$file" --force
}

cmd_logs() {
    journalctl -u mailctl-init -u postfix -u dovecot -u rspamd -n 100 --no-pager
}

cmd_status() {
    $MAILCTL service status
}

cmd_reload() {
    local service="${1:-all}"
    $MAILCTL service reload "$service"
}

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

main() {
    if [[ $# -eq 0 ]]; then
        usage
        exit 1
    fi

    case "$1" in
        setup-domain) cmd_setup_domain "${@:2}" ;;
        add-user) cmd_add_user "${@:2}" ;;
        add-alias) cmd_add_alias "${@:2}" ;;
        rotate-dkim) cmd_rotate_dkim "${@:2}" ;;
        deploy) cmd_deploy ;;
        health) cmd_health ;;
        backup) cmd_backup "${@:2}" ;;
        restore) cmd_restore "${@:2}" ;;
        logs) cmd_logs ;;
        status) cmd_status ;;
        reload) cmd_reload "${@:2}" ;;
        *) usage; exit 1 ;;
    esac
}

main "$@"
```

### Enable Services
```bash
# Install systemd services
cp mailctl-init.service /etc/systemd/system/
cp mailctl-config-deploy.sh /usr/local/bin/
cp mailctl-manage /usr/local/bin/

chmod +x /usr/local/bin/mailctl-config-deploy.sh
chmod +x /usr/local/bin/mailctl-manage

# Enable and start
systemctl daemon-reload
systemctl enable mailctl-init
systemctl start mailctl-init
systemctl enable postfix dovecot rspamd redis postgresql
systemctl start postfix dovecot rspamd redis postgresql
```

---

## Backup & Restore

### Create Backup
```bash
# Full backup (database + config + DKIM keys)
mailctl backup create --output /var/backups/mailctl/backup-$(date +%Y%m%d-%H%M%S).tar.gz

# Selective backup
mailctl backup create --include database,config --output /var/backups/mailctl/config-backup.tar.gz
```

### List Backups
```bash
mailctl backup list --dir /var/backups/mailctl
```

### Restore Backup
```bash
# Full restore
mailctl backup restore /var/backups/mailctl/backup-20240115-030000.tar.gz --force

# Selective restore
mailctl backup restore /var/backups/mailctl/backup.tar.gz --include database
```

### Automated Backup (cron)
```bash
# /etc/cron.d/mailctl-backup
0 3 * * * root /usr/local/bin/mailctl-manage backup > /var/log/mailctl-backup.log 2>&1
0 4 * * 0 root find /var/backups/mailctl -name "*.tar.gz" -mtime +30 -delete
```

---

## Troubleshooting

### Common Issues

#### 1. Database Connection Failed
```bash
# Check PostgreSQL connectivity
psql "jdbc:postgresql://host:5432/mailctl?sslmode=require" -U mailctl -c "SELECT 1"

# Check config
cat /etc/mailctl/config.yaml | grep -A5 database
```

#### 2. Postfix Configuration Error
```bash
# Test config
postfix -c /etc/postfix check

# Check logs
journalctl -u postfix -n 50
```

#### 3. Dovecot Auth Failed
```bash
# Test SQL config
dovecot -c /etc/dovecot/dovecot.conf -n

# Check auth
doveadm auth test alice@example.com
```

#### 4. Rspamd DKIM Signing Not Working
```bash
# Test config
rspamadm configtest

# Check DKIM maps
cat /etc/rspamd/dkim_keys.map
cat /etc/rspamd/dkim_selectors.map
```

#### 5. DKIM Key Permissions
```bash
# Fix permissions
chown -R rspamd:rspamd /etc/rspamd/dkim
chmod 600 /etc/rspamd/dkim/*.private
chmod 644 /etc/rspamd/dkim/*.public
```

### Diagnostic Commands
```bash
# Full health check
mailctl doctor run

# Service status
mailctl service status

# Check queue
postqueue -p

# Check mailboxes
ls -la /var/mail/example.com/

# Test SMTP
swaks --to alice@example.com --from test@external.com --server localhost:25

# Test IMAP
openssl s_client -connect localhost:993 -crlf
```

### Log Locations
| Service | Log Location |
|---------|--------------|
| mailctl | /var/log/mailctl/mailctl.log |
| Postfix | journalctl -u postfix |
| Dovecot | journalctl -u dovecot |
| Rspamd | journalctl -u rspamd |
| PostgreSQL | journalctl -u postgresql |

---

## Security Checklist

- [ ] PostgreSQL TLS enabled with valid certificates
- [ ] Postfix submission (587) requires AUTH
- [ ] IMAP (993) requires TLS
- [ ] DKIM keys stored with 600 permissions
- [ ] mailctl config.yaml readable only by root
- [ ] Firewall: only 25, 587, 993, 4190 open externally
- [ ] Regular backup schedule configured
- [ ] DKIM rotation scheduled (annual)
- [ ] TLS certificates auto-renewed (certbot)

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 0.0.1-indev | 2026-08-14 | Initial implementation |

---

## Support

For issues:
1. Run `mailctl doctor run` for diagnostics
2. Check service logs with `journalctl -u <service>`
3. Verify configuration with `mailctl config validate`