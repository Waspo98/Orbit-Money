-- =============================================================================
-- 038_real_estate_and_sync_closure.sql
-- =============================================================================
-- Supports standalone properties, linked property loans (Option C), and
-- SimpleFIN closed/paid-off account detection without rebuilding accounts.
-- =============================================================================

ALTER TABLE accounts ADD COLUMN is_property INTEGER NOT NULL DEFAULT 0 CHECK (is_property IN (0, 1));
ALTER TABLE accounts ADD COLUMN property_name TEXT;
ALTER TABLE accounts ADD COLUMN linked_property_account_id INTEGER REFERENCES accounts(id) ON DELETE SET NULL;
ALTER TABLE accounts ADD COLUMN simplefin_last_seen_at TEXT;
ALTER TABLE accounts ADD COLUMN simplefin_missing_since TEXT;
ALTER TABLE accounts ADD COLUMN closure_prompt_dismissed_at TEXT;

CREATE INDEX IF NOT EXISTS idx_accounts_linked_property
  ON accounts(household_id, linked_property_account_id)
  WHERE linked_property_account_id IS NOT NULL;
