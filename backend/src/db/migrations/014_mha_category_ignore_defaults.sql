ALTER TABLE categories
  ADD COLUMN mha_default_ignored INTEGER NOT NULL DEFAULT 0 CHECK (mha_default_ignored IN (0, 1));

CREATE INDEX IF NOT EXISTS idx_categories_mha_default_ignored
  ON categories(mha_default_ignored);
