-- =============================================================================
-- 036_credit_card_billing.sql
-- =============================================================================
-- Adds APR and promotional APR tracking to credit card profiles. Rates are basis
-- points (2499 = 24.99%). Adds promotional APR expiration date (YYYY-MM-DD).
-- =============================================================================

ALTER TABLE credit_card_profiles ADD COLUMN apr_bps INTEGER CHECK (apr_bps IS NULL OR (apr_bps >= 0 AND apr_bps <= 10000));
ALTER TABLE credit_card_profiles ADD COLUMN promo_apr_bps INTEGER CHECK (promo_apr_bps IS NULL OR (promo_apr_bps >= 0 AND promo_apr_bps <= 10000));
ALTER TABLE credit_card_profiles ADD COLUMN promo_end_date TEXT CHECK (promo_end_date IS NULL OR promo_end_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]');
