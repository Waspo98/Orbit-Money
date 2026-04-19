-- =============================================================================
-- 010_budget_global_amount.sql
-- =============================================================================
-- Feedback from v14 shipping: users want budgets to be "the amount I aim to
-- spend each month" — not per-month amounts. Month navigation on the
-- Budgets page remains (to view past spending against the same cap), but
-- the underlying amount is global per category.
--
-- This migration:
--   1. Rebuilds `budgets` without the `month` column, restoring the
--      original one-row-per-category shape from migration 001.
--   2. Consolidates any v14 rows down to one-per-category by keeping the
--      amount from the most recent month (ordered by month DESC).
--
-- In practice Neal's DB has zero budget rows at the time this ships (v14
-- was installed yesterday and the page was new), so the consolidation is
-- a no-op. The query is written defensively so it handles the non-empty
-- case cleanly too.
-- =============================================================================

CREATE TABLE budgets_new (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  category_id  INTEGER NOT NULL UNIQUE REFERENCES categories(id) ON DELETE CASCADE,
  amount       REAL    NOT NULL,
  rollover     INTEGER NOT NULL DEFAULT 0 CHECK (rollover IN (0,1)),
  created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- For each category, pick the budget row with the highest month value
-- (most recent). ROW_NUMBER() + PARTITION BY is the cleanest SQL way.
INSERT INTO budgets_new (category_id, amount, rollover, created_at, updated_at)
SELECT category_id, amount, rollover, created_at, updated_at
  FROM (
    SELECT
      category_id, amount, rollover, created_at, updated_at,
      ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY month DESC) AS rn
    FROM budgets
  )
 WHERE rn = 1;

DROP TABLE budgets;
ALTER TABLE budgets_new RENAME TO budgets;

-- The old indexes referenced columns that no longer exist — recreate just
-- what we need now (category_id is already UNIQUE-indexed implicitly).
