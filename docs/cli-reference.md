# CLI Reference

Every command, what it actually does, and — importantly — what is **not**
implemented yet. Run them all through the container:

```bash
docker compose exec mailctl mailctl <command>
```

Add `-T` and `--password` for non-interactive use (`-T` disables TTY
allocation, needed when piping).

## Contents

- [Global options](#global-options)
- [domain](#domain) — [user](#user) — [alias](#alias) — [dkim](#dkim)
- [config](#config) — [health](#health) — [doctor](#doctor)
- [backup](#backup) — [service](#service)
- [Implementation status](#implementation-status)

## Global options

| Option | Effect |
|--------|--------|
| `-c, --config=<path>` | Config file. Defaults to `MAILCTL_CONFIG`, then `/etc/mailctl/config.yaml` |
| `-v, --verbose` | Verbose output |
| `-h, --help` | Usage. Does not touch the database |
| `-V, --version` | Version string. Does not touch the database |

`--help` and `--version` deliberately skip database initialization: they are the
commands you reach for when something is already broken.

---

## `domain`

Manages the domains this server accepts mail for. A domain must exist here
before users or DKIM keys can be created for it.

### `domain add <name>`

```bash
docker compose exec mailctl mailctl domain add example.com \
  --dkim-selector mail --dkim-key-size 2048 --dkim-algorithm rsa2048
```

Validates the name against `^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]...)*$`,
enforces 253-character total and 63-character label limits, rejects leading and
trailing hyphens, then inserts a row with status `ACTIVE`. The stored name is
always lowercased.

Refuses a duplicate. **Implemented.**

### `domain remove <name> [--force]`

Deletes the domain. Cascades to its users, aliases and DKIM keys via a foreign
key with `ON DELETE CASCADE` — so removing a domain deletes every mailbox it
holds. Prompts unless `--force`. **Implemented.**

### `domain list [--json]`

Lists domains with id, status and DKIM settings. `--json` emits a JSON array.
**Implemented.**

---

## `user`

Manages mailboxes. Each user belongs to exactly one domain.

### `user add <email> [--password <pw>] [--quota 1G]`

Creates a mailbox at `/var/mail/<domain>/<localpart>` and stores the password
hash. The password is prompted for when `--password` is omitted:

```bash
docker compose exec -T mailctl mailctl user add alice@example.com --password 'secret'
```

> **Password hashing is not production-ready.** `PasswordHasher` is named for
> Argon2id and declares Argon2id constants, but its inner `BCrypt` class
> actually computes `SHA-256(salt + password)` and prefixes the result with a
> BCrypt-*looking* `$2a$12$` string. That is neither BCrypt nor Argon2id: it is
> fast to brute-force, and the misleading prefix will fool any tool that tries to
> parse it. It also conflicts with the generated Dovecot config, which declares
> `default_pass_scheme = BLF-CRYPT`. Replace this with a real KDF before any
> real user exists.

Quota is stored as text (`1G`, `500M`) and written into the Dovecot `quota_rule`. **Implemented.**

### `user add-forwarding <email> --forward-to <targets> [--password <pw>]`

Creates a **forwarding-only** user: no mailbox is created, mail addressed to it is
rewritten to the bound external addresses, and it cannot read mail over IMAP. It
can still authenticate to send outbound mail.

```bash
docker compose exec -T mailctl mailctl user add-forwarding zazalng@worldstandard.group \
  --forward-to a@gmail.com,b@outlook.com --password 'secret'
```

Targets are comma-separated, normalised to lowercase. **Implemented.**

#### How forwarding-only works

| Concern | Mechanism |
|---------|-----------|
| Inbound mail | `virtual_alias_maps` rewrites the address to the external targets |
| No local storage | Excluded from `virtual_mailbox_maps`; no maildir is ever created |
| No IMAP read access | Dovecot `user_query` filters on `storage_enabled = TRUE`, so the mailbox lookup returns no row |
| Outbound send | Present in `passdb`, so `doveadm`-based SASL authentication succeeds on port 587 |
| Anti-spoofing | `sender_login_maps` maps each login to itself; `reject_sender_login_mismatch` blocks sending as anyone else |
| Spam/DKIM filtering | Rspamd via `non_smtpd_milters` on the **inbound** path |

`virtual_alias_maps` is evaluated before `virtual_mailbox_maps`, so a forwarding
recipient is redirected without a mailbox ever being consulted.

### `user set-forward <email> --forward-to <targets>`

Replaces the targets and makes the user forwarding-only. **Implemented.**

### `user to-mailbox <email> [--quota 1G]`

Converts a forwarding-only user into a normal mailbox user: creates the maildir,
sets `storage_enabled`, clears the targets. **Implemented.**

### `user to-forwarding <email> --forward-to <targets> [--force]`

Converts a mailbox user into forwarding-only. **Deletes the maildir and all
stored mail**, so it prompts for confirmation unless `--force`. **Implemented.**

### `user forwarding-list [domain]`

Lists forwarding-only users with their targets. **Implemented.**

### `user remove <email> [--force]`, `user disable <email>`, `user enable <email>`

`remove` deletes the row; `disable`/`enable` flip `status` between `ACTIVE` and
`DISABLED`. Dovecot's `password_query` and `user_query` both filter on
`status = 'ACTIVE'`, so a disabled user is refused at authentication time
without deleting anything. **Implemented.**

### `user passwd <email> [--password <pw>]`

Replaces the stored hash. **Implemented**, subject to the hashing caveat above.

### `user list [domain] [--json]`

Lists users, optionally filtered to one domain. **Implemented.**

---

## `alias`

Routes an address to one or more targets.

### `alias add <alias> <targets>`

```bash
docker compose exec mailctl mailctl alias add support@example.com alice@example.com
docker compose exec mailctl mailctl alias add sales@example.com alice@example.com,bob@example.com
```

Stored as an `alias → targets` row and rendered into `virtual_alias_maps`.
External forwarding is not currently validated — an external target will be
written to the map as-is, which risks backscatter. Restrict targets to your own
domains until that is enforced. **Implemented, with the caveat above.**

### `alias remove <alias> [--force]`, `alias list [domain] [--json]`

Straightforward delete and list. **Implemented.**

---

## `dkim`

Generates and rotates the keys that sign outbound mail.

### `dkim generate <domain> [--selector mail] [--key-size 2048] [--algorithm rsa2048]`

Generates a key pair, stores both PEMs and the DNS record, and **prints the DNS
record you must publish**:

```bash
docker compose exec mailctl mailctl dkim generate example.com
# DNS Record (add to your DNS):
# v=DKIM1; k=rsa; p=MIIBIjANBgkq...
```

Publish it as a TXT record at `<selector>._domainkey.<domain>`. Keys expire in
one year.

**RSA only.** Supported: `rsa2048` (default) and `rsa4096`; `--key-size` accepts
1024, 2048, 3072 or 4096. Any other algorithm is refused with an explanatory
message. Ed25519 (RFC 8463) is deliberately not offered — several major
receiving servers still fail to verify Ed25519 DKIM signatures, so an Ed25519 key
means mail that silently fails to authenticate in the places it matters most.

Refuses to overwrite an existing `(domain, selector)` pair — use `dkim rotate`
instead. **Implemented.**

### `dkim show <domain> [--selector mail] [--dns-format]`

Prints the public key, metadata and DNS record. `--dns-format` prints only the
record, which is convenient for piping into a DNS update script. **Implemented.**

### `dkim rotate <domain> [--selector mail]`

Marks the existing key `RETIRED` and generates a fresh pair with the same
selector, then prints the new DNS record. Note this replaces the record for the
same selector, so the change takes effect as soon as DNS propagates — plan a
rotation window accordingly. **Implemented.**

---

## `config`

Renders the mail services' configuration from database state.

### `config generate [--output-dir /tmp/mailctl-config] [--services postfix,dovecot,rspamd]`

Writes `main.cf`, the three `virtual_*_maps` files, `dovecot.conf`,
`dovecot-sql.conf.ext`, `dkim_signing.conf`, `options.conf`, the three DKIM maps
and `dkim/<selector>.private`.

Under Docker, render into the bind-mounted directory and then apply:

```bash
docker compose exec mailctl mailctl config generate --output-dir /etc/mailctl/generated
bash scripts/apply-config.sh
```

The `--services` option is accepted but not yet honoured; all services are
always generated. **Implemented (flag pending).**

### `config validate [--config-dir /etc]`

Intended to run `postfix -c <dir> check`, `dovecot -n` and `rspamadm configtest`
against a candidate directory. **Not implemented** — it currently resolves
`<config-dir>/mailctl-config` and reports whether that path exists, which is not
a validation. Worse, `ConfigGenerator.deployConfig` (the code path) shells out to
those binaries, which do not exist in the mailctl image, so it would fail if it
were reached. `scripts/apply-config.sh` is the working substitute.

### `config show [--service all] [--format text]`

Prints the effective configuration. **Not implemented** — prints a header only.

### `config deploy [--config-dir /tmp/mailctl-config] [--dry-run]`

Validates, atomically installs, runs `postmap`, then reloads all three services.

`--dry-run` only prints a message. The real path has the same problem as
`validate`: it invokes `postfix check`, `dovecot -n`, `rspamadm configtest` and
the reload commands inside the mailctl container, where none of those binaries
exist. Use `scripts/apply-config.sh` instead.

---

## `health`

### `health check [--json]`

Runs 22 checks and prints them grouped as DNS / TLS / Services / Storage /
Security, ending in `HEALTHY` or `UNHEALTHY`. See [doctor](#doctor) for how much
of this is real.

### `health liveness`

Prints `alive`. Exits 1 if services are unavailable.

### `health readiness`

Prints `ready` or `not ready`, exit code 0 or 1. Suitable as a container probe.

The entrypoint uses `mailctl health` as its readiness gate while applying
migrations, which is why a database failure surfaces during container start.

---

## `doctor`

### `doctor run [--json] [--fix]`

Runs the same 22 checks and prints a categorised report.

> **Read this before trusting a green result.** Of the 22 checks, these are
> **placeholders that return `passed = true` without testing anything**: DNS MX,
> DNS SPF, DNS DKIM, DNS DMARC, DNS PTR, Mailbox Permissions, Firewall, Open
> Relay, Submission Auth, IMAP TLS. The queue count is also a placeholder. TLS
> Certificate only checks that both files exist, then hardcodes
> `expires in 71 days`.
>
> Checks that do real work: Hostname, FQDN, Postfix/Dovecot/Rspamd config,
> PostgreSQL reachability, and Disk Space (fails under 10% free).
>
> Service checks call `systemctl is-active`, which does not exist in the
> container, so under Docker they report as failures. A `HEALTHY` result today
> means "nothing errored", not "everything verified".

`--fix` is accepted but not implemented.

### `doctor dns`, `doctor tls`, `doctor storage`, `doctor security`

**Not implemented.** Each prints a placeholder line. Note these differ from
`doctor run`, which at least executes the subset described above.

### `doctor services`

Runs `systemctl is-active` for postfix, dovecot, rspamd and postgresql. Works on
a systemd host; reports errors inside the container, where there is no systemd.
**Implemented, host-only.**

---

## `backup`

### `backup create [--output <path>] [--include database,config,dkim]`

**Not implemented.** Computes a timestamped default path and prints
`Backup created (placeholder)` without writing anything. Do not rely on it.

Back up the database directly instead:

```bash
docker compose exec postgres pg_dump -U mailctl mailctl > mailctl-$(date +%F).sql
```

DKIM private keys also need backing up — they live in `dkim_keys.private_key_pem`
and in `dkim/`. Losing them means reissuing DKIM and updating DNS.

### `backup restore <file> [--include ...] [--force]`

**Not implemented.** Prompts for confirmation unless `--force`, then prints
`Restore completed (placeholder)`.

### `backup list [--dir /var/backups/mailctl]`

Lists `*.tar.gz` files with a human-readable size. The only backup subcommand
that does real work, though nothing currently produces matching archives.

---

## `service`

Controls the mail daemons through `ProcessRunner`.

| Command | Effect |
|---------|--------|
| `service status [name]` | `systemctl is-active` per service |
| `service reload postfix\|dovecot\|rspamd\|all` | `postfix reload`, `doveadm reload`, `rspamadm reload` |
| `service restart <name>` | `systemctl restart` |
| `service enable <name>` / `disable <name>` | `systemctl enable` / `disable` |

> **These do not work under Docker.** The mailctl image has no `postfix`,
> `doveadm`, `rspamadm` or `systemctl` binaries, and reloading a sibling
> container requires `docker exec`, not a local process call. Under the Compose
> deployment use `bash scripts/apply-config.sh`, which copies the rendered
> configuration into the service mount points, runs `postmap` inside the Postfix
> container, and restarts Postfix, Dovecot and Rspamd.

`reload all` also throws on any name other than the three known services, so
`service reload foo` fails with `Unknown service`.

---

## Implementation status

Honest summary for `0.0.1-indev`.

### Working

| Area | Detail |
|------|--------|
| `domain` | add, remove, list — full validation and persistence |
| `user` | add, add-forwarding, set-forward, to-mailbox, to-forwarding, remove, disable, enable, passwd, list, forwarding-list — **except** password hashing |
| Forwarding-only users | No mailbox, external rewrite, send-only, IMAP refused. Verified end-to-end |
| `alias` | add, remove, list |
| `dkim` | generate, show, rotate (RSA: rsa2048 / rsa4096) |
| `config generate` | renders all Postfix, Dovecot and Rspamd configuration |
| `health liveness` / `readiness` | usable as container probes |
| `doctor run` (partial) | Hostname, FQDN, PostgreSQL, disk, and the three config checks |
| `backup list` | lists existing archives |
| Database | Flyway migrations, local/external routing, enforced TLS |
| Config | YAML loading with `MAILCTL_*` environment overrides |

### Not working

| Command | Problem |
|---------|---------|
| `password hashing` | SHA-256 with a misleading `$2a$` prefix; not BCrypt or Argon2id |
| `config validate` | Now runs `postfix -c check`, `dovecot -n`, `rspamadm configtest`; the clients ship in the image |
| `config deploy` | Validation and reload steps now work; reload still targets the local daemon, so prefer `scripts/apply-config.sh` |
| `config show` | Placeholder |
| `service *` | Client binaries are present, but reload cannot signal a sibling container; use `scripts/apply-config.sh` |
| `doctor dns/tls/storage/security` | Placeholders |
| `doctor run` (most checks) | Returns `passed = true` without testing |
| `doctor services` | Calls `systemctl`; the postfix/dovecot/rspamd config checks now work |
| `backup create` / `restore` | Placeholders; use `pg_dump` |
| `--services` flag on `config generate` | Accepted, ignored |
| `doctor run --fix` | Accepted, ignored |

### Also worth knowing

- **Rspamd milter address.** Generated `main.cf` uses `inet:localhost:11332`.
  Under Compose, Postfix must dial `inet:rspamd:11332` because Rspamd is a
  separate container. `PostfixConfig` needs a configurable milter host.
- **`ConfigGenerator` line 72** calls
  `dkimKeyRepository.findByDomainId(null)` and discards the result. Harmless
  today because the result is unused, but it issues a query with a null domain
  and should be deleted.
- **`DkimKeyManager.rotateKey`** relies on the `(domain_id, selector)` unique
  constraint to replace the old key. It writes the `RETIRED` row and then a new
  `ACTIVE` row with the same selector — if that constraint is ever relaxed, the
  repository's `ON CONFLICT (id)` will no longer upsert and you will accumulate
  duplicate selector rows.
