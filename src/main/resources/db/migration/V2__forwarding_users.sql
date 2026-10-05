-- ===========================================
-- Forwarding-only users
--
-- A user can be either:
--   storage_enabled = TRUE   a real mailbox; mail is delivered to /var/mail
--   storage_enabled = FALSE  no mailbox; mail is forwarded to forward_targets
--
-- A forwarding-only user still authenticates for outbound SMTP submission
-- (so they can send), but must NOT appear in virtual_mailbox_maps and must NOT
-- be able to read mail over IMAP.
--
-- forward_targets is a comma-separated list of external addresses.
-- ===========================================

ALTER TABLE users ADD COLUMN storage_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN forward_targets  TEXT;

-- Keep the comment wording honest: these drive the generated Dovecot queries.
COMMENT ON COLUMN users.storage_enabled IS
    'FALSE = forwarding-only: no maildir, excluded from virtual_mailbox_maps and from the Dovecot userdb (no IMAP), but still present in passdb so the user can authenticate for SMTP submission.';

COMMENT ON COLUMN users.forward_targets IS
    'Comma-separated external addresses for a forwarding-only user. NULL for storage users.';

-- Forwarding lookups render per user, so a partial index helps config generation.
CREATE INDEX idx_users_forwarding
    ON users(domain_id)
    WHERE storage_enabled = FALSE AND forward_targets IS NOT NULL;