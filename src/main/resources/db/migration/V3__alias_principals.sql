-- ===========================================
-- Aliases as first-class mail principals
--
-- An alias is an ADDRESS THAT RECEIVES MAIL. To do the three things a
-- real alias must do it needs the same identity data a user has:
--
--   password_hash  authenticate for IMAP / POP3 / SMTP submission
--   mailbox_path   the maildir the alias collects its mail into
--   quota          per-mailbox storage limit
--   status         ACTIVE / DISABLED, so access can be revoked
--
-- Existing aliases keep working: they get an empty password_hash and an
-- empty mailbox_path, which means "routing only" - no mailbox is claimed
-- and the alias cannot authenticate until an operator sets a password.
--
-- The address of an alias and the address of a user must never collide:
-- both are principals in the same address space, and a collision would
-- make the Dovecot SQL ambiguous (two rows for one login). The trigger
-- below enforces that in the database, so it holds for every writer.
-- ===========================================

ALTER TABLE aliases ADD COLUMN password_hash TEXT NOT NULL DEFAULT '';
ALTER TABLE aliases ADD COLUMN mailbox_path TEXT NOT NULL DEFAULT '';
ALTER TABLE aliases ADD COLUMN quota        TEXT NOT NULL DEFAULT '1G';
ALTER TABLE aliases ADD COLUMN status       TEXT NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_aliases_status ON aliases(status);

-- A mailbox alias is identified by its mailbox_path, and a user is
-- identified by its email. A single address may exist in only one of
-- the two tables, otherwise Dovecot's SQL would return two rows for the
-- same login.
CREATE OR REPLACE FUNCTION aliases_reject_user_collision() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE email = NEW.alias) THEN
        RAISE EXCEPTION 'address % already exists as a user', NEW.alias
            USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_aliases_no_user_collision
    BEFORE INSERT OR UPDATE OF alias ON aliases
    FOR EACH ROW EXECUTE FUNCTION aliases_reject_user_collision();

CREATE OR REPLACE FUNCTION users_reject_alias_collision() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM aliases WHERE alias = NEW.email) THEN
        RAISE EXCEPTION 'address % already exists as an alias', NEW.email
            USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_no_alias_collision
    BEFORE INSERT OR UPDATE OF email ON users
    FOR EACH ROW EXECUTE FUNCTION users_reject_alias_collision();

-- ===========================================
-- Unified principal view
--
-- Dovecot's passdb and userdb each run a single SQL query, but a login
-- may belong to either table: a user or an alias. This view presents
-- both as one row set so one query serves both.
--
--   principal    the login name (the full lowercased address)
--   password_hash  empty for principals that cannot authenticate
--   home/mail    the maildir the principal reads and collects into
--   quota_rule   the per-principal quota, in the Dovecot 2.3 rule syntax
--                 ("<mailbox mask>:<limits>", e.g. "*:storage=1G")
--   has_mailbox  TRUE only for principals that actually own a maildir.
--                 A forwarding-only user is a principal that can send but
--                 must not read, so it is excluded from the userdb.
-- ===========================================
CREATE VIEW mail_principals AS
    SELECT u.email           AS principal,
           u.password_hash   AS password_hash,
           u.mailbox_path    AS home,
           'maildir:' || u.mailbox_path AS mail,
           '*:storage=' || u.quota AS quota_rule,
           u.status          AS status,
           (u.storage_enabled AND u.mailbox_path <> '') AS has_mailbox
      FROM users u
    UNION ALL
    SELECT a.alias           AS principal,
           a.password_hash   AS password_hash,
           a.mailbox_path    AS home,
           'maildir:' || a.mailbox_path AS mail,
           '*:storage=' || a.quota AS quota_rule,
           a.status          AS status,
           (a.mailbox_path <> '') AS has_mailbox
      FROM aliases a;
