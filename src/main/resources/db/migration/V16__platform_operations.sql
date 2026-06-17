-- Phase 6 platform operations: support tickets, announcements, and knowledge base.
-- Tables are soft-delete capable; mutations are exposed through RBAC-gated controllers.

CREATE TABLE platform_support_tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    subject         TEXT NOT NULL,
    category        TEXT NOT NULL DEFAULT 'GENERAL',
    priority        TEXT NOT NULL DEFAULT 'NORMAL',
    status          TEXT NOT NULL DEFAULT 'OPEN',
    description     TEXT,
    owner_user_id   UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID,
    soft_deleted_at TIMESTAMPTZ,
    CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER', 'RESOLVED'))
);
CREATE INDEX idx_platform_support_tickets_tenant ON platform_support_tickets (tenant_id, status);
CREATE INDEX idx_platform_support_tickets_status ON platform_support_tickets (status, updated_at DESC);

CREATE TABLE platform_announcements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           TEXT NOT NULL,
    body            TEXT NOT NULL,
    audience        TEXT NOT NULL DEFAULT 'ALL_TENANTS',
    status          TEXT NOT NULL DEFAULT 'DRAFT',
    send_at         TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID,
    soft_deleted_at TIMESTAMPTZ,
    CHECK (audience IN ('ALL_TENANTS', 'ACTIVE_TENANTS', 'TRIAL_TENANTS', 'PAST_DUE_TENANTS')),
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'SENT'))
);
CREATE INDEX idx_platform_announcements_status ON platform_announcements (status, updated_at DESC);

CREATE TABLE platform_knowledge_articles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           TEXT NOT NULL,
    category        TEXT NOT NULL DEFAULT 'Operations',
    summary         TEXT,
    body            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      UUID,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_platform_knowledge_articles_category ON platform_knowledge_articles (category, updated_at DESC);

INSERT INTO platform_support_tickets (tenant_id, subject, category, priority, status, description)
SELECT t.id,
       'Onboarding readiness check',
       'ONBOARDING',
       CASE WHEN t.status = 'ACTIVE' THEN 'NORMAL' ELSE 'HIGH' END,
       'OPEN',
       'Review admin user setup, subscription status, and first CRM records.'
FROM tenants t
WHERE t.created_at >= now() - interval '30 days'
ON CONFLICT DO NOTHING;

INSERT INTO platform_announcements (title, body, audience, status)
VALUES
    ('New workspace controls available',
     'Subscription plans, feature access, tenant health, and operations consoles are now available in the new platform UI.',
     'ALL_TENANTS',
     'DRAFT')
ON CONFLICT DO NOTHING;

INSERT INTO platform_knowledge_articles (title, category, summary, body)
VALUES
    ('Tenant onboarding checklist', 'Onboarding',
     'Steps for moving a new tenant from signup to healthy usage.',
     'Confirm billing plan, invite admins, verify CRM modules, import seed data, and schedule the first adoption review.'),
    ('Feature flag rollout guide', 'Feature Flags',
     'Recommended process for enabling a new feature for tenant cohorts.',
     'Start with internal tenants, enable a pilot tenant cohort, watch audit and support signals, then promote to default-on when stable.'),
    ('Support escalation policy', 'Support',
     'How platform operators triage urgent tenant issues.',
     'Classify urgency, assign an owner, update the ticket every business day, and document resolution notes before closing.')
ON CONFLICT DO NOTHING;
