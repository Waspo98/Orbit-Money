-- =============================================================================
-- 007_transactions_updated_at.sql
-- =============================================================================
-- Adds updated_at column to transactions. SQLite requires a constant default
-- for ALTER TABLE ADD COLUMN, so we use a placeholder then backfill from
-- imported_at (which every transaction already has).
-- =============================================================================

ALTER TABLE transactions ADD COLUMN updated_at TEXT NOT NULL DEFAULT '1970-01-01 00:00:00';

UPDATE transactions SET updated_at = imported_at;
