-- ===========================================
-- mailctl initial schema
--
-- Column names and types here MUST match what the JDBC repositories read:
--   JdbcDomainRepository, JdbcUserRepository,
--   JdbcAliasRepository, JdbcDkimKeyRepository
-- Those use rs.getString("domain_id"), rs.getTimestamp("created_at"), etc.,
-- so renaming anything here breaks the runtime with a SQLException.
-- ===========================================

-- Domains -------------------------------------------------------
CREATE TABLE domains (
    id              UUID PRIMARY KEY,
    name            TEXT        NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status          TEXT        NOT NULL DEFAULT 'ACTIVE',
    dkim_selector   TEXT        NOT NULL DEFAULT 'mail',
    dkim_key_size   INTEGER     NOT NULL DEFAULT 2048,
    dkim_algorithm  TEXT        NOT NULL DEFAULT 'rsa2048'
);

CREATE INDEX idx_domains_status ON domains(status);

-- Users ---------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY,
    domain_id       UUID        NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    email           TEXT        NOT NULL UNIQUE,
    password_hash   TEXT        NOT NULL,
    mailbox_path    TEXT        NOT NULL,
    quota           TEXT        NOT NULL DEFAULT '1G',
    status          TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);

CREATE INDEX idx_users_domain_id ON users(domain_id);
CREATE INDEX idx_users_status    ON users(status);

-- Aliases -------------------------------------------------------
CREATE TABLE aliases (
    id              UUID PRIMARY KEY,
    domain_id       UUID        NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    alias           TEXT        NOT NULL UNIQUE,
    targets         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_aliases_domain_id ON aliases(domain_id);

-- DKIM keys ------------------------------------------------------
CREATE TABLE dkim_keys (
    id                UUID PRIMARY KEY,
    domain_id         UUID        NOT NULL REFERENCES domains(id) ON DELETE CASCADE,
    selector          TEXT        NOT NULL,
    algorithm         TEXT        NOT NULL,
    key_size          INTEGER     NOT NULL,
    private_key_pem   TEXT        NOT NULL,
    public_key_pem    TEXT        NOT NULL,
    dns_record        TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_dkim_domain_selector UNIQUE (domain_id, selector)
);

CREATE INDEX idx_dkim_keys_domain_id ON dkim_keys(domain_id);
CREATE INDEX idx_dkim_keys_status    ON dkim_keys(status);