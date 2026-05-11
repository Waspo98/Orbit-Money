import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import { formatLocalDate, formatLocalMonth, isValidOptionalDateOnly } from '../lib/localDate.js';
import {
  sendNotFound,
  sendOk,
  sendRouteError,
  sendServerError
} from '../lib/http.js';
import { dollarsToCents, moneyFieldsToDollars } from '../lib/money.js';
import { parseBoundedInteger, readIdParam } from '../lib/routeParams.js';

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
const RETIREMENT_TYPES = new Set(['none', '401k', '403b', '457b', 'ira', 'roth_ira', 'hsa', 'sep_ira', 'simple_ira', 'pension', 'other']);
const RETIREMENT_ACCOUNT_KINDS = new Set(['401k', '403b', '457b', 'ira', 'roth_ira', 'hsa', 'sep_ira', 'simple_ira', 'pension', 'other']);

const PAY_PERIODS = {
  weekly: 52,
  biweekly: 26,
  semimonthly: 24,
  monthly: 12,
  annual: 1,
  none: 0
};
const MEMBER_MONEY_FIELDS = [
  'gross_income_annual',
  'net_pay_per_period',
  'employee_contribution_annual',
  'employer_match_annual_cap',
  'health_premium_per_month',
  'hsa_contribution_annual',
  'dependent_care_fsa_annual',
  'other_benefits_annual'
];
const ACCOUNT_MONEY_FIELDS = ['current_balance', 'estimated_value'];

function householdError(message, status = 400) {
  const err = new Error(message);
  err.status = status;
  return err;
}

function validDate(value) {
  return isValidOptionalDateOnly(value);
}

function today() {
  return formatLocalDate();
}

function monthKey(date) {
  return String(date || '').slice(0, 7);
}

function addMonths(key, amount) {
  const [year, month] = key.split('-').map(Number);
  const d = new Date(year, month - 1 + amount, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
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

function cleanMoney(value, fallback = 0) {
  if (value === null || value === undefined || value === '') return fallback;
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return dollarsToCents(Math.max(0, number));
}

function serializeMember(row) {
  return moneyFieldsToDollars(row, MEMBER_MONEY_FIELDS);
}

function serializeAccount(row) {
  return moneyFieldsToDollars(row, ACCOUNT_MONEY_FIELDS);
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
    gross_income_annual: cleanMoney(body?.gross_income_annual),
    net_pay_per_period: cleanMoney(body?.net_pay_per_period),
    pay_frequency: payFrequency,
    pay_periods_per_year: periodsFor(payFrequency, body),
    retirement_account_type: retirementType,
    employee_contribution_percent: Math.min(100, cleanNumber(body?.employee_contribution_percent)),
    employee_contribution_annual: cleanMoney(body?.employee_contribution_annual),
    employer_match_percent: Math.min(100, cleanNumber(body?.employer_match_percent)),
    employer_match_limit_percent: Math.min(100, cleanNumber(body?.employer_match_limit_percent)),
    employer_match_annual_cap: cleanMoney(body?.employer_match_annual_cap),
    health_premium_per_month: cleanMoney(body?.health_premium_per_month),
    hsa_contribution_annual: cleanMoney(body?.hsa_contribution_annual),
    dependent_care_fsa_annual: cleanMoney(body?.dependent_care_fsa_annual),
    other_benefits_annual: cleanMoney(body?.other_benefits_annual),
    notes: cleanString(body?.notes, 800),
    retirement_accounts: normalizeRetirementAccounts(body?.retirement_accounts)
  };
}

function normalizeRetirementAccounts(value) {
  if (!Array.isArray(value)) return [];
  const seen = new Set();
  return value
    .map((item) => {
      const accountId = Number(item?.account_id ?? item?.accountId);
      if (!Number.isInteger(accountId) || accountId <= 0 || seen.has(accountId)) return null;
      seen.add(accountId);
      const accountKind = RETIREMENT_ACCOUNT_KINDS.has(item?.account_kind)
        ? item.account_kind
        : 'other';
      return { account_id: accountId, account_kind: accountKind };
    })
    .filter(Boolean);
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
    gross_income_annual: cleanMoney(body?.gross_income_annual, member.gross_income_annual),
    net_pay_per_period: cleanMoney(body?.net_pay_per_period, member.net_pay_per_period),
    pay_frequency: payFrequency,
    pay_periods_per_year: periodsFor(payFrequency, body?.pay_periods_per_year == null ? member : body),
    employee_contribution_percent: Math.min(100, cleanNumber(body?.employee_contribution_percent, member.employee_contribution_percent)),
    employee_contribution_annual: cleanMoney(body?.employee_contribution_annual, member.employee_contribution_annual),
    employer_match_percent: Math.min(100, cleanNumber(body?.employer_match_percent, member.employer_match_percent)),
    employer_match_limit_percent: Math.min(100, cleanNumber(body?.employer_match_limit_percent, member.employer_match_limit_percent)),
    employer_match_annual_cap: cleanMoney(body?.employer_match_annual_cap, member.employer_match_annual_cap),
    health_premium_per_month: cleanMoney(body?.health_premium_per_month, member.health_premium_per_month),
    hsa_contribution_annual: cleanMoney(body?.hsa_contribution_annual, member.hsa_contribution_annual),
    dependent_care_fsa_annual: cleanMoney(body?.dependent_care_fsa_annual, member.dependent_care_fsa_annual),
    other_benefits_annual: cleanMoney(body?.other_benefits_annual, member.other_benefits_annual),
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

function accountBalance(row) {
  const estimated = Number(row.estimated_value);
  if (Number.isFinite(estimated) && estimated > 0) return estimated;
  const current = Number(row.current_balance);
  return Number.isFinite(current) ? current : 0;
}

function replaceRetirementAccounts(householdId, memberId, accounts) {
  db.prepare('DELETE FROM household_retirement_accounts WHERE member_id = ? AND household_id = ?').run(memberId, householdId);
  const insert = db.prepare(
    `INSERT INTO household_retirement_accounts (
       household_id, member_id, account_id, account_kind
     ) VALUES (
       @household_id, @member_id, @account_id, @account_kind
     )
     ON CONFLICT(member_id, account_id) DO UPDATE SET
       account_kind = excluded.account_kind,
       updated_at = datetime('now')`
  );
  accounts.forEach((account) => {
    const exists = db
      .prepare('SELECT id FROM accounts WHERE id = ? AND household_id = ? AND is_archived = 0')
      .get(account.account_id, householdId);
    if (!exists) throw householdError('Linked retirement account was not found.', 400);
    insert.run({ household_id: householdId, member_id: memberId, ...account });
  });
}

function buildPayload(householdId) {
  const members = db
    .prepare(
      `SELECT *
         FROM household_members
        WHERE household_id = ?
        ORDER BY id ASC`
    )
    .all(householdId)
    .map(serializeMember)
    .map(decorateMember);

  const linkedAccounts = db
    .prepare(
      `SELECT hra.id, hra.member_id, hra.account_id, hra.account_kind,
              a.name AS account_name, a.type AS account_type, a.institution,
              a.current_balance, a.estimated_value, a.is_archived
         FROM household_retirement_accounts hra
         JOIN accounts a ON a.id = hra.account_id
        WHERE hra.household_id = ?
          AND a.household_id = ?
          AND a.is_archived = 0
        ORDER BY hra.member_id ASC, hra.account_kind ASC, a.sort_order ASC, a.name ASC`
    )
    .all(householdId, householdId)
    .map(serializeAccount)
    .map((row) => ({
      ...row,
      balance: accountBalance(row)
    }));

  const linkedByMember = linkedAccounts.reduce((map, account) => {
    if (!map.has(account.member_id)) map.set(account.member_id, []);
    map.get(account.member_id).push(account);
    return map;
  }, new Map());

  const decoratedMembers = members.map((member) => ({
    ...member,
    retirement_accounts: linkedByMember.get(member.id) || []
  }));

  const records = db
    .prepare(
      `SELECT r.*, m.name AS member_name
         FROM household_income_records r
         JOIN household_members m ON m.id = r.member_id
        WHERE r.household_id = ?
          AND m.household_id = ?
        ORDER BY r.effective_date DESC, r.id DESC
        LIMIT 80`
    )
    .all(householdId, householdId)
    .map(serializeMember)
    .map((row) => ({
      ...row,
      net_pay_annual: (Number(row.net_pay_per_period) || 0) * (Number(row.pay_periods_per_year) || 0),
      employee_retirement_annual: employeeContributionAnnual(row),
      employer_retirement_annual: employerMatchAnnual(row)
    }));

  const summary = decoratedMembers.reduce(
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

  summary.retirement_account_balance = linkedAccounts.reduce((sum, account) => sum + account.balance, 0);
  summary.hsa_account_balance = linkedAccounts
    .filter((account) => account.account_kind === 'hsa')
    .reduce((sum, account) => sum + account.balance, 0);
  summary.linked_retirement_account_count = linkedAccounts.length;

  return { summary, members: decoratedMembers, records, retirement_accounts: linkedAccounts };
}

function buildRetirementHistory(householdId, months) {
  const accounts = db
    .prepare(
      `SELECT a.id, a.name, a.type, a.institution, hra.account_kind,
              a.current_balance / 100.0 AS current_balance,
              a.estimated_value / 100.0 AS estimated_value
         FROM household_retirement_accounts hra
         JOIN accounts a ON a.id = hra.account_id
        WHERE hra.household_id = ?
          AND a.household_id = ?
          AND a.is_archived = 0
        ORDER BY hra.account_kind ASC, a.sort_order ASC, a.name ASC`
    )
    .all(householdId, householdId)
    .map((row) => ({
      ...row,
      balance: accountBalance(row)
    }));

  if (accounts.length === 0) {
    return { accounts: [], history: [] };
  }

  const accountIds = accounts.map((account) => account.id);
  const placeholders = accountIds.map(() => '?').join(', ');
  const todayMonth = formatLocalMonth();

  const latestTransaction = db
    .prepare(
      `SELECT MAX(date) AS latest, MIN(date) AS earliest
         FROM transactions
        WHERE household_id = ?
          AND account_id IN (${placeholders})`
    )
    .get(householdId, ...accountIds);

  const latestRecord = db
    .prepare(
      `SELECT MAX(record_date) AS latest, MIN(record_date) AS earliest
         FROM account_balance_records
        WHERE household_id = ?
          AND account_id IN (${placeholders})`
    )
    .get(householdId, ...accountIds);

  const latestMonth = [monthKey(latestTransaction.latest), monthKey(latestRecord.latest), todayMonth]
    .filter(Boolean)
    .sort()
    .at(-1) || todayMonth;
  const earliestMonth = [monthKey(latestTransaction.earliest), monthKey(latestRecord.earliest)]
    .filter(Boolean)
    .sort()[0] || latestMonth;
  const floorMonth = addMonths(latestMonth, -(months - 1));
  const firstMonth = earliestMonth > floorMonth ? earliestMonth : floorMonth;

  const monthlyDeltas = db
    .prepare(
      `SELECT account_id, substr(date, 1, 7) AS month, SUM(amount) / 100.0 AS amount
         FROM transactions
        WHERE household_id = ?
          AND account_id IN (${placeholders})
          AND date >= ?
        GROUP BY account_id, substr(date, 1, 7)
        ORDER BY month ASC`
    )
    .all(householdId, ...accountIds, `${firstMonth}-01`);

  const balanceRecords = db
    .prepare(
      `SELECT account_id, record_date, balance / 100.0 AS balance
         FROM account_balance_records
        WHERE household_id = ?
          AND account_id IN (${placeholders})
          AND record_date <= ?
        ORDER BY account_id ASC, record_date DESC`
    )
    .all(householdId, ...accountIds, `${latestMonth}-31`);

  const balancesByAccount = new Map(accounts.map((account) => [account.id, Number(account.balance) || 0]));
  const recordsByAccount = new Map();
  for (const record of balanceRecords) {
    const accountId = Number(record.account_id);
    if (!recordsByAccount.has(accountId)) recordsByAccount.set(accountId, []);
    recordsByAccount.get(accountId).push({
      month: monthKey(record.record_date),
      date: record.record_date,
      balance: Number(record.balance) || 0
    });
  }

  function snapshotBalanceForMonth(accountId, month) {
    const records = recordsByAccount.get(accountId);
    if (!records) return null;
    const record = records.find((item) => item.month === month);
    return record ? record.balance : null;
  }

  const deltasByMonth = new Map();
  for (const row of monthlyDeltas) {
    if (!deltasByMonth.has(row.month)) deltasByMonth.set(row.month, []);
    deltasByMonth.get(row.month).push(row);
  }

  const rows = [];
  const accountRows = [];
  let cursor = latestMonth;
  while (cursor >= firstMonth) {
    const accountBalances = accounts.map((account) => {
      const snapshot = snapshotBalanceForMonth(account.id, cursor);
      const balance = snapshot ?? balancesByAccount.get(account.id) ?? 0;
      if (snapshot !== null) balancesByAccount.set(account.id, balance);
      return {
        account_id: account.id,
        balance
      };
    });
    const total = accountBalances.reduce((sum, account) => sum + account.balance, 0);
    accountRows.push({ month: cursor, accounts: accountBalances });
    rows.push({ month: cursor, balance: total, accounts: accountBalances });

    for (const delta of deltasByMonth.get(cursor) || []) {
      balancesByAccount.set(
        delta.account_id,
        (balancesByAccount.get(delta.account_id) || 0) - (Number(delta.amount) || 0)
      );
    }

    cursor = addMonths(cursor, -1);
  }

  const accountRowsAsc = accountRows.reverse();
  const latestAccountRow = accountRowsAsc.at(-1) || null;
  const latestYear = String(latestMonth).slice(0, 4);
  const ytdStartRow =
    accountRowsAsc.find((row) => row.month >= `${latestYear}-01`) ||
    accountRowsAsc[0] ||
    null;
  const balanceForAccount = (row, accountId) => (
    row?.accounts.find((account) => Number(account.account_id) === Number(accountId))?.balance ?? null
  );

  return {
    accounts: accounts.map((account) => {
      const currentBalance = balanceForAccount(latestAccountRow, account.id) ?? account.balance;
      const ytdStartBalance = balanceForAccount(ytdStartRow, account.id);
      const ytdChange = ytdStartBalance === null ? null : currentBalance - ytdStartBalance;
      const ytdGrowthPercent = ytdStartBalance > 0 ? (ytdChange / ytdStartBalance) * 100 : null;
      return {
        id: account.id,
        name: account.name,
        type: account.type,
        institution: account.institution,
        account_kind: account.account_kind,
        balance: account.balance,
        ytd_change: ytdChange,
        ytd_growth_percent: ytdGrowthPercent,
        ytd_start_balance: ytdStartBalance
      };
    }),
    history: rows.reverse()
  };
}

function insertIncomeRecord(householdId, record) {
  db.prepare(
    `INSERT INTO household_income_records (
       household_id, member_id, effective_date, gross_income_annual, net_pay_per_period,
       pay_frequency, pay_periods_per_year, employee_contribution_percent,
       employee_contribution_annual, employer_match_percent,
       employer_match_limit_percent, employer_match_annual_cap,
       health_premium_per_month, hsa_contribution_annual,
       dependent_care_fsa_annual, other_benefits_annual, source, notes
     ) VALUES (
       @household_id, @member_id, @effective_date, @gross_income_annual, @net_pay_per_period,
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
  ).run({ household_id: householdId, ...record });
}

router.get('/', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    sendOk(res, buildPayload(householdId));
  } catch (err) {
    console.error('Household load failed:', err);
    sendServerError(res, err);
  }
});

router.get('/retirement-history', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const months = parseBoundedInteger(req.query.months, {
    fallback: 120,
    min: 3,
    max: 1200
  });
  try {
    sendOk(res, buildRetirementHistory(householdId, months));
  } catch (err) {
    console.error('Retirement history failed:', err);
    sendServerError(res, err);
  }
});

router.post('/members', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const member = normalizeMemberBody(req.body || {});
    const run = db.transaction(() => {
      const result = db.prepare(
        `INSERT INTO household_members (
           household_id, name, role, birth_date, employment_status, employer, job_title,
           gross_income_annual, net_pay_per_period, pay_frequency, pay_periods_per_year,
           retirement_account_type, employee_contribution_percent,
           employee_contribution_annual, employer_match_percent,
           employer_match_limit_percent, employer_match_annual_cap,
           health_premium_per_month, hsa_contribution_annual,
           dependent_care_fsa_annual, other_benefits_annual, notes
         ) VALUES (
           @household_id, @name, @role, @birth_date, @employment_status, @employer, @job_title,
           @gross_income_annual, @net_pay_per_period, @pay_frequency, @pay_periods_per_year,
           @retirement_account_type, @employee_contribution_percent,
           @employee_contribution_annual, @employer_match_percent,
           @employer_match_limit_percent, @employer_match_annual_cap,
           @health_premium_per_month, @hsa_contribution_annual,
           @dependent_care_fsa_annual, @other_benefits_annual, @notes
         )`
      ).run({ household_id: householdId, ...member });
      const saved = { id: result.lastInsertRowid, ...member };
      replaceRetirementAccounts(householdId, saved.id, member.retirement_accounts);
      insertIncomeRecord(householdId, normalizeIncomeRecordBody(saved, {
        effective_date: req.body?.effective_date,
        source: 'profile'
      }));
    });
    run();
    sendOk(res, { success: true, ...buildPayload(householdId) });
  } catch (err) {
    console.error('Household member create failed:', err);
    sendRouteError(res, err);
  }
});

router.put('/members/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'member');
  if (id === null) return;

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
          WHERE id = @id AND household_id = @household_id`
      ).run({ id, household_id: householdId, ...member });
      if (result.changes === 0) throw householdError('Household member not found.', 404);
      replaceRetirementAccounts(householdId, id, member.retirement_accounts);
      insertIncomeRecord(householdId, normalizeIncomeRecordBody({ id, ...member }, {
        effective_date: req.body?.effective_date,
        source: 'profile'
      }));
    });
    run();
    sendOk(res, { success: true, ...buildPayload(householdId) });
  } catch (err) {
    console.error('Household member update failed:', err);
    sendRouteError(res, err);
  }
});

router.post('/members/:id/income-records', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'member');
  if (id === null) return;

  try {
    const member = db.prepare('SELECT * FROM household_members WHERE id = ? AND household_id = ?').get(id, householdId);
    if (!member) throw householdError('Household member not found.', 404);
    insertIncomeRecord(householdId, normalizeIncomeRecordBody(member, req.body || {}));
    sendOk(res, { success: true, ...buildPayload(householdId) });
  } catch (err) {
    console.error('Household income record failed:', err);
    sendRouteError(res, err);
  }
});

router.delete('/members/:id', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'member');
  if (id === null) return;

  try {
    const result = db.prepare('DELETE FROM household_members WHERE id = ? AND household_id = ?').run(id, householdId);
    if (result.changes === 0) return sendNotFound(res, 'Household member not found.');
    sendOk(res, { success: true, ...buildPayload(householdId) });
  } catch (err) {
    console.error('Household member delete failed:', err);
    sendServerError(res, err);
  }
});

export default router;
