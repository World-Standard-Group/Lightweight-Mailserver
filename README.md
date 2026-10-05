# mailctl

A lightweight mail-server control plane. Java owns **state and orchestration**;
Postfix, Dovecot and Rspamd own **mail protocols and filtering**.

mailctl is a CLI, not a daemon. It stores domains, users, aliases and DKIM keys
in PostgreSQL, then renders the Postfix, Dovecot and Rspamd configuration those
services need. Everything runs in Docker; you drive it with `docker compose exec`.

## Quick start

```bash
cp .env.example .env
$EDITOR .env                 # set MAIL_DOMAIN, MAIL_HOSTNAME, POSTGRES_PASSWORD
bash scripts/start.sh
docker compose exec mailctl mailctl domain add example.com
docker compose exec mailctl mailctl dkim generate example.com   # prints a DNS record
bash scripts/apply-config.sh
```

Update to a new version with one command:

```bash
bash scripts/update.sh
```

## Documentation

| Document | What it covers |
|----------|----------------|
| [docs/architecture.md](docs/architecture.md) | How the pieces fit together, with diagrams |
| [docs/cli-reference.md](docs/cli-reference.md) | Every command, what it does, and what is not implemented yet |
| [docs/deployment.md](docs/deployment.md) | Deployment, both database routes, DNS, troubleshooting |
| [keys/README.md](keys/README.md) | TLS material for the external database route |

## Commands at a glance

```bash
mailctl domain add|remove|list
mailctl user   add|add-forwarding|set-forward|to-mailbox|to-forwarding
               remove|disable|enable|passwd|list|forwarding-list
mailctl alias  add|remove|list
mailctl dkim   generate|show|rotate
mailctl config generate|validate|show|deploy
mailctl health check|liveness|readiness
mailctl doctor run|dns|tls|services|storage|security
mailctl backup create|restore|list
mailctl service status|reload|restart|enable|disable
```

Run any of them through the container:

```bash
docker compose exec mailctl mailctl domain list
docker compose exec mailctl mailctl doctor run
```

## Project layout

```
src/main/java/group/worldstandard/
  Main.java              picocli entry point + dependency injection
  ServiceRegistry.java   singleton wiring
  cli/                   one class per command group
  domain/                Domain record, repository, service
  identity/              User record, repository, service
  routing/               Alias record, repository, service
  security/              DKIM keys, password hashing
  mail/                  Postfix/Dovecot/Rspamd config renderers
  infrastructure/        config, database, files, processes, health
src/main/resources/db/migration/   Flyway migrations
```

## Forwarding-only users

A user can either have a mailbox or forward mail without storing any:

```bash
docker compose exec -T mailctl mailctl user add-forwarding zazalng@worldstandard.group \
  --forward-to a@gmail.com,b@outlook.com --password 'secret'
bash scripts/apply-config.sh
```

No maildir is created. Inbound mail is rewritten to the external targets, and the
user is refused IMAP access while still being able to send over authenticated
TLS on port 587. See [docs/cli-reference.md](docs/cli-reference.md#user) for the
mechanism.

## DKIM

Signing keys are RSA only (`rsa2048` or `rsa4096`):

```bash
docker compose exec mailctl mailctl dkim generate example.com
```

Ed25519 is intentionally not offered. It would need the raw 32-byte key in the
DNS record rather than the X.509 SPKI, and several major receiving servers still
cannot verify Ed25519 DKIM signatures.

## Requirements

- Java 25 (built in Docker; use any JDK 25 locally)
- Docker with Compose v2
- PostgreSQL 12+ for the external route (`channelBinding=require` needs SCRAM)

## Status

Version `0.0.1-indev`. Some commands are still placeholders — see
[docs/cli-reference.md](docs/cli-reference.md) for an explicit list of what is
implemented and what is not. In particular `doctor` reports several checks as
passing without actually performing them, and `backup create`/`restore` do not
yet write or read an archive.
