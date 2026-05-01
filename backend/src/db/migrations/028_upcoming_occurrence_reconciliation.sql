-- 028_upcoming_occurrence_reconciliation.sql
-- Tracks generated Upcoming occurrences so the backend can later tell whether
-- projected bills, income, subscriptions, and giving transactions actually
-- appeared in synced transaction history.

CREATE TABLE upcoming_occurrences (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id           INTEGER NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  upcoming_item_id       INTEGER NOT NULL REFERENCES upcoming_items(id) ON DELETE CASCADE,
  expected_date          TEXT    NOT NULL,
  grace_until            TEXT    NOT NULL,
  status                 TEXT    NOT NULL DEFAULT 'pending'
                         CHECK (status IN ('pending', 'matched', 'missed')),
  matched_transaction_id INTEGER REFERENCES transactions(id) ON DELETE SET NULL,
  matched_at             TEXT,
  missed_at              TEXT,
  created_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(household_id, upcoming_item_id, expected_date)
);

CREATE INDEX idx_upcoming_occurrences_household_status_date
  ON upcoming_occurrences(household_id, status, expected_date);

CREATE INDEX idx_upcoming_occurrences_household_grace
  ON upcoming_occurrences(household_id, status, grace_until);

CREATE INDEX idx_upcoming_occurrences_match
  ON upcoming_occurrences(household_id, matched_transaction_id)
  WHERE matched_transaction_id IS NOT NULL;
