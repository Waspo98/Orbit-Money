-- =============================================================================
-- 005_add_mortgage_type.sql
-- =============================================================================
-- Adds 'mortgage' to the accounts.type CHECK constraint.
--
-- SQLite doesn't support ALTER TABLE to modify a CHECK constraint directly —
-- we have to rebuild the table. The standard safe procedure:
--   1. Create new table with updated constraint
--   2. Copy data from old to new
--   3. Drop old, rename new
--   4. Recreate indexes
--
-- Foreign keys that REFERENCE accounts (transactions.account_id,
-- goals.linked_account_id) continue to work after the rename.
-- =============================================================================

PRAGMA foreign_keys = OFF;

CREATE TABLE accounts_new (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  name                  TEXT    NOT NULL,
  type                  TEXT    NOT NULL CHECK (type IN ('checking','savings','credit','investment','loan','mortgage','cash','other')),
  institution           TEXT,
  account_number_last4  TEXT,
  current_balance       REAL    NOT NULL DEFAULT 0,
  is_manual             INTEGER NOT NULL DEFAULT 1 CHECK (is_manual IN (0,1)),
  simplefin_account_id  TEXT    UNIQUE,
  is_archived           INTEGER NOT NULL DEFAULT 0 CHECK (is_archived IN (0,1)),
  created_at            TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at            TEXT    NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO accounts_new
  SELECT id, name, type, institution, account_number_last4,
         current_balance, is_manual, simplefin_account_id, is_archived,
         created_at, updated_at
    FROM accounts;

DROP TABLE accounts;
ALTER TABLE accounts_new RENAME TO accounts;

-- Recreate the composite unique index that was on the old table.
CREATE UNIQUE INDEX idx_accounts_institution_last4
  ON accounts(institution, account_number_last4)
  WHERE institution IS NOT NULL AND account_number_last4 IS NOT NULL;

PRAGMA foreign_keys = ON;
