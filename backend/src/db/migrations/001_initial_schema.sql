-- =============================================================================
-- 001_initial_schema.sql
-- =============================================================================
-- Creates all Phase 1 tables for Budget Tracker.
--
-- Sign convention: expenses are stored as NEGATIVE, income as POSITIVE.
-- (Rocket Money CSV uses the inverse convention — flipped at import time.)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- accounts
-- -----------------------------------------------------------------------------
-- One row per financial account (checking, savings, credit card, investment, etc).
-- is_manual = 1 means user-managed balance; 0 means SimpleFIN updates it.
CREATE TABLE accounts (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  name                  TEXT    NOT NULL,
  type                  TEXT    NOT NULL CHECK (type IN ('checking','savings','credit','investment','loan','cash','other')),
  institution           TEXT,
  account_number_last4  TEXT,
  current_balance       REAL    NOT NULL DEFAULT 0,
  is_manual             INTEGER NOT NULL DEFAULT 1 CHECK (is_manual IN (0,1)),
  simplefin_account_id  TEXT    UNIQUE,
  is_archived           INTEGER NOT NULL DEFAULT 0 CHECK (is_archived IN (0,1)),
  created_at            TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at            TEXT    NOT NULL DEFAULT (datetime('now'))
);


-- -----------------------------------------------------------------------------
-- categories
-- -----------------------------------------------------------------------------
-- Categorization for transactions. is_transfer = 1 excludes from budget math
-- (e.g. credit card payments, internal transfers). is_income marks earning sources.
CREATE TABLE categories (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  name         TEXT    NOT NULL UNIQUE,
  color        TEXT    NOT NULL DEFAULT '#888888',
  icon         TEXT,
  is_transfer  INTEGER NOT NULL DEFAULT 0 CHECK (is_transfer IN (0,1)),
  is_income    INTEGER NOT NULL DEFAULT 0 CHECK (is_income IN (0,1)),
  sort_order   INTEGER NOT NULL DEFAULT 0,
  created_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_categories_sort ON categories(sort_order, name);


-- -----------------------------------------------------------------------------
-- transactions
-- -----------------------------------------------------------------------------
-- Every transaction. Unique (source, external_id) prevents duplicate imports
-- from SimpleFIN re-syncs. original_description is the raw bank text and is
-- never mutated by rules — merchant is the display name that rules modify.
CREATE TABLE transactions (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  account_id            INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  date                  TEXT    NOT NULL,
  amount                REAL    NOT NULL,
  merchant              TEXT    NOT NULL,
  original_description  TEXT,
  category_id           INTEGER REFERENCES categories(id) ON DELETE SET NULL,
  notes                 TEXT,
  is_transfer           INTEGER NOT NULL DEFAULT 0 CHECK (is_transfer IN (0,1)),
  transfer_pair_id      INTEGER REFERENCES transactions(id) ON DELETE SET NULL,
  is_ignored            INTEGER NOT NULL DEFAULT 0 CHECK (is_ignored IN (0,1)),
  source                TEXT    NOT NULL CHECK (source IN ('csv_import','simplefin','manual')),
  external_id           TEXT,
  imported_at           TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- Prevent duplicate imports from the same source.
-- NULL external_ids (e.g. manual entries) are allowed to repeat.
CREATE UNIQUE INDEX idx_transactions_source_external
  ON transactions(source, external_id)
  WHERE external_id IS NOT NULL;

CREATE INDEX idx_transactions_date         ON transactions(date DESC);
CREATE INDEX idx_transactions_account_date ON transactions(account_id, date DESC);
CREATE INDEX idx_transactions_category     ON transactions(category_id);
CREATE INDEX idx_transactions_merchant     ON transactions(merchant);


-- -----------------------------------------------------------------------------
-- rules
-- -----------------------------------------------------------------------------
-- Rules engine. conditions is a JSON array of {field, operator, value}.
-- actions is a JSON array of {type, value}. Rules run in priority DESC order.
CREATE TABLE rules (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT    NOT NULL,
  conditions  TEXT    NOT NULL,
  actions     TEXT    NOT NULL,
  priority    INTEGER NOT NULL DEFAULT 0,
  enabled     INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
  created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_rules_priority ON rules(enabled, priority DESC);


-- -----------------------------------------------------------------------------
-- budgets (Phase 2)
-- -----------------------------------------------------------------------------
-- Monthly spending limit per category. rollover carries unused amount forward.
CREATE TABLE budgets (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  category_id  INTEGER NOT NULL UNIQUE REFERENCES categories(id) ON DELETE CASCADE,
  amount       REAL    NOT NULL,
  rollover     INTEGER NOT NULL DEFAULT 0 CHECK (rollover IN (0,1)),
  created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);


-- -----------------------------------------------------------------------------
-- goals (Phase 2)
-- -----------------------------------------------------------------------------
-- Savings goals. Optionally linked to an account for auto-progress tracking.
CREATE TABLE goals (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  name               TEXT    NOT NULL,
  target_amount      REAL    NOT NULL,
  current_amount     REAL    NOT NULL DEFAULT 0,
  target_date        TEXT,
  linked_account_id  INTEGER REFERENCES accounts(id) ON DELETE SET NULL,
  created_at         TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at         TEXT    NOT NULL DEFAULT (datetime('now'))
);


-- -----------------------------------------------------------------------------
-- simplefin_config
-- -----------------------------------------------------------------------------
-- Singleton row (enforced by CHECK) holding the encrypted SimpleFIN access URL.
-- Encryption uses SIMPLEFIN_ENCRYPTION_KEY from env (AES-256-GCM).
CREATE TABLE simplefin_config (
  id                    INTEGER PRIMARY KEY CHECK (id = 1),
  access_url_encrypted  TEXT,
  last_sync_at          TEXT,
  sync_enabled          INTEGER NOT NULL DEFAULT 1 CHECK (sync_enabled IN (0,1)),
  updated_at            TEXT    NOT NULL DEFAULT (datetime('now'))
);
