-- 027_upcoming_recurrence_projection.sql
-- Adds richer recurrence rules and amount projection settings to Upcoming.

ALTER TABLE upcoming_items
  ADD COLUMN recurrence_rule TEXT;

ALTER TABLE upcoming_items
  ADD COLUMN amount_strategy TEXT NOT NULL DEFAULT 'fixed'
    CHECK (amount_strategy IN ('fixed', 'history_average'));

ALTER TABLE upcoming_items
  ADD COLUMN amount_lookback_months INTEGER NOT NULL DEFAULT 6;
