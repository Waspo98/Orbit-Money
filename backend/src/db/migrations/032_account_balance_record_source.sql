-- =============================================================================
-- 032_account_balance_record_source.sql
-- =============================================================================
-- Track whether a dated balance snapshot was entered manually or captured from
-- SimpleFIN sync. Existing rows are treated as manual so user-entered history
-- continues to take precedence over automatic snapshots.
-- =============================================================================

ALTER TABLE account_balance_records
  ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'
  CHECK (source IN ('manual', 'simplefin'));

CREATE INDEX idx_account_balance_records_household_source_date
  ON account_balance_records(household_id, source, record_date DESC);
