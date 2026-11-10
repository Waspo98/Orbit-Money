-- =============================================================================
-- 035_credit_card_strategy.sql
-- =============================================================================
-- Adds statement/payment cycle days and strategy_json (5/24 lifecycle, player
-- assignment, SUB spend tracker, and SUB cooldown metadata) to credit_card_profiles.
-- =============================================================================

ALTER TABLE credit_card_profiles ADD COLUMN statement_closing_day INTEGER;
ALTER TABLE credit_card_profiles ADD COLUMN payment_due_day INTEGER;
ALTER TABLE credit_card_profiles ADD COLUMN strategy_json TEXT NOT NULL DEFAULT '{}';
