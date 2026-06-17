-- Platform foundation: tenants, users, audit log.
-- Convention: every tenant-owned business table carries
--   tenant_id, owner_user_id, created_by, updated_by, created_at, updated_at,
--   version, soft_deleted_at
-- and an index whose leading column is tenant_id.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT NOT NULL,
    slug            TEXT NOT NULL UNIQUE,
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    email           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    first_name      TEXT NOT NULL,
    last_name       TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'REP')),
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);
CREATE UNIQUE INDEX idx_users_email_global ON users (lower(email));
CREATE INDEX idx_users_tenant ON users (tenant_id);

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    actor_user_id   UUID,
    action          TEXT NOT NULL,           -- CREATE / UPDATE / DELETE / CONVERT / LOGIN ...
    object_type     TEXT NOT NULL,           -- e.g. ACCOUNT, LEAD, QUOTE
    object_id       UUID,
    detail          JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_tenant_object ON audit_log (tenant_id, object_type, object_id);
CREATE INDEX idx_audit_tenant_time ON audit_log (tenant_id, created_at DESC);
