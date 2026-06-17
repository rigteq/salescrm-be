-- Enterprise RBAC foundation: permissions, roles, role-permission mappings,
-- and user-role assignments. Existing users.role remains as a denormalized
-- primary role code for compatibility with older auth/UI paths.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT false;

UPDATE users
SET role = CASE role
    WHEN 'ADMIN' THEN 'COMPANY_OWNER'
    WHEN 'MANAGER' THEN 'SALES_MANAGER'
    WHEN 'REP' THEN 'SALES_EXECUTIVE'
    ELSE role
END;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN (
        'PLATFORM_OWNER',
        'COMPANY_OWNER',
        'COMPANY_ADMIN',
        'SALES_MANAGER',
        'SALES_EXECUTIVE',
        'FINANCE',
        'VIEWER'
    ));

CREATE TABLE permissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                TEXT NOT NULL UNIQUE,
    name                TEXT NOT NULL,
    description         TEXT,
    module              TEXT NOT NULL,
    system_permission   BOOLEAN NOT NULL DEFAULT true,
    is_deleted          BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (code = upper(code) AND code ~ '^[A-Z0-9_]+$')
);
CREATE INDEX idx_permissions_module ON permissions (module, code);

CREATE TABLE roles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                TEXT NOT NULL,
    name                TEXT NOT NULL,
    description         TEXT,
    hierarchy_level     INT NOT NULL CHECK (hierarchy_level BETWEEN 1 AND 100),
    system_role         BOOLEAN NOT NULL DEFAULT false,
    editable            BOOLEAN NOT NULL DEFAULT true,
    company_id          UUID REFERENCES tenants(id),
    is_deleted          BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (code = upper(code) AND code ~ '^[A-Z0-9_]+$')
);
CREATE UNIQUE INDEX idx_roles_global_code ON roles (code) WHERE company_id IS NULL;
CREATE UNIQUE INDEX idx_roles_company_code ON roles (company_id, code) WHERE company_id IS NOT NULL;
CREATE INDEX idx_roles_company_level ON roles (company_id, hierarchy_level);

CREATE TABLE role_permissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id             UUID NOT NULL REFERENCES roles(id),
    permission_id       UUID NOT NULL REFERENCES permissions(id),
    company_id          UUID REFERENCES tenants(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (role_id, permission_id)
);
CREATE INDEX idx_role_permissions_role ON role_permissions (role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions (permission_id);

CREATE TABLE user_roles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    role_id             UUID NOT NULL REFERENCES roles(id),
    company_id          UUID NOT NULL REFERENCES tenants(id),
    primary_role        BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, role_id, company_id)
);
CREATE UNIQUE INDEX idx_user_roles_primary ON user_roles (user_id, company_id)
    WHERE primary_role = true;
CREATE INDEX idx_user_roles_company_role ON user_roles (company_id, role_id);
