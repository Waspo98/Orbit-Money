-- 031_push_notifications.sql
-- Stores browser push subscriptions and per-user delivery history. Notification
-- preferences themselves stay in user_preferences so they remain account-scoped
-- with the rest of the UI customization.

CREATE TABLE push_subscriptions (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id    INTEGER NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  endpoint        TEXT    NOT NULL,
  p256dh          TEXT    NOT NULL,
  auth            TEXT    NOT NULL,
  expiration_time INTEGER,
  user_agent      TEXT,
  created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at      TEXT    NOT NULL DEFAULT (datetime('now')),
  last_seen_at    TEXT    NOT NULL DEFAULT (datetime('now')),
  disabled_at     TEXT,
  UNIQUE(endpoint)
);

CREATE INDEX idx_push_subscriptions_household_user
  ON push_subscriptions(household_id, user_id, disabled_at);

CREATE TABLE notification_delivery_log (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  household_id INTEGER NOT NULL REFERENCES households(id) ON DELETE CASCADE,
  user_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type         TEXT    NOT NULL,
  dedupe_key   TEXT    NOT NULL,
  title        TEXT,
  target_url   TEXT,
  sent_at      TEXT    NOT NULL DEFAULT (datetime('now')),
  status       TEXT    NOT NULL DEFAULT 'sent' CHECK (status IN ('sent','failed')),
  error        TEXT,
  UNIQUE(household_id, user_id, type, dedupe_key)
);

CREATE INDEX idx_notification_delivery_household_type
  ON notification_delivery_log(household_id, type, sent_at DESC);
