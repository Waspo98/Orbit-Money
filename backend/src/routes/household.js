import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';

const router = express.Router();

const ROLES = new Set(['adult', 'child']);
const EMPLOYMENT_STATUSES = new Set([
  'employed',
  'self_employed',
  'stay_at_home',
  'student',
  'retired',
  'unemployed',
  'other'
]);
const PAY_FREQUENCIES = new Set(['weekly', 'biweekly', 'semimonthly', 'monthly', 'annual', 'none']);
const RETIREMENT_TYPES = new Set(['none', '401k', '403b', '457b', 'ira', 'roth_ira', 'sep_ira', 'simple_ira', 'pension', 'other']);

const PAY_PERIODS = {
  weekly: 52,
  biweekly: 26,
  semimonthly: 24,
  monthly: 12,
  annual: 1,
  none: 0
};

function householdError(message, status = 400) {
  const err = new Error(message);
  err.status = status;
  return err;
}

function validDate(value) {
  return !value || /^\d{4}-\d{2}-\d{2}$/.test(value);
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function cleanString(value, max = 120) {
  if (value === null || value === undefined) return null;
  const text = String(value).trim();
  return text ? text.slice(0, max) : null;
}

function cleanNumber(value, fallback = 0) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(0, number);
}

function periodsFor(payFrequency, body) {
  const explicit = cleanNumber(body?.pay_periods_per_year, NaN);
  if (Number.isFinite(explicit)) return Math.min(366, explicit);
  return PAY_PERIODS[payFrequency] ?? 26;
}

function normalizeMemberBody(body) {
  const name = cleanString(body?.name, 90);
  if (!name) throw householdError('Name is required.');

  const role = ROLES.has(body?.role) ? body.role : 'adult';
  const employmentStatus = EMPLOYMENT_STATUSES.has(body?.employment_status)
    ? body.employment_status
    : 'employed';
  const payFrequency = PAY_FREQUENCIES.has(body?.pay_frequency)
    ? body.pay_frequency
    : 'biweekly';
  const retirementType = RETIREMENT_TYPES.has(body?.retirement_account_type)
    ? body.retirement_account_type
    : 'none';
  const birthDate = body?.birth_date ? String(body.birth_date) : null;
  if (!validDate(birthDate)) throw householdError('birth_date must use YYYY-MM-DD.');

  return {
    name,
    role,
    birth_date: birthDate,
    employment_status: employmentStatus,
    employer: cleanString(body?.employer, 120),
    job_title: cleanString(body?.job_title, 120),
    gross_income_annual: cleanNumber(body?.gross_income_annual),
    net_pay_per_period: cleanNumber(body?.net_pay_per_period),
    pay_frequency: payFrequency,
    pay_periods_per_year: periodsFor(payFrequency, body),
    retirement_account_type: retirementType,
    employee_contribution_percent: Math.min(100, cleanNumber(body?.employee_contribution_percent)),
    employee_contribution_annual: cleanNumber(body?.employee_contribution_annual),
    employer_match_percent: Math.min(100, cleanNumber(body?.employer_match_percent)),
    employer_match_limit_percent: Math.min(100, cleanNumber(body?.employer_match_limit_percent)),
    employer_match_annual_cap: cleanNumber(body?.employer_match_annual_cap),
    health_premium_per_month: cleanNumber(body?.health_premium_per_month),
    hsa_contribution_annual: cleanNumber(body?.hsa_contribution_annual),
    dependent_care_fsa_annual: cleanNumber(body?.dependent_care_fsa_annual),
    other_benefits_annual: cleanNumber(body?.other_benefits_annual),
    notes: cleanString(body?.notes, 800)
  };
}

function normalizeIncomeRecordBody(member, body) {
  const effectiveDate = body?.effective_date ? String(body.effective_date) : today();
  if (!validDate(effectiveDate)) throw householdError('effective_date must use YYYY-MM-DD.');
  const payFrequency = PAY_FREQUENCIES.has(body?.pay_frequency)
    ? body.pay_frequency
    : member.pay_frequency;

  return {
    member_id: member.id,
    effective_date: effectiveDate,
    gross_income_annual: cleanNumber(body?.gross_income_annual, member.gross_income_annual),
    net_pay_per_period: cleanNumber(body?.net_pay_per_period, member.net_pay_per_period),
    pay_frequency: payFrequency,
    pay_periods_per_year: periodsFor(payFrequency, body?.pay_periods_per_year == null ? member : body),
    employee_contribution_percent: Math.min(100, cleanNumber(body?.employee_contribution_percent, member.employee_contribution_percent)),
    employee_contribution_annual: cleanNumber(body?.employee_contribution_annual, member.employee_contribution_annual),
    employer_match_percent: Math.min(100, cleanNumber(body?.employer_match_percent, member.employer_match_percent)),
    employer_match_limit_percent: Math.min(100, cleanNumber(body?.employer_match_limit_percent, member.employer_match_limit_percent)),
    employer_match_annual_cap: cleanNumber(body?.employer_match_annual_cap, member.employer_match_annual_cap),
    health_premium_per_month: cleanNumber(body?.health_premium_per_month, member.health_premium_per_month),
    hsa_contribution_annual: cleanNumber(body?.hsa_contribution_annual, member.hsa_contribution_annual),
    dependent_care_fsa_annual: cleanNumber(body?.dependent_care_fsa_annual, member.dependent_care_fsa_annual),
    other_benefits_annual: cleanNumber(body?.other_benefits_annual, member.other_benefits_annual),
    source: cleanString(body?.source, 40) || 'manual',
    notes: cleanString(body?.notes, 800)
  };
}

function employeeContributionAnnual(row) {
  const explicit = Number(row.employee_contribution_annual) || 0;
  if (explicit > 0) return explicit;
  return (Number(row.gross_income_annual) || 0) * ((Number(row.employee_contribution_percent) || 0) / 100);
}

function employerMatchAnnual(row) {
  const gross = Number(row.gross_income_annual) || 0;
  const employeePercent = Number(row.employee_contribution_percent) || 0;
  const matchPercent = Number(row.employer_match_percent) || 0;
  const limitPercent = Number(row.employer_match_limit_percent) || employeePercent;
  const cap = Number(row.employer_match_annual_cap) || 0;
  const matchedBase = gross * (Math.min(employeePercent, limitPercent) / 100);
  const estimate = matchedBase * (matchPercent / 100);
  return cap > 0 ? Math.min(estimate, cap) : estimate;
}

function decorateMember(member) {
  const netPayAnnual = (Number(member.net_pay_per_period) || 0) * (Number(member.pay_periods_per_year) || 0);
  const employeeRetirementAnnual = employeeContributionAnnual(member);
  const employerRetirementAnnual = employerMatchAnnual(member);
  const totalBenefitsAnnual =
    employerRetirementAnnual +
    (Number(member.hsa_contribution_annual) || 0) +
    (Number(member.dependent_care_fsa_annual) || 0) +
    (Number(member.other_benefits_annual) || 0);

  return {
    ...member,
    net_pay_annual: netPayAnnual,
    employee_retirement_annual: employeeRetirementAnnual,
    employer_retirement_annual: employerRetirementAnnual,
    total_benefits_annual: totalBenefitsAnnual,
    household_value_annual: netPayAnnual + totalBenefitsAnnual
  };
}

function buildPayload() {
  const members = db
    .prepare(
      `SELECT *
         FROM household_members
        ORDER BY id ASC`
    )
    .all()
    .map(decorateMember);

  const records = db
    .prepare(
      `SELECT r.*, m.name AS member_name
         FROM household_income_records r
         JOIN household_members m ON m.id = r.member_id
        ORDER BY r.effective_date DESC, r.id DESC
        LIMIT 80`
    )
    .all()
    .map((row) => ({
      ...row,
      net_pay_annual: (Number(row.net_pay_per_period) || 0) * (Number(row.pay_periods_per_year) || 0),
      employee_retirement_annual: employeeContributionAnnual(row),
      employer_retirement_annual: employerMatchAnnual(row)
    }));

  const summary = members.reduce(
    (total, member) => ({
      member_count: total.member_count + 1,
      earners: total.earners + (member.employment_status === 'employed' || member.employment_status === 'self_employed' ? 1 : 0),
      gross_income_annual: total.gross_income_annual + (Number(member.gross_income_annual) || 0),
      net_pay_annual: total.net_pay_annual + (Number(member.net_pay_annual) || 0),
      employee_retirement_annual: total.employee_retirement_annual + (Number(member.employee_retirement_annual) || 0),
      employer_retirement_annual: total.employer_retirement_annual + (Number(member.employer_retirement_annual) || 0),
      total_benefits_annual: total.total_benefits_annual + (Number(member.total_benefits_annual) || 0)
    }),
    {
      member_count: 0,
      earners: 0,
      gross_income_annual: 0,
      net_pay_annual: 0,
      employee_retirement_annual: 0,
      employer_retirement_annual: 0,
      total_benefits_annual: 0
    }
  );

  return { summary, members, records };
}

function insertIncomeRecord(record) {
  db.prepare(
    `INSERT INTO household_income_records (
       member_id, effective_date, gross_income_annual, net_pay_per_period,
       pay_frequency, pay_periods_per_year, employee_contribution_percent,
       employee_contribution_annual, employer_match_percent,
       employer_match_limit_percent, employer_match_annual_cap,
       health_premium_per_month, hsa_contribution_annual,
       dependent_care_fsa_annual, other_benefits_annual, source, notes
     ) VALUES (
       @member_id, @effective_date, @gross_income_annual, @net_pay_per_period,
       @pay_frequency, @pay_periods_per_year, @employee_contribution_percent,
       @employee_contribution_annual, @employer_match_percent,
       @employer_match_limit_percent, @employer_match_annual_cap,
       @health_premium_per_month, @hsa_contribution_annual,
       @dependent_care_fsa_annual, @other_benefits_annual, @source, @notes
     )
     ON CONFLICT(member_id, effective_date) DO UPDATE SET
       gross_income_annual = excluded.gross_income_annual,
       net_pay_per_period = excluded.net_pay_per_period,
       pay_frequency = excluded.pay_frequency,
       pay_periods_per_year = excluded.pay_periods_per_year,
       employee_contribution_percent = excluded.employee_contribution_percent,
       employee_contribution_annual = excluded.employee_contribution_annual,
       employer_match_percent = excluded.employer_match_percent,
       employer_match_limit_percent = excluded.employer_match_limit_percent,
       employer_match_annual_cap = excluded.employer_match_annual_cap,
       health_premium_per_month = excluded.health_premium_per_month,
       hsa_contribution_annual = excluded.hsa_contribution_annual,
       dependent_care_fsa_annual = excluded.dependent_care_fsa_annual,
       other_benefits_annual = excluded.other_benefits_annual,
       source = excluded.source,
       notes = excluded.notes,
       updated_at = datetime('now')`
  ).run(record);
}

router.get('/', requireAuth, (req, res) => {
  try {
    res.json(buildPayload());
  } catch (err) {
    console.error('Household load failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.post('/members', requireAuth, (req, res) => {
  try {
    const member = normalizeMemberBody(req.body || {});
    const run = db.transaction(() => {
      const result = db.prepare(
        `INSERT INTO household_members (
           name, role, birth_date, employment_status, employer, job_title,
           gross_income_annual, net_pay_per_period, pay_frequency, pay_periods_per_year,
           retirement_account_type, employee_contribution_percent,
           employee_contribution_annual, employer_match_percent,
           employer_match_limit_percent, employer_match_annual_cap,
           health_premium_per_month, hsa_contribution_annual,
           dependent_care_fsa_annual, other_benefits_annual, notes
         ) VALUES (
           @name, @role, @birth_date, @employment_status, @employer, @job_title,
           @gross_income_annual, @net_pay_per_period, @pay_frequency, @pay_periods_per_year,
           @retirement_account_type, @employee_contribution_percent,
           @employee_contribution_annual, @employer_match_percent,
           @employer_match_limit_percent, @employer_match_annual_cap,
           @health_premium_per_month, @hsa_contribution_annual,
           @dependent_care_fsa_annual, @other_benefits_annual, @notes
         )`
      ).run(member);
      const saved = { id: result.lastInsertRowid, ...member };
      insertIncomeRecord(normalizeIncomeRecordBody(saved, { ...member, effective_date: req.body?.effective_date, source: 'profile' }));
    });
    run();
    res.json({ success: true, ...buildPayload() });
  } catch (err) {
    console.error('Household member create failed:', err);
    res.status(err.status || 500).json({ error: err.message });
  }
});

router.put('/members/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) return res.status(400).json({ error: 'Invalid member id.' });

  try {
    const member = normalizeMemberBody(req.body || {});
    const run = db.transaction(() => {
      const result = db.prepare(
        `UPDATE household_members
            SET name = @name,
                role = @role,
                birth_date = @birth_date,
                employment_status = @employment_status,
                employer = @employer,
                job_title = @job_title,
                gross_income_annual = @gross_income_annual,
                net_pay_per_period = @net_pay_per_period,
                pay_frequency = @pay_frequency,
                pay_periods_per_year = @pay_periods_per_year,
                retirement_account_type = @retirement_account_type,
                employee_contribution_percent = @employee_contribution_percent,
                employee_contribution_annual = @employee_contribution_annual,
                employer_match_percent = @employer_match_percent,
                employer_match_limit_percent = @employer_match_limit_percent,
                employer_match_annual_cap = @employer_match_annual_cap,
                health_premium_per_month = @health_premium_per_month,
                hsa_contribution_annual = @hsa_contribution_annual,
                dependent_care_fsa_annual = @dependent_care_fsa_annual,
                other_benefits_annual = @other_benefits_annual,
                notes = @notes,
                updated_at = datetime('now')
          WHERE id = @id`
      ).run({ id, ...member });
      if (result.changes === 0) throw householdError('Household member not found.', 404);
      insertIncomeRecord(normalizeIncomeRecordBody({ id, ...member }, { ...member, effective_date: req.body?.effective_date, source: 'profile' }));
    });
    run();
    res.json({ success: true, ...buildPayload() });
  } catch (err) {
    console.error('Household member update failed:', err);
    res.status(err.status || 500).json({ error: err.message });
  }
});

router.post('/members/:id/income-records', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) return res.status(400).json({ error: 'Invalid member id.' });

  try {
    const member = db.prepare('SELECT * FROM household_members WHERE id = ?').get(id);
    if (!member) throw householdError('Household member not found.', 404);
    insertIncomeRecord(normalizeIncomeRecordBody(member, req.body || {}));
    res.json({ success: true, ...buildPayload() });
  } catch (err) {
    console.error('Household income record failed:', err);
    res.status(err.status || 500).json({ error: err.message });
  }
});

router.delete('/members/:id', requireAuth, (req, res) => {
  const id = parseInt(req.params.id, 10);
  if (!Number.isFinite(id)) return res.status(400).json({ error: 'Invalid member id.' });

  try {
    const result = db.prepare('DELETE FROM household_members WHERE id = ?').run(id);
    if (result.changes === 0) return res.status(404).json({ error: 'Household member not found.' });
    res.json({ success: true, ...buildPayload() });
  } catch (err) {
    console.error('Household member delete failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
