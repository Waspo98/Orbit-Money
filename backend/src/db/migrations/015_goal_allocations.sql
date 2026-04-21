-- Adds multi-account savings goal allocations.
-- The original goals table remains in place for compatibility; current_amount
-- is now computed from linked account allocations at read time.

ALTER TABLE goals ADD COLUMN kind TEXT NOT NULL DEFAULT 'custom';
ALTER TABLE goals ADD COLUMN icon TEXT NOT NULL DEFAULT 'target';
ALTER TABLE goals ADD COLUMN notes TEXT;

CREATE TABLE goal_account_allocations (
  id                INTEGER PRIMARY KEY AUTOINCREMENT,
  goal_id           INTEGER NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
  account_id        INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  allocation_type   TEXT    NOT NULL CHECK (allocation_type IN ('percent','fixed')),
  allocation_value  REAL    NOT NULL DEFAULT 0 CHECK (allocation_value >= 0),
  reserve_amount    REAL    NOT NULL DEFAULT 0 CHECK (reserve_amount >= 0),
  created_at        TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at        TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(goal_id, account_id)
);

CREATE INDEX idx_goal_allocations_goal
  ON goal_account_allocations(goal_id);

CREATE INDEX idx_goal_allocations_account
  ON goal_account_allocations(account_id);
