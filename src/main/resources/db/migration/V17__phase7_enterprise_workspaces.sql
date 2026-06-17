-- Phase 7 enterprise differentiators: approvals, saved reports, and white-label branding.
-- Tenant-owned tables carry tenant_id, audit metadata, version where mutable, and soft delete.

CREATE TABLE approval_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    record_id         UUID REFERENCES crm_records(id),
    title             TEXT NOT NULL,
    category          TEXT NOT NULL DEFAULT 'GENERAL',
    amount            NUMERIC(14,2),
    currency          TEXT NOT NULL DEFAULT 'USD',
    status            TEXT NOT NULL DEFAULT 'PENDING',
    requester_user_id UUID REFERENCES users(id),
    approver_user_id  UUID REFERENCES users(id),
    decision_note     TEXT,
    created_by        UUID,
    updated_by        UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at   TIMESTAMPTZ,
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX idx_approval_requests_tenant_status ON approval_requests (tenant_id, status, updated_at DESC);
CREATE INDEX idx_approval_requests_tenant_record ON approval_requests (tenant_id, record_id);

CREATE TABLE saved_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            TEXT NOT NULL,
    description     TEXT,
    report_type     TEXT NOT NULL DEFAULT 'PIPELINE',
    config_json     TEXT NOT NULL DEFAULT '{}',
    visibility      TEXT NOT NULL DEFAULT 'COMPANY',
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ,
    CHECK (visibility IN ('COMPANY', 'TEAM', 'PRIVATE'))
);
CREATE INDEX idx_saved_reports_tenant_type ON saved_reports (tenant_id, report_type, updated_at DESC);

CREATE TABLE tenant_branding (
    tenant_id       UUID PRIMARY KEY REFERENCES tenants(id),
    brand_name      TEXT NOT NULL,
    logo_url        TEXT,
    primary_color   TEXT NOT NULL DEFAULT '#4F46E5',
    accent_color    TEXT NOT NULL DEFAULT '#10B981',
    font_family     TEXT NOT NULL DEFAULT 'Inter',
    custom_domain   TEXT,
    updated_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO saved_reports (tenant_id, name, description, report_type, config_json, visibility)
SELECT t.id,
       'Pipeline Risk Board',
       'Open pipeline, stale deal, and approval risk view for leadership.',
       'PIPELINE',
       '{"timeframe":"quarter","groupBy":"owner","includeApprovals":true}',
       'COMPANY'
FROM tenants t
ON CONFLICT DO NOTHING;

INSERT INTO saved_reports (tenant_id, name, description, report_type, config_json, visibility)
SELECT t.id,
       'Lead Scoring Review',
       'Hot lead and next-best-action queue for sales managers.',
       'LEAD_SCORING',
       '{"scoreThreshold":70,"includeUnassigned":true}',
       'TEAM'
FROM tenants t
ON CONFLICT DO NOTHING;

INSERT INTO tenant_branding (tenant_id, brand_name, primary_color, accent_color)
SELECT t.id, t.name, '#4F46E5', '#10B981'
FROM tenants t
ON CONFLICT (tenant_id) DO NOTHING;
