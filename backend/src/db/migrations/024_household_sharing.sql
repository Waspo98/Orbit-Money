-- 024_household_sharing.sql
-- Allows a household owner/admin to share data with a partner by Authentik email.

CREATE TABLE household_shares (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id        INTEGER NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  invited_email       TEXT    NOT NULL,
  role                TEXT    NOT NULL DEFAULT 'member' CHECK (role IN ('admin','member')),
  created_by_user_id  INTEGER REFERENCES users(id) ON DELETE SET NULL,
  accepted_by_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
  accepted_at         TEXT,
  revoked_at          TEXT,
  created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX idx_household_shares_active_email
  ON household_shares(household_id, lower(invited_email))
  WHERE revoked_at IS NULL;

CREATE INDEX idx_household_shares_email
  ON household_shares(lower(invited_email), revoked_at);
