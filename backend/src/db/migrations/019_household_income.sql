-- =============================================================================
-- 019_household_income.sql
-- =============================================================================
-- Household member profiles plus dated compensation and benefit snapshots.
-- This is intentionally additive so future goal and retirement projections can
-- read historical income without changing existing account, goal, or import data.
-- =============================================================================

CREATE TABLE household_members (
  id                             INTEGER PRIMARY KEY AUTOINCREMENT,
  name                           TEXT    NOT NULL,
  role                           TEXT    NOT NULL DEFAULT 'adult',
  birth_date                     TEXT,
  employment_status              TEXT    NOT NULL DEFAULT 'employed',
  employer                       TEXT,
  job_title                      TEXT,
  gross_income_annual            REAL    NOT NULL DEFAULT 0,
  net_pay_per_period             REAL    NOT NULL DEFAULT 0,
  pay_frequency                  TEXT    NOT NULL DEFAULT 'biweekly',
  pay_periods_per_year           REAL    NOT NULL DEFAULT 26,
  retirement_account_type        TEXT,
  employee_contribution_percent  REAL    NOT NULL DEFAULT 0,
  employee_contribution_annual   REAL    NOT NULL DEFAULT 0,
  employer_match_percent         REAL    NOT NULL DEFAULT 0,
  employer_match_limit_percent   REAL    NOT NULL DEFAULT 0,
  employer_match_annual_cap      REAL    NOT NULL DEFAULT 0,
  health_premium_per_month       REAL    NOT NULL DEFAULT 0,
  hsa_contribution_annual        REAL    NOT NULL DEFAULT 0,
  dependent_care_fsa_annual      REAL    NOT NULL DEFAULT 0,
  other_benefits_annual          REAL    NOT NULL DEFAULT 0,
  notes                          TEXT,
  created_at                     TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at                     TEXT    NOT NULL DEFAULT (datetime('now')),
  CHECK (birth_date IS NULL OR birth_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]')
);

CREATE TABLE household_income_records (
  id                             INTEGER PRIMARY KEY AUTOINCREMENT,
  member_id                      INTEGER NOT NULL REFERENCES household_members(id) ON DELETE CASCADE,
  effective_date                 TEXT    NOT NULL,
  gross_income_annual            REAL    NOT NULL DEFAULT 0,
  net_pay_per_period             REAL    NOT NULL DEFAULT 0,
  pay_frequency                  TEXT    NOT NULL DEFAULT 'biweekly',
  pay_periods_per_year           REAL    NOT NULL DEFAULT 26,
  employee_contribution_percent  REAL    NOT NULL DEFAULT 0,
  employee_contribution_annual   REAL    NOT NULL DEFAULT 0,
  employer_match_percent         REAL    NOT NULL DEFAULT 0,
  employer_match_limit_percent   REAL    NOT NULL DEFAULT 0,
  employer_match_annual_cap      REAL    NOT NULL DEFAULT 0,
  health_premium_per_month       REAL    NOT NULL DEFAULT 0,
  hsa_contribution_annual        REAL    NOT NULL DEFAULT 0,
  dependent_care_fsa_annual      REAL    NOT NULL DEFAULT 0,
  other_benefits_annual          REAL    NOT NULL DEFAULT 0,
  source                         TEXT    NOT NULL DEFAULT 'manual',
  notes                          TEXT,
  created_at                     TEXT    NOT NULL DEFAULT (datetime('now')),
  updated_at                     TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE(member_id, effective_date),
  CHECK (effective_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]')
);

CREATE INDEX idx_household_income_records_member_date
  ON household_income_records(member_id, effective_date DESC);
