-- =============================================================================
-- 008_original_edited_split.sql
-- =============================================================================
-- Re-architects transactions so that original (bank/import) values are
-- preserved forever and rules/user edits write to separate `edited_*` columns.
-- Display reads COALESCE(edited_X, original_X) at the API layer.
--
-- Columns affected:
--   merchant → RENAMED to original_merchant (never mutated after insert)
--   new:  edited_merchant, edited_merchant_source
--   new:  edited_category_id, edited_category_id_source
--   new:  edited_is_transfer, edited_is_transfer_source
--   new:  edited_is_ignored, edited_is_ignored_source
--
-- `edited_*_source` is either NULL, 'user', or 'rule:{id}'. A NULL source
-- means no edit; the display falls back to the original. A 'user' source
-- means a manual edit (rules never touch it). A 'rule:{id}' source lets us
-- revert precisely which edits were caused by a rule when that rule is
-- deleted.
--
-- CHECK constraints on the new columns are enforced in application code
-- rather than SQLite — ADD COLUMN has restrictions on expressions.
-- =============================================================================

-- Rename the live merchant column to original_merchant.
-- (SQLite ≥3.25 supports ALTER TABLE RENAME COLUMN, which better-sqlite3 ships.)
ALTER TABLE transactions RENAME COLUMN merchant TO original_merchant;

-- Add the edited_* columns and source trackers.
ALTER TABLE transactions ADD COLUMN edited_merchant           TEXT;
ALTER TABLE transactions ADD COLUMN edited_merchant_source    TEXT;
ALTER TABLE transactions ADD COLUMN edited_category_id        INTEGER;
ALTER TABLE transactions ADD COLUMN edited_category_id_source TEXT;
ALTER TABLE transactions ADD COLUMN edited_is_transfer        INTEGER;
ALTER TABLE transactions ADD COLUMN edited_is_transfer_source TEXT;
ALTER TABLE transactions ADD COLUMN edited_is_ignored         INTEGER;
ALTER TABLE transactions ADD COLUMN edited_is_ignored_source  TEXT;

-- Rebuild the merchant index to point at the renamed column.
-- (The old index was created ON transactions(merchant); the rename preserved
--  it, but we explicitly drop + recreate to keep the name consistent with the
--  new column name for any DBA inspection.)
DROP INDEX IF EXISTS idx_transactions_merchant;
CREATE INDEX idx_transactions_merchant         ON transactions(original_merchant);
CREATE INDEX idx_transactions_edited_merchant  ON transactions(edited_merchant)
  WHERE edited_merchant IS NOT NULL;
CREATE INDEX idx_transactions_edited_merchant_source
  ON transactions(edited_merchant_source)
  WHERE edited_merchant_source IS NOT NULL;
