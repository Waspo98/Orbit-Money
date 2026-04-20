-- 013_mha_category_defaults.sql
-- Lets MHA Tracker auto-include transactions by category as well as account.

ALTER TABLE categories
  ADD COLUMN mha_default_eligible INTEGER NOT NULL DEFAULT 0 CHECK (mha_default_eligible IN (0,1));

CREATE INDEX idx_categories_mha_default
  ON categories(mha_default_eligible)
  WHERE mha_default_eligible = 1;
