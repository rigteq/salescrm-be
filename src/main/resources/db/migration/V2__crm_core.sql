-- Core revenue objects: pipelines/stages, accounts, contacts, leads, opportunities.

CREATE TABLE pipelines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            TEXT NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    owner_user_id   UUID,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_pipelines_tenant ON pipelines (tenant_id);

CREATE TABLE stages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    pipeline_id     UUID NOT NULL REFERENCES pipelines(id),
    name            TEXT NOT NULL,
    position        INT NOT NULL,
    probability     INT NOT NULL DEFAULT 0 CHECK (probability BETWEEN 0 AND 100),
    is_won          BOOLEAN NOT NULL DEFAULT false,
    is_lost         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_stages_tenant_pipeline ON stages (tenant_id, pipeline_id, position);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            TEXT NOT NULL,
    type            TEXT,
    industry        TEXT,
    website         TEXT,
    phone           TEXT,
    billing_country TEXT,
    description     TEXT,
    owner_user_id   UUID REFERENCES users(id),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_accounts_tenant_name ON accounts (tenant_id, name);

CREATE TABLE contacts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    account_id      UUID REFERENCES accounts(id),
    first_name      TEXT NOT NULL,
    last_name       TEXT NOT NULL,
    email           TEXT,
    phone           TEXT,
    title           TEXT,
    owner_user_id   UUID REFERENCES users(id),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_contacts_tenant_name ON contacts (tenant_id, last_name, first_name);
CREATE INDEX idx_contacts_tenant_account ON contacts (tenant_id, account_id);

CREATE TABLE leads (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    first_name      TEXT,
    last_name       TEXT NOT NULL,
    company_name    TEXT,
    email           TEXT,
    phone           TEXT,
    source          TEXT,
    status          TEXT NOT NULL DEFAULT 'NEW'
                    CHECK (status IN ('NEW', 'WORKING', 'QUALIFIED', 'UNQUALIFIED', 'CONVERTED')),
    converted_account_id     UUID REFERENCES accounts(id),
    converted_contact_id     UUID REFERENCES contacts(id),
    converted_opportunity_id UUID,
    owner_user_id   UUID REFERENCES users(id),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_leads_tenant_status ON leads (tenant_id, status);

CREATE TABLE opportunities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    account_id      UUID REFERENCES accounts(id),
    pipeline_id     UUID NOT NULL REFERENCES pipelines(id),
    stage_id        UUID NOT NULL REFERENCES stages(id),
    name            TEXT NOT NULL,
    amount          NUMERIC(14,2),
    currency        TEXT NOT NULL DEFAULT 'USD',
    close_date      DATE,
    probability     INT CHECK (probability BETWEEN 0 AND 100),
    forecast_category TEXT NOT NULL DEFAULT 'PIPELINE'
                    CHECK (forecast_category IN ('PIPELINE', 'BEST_CASE', 'COMMIT', 'CLOSED')),
    status          TEXT NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'WON', 'LOST')),
    next_step       TEXT,
    owner_user_id   UUID REFERENCES users(id),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_opps_tenant_stage ON opportunities (tenant_id, pipeline_id, stage_id);
CREATE INDEX idx_opps_tenant_account ON opportunities (tenant_id, account_id);

ALTER TABLE leads
    ADD CONSTRAINT fk_leads_converted_opportunity
    FOREIGN KEY (converted_opportunity_id) REFERENCES opportunities(id);
