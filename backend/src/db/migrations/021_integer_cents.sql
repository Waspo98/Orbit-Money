-- =============================================================================
-- 021_integer_cents.sql
-- =============================================================================
-- Store money as integer cents instead of floating point dollar values.
--
-- Percent fields remain REAL because they are not money. Goal allocations used
-- one mixed column for both percent and fixed-dollar values, so this migration
-- splits that value into allocation_percent and allocation_amount.
-- =============================================================================

-- accounts
ALTER TABLE accounts RENAME COLUMN current_balance TO current_balance_dollars_legacy;
ALTER TABLE accounts ADD COLUMN current_balance INTEGER NOT NULL DEFAULT 0;
UPDATE accounts
   SET current_balance = ROUND(COALESCE(current_balance_dollars_legacy, 0) * 100);
ALTER TABLE accounts DROP COLUMN current_balance_dollars_legacy;

ALTER TABLE accounts RENAME COLUMN estimated_value TO estimated_value_dollars_legacy;
ALTER TABLE accounts ADD COLUMN estimated_value INTEGER;
UPDATE accounts
   SET estimated_value = CASE
     WHEN estimated_value_dollars_legacy IS NULL THEN NULL
     ELSE ROUND(estimated_value_dollars_legacy * 100)
   END;
ALTER TABLE accounts DROP COLUMN estimated_value_dollars_legacy;

-- transactions
ALTER TABLE transactions RENAME COLUMN amount TO amount_dollars_legacy;
ALTER TABLE transactions ADD COLUMN amount INTEGER NOT NULL DEFAULT 0;
UPDATE transactions
   SET amount = ROUND(COALESCE(amount_dollars_legacy, 0) * 100);
ALTER TABLE transactions DROP COLUMN amount_dollars_legacy;

-- budgets
ALTER TABLE budgets RENAME COLUMN amount TO amount_dollars_legacy;
ALTER TABLE budgets ADD COLUMN amount INTEGER NOT NULL DEFAULT 0;
UPDATE budgets
   SET amount = ROUND(COALESCE(amount_dollars_legacy, 0) * 100);
ALTER TABLE budgets DROP COLUMN amount_dollars_legacy;

-- goals
ALTER TABLE goals RENAME COLUMN target_amount TO target_amount_dollars_legacy;
ALTER TABLE goals ADD COLUMN target_amount INTEGER NOT NULL DEFAULT 0;
UPDATE goals
   SET target_amount = ROUND(COALESCE(target_amount_dollars_legacy, 0) * 100);
ALTER TABLE goals DROP COLUMN target_amount_dollars_legacy;

ALTER TABLE goals RENAME COLUMN current_amount TO current_amount_dollars_legacy;
ALTER TABLE goals ADD COLUMN current_amount INTEGER NOT NULL DEFAULT 0;
UPDATE goals
   SET current_amount = ROUND(COALESCE(current_amount_dollars_legacy, 0) * 100);
ALTER TABLE goals DROP COLUMN current_amount_dollars_legacy;

-- account_balance_records
ALTER TABLE account_balance_records RENAME COLUMN balance TO balance_dollars_legacy;
ALTER TABLE account_balance_records ADD COLUMN balance INTEGER NOT NULL DEFAULT 0;
UPDATE account_balance_records
   SET balance = ROUND(COALESCE(balance_dollars_legacy, 0) * 100);
ALTER TABLE account_balance_records DROP COLUMN balance_dollars_legacy;

-- goal_account_allocations
CREATE TABLE goal_account_allocations_new (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  goal_id            INTEGER NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
  account_id         INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  allocation_type    TEXT    NOT NULL CHECK (allocation_type IN ('percent','fixed')),
  allocation_percent REAL    NOT NULL DEFAULT 0 CHECK (allocation_percent >= 0),
  allocation_amount  INTEGER NOT NULL DEFAULT 0 CHECK (allocation_amount >= 0),
  reserve_amount     INTEGER NOT NULL DEFAULT 0 CHECK (reserve_amount >= 0),
  created_at         TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at         TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(goal_id, account_id)
);

INSERT INTO goal_account_allocations_new (
  id, goal_id, account_id, allocation_type, allocation_percent,
  allocation_amount, reserve_amount, created_at, updated_at
)
SELECT
  id,
  goal_id,
  account_id,
  allocation_type,
  CASE WHEN allocation_type = 'percent' THEN allocation_value ELSE 0 END,
  CASE WHEN allocation_type = 'fixed' THEN ROUND(COALESCE(allocation_value, 0) * 100) ELSE 0 END,
  ROUND(COALESCE(reserve_amount, 0) * 100),
  created_at,
  updated_at
FROM goal_account_allocations;

DROP TABLE goal_account_allocations;
ALTER TABLE goal_account_allocations_new RENAME TO goal_account_allocations;

CREATE INDEX idx_goal_allocations_goal
  ON goal_account_allocations(goal_id);

CREATE INDEX idx_goal_allocations_account
  ON goal_account_allocations(account_id);

-- household_members
ALTER TABLE household_members RENAME COLUMN gross_income_annual TO gross_income_annual_dollars_legacy;
ALTER TABLE household_members ADD COLUMN gross_income_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_members SET gross_income_annual = ROUND(COALESCE(gross_income_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_members DROP COLUMN gross_income_annual_dollars_legacy;

ALTER TABLE household_members RENAME COLUMN net_pay_per_period TO net_pay_per_period_dollars_legacy;
ALTER TABLE household_members ADD COLUMN net_pay_per_period INTEGER NOT NULL DEFAULT 0;
UPDATE household_members SET net_pay_per_period = ROUND(COALESCE(net_pay_per_period_dollars_legacy, 0) * 100);
ALTER TABLE household_members DROP COLUMN net_pay_per_period_dollars_legacy;

ALTER TABLE household_members RENAME COLUMN employee_contribution_annual TO employee_contribution_annual_dollars_legacy;
ALTER TABLE household_members ADD COLUMN employee_contribution_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_members SET employee_contribution_annual = ROUND(COALESCE(employee_contribution_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_members DROP COLUMN employee_contribution_annual_dollars_legacy;

ALTER TABLE household_members RENAME COLUMN employer_match_annual_cap TO employer_match_annual_cap_dollars_legacy;
ALTER TABLE household_members ADD COLUMN employer_match_annual_cap INTEGER NOT NULL DEFAULT 0;
UPDATE household_members SET employer_match_annual_cap = ROUND(COALESCE(employer_match_annual_cap_dollars_legacy, 0) * 100);
ALTER TABLE household_members DROP COLUMN employer_match_annual_cap_dollars_legacy;

ALTER TABLE household_members RENAME COLUMN health_premium_per_month TO health_premium_per_month_dollars_legacy;
ALTER TABLE household_members ADD COLUMN health_premium_per_month INTEGER NOT NULL DEFAULT 0;
UPDATE household_members SET health_premium_per_month = ROUND(COALESCE(health_premium_per_month_dollars_legacy, 0) * 100);
ALTER TABLE household_members DROP COLUMN health_premium_per_month_dollars_legacy;

ALTER TABLE household_members RENAME COLUMN hsa_contribution_annual TO hsa_contribution_annual_dollars_legacy;
ALTER TABLE household_members ADD COLUMN hsa_contribution_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_members SET hsa_contribution_annual = ROUND(COALESCE(hsa_contribution_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_members DROP COLUMN hsa_contribution_annual_dollars_legacy;

ALTER TABLE household_members RENAME COLUMN dependent_care_fsa_annual TO dependent_care_fsa_annual_dollars_legacy;
ALTER TABLE household_members ADD COLUMN dependent_care_fsa_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_members SET dependent_care_fsa_annual = ROUND(COALESCE(dependent_care_fsa_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_members DROP COLUMN dependent_care_fsa_annual_dollars_legacy;

ALTER TABLE household_members RENAME COLUMN other_benefits_annual TO other_benefits_annual_dollars_legacy;
ALTER TABLE household_members ADD COLUMN other_benefits_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_members SET other_benefits_annual = ROUND(COALESCE(other_benefits_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_members DROP COLUMN other_benefits_annual_dollars_legacy;

-- household_income_records
ALTER TABLE household_income_records RENAME COLUMN gross_income_annual TO gross_income_annual_dollars_legacy;
ALTER TABLE household_income_records ADD COLUMN gross_income_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_income_records SET gross_income_annual = ROUND(COALESCE(gross_income_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_income_records DROP COLUMN gross_income_annual_dollars_legacy;

ALTER TABLE household_income_records RENAME COLUMN net_pay_per_period TO net_pay_per_period_dollars_legacy;
ALTER TABLE household_income_records ADD COLUMN net_pay_per_period INTEGER NOT NULL DEFAULT 0;
UPDATE household_income_records SET net_pay_per_period = ROUND(COALESCE(net_pay_per_period_dollars_legacy, 0) * 100);
ALTER TABLE household_income_records DROP COLUMN net_pay_per_period_dollars_legacy;

ALTER TABLE household_income_records RENAME COLUMN employee_contribution_annual TO employee_contribution_annual_dollars_legacy;
ALTER TABLE household_income_records ADD COLUMN employee_contribution_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_income_records SET employee_contribution_annual = ROUND(COALESCE(employee_contribution_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_income_records DROP COLUMN employee_contribution_annual_dollars_legacy;

ALTER TABLE household_income_records RENAME COLUMN employer_match_annual_cap TO employer_match_annual_cap_dollars_legacy;
ALTER TABLE household_income_records ADD COLUMN employer_match_annual_cap INTEGER NOT NULL DEFAULT 0;
UPDATE household_income_records SET employer_match_annual_cap = ROUND(COALESCE(employer_match_annual_cap_dollars_legacy, 0) * 100);
ALTER TABLE household_income_records DROP COLUMN employer_match_annual_cap_dollars_legacy;

ALTER TABLE household_income_records RENAME COLUMN health_premium_per_month TO health_premium_per_month_dollars_legacy;
ALTER TABLE household_income_records ADD COLUMN health_premium_per_month INTEGER NOT NULL DEFAULT 0;
UPDATE household_income_records SET health_premium_per_month = ROUND(COALESCE(health_premium_per_month_dollars_legacy, 0) * 100);
ALTER TABLE household_income_records DROP COLUMN health_premium_per_month_dollars_legacy;

ALTER TABLE household_income_records RENAME COLUMN hsa_contribution_annual TO hsa_contribution_annual_dollars_legacy;
ALTER TABLE household_income_records ADD COLUMN hsa_contribution_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_income_records SET hsa_contribution_annual = ROUND(COALESCE(hsa_contribution_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_income_records DROP COLUMN hsa_contribution_annual_dollars_legacy;

ALTER TABLE household_income_records RENAME COLUMN dependent_care_fsa_annual TO dependent_care_fsa_annual_dollars_legacy;
ALTER TABLE household_income_records ADD COLUMN dependent_care_fsa_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_income_records SET dependent_care_fsa_annual = ROUND(COALESCE(dependent_care_fsa_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_income_records DROP COLUMN dependent_care_fsa_annual_dollars_legacy;

ALTER TABLE household_income_records RENAME COLUMN other_benefits_annual TO other_benefits_annual_dollars_legacy;
ALTER TABLE household_income_records ADD COLUMN other_benefits_annual INTEGER NOT NULL DEFAULT 0;
UPDATE household_income_records SET other_benefits_annual = ROUND(COALESCE(other_benefits_annual_dollars_legacy, 0) * 100);
ALTER TABLE household_income_records DROP COLUMN other_benefits_annual_dollars_legacy;
