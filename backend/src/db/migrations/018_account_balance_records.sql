-- =============================================================================
-- 018_account_balance_records.sql
-- =============================================================================
-- Dated account balance snapshots for accounts whose external balance feed is
-- unavailable or unreliable.
-- =============================================================================

CREATE TABLE account_balance_records (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  account_id  INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  record_date TEXT    NOT NULL,
  balance     REAL    NOT NULL,
  created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(account_id, record_date),
  CHECK (record_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]')
);

CREATE INDEX idx_account_balance_records_account_date
  ON account_balance_records(account_id, record_date DESC);
