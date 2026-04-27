-- =============================================================================
-- 023_multi_user_households.sql
-- =============================================================================
-- Introduces user + household ownership for multi-user Authentik support.
--
-- Existing single-user data is preserved by assigning every current row to
-- household id 1 ("Neal Household") and creating a local owner user for it.
-- =============================================================================

CREATE TABLE users (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  authentik_sub  TEXT UNIQUE,
  username       TEXT UNIQUE,
  email          TEXT,
  display_name   TEXT,
  is_local_admin INTEGER NOT NULL DEFAULT 0 CHECK (is_local_admin IN (0,1)),
  created_at     TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE households (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  name             TEXT NOT NULL,
  default_currency TEXT NOT NULL DEFAULT 'USD',
  created_at       TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE household_memberships (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id INTEGER NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role         TEXT    NOT NULL DEFAULT 'owner' CHECK (role IN ('owner','admin','member')),
  created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(household_id, user_id)
);

INSERT INTO households (id, name)
VALUES (1, 'Neal Household');

INSERT INTO users (id, username, display_name, is_local_admin)
VALUES (1, 'admin', 'Admin', 1);

INSERT INTO household_memberships (household_id, user_id, role)
VALUES (1, 1, 'owner');

CREATE TABLE app_default_settings (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO app_default_settings (key, value)
VALUES ('mha_tracker_enabled', '0');

CREATE TABLE app_default_categories (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  name                 TEXT    NOT NULL UNIQUE,
  color                TEXT    NOT NULL DEFAULT '#888888',
  icon                 TEXT,
  is_transfer          INTEGER NOT NULL DEFAULT 0 CHECK (is_transfer IN (0,1)),
  is_income            INTEGER NOT NULL DEFAULT 0 CHECK (is_income IN (0,1)),
  sort_order           INTEGER NOT NULL DEFAULT 0,
  mha_default_eligible INTEGER NOT NULL DEFAULT 0 CHECK (mha_default_eligible IN (0,1)),
  mha_default_ignored  INTEGER NOT NULL DEFAULT 0 CHECK (mha_default_ignored IN (0,1)),
  created_at           TEXT    NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO app_default_categories (
  name, color, icon, is_transfer, is_income, sort_order,
  mha_default_eligible, mha_default_ignored, created_at
)
SELECT name, color, icon, is_transfer, is_income, sort_order,
       mha_default_eligible, mha_default_ignored, created_at
  FROM categories
 ORDER BY sort_order ASC, name ASC;

DROP INDEX IF EXISTS idx_accounts_institution_last4;
DROP INDEX IF EXISTS idx_accounts_sort_order;
DROP INDEX IF EXISTS idx_accounts_mha_default;

CREATE TABLE accounts_new (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id          INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE,
  name                  TEXT    NOT NULL,
  type                  TEXT    NOT NULL CHECK (type IN ('checking','savings','credit','investment','loan','mortgage','cash','other')),
  institution           TEXT,
  account_number_last4  TEXT,
  simplefin_account_id  TEXT,
  is_manual             INTEGER NOT NULL DEFAULT 1 CHECK (is_manual IN (0,1)),
  is_archived           INTEGER NOT NULL DEFAULT 0 CHECK (is_archived IN (0,1)),
  sort_order            INTEGER NOT NULL DEFAULT 0,
  mha_default_eligible  INTEGER NOT NULL DEFAULT 0 CHECK (mha_default_eligible IN (0,1)),
  current_balance       INTEGER NOT NULL DEFAULT 0,
  estimated_value       INTEGER,
  created_at            TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at            TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(household_id, simplefin_account_id)
);

INSERT INTO accounts_new (
  id, household_id, name, type, institution, account_number_last4,
  simplefin_account_id, is_manual, is_archived, sort_order,
  mha_default_eligible, current_balance, estimated_value, created_at, updated_at
)
SELECT id, 1, name, type, institution, account_number_last4,
       simplefin_account_id, is_manual, is_archived, sort_order,
       mha_default_eligible, current_balance, estimated_value, created_at, updated_at
  FROM accounts;

DROP TABLE accounts;
ALTER TABLE accounts_new RENAME TO accounts;

CREATE UNIQUE INDEX idx_accounts_household_institution_last4
  ON accounts(household_id, institution, account_number_last4)
  WHERE institution IS NOT NULL AND account_number_last4 IS NOT NULL;

CREATE INDEX idx_accounts_household_sort_order
  ON accounts(household_id, is_archived, sort_order, name);

CREATE INDEX idx_accounts_mha_default
  ON accounts(household_id, mha_default_eligible)
  WHERE mha_default_eligible = 1;

DROP INDEX IF EXISTS idx_categories_sort;

CREATE TABLE categories_new (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id         INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE,
  name                 TEXT    NOT NULL,
  color                TEXT    NOT NULL DEFAULT '#888888',
  icon                 TEXT,
  is_transfer          INTEGER NOT NULL DEFAULT 0 CHECK (is_transfer IN (0,1)),
  is_income            INTEGER NOT NULL DEFAULT 0 CHECK (is_income IN (0,1)),
  sort_order           INTEGER NOT NULL DEFAULT 0,
  created_at           TEXT    NOT NULL DEFAULT (datetime('now')),
  mha_default_eligible INTEGER NOT NULL DEFAULT 0 CHECK (mha_default_eligible IN (0,1)),
  mha_default_ignored  INTEGER NOT NULL DEFAULT 0 CHECK (mha_default_ignored IN (0,1)),
  UNIQUE(household_id, name)
);

INSERT INTO categories_new (
  id, household_id, name, color, icon, is_transfer, is_income,
  sort_order, created_at, mha_default_eligible, mha_default_ignored
)
SELECT id, 1, name, color, icon, is_transfer, is_income,
       sort_order, created_at, mha_default_eligible, mha_default_ignored
  FROM categories;

DROP TABLE categories;
ALTER TABLE categories_new RENAME TO categories;

CREATE INDEX idx_categories_household_sort
  ON categories(household_id, sort_order, name);

DROP INDEX IF EXISTS idx_transactions_source_external;
DROP INDEX IF EXISTS idx_transactions_date;
DROP INDEX IF EXISTS idx_transactions_account_date;
DROP INDEX IF EXISTS idx_transactions_category;
DROP INDEX IF EXISTS idx_transactions_merchant;
DROP INDEX IF EXISTS idx_transactions_edited_merchant;
DROP INDEX IF EXISTS idx_transactions_edited_merchant_source;
DROP INDEX IF EXISTS idx_transactions_edited_mha;

ALTER TABLE transactions
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX idx_transactions_household_source_external
  ON transactions(household_id, source, external_id)
  WHERE external_id IS NOT NULL;

CREATE INDEX idx_transactions_household_date
  ON transactions(household_id, date DESC);

CREATE INDEX idx_transactions_household_account_date
  ON transactions(household_id, account_id, date DESC);

CREATE INDEX idx_transactions_household_category
  ON transactions(household_id, category_id);

CREATE INDEX idx_transactions_household_merchant
  ON transactions(household_id, original_merchant);

CREATE INDEX idx_transactions_edited_merchant
  ON transactions(household_id, edited_merchant)
  WHERE edited_merchant IS NOT NULL;

CREATE INDEX idx_transactions_edited_merchant_source
  ON transactions(household_id, edited_merchant_source)
  WHERE edited_merchant_source IS NOT NULL;

CREATE INDEX idx_transactions_edited_mha
  ON transactions(household_id, edited_mha_eligible)
  WHERE edited_mha_eligible IS NOT NULL;

CREATE TABLE rules_new (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE,
  name         TEXT    NOT NULL,
  conditions   TEXT    NOT NULL,
  actions      TEXT    NOT NULL,
  priority     INTEGER NOT NULL DEFAULT 0,
  enabled      INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0,1)),
  created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);

INSERT INTO rules_new (
  id, household_id, name, conditions, actions, priority, enabled, created_at, updated_at
)
SELECT id, 1, name, conditions, actions, priority, enabled, created_at, updated_at
  FROM rules;

DROP TABLE rules;
ALTER TABLE rules_new RENAME TO rules;

CREATE INDEX idx_rules_household_priority
  ON rules(household_id, enabled, priority DESC);

CREATE TABLE budgets_new (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE,
  category_id  INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  rollover     INTEGER NOT NULL DEFAULT 0 CHECK (rollover IN (0,1)),
  created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  amount       INTEGER NOT NULL DEFAULT 0,
  UNIQUE(household_id, category_id)
);

INSERT INTO budgets_new (
  id, household_id, category_id, rollover, created_at, updated_at, amount
)
SELECT id, 1, category_id, rollover, created_at, updated_at, amount
  FROM budgets;

DROP TABLE budgets;
ALTER TABLE budgets_new RENAME TO budgets;

CREATE INDEX idx_budgets_household_category
  ON budgets(household_id, category_id);

ALTER TABLE goals
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE INDEX idx_goals_household_sort
  ON goals(household_id, sort_order, id);

ALTER TABLE goal_account_allocations
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE INDEX idx_goal_allocations_household_goal
  ON goal_account_allocations(household_id, goal_id);

CREATE INDEX idx_goal_allocations_household_account
  ON goal_account_allocations(household_id, account_id);

ALTER TABLE account_balance_records
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE INDEX idx_account_balance_records_household_account_date
  ON account_balance_records(household_id, account_id, record_date DESC);

CREATE UNIQUE INDEX idx_account_balance_records_household_unique
  ON account_balance_records(household_id, account_id, record_date);

CREATE TABLE simplefin_config_new (
  household_id          INTEGER PRIMARY KEY REFERENCES households(id) ON DELETE CASCADE,
  access_url_encrypted  TEXT,
  last_sync_at          TEXT,
  sync_enabled          INTEGER NOT NULL DEFAULT 1 CHECK (sync_enabled IN (0,1)),
  updated_at            TEXT    NOT NULL DEFAULT (datetime('now')),
  cutover_date          TEXT
);

INSERT INTO simplefin_config_new (
  household_id, access_url_encrypted, last_sync_at, sync_enabled, updated_at, cutover_date
)
SELECT 1, access_url_encrypted, last_sync_at, sync_enabled, updated_at, cutover_date
  FROM simplefin_config
 WHERE id = 1;

DROP TABLE simplefin_config;
ALTER TABLE simplefin_config_new RENAME TO simplefin_config;

ALTER TABLE sync_log
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE INDEX idx_sync_log_household_started
  ON sync_log(household_id, started_at DESC);

CREATE TABLE app_settings_new (
  household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE,
  key          TEXT NOT NULL,
  value        TEXT NOT NULL,
  updated_at   TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (household_id, key)
);

INSERT INTO app_settings_new (household_id, key, value, updated_at)
SELECT 1, key, value, updated_at
  FROM app_settings;

DROP TABLE app_settings;
ALTER TABLE app_settings_new RENAME TO app_settings;

ALTER TABLE household_members
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE INDEX idx_household_members_household
  ON household_members(household_id, id);

ALTER TABLE household_income_records
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE INDEX idx_household_income_records_household_member_date
  ON household_income_records(household_id, member_id, effective_date DESC);

ALTER TABLE household_retirement_accounts
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE INDEX idx_household_retirement_accounts_household_member
  ON household_retirement_accounts(household_id, member_id);

ALTER TABLE upcoming_items
  ADD COLUMN household_id INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE;

CREATE INDEX idx_upcoming_items_household_status_date
  ON upcoming_items(household_id, status, next_date);

CREATE INDEX idx_upcoming_items_household_kind_date
  ON upcoming_items(household_id, kind, next_date);

CREATE TABLE upcoming_dismissed_suggestions_new (
  household_id    INTEGER NOT NULL DEFAULT 1 REFERENCES households(id) ON DELETE CASCADE,
  suggestion_key  TEXT NOT NULL,
  dismissed_at    TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (household_id, suggestion_key)
);

INSERT INTO upcoming_dismissed_suggestions_new (household_id, suggestion_key, dismissed_at)
SELECT 1, suggestion_key, dismissed_at
  FROM upcoming_dismissed_suggestions;

DROP TABLE upcoming_dismissed_suggestions;
ALTER TABLE upcoming_dismissed_suggestions_new RENAME TO upcoming_dismissed_suggestions;
