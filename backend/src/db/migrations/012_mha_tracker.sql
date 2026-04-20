-- 012_mha_tracker.sql
-- Adds optional Ministerial Housing Allowance tracking.
--
-- Storage model:
-- - app_settings.mha_tracker_enabled gates whether the feature appears in the UI.
-- - accounts.mha_default_eligible marks accounts whose transactions count by default.
-- - transactions.edited_mha_eligible lets a user override the account default per transaction.

CREATE TABLE IF NOT EXISTS app_settings (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT OR IGNORE INTO app_settings (key, value)
VALUES ('mha_tracker_enabled', '0');

ALTER TABLE accounts
  ADD COLUMN mha_default_eligible INTEGER NOT NULL DEFAULT 0 CHECK (mha_default_eligible IN (0,1));

ALTER TABLE transactions
  ADD COLUMN edited_mha_eligible INTEGER CHECK (edited_mha_eligible IN (0,1));

ALTER TABLE transactions
  ADD COLUMN edited_mha_eligible_source TEXT;

CREATE INDEX idx_accounts_mha_default
  ON accounts(mha_default_eligible)
  WHERE mha_default_eligible = 1;

CREATE INDEX idx_transactions_edited_mha
  ON transactions(edited_mha_eligible)
  WHERE edited_mha_eligible IS NOT NULL;
