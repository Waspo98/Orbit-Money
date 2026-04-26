-- 022_upcoming_items.sql
-- Persistent upcoming bills, subscriptions, and income.

CREATE TABLE IF NOT EXISTS upcoming_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('bill', 'subscription', 'income')),
  source TEXT NOT NULL DEFAULT 'manual' CHECK (source IN ('manual', 'transaction', 'suggestion')),
  merchant TEXT,
  amount INTEGER NOT NULL DEFAULT 0,
  direction TEXT NOT NULL DEFAULT 'expense' CHECK (direction IN ('expense', 'income')),
  frequency_type TEXT NOT NULL DEFAULT 'monthly'
    CHECK (frequency_type IN ('weekly', 'biweekly', 'semimonthly', 'monthly', 'bimonthly', 'yearly', 'custom')),
  frequency_interval INTEGER NOT NULL DEFAULT 1,
  frequency_unit TEXT NOT NULL DEFAULT 'months' CHECK (frequency_unit IN ('days', 'weeks', 'months')),
  next_date TEXT NOT NULL,
  category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
  account_id INTEGER REFERENCES accounts(id) ON DELETE SET NULL,
  source_transaction_id INTEGER REFERENCES transactions(id) ON DELETE SET NULL,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'paused')),
  notes TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_upcoming_items_status_date
  ON upcoming_items(status, next_date);

CREATE INDEX IF NOT EXISTS idx_upcoming_items_kind_date
  ON upcoming_items(kind, next_date);

CREATE TABLE IF NOT EXISTS upcoming_dismissed_suggestions (
  suggestion_key TEXT PRIMARY KEY,
  dismissed_at TEXT NOT NULL DEFAULT (datetime('now'))
);
