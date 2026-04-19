-- 011_account_estimated_value.sql
-- Stores a manually entered home value for mortgage accounts so real estate
-- equity can be included in net worth without changing mortgage balances.

ALTER TABLE accounts ADD COLUMN estimated_value REAL;
