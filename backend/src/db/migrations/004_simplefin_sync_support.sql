-- =============================================================================
-- 004_simplefin_sync_support.sql
-- =============================================================================
-- Adds:
--   - cutover_date column on simplefin_config (RM owns pre, SimpleFIN owns post)
--   - sync_log table for history, error surfacing, and the in-app banner
-- =============================================================================

ALTER TABLE simplefin_config ADD COLUMN cutover_date TEXT;

CREATE TABLE sync_log (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  started_at            TEXT    NOT NULL DEFAULT (datetime('now')),
  finished_at           TEXT,
  status                TEXT    NOT NULL CHECK (status IN ('running','success','error')),
  trigger               TEXT    NOT NULL CHECK (trigger IN ('manual','scheduled','auto')),
  transactions_inserted INTEGER NOT NULL DEFAULT 0,
  transactions_skipped  INTEGER NOT NULL DEFAULT 0,
  rm_rows_deleted       INTEGER NOT NULL DEFAULT 0,
  accounts_created      INTEGER NOT NULL DEFAULT 0,
  accounts_unmatched    INTEGER NOT NULL DEFAULT 0,
  transfers_matched     INTEGER NOT NULL DEFAULT 0,
  error_message         TEXT
);

CREATE INDEX idx_sync_log_started ON sync_log(started_at DESC);
