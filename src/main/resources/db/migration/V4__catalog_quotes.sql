-- Product catalog, price books, and quotes.

CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            TEXT NOT NULL,
    sku             TEXT,
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT true,
    owner_user_id   UUID,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_products_tenant_name ON products (tenant_id, name);
CREATE UNIQUE INDEX idx_products_tenant_sku ON products (tenant_id, sku) WHERE sku IS NOT NULL;

CREATE TABLE price_books (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            TEXT NOT NULL,
    currency        TEXT NOT NULL DEFAULT 'USD',
    is_default      BOOLEAN NOT NULL DEFAULT false,
    active          BOOLEAN NOT NULL DEFAULT true,
    owner_user_id   UUID,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_price_books_tenant ON price_books (tenant_id);

CREATE TABLE price_book_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    price_book_id   UUID NOT NULL REFERENCES price_books(id),
    product_id      UUID NOT NULL REFERENCES products(id),
    unit_price      NUMERIC(14,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (price_book_id, product_id)
);
CREATE INDEX idx_pbe_tenant_book ON price_book_entries (tenant_id, price_book_id);

CREATE TABLE quotes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    opportunity_id  UUID REFERENCES opportunities(id),
    account_id      UUID REFERENCES accounts(id),
    quote_number    TEXT NOT NULL,
    name            TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    currency        TEXT NOT NULL DEFAULT 'USD',
    subtotal        NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_total  NUMERIC(14,2) NOT NULL DEFAULT 0,
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    valid_until     DATE,
    owner_user_id   UUID REFERENCES users(id),
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    soft_deleted_at TIMESTAMPTZ,
    UNIQUE (tenant_id, quote_number)
);
CREATE INDEX idx_quotes_tenant_opportunity ON quotes (tenant_id, opportunity_id);

CREATE TABLE quote_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    quote_id        UUID NOT NULL REFERENCES quotes(id) ON DELETE CASCADE,
    product_id      UUID REFERENCES products(id),
    description     TEXT NOT NULL,
    quantity        NUMERIC(12,2) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_pct    NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (discount_pct BETWEEN 0 AND 100),
    line_total      NUMERIC(14,2) NOT NULL DEFAULT 0,
    position        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_quote_lines_tenant_quote ON quote_lines (tenant_id, quote_id, position);

-- Per-tenant monotonically increasing quote numbers.
CREATE TABLE quote_counters (
    tenant_id       UUID PRIMARY KEY REFERENCES tenants(id),
    next_number     BIGINT NOT NULL DEFAULT 1
);
