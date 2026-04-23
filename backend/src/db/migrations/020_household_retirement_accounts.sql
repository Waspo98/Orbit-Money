-- =============================================================================
-- 020_household_retirement_accounts.sql
-- =============================================================================
-- Links household members to existing account rows for retirement projections.
-- Account balances stay owned by the accounts table; this table only records
-- which household member should receive retirement-planning credit for them.
-- =============================================================================

CREATE TABLE household_retirement_accounts (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  member_id     INTEGER NOT NULL REFERENCES household_members(id) ON DELETE CASCADE,
  account_id    INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  account_kind  TEXT    NOT NULL DEFAULT 'other',
  created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at    TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(member_id, account_id)
);

CREATE INDEX idx_household_retirement_accounts_member
  ON household_retirement_accounts(member_id);

CREATE INDEX idx_household_retirement_accounts_account
  ON household_retirement_accounts(account_id);
