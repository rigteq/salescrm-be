-- Per-tenant settings (e.g. WhatsApp message template) and in-app notifications.

CREATE TABLE tenant_settings (
    tenant_id          UUID PRIMARY KEY REFERENCES tenants(id),
    whatsapp_template  TEXT,
    default_currency   TEXT NOT NULL DEFAULT 'USD',
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    user_id         UUID REFERENCES users(id),   -- recipient; null = whole tenant
    type            TEXT NOT NULL,                 -- TASK_DUE, TASK_OVERDUE, DEAL_WON, ...
    title           TEXT NOT NULL,
    message         TEXT,
    link_object_type TEXT,                         -- ACCOUNT / OPPORTUNITY / TASK ...
    link_object_id   UUID,
    dedupe_key      TEXT,                          -- prevents duplicate reminders
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_recipient
    ON notifications (tenant_id, user_id, read_at, created_at DESC);
CREATE UNIQUE INDEX idx_notifications_dedupe
    ON notifications (tenant_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
