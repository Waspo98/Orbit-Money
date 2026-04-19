-- =============================================================================
-- 006_account_sort_order.sql
-- =============================================================================
-- Adds a sort_order column to accounts so users can drag-and-drop to reorder.
-- Seeds with existing alphabetical order (archived first falls to bottom
-- naturally since they're sorted separately in the UI).
-- =============================================================================

ALTER TABLE accounts ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

-- Seed with current name-based order, in increments of 10 to leave room for
-- insertion without renumbering everything.
UPDATE accounts
   SET sort_order = (
     SELECT (rn - 1) * 10
       FROM (
         SELECT id, ROW_NUMBER() OVER (ORDER BY is_archived ASC, name ASC) AS rn
           FROM accounts
       ) ranked
      WHERE ranked.id = accounts.id
   );

CREATE INDEX idx_accounts_sort_order ON accounts(sort_order);
