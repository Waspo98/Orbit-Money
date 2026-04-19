-- =============================================================================
-- 009_budget_monthly.sql
-- =============================================================================
-- Phase 1 budgets (v14). The original `budgets` table from migration 001
-- used `category_id UNIQUE` — one row per category, applied globally forever.
-- We need per-month granularity so the user can set different caps across
-- months AND keep historical budgets intact when backward-navigating.
--
-- New shape: (category_id, month) is the composite unique key. `month` is
-- YYYY-MM, enforced in application code (SQLite CHECK with strftime would
-- tangle with NOT NULL DEFAULT semantics).
--
-- Since the Budgets page has been a placeholder through v13, the existing
-- table is expected to be empty in practice. The SELECT INTO below handles
-- any stragglers by stamping them with the current month.
-- =============================================================================

CREATE TABLE budgets_new (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  category_id  INTEGER NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
  month        TEXT    NOT NULL,
  amount       REAL    NOT NULL,
  rollover     INTEGER NOT NULL DEFAULT 0 CHECK (rollover IN (0,1)),
  created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE (category_id, month)
);

-- Seed any existing rows with the current month so nothing is lost on
-- upgrade. strftime('%Y-%m', 'now') returns 'YYYY-MM' in UTC; if a user
-- is in Chicago at 11 PM local on the last day of the month, they might
-- land one month ahead — acceptable for a one-time migration of rows
-- that almost certainly don't exist in the target DB.
INSERT INTO budgets_new (id, category_id, month, amount, rollover, created_at, updated_at)
SELECT id, category_id, strftime('%Y-%m', 'now'), amount, rollover, created_at, updated_at
  FROM budgets;

DROP TABLE budgets;
ALTER TABLE budgets_new RENAME TO budgets;

CREATE INDEX idx_budgets_month ON budgets(month);
CREATE INDEX idx_budgets_category ON budgets(category_id);
