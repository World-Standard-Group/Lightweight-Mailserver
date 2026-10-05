# PostgreSQL TLS material (external database mode)

Only needed when `MAILCTL_DB_MODE=external`. In `local` mode the bundled
PostgreSQL container is used and none of this applies.

This directory is mounted read-only into the mailctl container at
`/etc/mailctl/keys`, so the container paths in `.env` are:

    /etc/mailctl/keys/<file>

## Files

| File | Required | Purpose |
|------|----------|---------|
| `rootCA.crt` | yes | CA chain that signed the database server certificate. Used by `sslmode=verify-full`. |
| `client.crt`  | no | Client certificate, if the server requires mTLS. |
| `client.key`  | no | Private key for `client.crt`. |

Everything here is a secret. The directory is git-ignored; keep it that way.

## Server-side prerequisites

The database must:

1. Present a TLS certificate whose **SAN matches `MAILCTL_DB_HOST`**
   (`verify-full` checks the hostname, not just the CA chain).
2. Be PostgreSQL 12 or newer for `channelBinding=require`.
3. Authenticate with SCRAM, not MD5. Check with:

   ```sql
   SHOW password_encryption;
   SELECT rolname, substring(rolpassword from 1 for 14) FROM pg_authid
    WHERE rolname = 'mailctl';
   -- 'SCRAM-SHA-256$...' is required; 'md5...' will break channel binding
   ```

4. (If mTLS) require client certificates and trust the same CA, e.g. in
   `postgresql.conf`:

   ```
   ssl = on
   ssl_cert_file = '/path/server.crt'
   ssl_key_file  = '/path/server.key'
   ssl_ca_file   = '/path/rootCA.crt'
   ```

5. (If mTLS) map the client certificate to a role:

   ```
   # rootCA.crt
   sslcert=mailctl-client
   ```

   ```sql
   CREATE ROLE mailctl LOGIN;
   GRANT ALL PRIVILEGES ON DATABASE mailctl TO mailctl;
   \c mailctl
   GRANT ALL ON SCHEMA public TO mailctl;
   ```

## Generating client material

If you have no CA yet, a throwaway one is enough to prove the connection works:

```bash
# CA
openssl req -new -x509 -days 3650 -nodes -newkey rsa:4096 \
  -keyout ca.key -out rootCA.crt -subj "/CN=mailctl-db-ca"

# client key + cert signed by that CA
openssl req -new -newkey rsa:4096 -nodes \
  -keyout client.key -out client.csr -subj "/CN=mailctl-client"
openssl x509 -req -in client.csr -days 825 -CA rootCA.crt -CAkey ca.key \
  -CAcreateserial -out client.crt
```

Do not use a self-signed CA in production; use your organisation's CA so the
server certificate can be rotated independently.

## Key permissions

PgJDBC refuses a private key that is group- or world-readable, and this
directory is mounted `0444`. The container entrypoint copies `client.key` to
`/etc/mailctl/pki/client.key` with mode `0600` and points the driver there, so
no host-side chmod dance is needed. If you run mailctl outside Docker, set the
key to `0600` yourself or the connection will fail with a permission error.

## Troubleshooting

| Symptom | Cause |
|---------|-------|
| `certificate verify failed` | The CA does not match the server certificate, or `rootCA.crt` holds only an intermediate. Include the full chain. |
| `hostname mismatch` / `No subject alternative names` | The certificate SAN does not contain `MAILCTL_DB_HOST`. Reissue it with the hostname. |
| `channel binding is required, but server does not support it` | Server is pre-12 or uses MD5. See prerequisites above. |
| `private key not accessible` / `key password must be at most 100 bytes` | Key permissions or encryption. Let the entrypoint stage the 0600 copy. |
| `external mode requires MAILCTL_DB_SSL_ROOT_CERT` | Variable unset, or the path does not start with `/etc/mailctl/keys/`. |
