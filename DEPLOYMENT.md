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

### 1. Build the Project
```bash
cd /path/to/lightweight-mailserver
./mvnw package
# or with system Maven
mvn package
```

### 2. Verify Build
```bash
java -jar target/lw-mailserver-0.0.1-indev.jar --help
```

### 3. Run with Docker (Recommended)
```bash
docker-compose up -d
```

---

## Docker Deployment

### docker-compose.yml
```yaml
version: '3.8'

services:
  # PostgreSQL (external in production, but included for dev)
  postgres:
    image: postgres:18-trixie
    container_name: mailctl-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DATABASE:-changeme}
      POSTGRES_USER: ${POSTGRES_USERNAME:-changeme}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}
    volumes:
      - postgres_data:/var/lib/postgresql
      - ./init-db:/docker-entrypoint-initdb.d
    networks:
      - mailnet
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U mailctl -d mailctl"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis for Rspamd
  redis:
    image: redis:trixie
    container_name: mailctl-redis
    volumes:
      - redis_data:/data
    networks:
      - mailnet
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Rspamd
  rspamd:
    image: rspamd/rspamd:latest
    container_name: mailctl-rspamd
    depends_on:
      redis:
        condition: service_healthy
    volumes:
      - ./config/rspamd:/etc/rspamd/local.d
      - ./dkim:/etc/rspamd/dkim
      - rspamd_data:/var/lib/rspamd
    ports:
      - "11332:11332"  # milter
      - "11333:11333"  # normal
      - "11334:11334"  # controller
    networks:
      - mailnet
    environment:
      - REDIS_HOST=redis

  # Postfix
  postfix:
    image: catatnight/postfix:latest
    container_name: mailctl-postfix
    depends_on:
      - rspamd
      - dovecot
    volumes:
      - ./config/postfix:/etc/postfix
      - mail_data:/var/mail
      - ./dkim:/etc/rspamd/dkim
    ports:
      - "25:25"
      - "587:587"
      - "465:465"
    environment:
      - MAIL_DOMAIN=${MAIL_DOMAIN:-example.com}
      - SMTP_USER=${SMTP_USER}
      - SMTP_PASSWORD=${SMTP_PASSWORD}
    networks:
      - mailnet

  # Dovecot
  dovecot:
    image: dovecot/dovecot:latest
    container_name: mailctl-dovecot
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - ./config/dovecot:/etc/dovecot
      - mail_data:/var/mail
    ports:
      - "143:143"
      - "993:993"
      - "4190:4190"  # sieve
    networks:
      - mailnet

  # mailctl CLI (control plane)
  mailctl:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: mailctl
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - ./config:/etc/mailctl
      - mail_data:/var/mail
      - ./dkim:/etc/rspamd/dkim
      - ./backups:/var/backups/mailctl
    networks:
      - mailnet
    entrypoint: ["sleep", "infinity"]

volumes:
  postgres_data:
  redis_data:
  rspamd_data:
  mail_data:
  backups:

networks:
  mailnet:
    driver: bridge
```

### Dockerfile
```dockerfile
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY .mvn .mvn
COPY mvnw .

RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache \
    postgresql-client \
    bash \
    coreutils \
    findutils

WORKDIR /app

COPY --from=builder /app/target/lw-mailserver-0.0.1-indev.jar mailctl.jar
COPY --from=builder /app/src/main/resources/db/migration /app/db/migration

ENV MAILCTL_CONFIG=/etc/mailctl/config.yaml
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["java", "-jar", "mailctl.jar"]
```

### Initialize Docker Environment
```bash
# 1. Create directory structure
mkdir -p config/postfix config/dovecot config/rspamd dkim backups init-db

# 2. Generate initial config
docker-compose run --rm mailctl mailctl config generate --output-dir /etc/mailctl

# 3. Generate DKIM keys for your domain
docker-compose run --rm mailctl mailctl domain add example.com
docker-compose run --rm mailctl mailctl dkim generate example.com

# 4. Deploy configs
docker-compose run --rm mailctl mailctl config deploy --config-dir /etc/mailctl

# 5. Start all services
docker-compose up -d
```

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