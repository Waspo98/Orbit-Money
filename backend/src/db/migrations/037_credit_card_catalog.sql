-- =============================================================================
-- 037_credit_card_catalog.sql
-- =============================================================================
-- Creates the self-hosted credit card template catalog and artwork cache tables
-- for 1-tap card setup, daily Sign-Up Bonus (SUB) synchronization from
-- credit-card-bonuses-api, and proxied official card face artwork.
-- Also adds optional artwork_url to credit_card_profiles alongside image_url.
-- =============================================================================

CREATE TABLE IF NOT EXISTS credit_card_catalog (
  id                                TEXT PRIMARY KEY,
  bonus_api_card_id                 TEXT,
  short_label                       TEXT NOT NULL,
  card_name                         TEXT NOT NULL,
  issuer_name                       TEXT NOT NULL,
  network                           TEXT NOT NULL,
  art_key                           TEXT NOT NULL,
  upstream_image_path               TEXT,
  annual_fee_cents                  INTEGER NOT NULL DEFAULT 0,
  is_annual_fee_waived              INTEGER NOT NULL DEFAULT 0,
  is_business_card                  INTEGER NOT NULL DEFAULT 0,
  counts_toward_524_override        INTEGER,
  sub_cooldown_months               INTEGER,
  default_sub_reward_text           TEXT,
  default_sub_estimated_value_cents INTEGER,
  default_sub_target_spend_cents    INTEGER,
  sub_spend_window_days             INTEGER DEFAULT 90,
  is_elevated_sub_offer             INTEGER NOT NULL DEFAULT 0,
  reward_categories_json            TEXT NOT NULL DEFAULT '[]',
  benefits_json                     TEXT NOT NULL DEFAULT '[]',
  sort_rank                         INTEGER NOT NULL DEFAULT 500,
  is_curated                        INTEGER NOT NULL DEFAULT 0,
  as_of                             TEXT NOT NULL DEFAULT '2026-11',
  last_synced_at                    TEXT,
  updated_at                        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_credit_card_catalog_issuer
  ON credit_card_catalog(issuer_name, sort_rank, card_name);

CREATE INDEX IF NOT EXISTS idx_credit_card_catalog_bonus_api_id
  ON credit_card_catalog(bonus_api_card_id);

CREATE TABLE IF NOT EXISTS credit_card_artwork_cache (
  card_id       TEXT PRIMARY KEY,
  content_type  TEXT NOT NULL,
  image_blob    BLOB NOT NULL,
  fetched_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

ALTER TABLE credit_card_profiles ADD COLUMN artwork_url TEXT;
