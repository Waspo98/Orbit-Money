-- 030_user_preferences.sql
-- Stores per-account UI preferences on the server so customization follows a
-- user across browsers while remaining scoped to the active household.

CREATE TABLE user_preferences (
  household_id INTEGER NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  key          TEXT    NOT NULL,
  value_json   TEXT    NOT NULL,
  updated_at   TEXT    NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (household_id, user_id, key)
);

CREATE INDEX idx_user_preferences_household_user
  ON user_preferences(household_id, user_id, updated_at DESC);
