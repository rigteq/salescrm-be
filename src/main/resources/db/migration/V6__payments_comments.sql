-- Lightweight payment tracking against (typically won) opportunities, plus
-- record-level discussion comments.

CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    opportunity_id  UUID NOT NULL REFERENCES opportunities(id),
    kind            TEXT NOT NULL CHECK (kind IN ('RECEIVED', 'DUE')),
    amount          NUMERIC(14,2) NOT NULL,
    currency        TEXT NOT NULL DEFAULT 'USD',
    due_date        DATE,
    received_date   DATE,
    note            TEXT,
    owner_user_id   UUID REFERENCES users(id),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_payments_tenant_opportunity ON payments (tenant_id, opportunity_id);

CREATE TABLE comments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    related_object_type TEXT NOT NULL
                        CHECK (related_object_type IN ('ACCOUNT', 'CONTACT', 'LEAD', 'OPPORTUNITY')),
    related_object_id   UUID NOT NULL,
    body                TEXT NOT NULL,
    author_user_id      UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    soft_deleted_at     TIMESTAMPTZ
);
CREATE INDEX idx_comments_tenant_record
    ON comments (tenant_id, related_object_type, related_object_id, created_at);
