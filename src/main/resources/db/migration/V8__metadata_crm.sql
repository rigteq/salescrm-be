-- Metadata-driven CRM platform replacement.
-- Fixed CRM tables remain as migration/fallback sources; new APIs use the
-- tenant module + crm_records model.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS selected_template_key TEXT,
    ADD COLUMN IF NOT EXISTS setup_completed BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS setup_completed_at TIMESTAMPTZ;

ALTER TABLE pipelines
    ADD COLUMN IF NOT EXISTS module_key TEXT NOT NULL DEFAULT 'opportunities';
CREATE INDEX IF NOT EXISTS idx_pipelines_tenant_module ON pipelines (tenant_id, module_key);

ALTER TABLE stages
    ADD COLUMN IF NOT EXISTS color TEXT NOT NULL DEFAULT '#4f46e5',
    ADD COLUMN IF NOT EXISTS sequence INT,
    ADD COLUMN IF NOT EXISTS stage_status TEXT NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS outcome_type TEXT;
UPDATE stages
SET sequence = position
WHERE sequence IS NULL;
UPDATE stages
SET stage_status = CASE WHEN is_won OR is_lost THEN 'CLOSED' ELSE 'OPEN' END,
    outcome_type = CASE WHEN is_won THEN 'WON' WHEN is_lost THEN 'LOST' ELSE NULL END;

ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_related_object_type_check;
ALTER TABLE tasks
    ADD CONSTRAINT tasks_related_object_type_check
    CHECK (related_object_type IN ('ACCOUNT', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CRM_RECORD'));
ALTER TABLE activities DROP CONSTRAINT IF EXISTS activities_related_object_type_check;
ALTER TABLE activities
    ADD CONSTRAINT activities_related_object_type_check
    CHECK (related_object_type IN ('ACCOUNT', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CRM_RECORD'));
ALTER TABLE comments DROP CONSTRAINT IF EXISTS comments_related_object_type_check;
ALTER TABLE comments
    ADD CONSTRAINT comments_related_object_type_check
    CHECK (related_object_type IN ('ACCOUNT', 'CONTACT', 'LEAD', 'OPPORTUNITY', 'CRM_RECORD'));

CREATE TABLE tenant_modules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    module_key      TEXT NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    singular_label  TEXT NOT NULL,
    plural_label    TEXT NOT NULL,
    icon            TEXT,
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, module_key)
);
CREATE INDEX idx_tenant_modules_tenant_order ON tenant_modules (tenant_id, display_order);

CREATE TABLE custom_fields (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    module_key            TEXT NOT NULL,
    field_key             TEXT NOT NULL,
    label                 TEXT NOT NULL,
    field_type            TEXT NOT NULL CHECK (field_type IN (
                            'TEXT','NUMBER','CURRENCY','DATE','DATETIME','DROPDOWN',
                            'MULTI_SELECT','CHECKBOX','PHONE','EMAIL','URL','FILE',
                            'USER_PICKER','TEAM_PICKER','ADDRESS','RATING')),
    options_json          TEXT NOT NULL DEFAULT '{}',
    validation_json       TEXT NOT NULL DEFAULT '{}',
    visibility_rules_json TEXT NOT NULL DEFAULT '{}',
    required              BOOLEAN NOT NULL DEFAULT false,
    display_order         INT NOT NULL DEFAULT 0,
    system_field          BOOLEAN NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    soft_deleted_at       TIMESTAMPTZ,
    UNIQUE (tenant_id, module_key, field_key)
);
CREATE INDEX idx_custom_fields_tenant_module ON custom_fields (tenant_id, module_key, display_order);

CREATE TABLE crm_records (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    module_key         TEXT NOT NULL,
    pipeline_id        UUID REFERENCES pipelines(id),
    stage_id           UUID REFERENCES stages(id),
    owner_user_id      UUID REFERENCES users(id),
    title              TEXT NOT NULL,
    status             TEXT NOT NULL DEFAULT 'OPEN',
    source             TEXT,
    priority           TEXT,
    amount             NUMERIC(14,2),
    currency           TEXT NOT NULL DEFAULT 'USD',
    created_by         UUID,
    updated_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at    TIMESTAMPTZ,
    legacy_object_type TEXT,
    legacy_object_id   UUID,
    UNIQUE (tenant_id, legacy_object_type, legacy_object_id)
);
CREATE INDEX idx_crm_records_tenant_module ON crm_records (tenant_id, module_key, created_at DESC);
CREATE INDEX idx_crm_records_tenant_stage ON crm_records (tenant_id, pipeline_id, stage_id);
CREATE INDEX idx_crm_records_tenant_owner ON crm_records (tenant_id, owner_user_id, status);

CREATE TABLE crm_record_custom_values (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    record_id       UUID NOT NULL REFERENCES crm_records(id),
    field_id        UUID REFERENCES custom_fields(id),
    field_key       TEXT NOT NULL,
    value_text      TEXT,
    value_number    NUMERIC(18,4),
    value_boolean   BOOLEAN,
    value_date      DATE,
    value_datetime  TIMESTAMPTZ,
    value_json      TEXT,
    file_url        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (record_id, field_key)
);
CREATE INDEX idx_record_values_tenant_record ON crm_record_custom_values (tenant_id, record_id);

CREATE TABLE forms (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    module_key          TEXT NOT NULL,
    name                TEXT NOT NULL,
    description         TEXT,
    pipeline_id         UUID REFERENCES pipelines(id),
    default_stage_id    UUID REFERENCES stages(id),
    active              BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    soft_deleted_at     TIMESTAMPTZ
);
CREATE INDEX idx_forms_tenant_module ON forms (tenant_id, module_key);

CREATE TABLE form_fields (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    form_id         UUID NOT NULL REFERENCES forms(id),
    field_id        UUID NOT NULL REFERENCES custom_fields(id),
    display_order   INT NOT NULL DEFAULT 0,
    required        BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (form_id, field_id)
);

CREATE TABLE workflow_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    module_key      TEXT NOT NULL,
    name            TEXT NOT NULL,
    trigger_type    TEXT NOT NULL CHECK (trigger_type IN (
                    'ON_CREATE','ON_UPDATE','ON_STAGE_CHANGE','ON_ASSIGNMENT_CHANGE',
                    'ON_NO_ACTIVITY','ON_DATE_REACHED','ON_STATUS_CHANGE')),
    conditions_json TEXT NOT NULL DEFAULT '{}',
    actions_json    TEXT NOT NULL DEFAULT '[]',
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_workflows_tenant_module ON workflow_rules (tenant_id, module_key, active);

CREATE TABLE workflow_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    workflow_rule_id UUID REFERENCES workflow_rules(id),
    record_id       UUID REFERENCES crm_records(id),
    status          TEXT NOT NULL DEFAULT 'PENDING',
    payload_json    TEXT NOT NULL DEFAULT '{}',
    run_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workflow_jobs_due ON workflow_jobs (status, run_at);

CREATE TABLE teams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            TEXT NOT NULL,
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    soft_deleted_at TIMESTAMPTZ,
    UNIQUE (tenant_id, name)
);

CREATE TABLE team_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    team_id         UUID NOT NULL REFERENCES teams(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    role_label      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, team_id, user_id)
);

CREATE TABLE assignment_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    module_key      TEXT NOT NULL,
    name            TEXT NOT NULL,
    assignment_type TEXT NOT NULL CHECK (assignment_type IN (
                    'MANUAL','ROUND_ROBIN','TEAM_BASED','LOCATION_BASED',
                    'PRIORITY_BASED','PRODUCT_BASED','WORKLOAD_BASED')),
    conditions_json TEXT NOT NULL DEFAULT '{}',
    priority_order  INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_assignment_rules_tenant_module ON assignment_rules (tenant_id, module_key, priority_order);

CREATE TABLE dashboards (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            TEXT NOT NULL,
    description     TEXT,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    soft_deleted_at TIMESTAMPTZ
);

CREATE TABLE dashboard_widgets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    dashboard_id    UUID NOT NULL REFERENCES dashboards(id),
    module_key      TEXT,
    title           TEXT NOT NULL,
    widget_type     TEXT NOT NULL CHECK (widget_type IN (
                    'COUNT','SUM','FUNNEL','BAR_CHART','LINE_CHART','PIE_CHART',
                    'TABLE','LEADERBOARD','SLA_CARD','TASK_LIST')),
    metric_key      TEXT,
    filters_json    TEXT NOT NULL DEFAULT '{}',
    chart_type      TEXT,
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dashboard_widgets_tenant_dashboard ON dashboard_widgets (tenant_id, dashboard_id, display_order);

CREATE TABLE record_tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    record_id   UUID NOT NULL REFERENCES crm_records(id),
    tag         TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, record_id, tag)
);

CREATE TABLE industry_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key    TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    description     TEXT,
    business_type   TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE industry_template_modules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES industry_templates(id),
    module_key      TEXT NOT NULL,
    singular_label  TEXT NOT NULL,
    plural_label    TEXT NOT NULL,
    icon            TEXT,
    display_order   INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (template_id, module_key)
);

CREATE TABLE industry_template_pipelines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES industry_templates(id),
    module_key      TEXT NOT NULL,
    pipeline_key    TEXT NOT NULL,
    name            TEXT NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (template_id, module_key, pipeline_key)
);

CREATE TABLE industry_template_stages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_pipeline_id UUID NOT NULL REFERENCES industry_template_pipelines(id),
    stage_key       TEXT NOT NULL,
    name            TEXT NOT NULL,
    probability     INT NOT NULL DEFAULT 0,
    color           TEXT NOT NULL DEFAULT '#4f46e5',
    sequence        INT NOT NULL DEFAULT 0,
    stage_status    TEXT NOT NULL DEFAULT 'OPEN',
    outcome_type    TEXT,
    UNIQUE (template_pipeline_id, stage_key)
);

CREATE TABLE industry_template_custom_fields (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES industry_templates(id),
    module_key      TEXT NOT NULL,
    field_key       TEXT NOT NULL,
    label           TEXT NOT NULL,
    field_type      TEXT NOT NULL,
    options_json    TEXT NOT NULL DEFAULT '{}',
    validation_json TEXT NOT NULL DEFAULT '{}',
    visibility_rules_json TEXT NOT NULL DEFAULT '{}',
    required        BOOLEAN NOT NULL DEFAULT false,
    display_order   INT NOT NULL DEFAULT 0,
    UNIQUE (template_id, module_key, field_key)
);

CREATE TABLE industry_template_forms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES industry_templates(id),
    module_key      TEXT NOT NULL,
    form_key        TEXT NOT NULL,
    name            TEXT NOT NULL,
    field_keys_json TEXT NOT NULL DEFAULT '[]',
    UNIQUE (template_id, module_key, form_key)
);

CREATE TABLE industry_template_workflows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES industry_templates(id),
    module_key      TEXT NOT NULL,
    name            TEXT NOT NULL,
    trigger_type    TEXT NOT NULL,
    conditions_json TEXT NOT NULL DEFAULT '{}',
    actions_json    TEXT NOT NULL DEFAULT '[]'
);

CREATE TABLE industry_template_dashboards (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES industry_templates(id),
    dashboard_key   TEXT NOT NULL,
    name            TEXT NOT NULL,
    widgets_json    TEXT NOT NULL DEFAULT '[]',
    UNIQUE (template_id, dashboard_key)
);

CREATE TABLE industry_template_roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     UUID NOT NULL REFERENCES industry_templates(id),
    role_code       TEXT NOT NULL,
    role_name       TEXT NOT NULL,
    permissions_json TEXT NOT NULL DEFAULT '[]',
    UNIQUE (template_id, role_code)
);

CREATE TABLE crm_record_migration_map (
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    legacy_object_type TEXT NOT NULL,
    legacy_object_id   UUID NOT NULL,
    record_id          UUID NOT NULL REFERENCES crm_records(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, legacy_object_type, legacy_object_id)
);

INSERT INTO tenant_modules (tenant_id, module_key, singular_label, plural_label, icon, display_order)
SELECT id, 'accounts', 'Account', 'Accounts', 'building', 10 FROM tenants
ON CONFLICT (tenant_id, module_key) DO NOTHING;
INSERT INTO tenant_modules (tenant_id, module_key, singular_label, plural_label, icon, display_order)
SELECT id, 'contacts', 'Contact', 'Contacts', 'user', 20 FROM tenants
ON CONFLICT (tenant_id, module_key) DO NOTHING;
INSERT INTO tenant_modules (tenant_id, module_key, singular_label, plural_label, icon, display_order)
SELECT id, 'leads', 'Lead', 'Leads', 'funnel', 30 FROM tenants
ON CONFLICT (tenant_id, module_key) DO NOTHING;
INSERT INTO tenant_modules (tenant_id, module_key, singular_label, plural_label, icon, display_order)
SELECT id, 'opportunities', 'Opportunity', 'Opportunities', 'target', 40 FROM tenants
ON CONFLICT (tenant_id, module_key) DO NOTHING;
INSERT INTO tenant_modules (tenant_id, module_key, singular_label, plural_label, icon, display_order)
SELECT id, 'quotes', 'Quote', 'Quotes', 'doc', 50 FROM tenants
ON CONFLICT (tenant_id, module_key) DO NOTHING;

INSERT INTO crm_records (
    tenant_id, module_key, owner_user_id, title, status, source, amount, created_by,
    updated_by, created_at, updated_at, soft_deleted_at, legacy_object_type, legacy_object_id
)
SELECT tenant_id, 'accounts', owner_user_id, name, coalesce(type, 'ACTIVE'), null, null,
       created_by, updated_by, created_at, updated_at, soft_deleted_at, 'ACCOUNT', id
FROM accounts
ON CONFLICT (tenant_id, legacy_object_type, legacy_object_id) DO NOTHING;

INSERT INTO crm_records (
    tenant_id, module_key, owner_user_id, title, status, source, created_by,
    updated_by, created_at, updated_at, soft_deleted_at, legacy_object_type, legacy_object_id
)
SELECT tenant_id, 'contacts', owner_user_id, trim(first_name || ' ' || last_name), 'ACTIVE', null,
       created_by, updated_by, created_at, updated_at, soft_deleted_at, 'CONTACT', id
FROM contacts
ON CONFLICT (tenant_id, legacy_object_type, legacy_object_id) DO NOTHING;

INSERT INTO crm_records (
    tenant_id, module_key, owner_user_id, title, status, source, created_by,
    updated_by, created_at, updated_at, soft_deleted_at, legacy_object_type, legacy_object_id
)
SELECT tenant_id, 'leads', owner_user_id, trim(coalesce(first_name,'') || ' ' || last_name),
       status, source, created_by, updated_by, created_at, updated_at, soft_deleted_at, 'LEAD', id
FROM leads
ON CONFLICT (tenant_id, legacy_object_type, legacy_object_id) DO NOTHING;

INSERT INTO crm_records (
    tenant_id, module_key, pipeline_id, stage_id, owner_user_id, title, status, source,
    amount, currency, created_by, updated_by, created_at, updated_at, soft_deleted_at,
    legacy_object_type, legacy_object_id
)
SELECT tenant_id, 'opportunities', pipeline_id, stage_id, owner_user_id, name, status, null,
       amount, currency, created_by, updated_by, created_at, updated_at, soft_deleted_at,
       'OPPORTUNITY', id
FROM opportunities
ON CONFLICT (tenant_id, legacy_object_type, legacy_object_id) DO NOTHING;

INSERT INTO crm_records (
    tenant_id, module_key, owner_user_id, title, status, amount, currency,
    created_by, updated_by, created_at, updated_at, soft_deleted_at, legacy_object_type, legacy_object_id
)
SELECT tenant_id, 'quotes', owner_user_id, name, status, total, currency,
       created_by, updated_by, created_at, updated_at, soft_deleted_at, 'QUOTE', id
FROM quotes
ON CONFLICT (tenant_id, legacy_object_type, legacy_object_id) DO NOTHING;

INSERT INTO crm_record_migration_map (tenant_id, legacy_object_type, legacy_object_id, record_id)
SELECT tenant_id, legacy_object_type, legacy_object_id, id
FROM crm_records
WHERE legacy_object_type IS NOT NULL
ON CONFLICT DO NOTHING;
