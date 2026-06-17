CREATE TABLE user_dashboard_layouts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    user_id               UUID NOT NULL REFERENCES users(id),
    dashboard_key         TEXT NOT NULL,
    widget_order_json     TEXT NOT NULL DEFAULT '[]',
    hidden_widget_ids_json TEXT NOT NULL DEFAULT '[]',
    owner_user_id         UUID,
    created_by            UUID,
    updated_by            UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at       TIMESTAMPTZ,
    UNIQUE (tenant_id, user_id, dashboard_key)
);

CREATE INDEX idx_user_dashboard_layouts_scope
    ON user_dashboard_layouts (tenant_id, user_id, dashboard_key)
    WHERE soft_deleted_at IS NULL;
