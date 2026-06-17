-- Tasks and the activity timeline.
-- Both attach polymorphically to a record via (related_object_type, related_object_id).

CREATE TABLE tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    title           TEXT NOT NULL,
    description     TEXT,
    due_at          TIMESTAMPTZ,
    priority        TEXT NOT NULL DEFAULT 'NORMAL'
                    CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
    status          TEXT NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    completed_at    TIMESTAMPTZ,
    related_object_type TEXT
                    CHECK (related_object_type IN ('ACCOUNT', 'CONTACT', 'LEAD', 'OPPORTUNITY')),
    related_object_id   UUID,
    owner_user_id   UUID REFERENCES users(id),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_tasks_tenant_owner_status ON tasks (tenant_id, owner_user_id, status, due_at);
CREATE INDEX idx_tasks_tenant_related ON tasks (tenant_id, related_object_type, related_object_id);

CREATE TABLE activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    type            TEXT NOT NULL
                    CHECK (type IN ('NOTE', 'CALL', 'EMAIL', 'MEETING', 'SYSTEM')),
    subject         TEXT NOT NULL,
    body            TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    related_object_type TEXT NOT NULL
                    CHECK (related_object_type IN ('ACCOUNT', 'CONTACT', 'LEAD', 'OPPORTUNITY')),
    related_object_id   UUID NOT NULL,
    owner_user_id   UUID REFERENCES users(id),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_activities_tenant_related
    ON activities (tenant_id, related_object_type, related_object_id, occurred_at DESC);
