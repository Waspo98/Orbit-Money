-- =============================================================================
-- 034_clean_credit_card_profiles.sql
-- =============================================================================
-- Removes external catalog-specific columns from credit card profiles and keeps
-- the user-entered card name as first-class local data.
-- =============================================================================

ALTER TABLE credit_card_profiles RENAME TO credit_card_profiles_old;

CREATE TABLE credit_card_profiles (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id          INTEGER NOT NULL,
  account_id            INTEGER NOT NULL,
  card_name             TEXT,
  issuer_name           TEXT,
  network               TEXT,
  image_url             TEXT,
  annual_fee            INTEGER,
  annual_fee_post_date  TEXT,
  credit_limit          INTEGER,
  authorized_users_json TEXT NOT NULL DEFAULT '[]',
  reward_categories_json TEXT NOT NULL DEFAULT '[]',
  benefits_json         TEXT NOT NULL DEFAULT '[]',
  notes                 TEXT,
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

INSERT INTO credit_card_profiles (
  id,
  household_id,
  account_id,
  card_name,
  issuer_name,
  network,
  image_url,
  annual_fee,
  annual_fee_post_date,
  credit_limit,
  authorized_users_json,
  reward_categories_json,
  benefits_json,
  notes,
  created_at,
  updated_at
)
SELECT
  id,
  household_id,
  account_id,
  cardapi_name,
  issuer_name,
  network,
  image_url,
  annual_fee,
  annual_fee_post_date,
  credit_limit,
  authorized_users_json,
  reward_categories_json,
  benefits_json,
  notes,
  created_at,
  updated_at
FROM credit_card_profiles_old;

DROP TABLE credit_card_profiles_old;

CREATE INDEX idx_credit_card_profiles_household_account
  ON credit_card_profiles(household_id, account_id);
