-- 029_permissions_import_batches.sql
-- Adds household write/read permissions plus import preview/undo tracking.

ALTER TABLE household_memberships
  ADD COLUMN access_level TEXT NOT NULL DEFAULT 'write'
    CHECK (access_level IN ('read','write'));

ALTER TABLE household_shares
  ADD COLUMN access_level TEXT NOT NULL DEFAULT 'write'
    CHECK (access_level IN ('read','write'));

CREATE TABLE import_batches (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id INTEGER NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  source       TEXT    NOT NULL DEFAULT 'rocket_money',
  filename     TEXT,
  status       TEXT    NOT NULL DEFAULT 'pending'
              CHECK (status IN ('pending','applied','undone')),
  preview_json TEXT    NOT NULL,
  summary_json TEXT,
  created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  applied_at   TEXT,
  undone_at    TEXT,
  updated_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_import_batches_household_status
  ON import_batches(household_id, status, created_at DESC);

CREATE TABLE import_batch_items (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id    INTEGER NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  import_batch_id INTEGER NOT NULL REFERENCES import_batches(id) ON DELETE CASCADE,
  table_name      TEXT    NOT NULL,
  row_id          INTEGER NOT NULL,
  created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(import_batch_id, table_name, row_id)
);

CREATE INDEX idx_import_batch_items_household_batch
  ON import_batch_items(household_id, import_batch_id, table_name);
