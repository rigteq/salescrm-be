-- Outcome metadata for Phase 4 deal closure UX.
-- Lost deals capture a reason; won opportunities enter a lightweight finance
-- handoff state that the finance UI can surface before the full invoice module.

ALTER TABLE crm_records
    ADD COLUMN IF NOT EXISTS lost_reason TEXT,
    ADD COLUMN IF NOT EXISTS finance_handoff_status TEXT,
    ADD COLUMN IF NOT EXISTS finance_handoff_at TIMESTAMPTZ;

ALTER TABLE crm_records DROP CONSTRAINT IF EXISTS crm_records_finance_handoff_status_check;
ALTER TABLE crm_records
    ADD CONSTRAINT crm_records_finance_handoff_status_check
    CHECK (finance_handoff_status IS NULL OR finance_handoff_status IN (
        'AWAITING_INVOICE',
        'INVOICE_DRAFTED',
        'COMPLETED'
    ));

UPDATE crm_records
SET finance_handoff_status = 'AWAITING_INVOICE',
    finance_handoff_at = COALESCE(updated_at, created_at, now())
WHERE tenant_id IS NOT NULL
  AND module_key = 'opportunities'
  AND status = 'WON'
  AND soft_deleted_at IS NULL
  AND finance_handoff_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_crm_records_finance_handoff
    ON crm_records (tenant_id, finance_handoff_status, finance_handoff_at DESC)
    WHERE finance_handoff_status IS NOT NULL;
