-- =============================================================================
-- 033_credit_card_profiles.sql
-- =============================================================================
-- Stores household-entered credit card details for credit-type accounts. Provider
-- fields come from optional catalog lookups, while personal fields such as
-- limits, fee timing, and authorized users remain local household data.
-- =============================================================================

CREATE TABLE credit_card_profiles (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id          INTEGER NOT NULL,
  account_id            INTEGER NOT NULL,
  cardapi_slug          TEXT,
  cardapi_name          TEXT,
  issuer_slug           TEXT,
  issuer_name           TEXT,
  network               TEXT,
  country               TEXT,
  card_type             TEXT,
  image_url             TEXT,
  product_url           TEXT,
  annual_fee            INTEGER,
  annual_fee_post_date  TEXT,
  credit_limit          INTEGER,
  authorized_users_json TEXT NOT NULL DEFAULT '[]',
  reward_categories_json TEXT NOT NULL DEFAULT '[]',
  benefits_json         TEXT NOT NULL DEFAULT '[]',
  notes                 TEXT,
  source_updated_at     TEXT,
  created_at            TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at            TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(household_id, account_id),
  FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE CASCADE,
  FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
  CHECK (annual_fee IS NULL OR annual_fee >= 0),
  CHECK (credit_limit IS NULL OR credit_limit >= 0),
  CHECK (
    annual_fee_post_date IS NULL
    OR annual_fee_post_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
  )
);

CREATE INDEX idx_credit_card_profiles_household_account
  ON credit_card_profiles(household_id, account_id);
