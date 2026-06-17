-- Phase 6 platform SaaS controls: subscription plans, tenant subscriptions,
-- feature flags, and tenant-specific feature overrides.

CREATE TABLE platform_subscription_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    description     TEXT,
    monthly_price   NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency        TEXT NOT NULL DEFAULT 'USD',
    seat_limit      INT,
    feature_limit   INT,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID,
    CHECK (code = upper(code) AND code ~ '^[A-Z0-9_]+$')
);

CREATE TABLE tenant_subscriptions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    plan_id                 UUID NOT NULL REFERENCES platform_subscription_plans(id),
    status                  TEXT NOT NULL DEFAULT 'TRIAL',
    seats_purchased         INT NOT NULL DEFAULT 1,
    trial_ends_at           TIMESTAMPTZ,
    current_period_ends_at  TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              UUID,
    UNIQUE (tenant_id),
    CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED'))
);
CREATE INDEX idx_tenant_subscriptions_plan ON tenant_subscriptions (plan_id);
CREATE INDEX idx_tenant_subscriptions_status ON tenant_subscriptions (status);

CREATE TABLE platform_feature_flags (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key            TEXT NOT NULL UNIQUE,
    name                TEXT NOT NULL,
    description         TEXT,
    enabled_by_default  BOOLEAN NOT NULL DEFAULT false,
    active              BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          UUID,
    CHECK (flag_key = lower(flag_key) AND flag_key ~ '^[a-z0-9_]+$')
);

CREATE TABLE tenant_feature_flags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    flag_id         UUID NOT NULL REFERENCES platform_feature_flags(id),
    enabled         BOOLEAN NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID,
    UNIQUE (tenant_id, flag_id)
);
CREATE INDEX idx_tenant_feature_flags_tenant ON tenant_feature_flags (tenant_id);
CREATE INDEX idx_tenant_feature_flags_flag ON tenant_feature_flags (flag_id);

INSERT INTO platform_subscription_plans (code, name, description, monthly_price, currency, seat_limit, feature_limit)
VALUES
    ('STARTER', 'Starter', 'Core CRM for small teams.', 49, 'USD', 5, 8),
    ('GROWTH', 'Growth', 'Automation, finance handoff, and team management.', 149, 'USD', 25, 18),
    ('ENTERPRISE', 'Enterprise', 'Advanced controls, platform support, and white-label readiness.', 499, 'USD', NULL, NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO tenant_subscriptions (tenant_id, plan_id, status, seats_purchased, trial_ends_at, current_period_ends_at)
SELECT t.id,
       (SELECT id FROM platform_subscription_plans WHERE code = 'GROWTH'),
       CASE WHEN t.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'TRIAL' END,
       GREATEST(1, (SELECT count(*) FROM users u WHERE u.tenant_id = t.id AND u.is_deleted = false)),
       now() + interval '14 days',
       now() + interval '30 days'
FROM tenants t
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO platform_feature_flags (flag_key, name, description, enabled_by_default)
VALUES
    ('new_ui', 'New Figma UI', 'Enables the role-based Figma shell and workspaces.', true),
    ('finance_handoff', 'Finance handoff', 'Routes won opportunities into the finance queue.', true),
    ('platform_health', 'Platform health consoles', 'Shows tenant, audit, and system health workspaces.', true),
    ('ai_assist', 'AI assist', 'Enables Phase 7 scoring and next-best-action workspaces.', false),
    ('white_label', 'White label', 'Enables tenant branding and custom theme controls.', false)
ON CONFLICT (flag_key) DO NOTHING;
