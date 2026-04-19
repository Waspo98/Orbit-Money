-- =============================================================================
-- 003_account_uniqueness.sql
-- =============================================================================
-- Prevents duplicate accounts when re-importing. Allows NULL institution or
-- NULL last4 (for manually-added accounts without a real bank backing) to
-- repeat — only enforced when both are present.
-- =============================================================================

CREATE UNIQUE INDEX idx_accounts_institution_last4
  ON accounts(institution, account_number_last4)
  WHERE institution IS NOT NULL AND account_number_last4 IS NOT NULL;
