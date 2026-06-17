-- The fixed-entity CRM model has been fully replaced by the metadata engine
-- (crm_records + custom fields + LOOKUP relations). Quotes and payments were
-- re-pointed onto crm_records in V9, so nothing references these tables anymore.
-- The migration map has served its purpose (V9 backfills) and is dropped too.
--
-- crm_records.legacy_object_type / legacy_object_id are intentionally kept as
-- provenance for records that originated from the old model.

DROP TABLE IF EXISTS crm_record_migration_map;
DROP TABLE IF EXISTS contacts CASCADE;
DROP TABLE IF EXISTS opportunities CASCADE;
DROP TABLE IF EXISTS leads CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;
