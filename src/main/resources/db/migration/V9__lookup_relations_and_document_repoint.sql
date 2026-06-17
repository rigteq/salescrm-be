-- Pure metadata model: relationships between records become a first-class
-- LOOKUP custom-field type (target record stored in value_record_id), and the
-- commercial documents (quotes, payments) are re-pointed from the legacy
-- opportunity/account tables onto crm_records. This is additive + data-migrating;
-- the legacy tables themselves are dropped in V10 once the app no longer reads them.

-- 1. Allow the LOOKUP field type.
ALTER TABLE custom_fields DROP CONSTRAINT IF EXISTS custom_fields_field_type_check;
ALTER TABLE custom_fields
    ADD CONSTRAINT custom_fields_field_type_check
    CHECK (field_type IN (
        'TEXT','NUMBER','CURRENCY','DATE','DATETIME','DROPDOWN',
        'MULTI_SELECT','CHECKBOX','PHONE','EMAIL','URL','FILE',
        'USER_PICKER','TEAM_PICKER','ADDRESS','RATING','LOOKUP'));

-- 2. A LOOKUP value is the id of the referenced crm_record.
ALTER TABLE crm_record_custom_values
    ADD COLUMN IF NOT EXISTS value_record_id UUID REFERENCES crm_records(id);
CREATE INDEX IF NOT EXISTS idx_crm_value_lookup
    ON crm_record_custom_values (tenant_id, field_key, value_record_id)
    WHERE value_record_id IS NOT NULL;

-- 3. Seed the standard relationship fields for every existing tenant. The lookup
--    target module is carried in options_json so no extra column is needed.
INSERT INTO custom_fields (tenant_id, module_key, field_key, label, field_type, options_json, required, display_order, system_field)
SELECT id, 'contacts', 'account', 'Account', 'LOOKUP', '{"lookupModuleKey":"accounts"}', false, 5, true FROM tenants
ON CONFLICT (tenant_id, module_key, field_key) DO NOTHING;
INSERT INTO custom_fields (tenant_id, module_key, field_key, label, field_type, options_json, required, display_order, system_field)
SELECT id, 'opportunities', 'account', 'Account', 'LOOKUP', '{"lookupModuleKey":"accounts"}', false, 5, true FROM tenants
ON CONFLICT (tenant_id, module_key, field_key) DO NOTHING;
INSERT INTO custom_fields (tenant_id, module_key, field_key, label, field_type, options_json, required, display_order, system_field)
SELECT id, 'quotes', 'opportunity', 'Opportunity', 'LOOKUP', '{"lookupModuleKey":"opportunities"}', false, 5, true FROM tenants
ON CONFLICT (tenant_id, module_key, field_key) DO NOTHING;

-- 4. Backfill the relationship values from the legacy foreign keys, resolving
--    legacy ids to record ids through the migration map.
INSERT INTO crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_record_id)
SELECT c.tenant_id, cm.record_id, cf.id, 'account', am.record_id
FROM contacts c
JOIN crm_record_migration_map cm ON cm.tenant_id = c.tenant_id AND cm.legacy_object_type = 'CONTACT' AND cm.legacy_object_id = c.id
JOIN crm_record_migration_map am ON am.tenant_id = c.tenant_id AND am.legacy_object_type = 'ACCOUNT' AND am.legacy_object_id = c.account_id
JOIN custom_fields cf ON cf.tenant_id = c.tenant_id AND cf.module_key = 'contacts' AND cf.field_key = 'account' AND cf.soft_deleted_at IS NULL
WHERE c.account_id IS NOT NULL
ON CONFLICT (record_id, field_key) DO UPDATE SET value_record_id = excluded.value_record_id, updated_at = now();

INSERT INTO crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_record_id)
SELECT o.tenant_id, om.record_id, cf.id, 'account', am.record_id
FROM opportunities o
JOIN crm_record_migration_map om ON om.tenant_id = o.tenant_id AND om.legacy_object_type = 'OPPORTUNITY' AND om.legacy_object_id = o.id
JOIN crm_record_migration_map am ON am.tenant_id = o.tenant_id AND am.legacy_object_type = 'ACCOUNT' AND am.legacy_object_id = o.account_id
JOIN custom_fields cf ON cf.tenant_id = o.tenant_id AND cf.module_key = 'opportunities' AND cf.field_key = 'account' AND cf.soft_deleted_at IS NULL
WHERE o.account_id IS NOT NULL
ON CONFLICT (record_id, field_key) DO UPDATE SET value_record_id = excluded.value_record_id, updated_at = now();

INSERT INTO crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_record_id)
SELECT q.tenant_id, qm.record_id, cf.id, 'opportunity', om.record_id
FROM quotes q
JOIN crm_record_migration_map qm ON qm.tenant_id = q.tenant_id AND qm.legacy_object_type = 'QUOTE' AND qm.legacy_object_id = q.id
JOIN crm_record_migration_map om ON om.tenant_id = q.tenant_id AND om.legacy_object_type = 'OPPORTUNITY' AND om.legacy_object_id = q.opportunity_id
JOIN custom_fields cf ON cf.tenant_id = q.tenant_id AND cf.module_key = 'quotes' AND cf.field_key = 'opportunity' AND cf.soft_deleted_at IS NULL
WHERE q.opportunity_id IS NOT NULL
ON CONFLICT (record_id, field_key) DO UPDATE SET value_record_id = excluded.value_record_id, updated_at = now();

-- 5. Re-point quotes onto crm_records (opportunity + account become record refs).
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS record_id UUID REFERENCES crm_records(id);
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS account_record_id UUID REFERENCES crm_records(id);
UPDATE quotes q SET record_id = m.record_id
FROM crm_record_migration_map m
WHERE m.tenant_id = q.tenant_id AND m.legacy_object_type = 'OPPORTUNITY' AND m.legacy_object_id = q.opportunity_id;
UPDATE quotes q SET account_record_id = m.record_id
FROM crm_record_migration_map m
WHERE m.tenant_id = q.tenant_id AND m.legacy_object_type = 'ACCOUNT' AND m.legacy_object_id = q.account_id;
ALTER TABLE quotes DROP COLUMN IF EXISTS opportunity_id;
ALTER TABLE quotes DROP COLUMN IF EXISTS account_id;
CREATE INDEX IF NOT EXISTS idx_quotes_tenant_record ON quotes (tenant_id, record_id);

-- 6b. Seed the standard contact/lead/account fields so the metadata modules are
--     a complete CRM (and bulk import has somewhere to put email/phone/etc.).
INSERT INTO custom_fields (tenant_id, module_key, field_key, label, field_type, required, display_order, system_field)
SELECT t.id, v.module_key, v.field_key, v.label, v.field_type, false, v.display_order, true
FROM tenants t
CROSS JOIN (VALUES
    ('contacts', 'email', 'Email', 'EMAIL', 10),
    ('contacts', 'phone', 'Phone', 'PHONE', 20),
    ('contacts', 'title', 'Title', 'TEXT', 30),
    ('leads', 'email', 'Email', 'EMAIL', 30),
    ('leads', 'phone', 'Phone', 'PHONE', 40),
    ('leads', 'company_name', 'Company', 'TEXT', 50),
    ('accounts', 'website', 'Website', 'URL', 20),
    ('accounts', 'phone', 'Phone', 'PHONE', 30),
    ('accounts', 'type', 'Type', 'TEXT', 40),
    ('accounts', 'billing_country', 'Billing country', 'TEXT', 50)
) AS v(module_key, field_key, label, field_type, display_order)
ON CONFLICT (tenant_id, module_key, field_key) DO NOTHING;

-- 6c. Backfill the new standard fields from the legacy columns.
INSERT INTO crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_text)
SELECT c.tenant_id, cm.record_id, cf.id, cf.field_key,
       CASE cf.field_key WHEN 'email' THEN c.email WHEN 'phone' THEN c.phone WHEN 'title' THEN c.title END
FROM contacts c
JOIN crm_record_migration_map cm ON cm.tenant_id = c.tenant_id AND cm.legacy_object_type = 'CONTACT' AND cm.legacy_object_id = c.id
JOIN custom_fields cf ON cf.tenant_id = c.tenant_id AND cf.module_key = 'contacts' AND cf.field_key IN ('email','phone','title') AND cf.soft_deleted_at IS NULL
WHERE COALESCE(CASE cf.field_key WHEN 'email' THEN c.email WHEN 'phone' THEN c.phone WHEN 'title' THEN c.title END, '') <> ''
ON CONFLICT (record_id, field_key) DO NOTHING;

INSERT INTO crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_text)
SELECT l.tenant_id, lm.record_id, cf.id, cf.field_key,
       CASE cf.field_key WHEN 'email' THEN l.email WHEN 'phone' THEN l.phone WHEN 'company_name' THEN l.company_name END
FROM leads l
JOIN crm_record_migration_map lm ON lm.tenant_id = l.tenant_id AND lm.legacy_object_type = 'LEAD' AND lm.legacy_object_id = l.id
JOIN custom_fields cf ON cf.tenant_id = l.tenant_id AND cf.module_key = 'leads' AND cf.field_key IN ('email','phone','company_name') AND cf.soft_deleted_at IS NULL
WHERE COALESCE(CASE cf.field_key WHEN 'email' THEN l.email WHEN 'phone' THEN l.phone WHEN 'company_name' THEN l.company_name END, '') <> ''
ON CONFLICT (record_id, field_key) DO NOTHING;

INSERT INTO crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_text)
SELECT a.tenant_id, am.record_id, cf.id, cf.field_key,
       CASE cf.field_key WHEN 'website' THEN a.website WHEN 'phone' THEN a.phone WHEN 'type' THEN a.type
            WHEN 'billing_country' THEN a.billing_country WHEN 'industry' THEN a.industry END
FROM accounts a
JOIN crm_record_migration_map am ON am.tenant_id = a.tenant_id AND am.legacy_object_type = 'ACCOUNT' AND am.legacy_object_id = a.id
JOIN custom_fields cf ON cf.tenant_id = a.tenant_id AND cf.module_key = 'accounts' AND cf.field_key IN ('website','phone','type','billing_country','industry') AND cf.soft_deleted_at IS NULL
WHERE COALESCE(CASE cf.field_key WHEN 'website' THEN a.website WHEN 'phone' THEN a.phone WHEN 'type' THEN a.type
            WHEN 'billing_country' THEN a.billing_country WHEN 'industry' THEN a.industry END, '') <> ''
ON CONFLICT (record_id, field_key) DO NOTHING;

-- 6d. Opportunity forecasting attributes become custom fields so analytics can
--     read everything from the metadata model.
INSERT INTO custom_fields (tenant_id, module_key, field_key, label, field_type, options_json, required, display_order, system_field)
SELECT id, 'opportunities', 'close_date', 'Close date', 'DATE', '{}', false, 6, true FROM tenants
ON CONFLICT (tenant_id, module_key, field_key) DO NOTHING;
INSERT INTO custom_fields (tenant_id, module_key, field_key, label, field_type, options_json, required, display_order, system_field)
SELECT id, 'opportunities', 'forecast_category', 'Forecast category', 'DROPDOWN',
       '{"options":["PIPELINE","BEST_CASE","COMMIT","CLOSED"]}', false, 7, true FROM tenants
ON CONFLICT (tenant_id, module_key, field_key) DO NOTHING;

INSERT INTO crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_date)
SELECT o.tenant_id, om.record_id, cf.id, 'close_date', o.close_date
FROM opportunities o
JOIN crm_record_migration_map om ON om.tenant_id = o.tenant_id AND om.legacy_object_type = 'OPPORTUNITY' AND om.legacy_object_id = o.id
JOIN custom_fields cf ON cf.tenant_id = o.tenant_id AND cf.module_key = 'opportunities' AND cf.field_key = 'close_date' AND cf.soft_deleted_at IS NULL
WHERE o.close_date IS NOT NULL
ON CONFLICT (record_id, field_key) DO NOTHING;

INSERT INTO crm_record_custom_values (tenant_id, record_id, field_id, field_key, value_text)
SELECT o.tenant_id, om.record_id, cf.id, 'forecast_category', o.forecast_category
FROM opportunities o
JOIN crm_record_migration_map om ON om.tenant_id = o.tenant_id AND om.legacy_object_type = 'OPPORTUNITY' AND om.legacy_object_id = o.id
JOIN custom_fields cf ON cf.tenant_id = o.tenant_id AND cf.module_key = 'opportunities' AND cf.field_key = 'forecast_category' AND cf.soft_deleted_at IS NULL
WHERE COALESCE(o.forecast_category, '') <> ''
ON CONFLICT (record_id, field_key) DO NOTHING;

-- 7. Re-point payments onto crm_records.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS record_id UUID REFERENCES crm_records(id);
UPDATE payments p SET record_id = m.record_id
FROM crm_record_migration_map m
WHERE m.tenant_id = p.tenant_id AND m.legacy_object_type = 'OPPORTUNITY' AND m.legacy_object_id = p.opportunity_id;
ALTER TABLE payments ALTER COLUMN record_id SET NOT NULL;
ALTER TABLE payments DROP COLUMN IF EXISTS opportunity_id;
CREATE INDEX IF NOT EXISTS idx_payments_tenant_record ON payments (tenant_id, record_id);
