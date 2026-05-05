import { dollarsToCents } from '../lib/money.js';
import { MHA_TAX_PROFILE_KEY } from './mhaTaxEstimate.js';
import { reapplyRulesToAllTransactions } from './ruleMatcher.js';

const ACCOUNT_SEED = [
  {
    key: 'demo-checking',
    name: 'Demo Everyday Checking',
    type: 'checking',
    institution: 'Orbit Demo Bank',
    last4: '1024',
    balance: 4286.52,
    sortOrder: 0
  },
  {
    key: 'demo-savings',
    name: 'Demo Rainy Day Savings',
    type: 'savings',
    institution: 'Orbit Demo Bank',
    last4: '2048',
    balance: 12840.11,
    sortOrder: 10
  },
  {
    key: 'demo-cash',
    name: 'Demo Cash Wallet',
    type: 'cash',
    institution: 'Orbit Demo Bank',
    last4: null,
    balance: 240.0,
    sortOrder: 20
  },
  {
    key: 'demo-credit',
    name: 'Demo Rewards Card',
    type: 'credit',
    institution: 'Northstar Card',
    last4: '4096',
    balance: -842.37,
    sortOrder: 30
  },
  {
    key: 'demo-travel-credit',
    name: 'Demo Travel Card',
    type: 'credit',
    institution: 'Summit Visa',
    last4: '7788',
    balance: -319.84,
    sortOrder: 40
  },
  {
    key: 'demo-investment',
    name: 'Demo Brokerage',
    type: 'investment',
    institution: 'Northstar Investments',
    last4: '6677',
    balance: 28450.72,
    sortOrder: 50
  },
  {
    key: 'demo-loan',
    name: 'Demo Auto Loan',
    type: 'loan',
    institution: 'Orbit Demo Bank',
    last4: '5150',
    balance: -14620.35,
    sortOrder: 60
  },
  {
    key: 'demo-mortgage',
    name: 'Demo Home Mortgage',
    type: 'mortgage',
    institution: 'Orbit Demo Bank',
    last4: '3030',
    balance: -286400.0,
    estimatedValue: 365000.0,
    sortOrder: 70
  },
  {
    key: 'demo-other',
    name: 'Demo Other Asset',
    type: 'other',
    institution: 'Orbit Demo Bank',
    last4: '9090',
    balance: 1250.0,
    sortOrder: 80
  }
];

const DEMO_HOUSEHOLD_MEMBERS = [
  {
    name: 'Jordan Brooks',
    role: 'adult',
    birthDate: '1987-06-14',
    employmentStatus: 'employed',
    employer: 'Grace Harbor Church',
    jobTitle: 'Associate Pastor',
    grossIncomeAnnual: 72000,
    netPayPerPeriod: 2125,
    payFrequency: 'biweekly',
    payPeriodsPerYear: 26,
    retirementAccountType: '403b',
    employeeContributionPercent: 8,
    employerMatchPercent: 50,
    employerMatchLimitPercent: 6,
    healthPremiumPerMonth: 380,
    hsaContributionAnnual: 2400,
    dependentCareFsaAnnual: 0,
    otherBenefitsAnnual: 600,
    notes: 'Sample ministry income for MHA and household tax testing.'
  },
  {
    name: 'Taylor Brooks',
    role: 'adult',
    birthDate: '1989-10-03',
    employmentStatus: 'employed',
    employer: 'Northstar Health',
    jobTitle: 'Clinical Operations Manager',
    grossIncomeAnnual: 110000,
    netPayPerPeriod: 3200,
    payFrequency: 'biweekly',
    payPeriodsPerYear: 26,
    retirementAccountType: '401k',
    employeeContributionPercent: 6,
    employerMatchPercent: 100,
    employerMatchLimitPercent: 4,
    healthPremiumPerMonth: 0,
    hsaContributionAnnual: 1200,
    dependentCareFsaAnnual: 2500,
    otherBenefitsAnnual: 0,
    notes: 'Sample spouse income to exercise household bracket estimates.'
  }
];

const DEMO_MHA_TAX_PROFILE = {
  mode: 'household',
  simpleFederalRate: 22,
  simpleStateRate: 4.95,
  filingStatus: 'married_joint',
  state: 'IL',
  deductionMode: 'standard',
  itemizedDeduction: 0,
  taxableIncomeOverride: null,
  stateRateOverride: null
};

const MHA_ELIGIBLE_CATEGORY_NAMES = [
  'Bills & Utilities',
  'Home & Garden',
  'Loan Payment',
  'Taxes'
];

const MONTHLY_TXN_TEMPLATE = [
  { key: 'payroll-ministry', accountKey: 'checking', amount: 6000, variance: 0, merchant: 'Grace Harbor Church Payroll', categoryName: 'Income', day: 1 },
  { key: 'payroll-spouse', accountKey: 'checking', amount: 9166.67, variance: 0, merchant: 'Northstar Health Payroll', categoryName: 'Income', day: 1 },
  { key: 'mortgage', accountKey: 'checking', amount: -1950, variance: 0, merchant: 'Home Mortgage ACH', categoryName: 'Loan Payment', day: 2, note: 'Sample MHA housing cost.' },
  { key: 'electric', accountKey: 'checking', amount: -178.4, variance: 13, merchant: 'Electric Utility Co.', categoryName: 'Bills & Utilities', day: 5, note: 'Sample MHA utility cost.' },
  { key: 'gas', accountKey: 'checking', amount: -82.1, variance: 9, merchant: 'Gas & Heat Utility', categoryName: 'Bills & Utilities', day: 6, note: 'Sample MHA utility cost.' },
  { key: 'internet', accountKey: 'credit', amount: -96, variance: 0, merchant: 'Fiber Internet Provider', categoryName: 'Bills & Utilities', day: 7, note: 'Sample MHA utility cost.' },
  { key: 'mobile', accountKey: 'credit', amount: -68, variance: 0, merchant: 'Mobile Wireless', categoryName: 'Bills & Utilities', day: 8 },
  { key: 'walmart-grocery', accountKey: 'credit', amount: -142.35, variance: 18, merchant: 'WAL-MART SUPERCENTER #5402', categoryName: 'Groceries', day: 9 },
  { key: 'target-shop', accountKey: 'credit', amount: -86.2, variance: 12, merchant: 'TARGET T-2201', categoryName: 'Shopping', day: 11 },
  { key: 'portillos', accountKey: 'credit', amount: -42.8, variance: 5, merchant: 'PORTILLOS HOT DOGS 12', categoryName: 'Dining & Drinks', day: 13 },
  { key: 'fuel', accountKey: 'checking', amount: -118.45, variance: 10, merchant: 'Lakeside Fuel Stop', categoryName: 'Auto & Transport', day: 14 },
  { key: 'savings-out', accountKey: 'checking', amount: -500, variance: 0, merchant: 'Transfer to Savings', categoryName: 'Internal Transfers', day: 15 },
  { key: 'savings-in', accountKey: 'savings', amount: 500, variance: 0, merchant: 'Transfer from Checking', categoryName: 'Internal Transfers', day: 15 },
  { key: 'giving', accountKey: 'checking', amount: -420, variance: 0, merchant: 'Grace Harbor Giving', categoryName: 'Charitable Donations', day: 18 },
  { key: 'streaming', accountKey: 'credit', amount: -17.99, variance: 0, merchant: 'Streamly', categoryName: 'Entertainment & Rec.', day: 19 },
  { key: 'software', accountKey: 'credit', amount: -24.99, variance: 0, merchant: 'PhotoCloud', categoryName: 'Software & Tech', day: 20 },
  { key: 'pet', accountKey: 'credit', amount: -38.5, variance: 6, merchant: 'Pet Pantry', categoryName: 'Pets', day: 22 },
  { key: 'water', accountKey: 'checking', amount: -121.44, variance: 8, merchant: 'Water Utility', categoryName: 'Bills & Utilities', day: 24, note: 'Sample MHA utility cost.' },
  { key: 'home-garden', accountKey: 'credit', amount: -95.75, variance: 14, merchant: 'Garden Supply', categoryName: 'Home & Garden', day: 25, note: 'Sample MHA home expense.' },
  { key: 'card-payment-out', accountKey: 'checking', amount: -1550, variance: 0, merchant: 'Northstar Card Payment', categoryName: 'Credit Card Payment', day: 27 },
  { key: 'card-payment-in', accountKey: 'credit', amount: 1550, variance: 0, merchant: 'Payment Thank You', categoryName: 'Credit Card Payment', day: 27 }
];

const OCCASIONAL_TXN_TEMPLATE = [
  { monthsAgo: [1, 4, 7, 10], key: 'home-repair', accountKey: 'checking', amount: -360, merchant: 'Home Repair Co.', categoryName: 'Home & Garden', day: 16, note: 'Sample MHA repair cost.' },
  { monthsAgo: [2, 8], key: 'property-tax', accountKey: 'checking', amount: -2850, merchant: 'County Property Tax', categoryName: 'Taxes', day: 12, note: 'Sample MHA property tax cost.' },
  { monthsAgo: [3, 9], key: 'insurance', accountKey: 'checking', amount: -740, merchant: 'Homeowners Insurance', categoryName: 'Bills & Utilities', day: 10, note: 'Sample MHA housing cost.' },
  { monthsAgo: [5], key: 'vacation', accountKey: 'travel-credit', amount: -860, merchant: 'Weekend Hotel', categoryName: 'Travel & Vacation', day: 21 },
  { monthsAgo: [6], key: 'medical', accountKey: 'checking', amount: -240, merchant: 'Medical Clinic', categoryName: 'Medical', day: 17 },
  { monthsAgo: [0, 6], key: 'marketplace', accountKey: 'checking', amount: 180, merchant: 'Marketplace Sale', categoryName: 'Venmo/Marketplace', day: 23 }
];

const DEMO_RULE_SEED = [
  ['Demo: Walmart merchant cleanup', 'WAL', 'Walmart', 'Groceries', 80],
  ['Demo: Target merchant cleanup', 'TARGET', 'Target', 'Shopping', 79],
  ['Demo: Portillo\'s merchant cleanup', 'PORTILLOS', 'Portillo\'s', 'Dining & Drinks', 78],
  ['Demo: Jimmy John\'s merchant cleanup', 'JIMMY', 'Jimmy John\'s', 'Dining & Drinks', 77],
  ['Demo: Culver\'s merchant cleanup', 'CULVERS', 'Culver\'s', 'Dining & Drinks', 76],
  ['Demo: McDonald\'s merchant cleanup', 'MCDONALDS', 'McDonald\'s', 'Dining & Drinks', 75],
  ['Demo: Wendy\'s merchant cleanup', 'WENDYS', 'Wendy\'s', 'Dining & Drinks', 74],
  ['Demo: Old Navy merchant cleanup', 'OLD', 'Old Navy', 'Shopping', 73]
];

const BUDGET_SEED = [
  ['Groceries', 650],
  ['Dining & Drinks', 260],
  ['Bills & Utilities', 480],
  ['Entertainment & Rec.', 180],
  ['Auto & Transport', 220],
  ['Shopping', 240],
  ['Health & Wellness', 200],
  ['Home & Garden', 300],
  ['Pets', 90],
  ['Software & Tech', 50],
  ['Travel & Vacation', 175]
];

function pad2(value) {
  return String(value).padStart(2, '0');
}

function isoDate(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

function monthStart(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1, 12, 0, 0, 0);
}

function addMonths(date, offset) {
  return new Date(date.getFullYear(), date.getMonth() + offset, 1, 12, 0, 0, 0);
}

function lastCompletedMonthStart(now = new Date()) {
  return addMonths(monthStart(now), -1);
}

function dateInMonth(month, day) {
  const lastDay = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  return isoDate(new Date(month.getFullYear(), month.getMonth(), Math.min(day, lastDay), 12, 0, 0, 0));
}

function variedAmount(baseAmount, monthIndex, variance = 0) {
  if (!variance) return baseAmount;
  return Math.round((baseAmount + (((monthIndex % 5) - 2) * variance)) * 100) / 100;
}

export function buildDemoTransactions(now = new Date()) {
  const endMonth = lastCompletedMonthStart(now);
  const transactions = [];

  for (let monthsAgo = 0; monthsAgo < 12; monthsAgo += 1) {
    const month = addMonths(endMonth, -monthsAgo);
    const monthKey = `${month.getFullYear()}${pad2(month.getMonth() + 1)}`;
    const monthIndex = 11 - monthsAgo;
    const templates = [
      ...MONTHLY_TXN_TEMPLATE,
      ...OCCASIONAL_TXN_TEMPLATE.filter((item) => item.monthsAgo.includes(monthsAgo))
    ];

    templates.forEach((item, templateIndex) => {
      transactions.push({
        accountKey: item.accountKey,
        amount: variedAmount(item.amount, monthIndex, item.variance),
        merchant: item.merchant,
        categoryName: item.categoryName,
        date: dateInMonth(month, item.day),
        originalDescription: `${item.merchant} demo transaction ${monthKey}`,
        note: item.note || null,
        sortKey: `${monthKey}-${pad2(item.day)}-${pad2(templateIndex)}`
      });
    });
  }

  return transactions
    .sort((a, b) => b.sortKey.localeCompare(a.sortKey))
    .map((transaction, index) => ({
      ...transaction,
      externalId: `demo-txn-${String(index + 1).padStart(3, '0')}`
    }));
}

function loadCategoryMap(db, householdId = 1) {
  const rows = db.prepare('SELECT id, name, is_transfer FROM categories WHERE household_id = ?').all(householdId);
  return new Map(rows.map((c) => [c.name, c]));
}

function upsertDemoAccounts(db, householdId = 1) {
  const findByName = db.prepare('SELECT id FROM accounts WHERE household_id = ? AND name = ?');
  const insertAccount = db.prepare(`
    INSERT INTO accounts
      (household_id, name, type, institution, account_number_last4, current_balance,
       estimated_value, is_manual, is_archived, sort_order)
    VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, ?)
  `);
  const updateAccount = db.prepare(`
    UPDATE accounts
       SET type = ?,
           institution = ?,
           account_number_last4 = ?,
           current_balance = ?,
           estimated_value = ?,
           is_archived = 0,
           sort_order = ?,
           updated_at = datetime('now')
     WHERE id = ?
  `);

  const accountIds = {};
  for (const account of ACCOUNT_SEED) {
    const existing = findByName.get(householdId, account.name);
    if (existing) {
      updateAccount.run(
        account.type,
        account.institution,
        account.last4,
        dollarsToCents(account.balance),
        account.estimatedValue == null ? null : dollarsToCents(account.estimatedValue),
        account.sortOrder,
        existing.id
      );
      accountIds[account.key.replace('demo-', '')] = existing.id;
    } else {
      const result = insertAccount.run(
        householdId,
        account.name,
        account.type,
        account.institution,
        account.last4,
        dollarsToCents(account.balance),
        account.estimatedValue == null ? null : dollarsToCents(account.estimatedValue),
        account.sortOrder
      );
      accountIds[account.key.replace('demo-', '')] = result.lastInsertRowid;
    }
  }
  return accountIds;
}

function putDemoSetting(db, householdId, key, value, { overwrite = true } = {}) {
  if (!overwrite) {
    db.prepare(
      `INSERT OR IGNORE INTO app_settings (household_id, key, value, updated_at)
       VALUES (?, ?, ?, datetime('now'))`
    ).run(householdId, key, value);
    return;
  }

  db.prepare(
    `INSERT INTO app_settings (household_id, key, value, updated_at)
     VALUES (?, ?, ?, datetime('now'))
     ON CONFLICT(household_id, key) DO UPDATE SET
       value = excluded.value,
       updated_at = datetime('now')`
  ).run(householdId, key, value);
}

function upsertDemoMhaSettings(db, categories, householdId = 1) {
  putDemoSetting(db, householdId, 'mha_tracker_enabled', '1');
  putDemoSetting(db, householdId, MHA_TAX_PROFILE_KEY, JSON.stringify(DEMO_MHA_TAX_PROFILE), {
    overwrite: false
  });

  const markCategory = db.prepare(
    `UPDATE categories
        SET mha_default_eligible = 1,
            mha_default_ignored = 0
      WHERE household_id = ?
        AND name = ?`
  );

  for (const categoryName of MHA_ELIGIBLE_CATEGORY_NAMES) {
    if (categories.has(categoryName)) {
      markCategory.run(householdId, categoryName);
      const category = categories.get(categoryName);
      categories.set(categoryName, {
        ...category,
        mha_default_eligible: 1,
        mha_default_ignored: 0
      });
    }
  }
}

function upsertDemoHouseholdMembers(db, accountIds, householdId = 1, options = {}) {
  const effectiveDate = isoDate(addMonths(lastCompletedMonthStart(options.now), -11));
  const findMember = db.prepare(
    'SELECT id FROM household_members WHERE household_id = ? AND name = ?'
  );
  const insertMember = db.prepare(`
    INSERT INTO household_members (
      household_id, name, role, birth_date, employment_status, employer, job_title,
      gross_income_annual, net_pay_per_period, pay_frequency, pay_periods_per_year,
      retirement_account_type, employee_contribution_percent, employee_contribution_annual,
      employer_match_percent, employer_match_limit_percent, employer_match_annual_cap,
      health_premium_per_month, hsa_contribution_annual, dependent_care_fsa_annual,
      other_benefits_annual, notes
    ) VALUES (
      @household_id, @name, @role, @birth_date, @employment_status, @employer, @job_title,
      @gross_income_annual, @net_pay_per_period, @pay_frequency, @pay_periods_per_year,
      @retirement_account_type, @employee_contribution_percent, @employee_contribution_annual,
      @employer_match_percent, @employer_match_limit_percent, @employer_match_annual_cap,
      @health_premium_per_month, @hsa_contribution_annual, @dependent_care_fsa_annual,
      @other_benefits_annual, @notes
    )
  `);
  const updateMember = db.prepare(`
    UPDATE household_members
       SET role = @role,
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
     WHERE id = @id
       AND household_id = @household_id
  `);
  const upsertIncomeRecord = db.prepare(`
    INSERT INTO household_income_records (
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
      @dependent_care_fsa_annual, @other_benefits_annual, 'sample', @notes
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
      updated_at = datetime('now')
  `);
  const linkRetirementAccount = db.prepare(`
    INSERT INTO household_retirement_accounts (household_id, member_id, account_id, account_kind)
    VALUES (?, ?, ?, ?)
    ON CONFLICT(member_id, account_id) DO UPDATE SET
      household_id = excluded.household_id,
      account_kind = excluded.account_kind,
      updated_at = datetime('now')
  `);

  let changed = 0;
  for (const member of DEMO_HOUSEHOLD_MEMBERS) {
    const row = {
      household_id: householdId,
      name: member.name,
      role: member.role,
      birth_date: member.birthDate,
      employment_status: member.employmentStatus,
      employer: member.employer,
      job_title: member.jobTitle,
      gross_income_annual: dollarsToCents(member.grossIncomeAnnual),
      net_pay_per_period: dollarsToCents(member.netPayPerPeriod),
      pay_frequency: member.payFrequency,
      pay_periods_per_year: member.payPeriodsPerYear,
      retirement_account_type: member.retirementAccountType,
      employee_contribution_percent: member.employeeContributionPercent,
      employee_contribution_annual: 0,
      employer_match_percent: member.employerMatchPercent,
      employer_match_limit_percent: member.employerMatchLimitPercent,
      employer_match_annual_cap: 0,
      health_premium_per_month: dollarsToCents(member.healthPremiumPerMonth),
      hsa_contribution_annual: dollarsToCents(member.hsaContributionAnnual),
      dependent_care_fsa_annual: dollarsToCents(member.dependentCareFsaAnnual),
      other_benefits_annual: dollarsToCents(member.otherBenefitsAnnual),
      notes: member.notes
    };
    const existing = findMember.get(householdId, member.name);
    const memberId = existing
      ? (updateMember.run({ ...row, id: existing.id }), existing.id)
      : insertMember.run(row).lastInsertRowid;

    upsertIncomeRecord.run({
      ...row,
      member_id: memberId,
      effective_date: effectiveDate
    });

    if (accountIds.investment) {
      linkRetirementAccount.run(householdId, memberId, accountIds.investment, member.retirementAccountType);
    }
    changed++;
  }
  return changed;
}

function upsertDemoRules(db, categories, householdId = 1) {
  const findRule = db.prepare('SELECT id FROM rules WHERE household_id = ? AND name = ?');
  const insertRule = db.prepare(`
    INSERT INTO rules (household_id, name, conditions, actions, priority, enabled)
    VALUES (?, ?, ?, ?, ?, 1)
  `);
  const updateRule = db.prepare(`
    UPDATE rules
       SET conditions = ?,
           actions = ?,
           priority = ?,
           enabled = 1,
           updated_at = datetime('now')
     WHERE name = ? AND household_id = ?
  `);

  let changed = 0;
  for (const [name, matchText, displayName, categoryName, priority] of DEMO_RULE_SEED) {
    const category = categories.get(categoryName);
    const conditions = JSON.stringify([
      { field: 'merchant', operator: 'contains', value: matchText }
    ]);
    const actions = [
      { type: 'rename', value: displayName }
    ];
    if (category) {
      actions.push({ type: 'categorize', value: category.id });
    }
    const actionsJson = JSON.stringify(actions);
    const existing = findRule.get(householdId, name);
    if (existing) {
      updateRule.run(conditions, actionsJson, priority, name, householdId);
    } else {
      insertRule.run(householdId, name, conditions, actionsJson, priority);
    }
    changed++;
  }
  return changed;
}

function refreshExistingDemoData(db, categories, householdId = 1, options = {}) {
  const hasDemoTransactions = db
    .prepare("SELECT COUNT(*) AS c FROM transactions WHERE household_id = ? AND external_id LIKE 'demo-txn-%'")
    .get(householdId).c > 0;

  if (!hasDemoTransactions) {
    return { refreshed: false, transactions: 0, rules: 0 };
  }

  const accountIds = upsertDemoAccounts(db, householdId);
  const transactions = buildDemoTransactions(options.now);
  const uncategorized = categories.get('Uncategorized');
  const updateTxn = db.prepare(`
    UPDATE transactions
       SET account_id = ?,
           date = ?,
           amount = ?,
           original_merchant = ?,
           original_description = ?,
           category_id = ?,
           notes = ?,
           is_transfer = ?,
       updated_at = datetime('now')
     WHERE external_id = ?
       AND source = 'manual'
       AND household_id = ?
  `);
  const insertTxn = db.prepare(`
    INSERT INTO transactions
      (account_id, date, amount, original_merchant, original_description,
       category_id, notes, is_transfer, is_ignored, source, external_id,
       updated_at, household_id)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'manual', ?, datetime('now'), ?)
  `);
  const findTxn = db.prepare('SELECT id FROM transactions WHERE household_id = ? AND external_id = ?');

  const run = db.transaction(() => {
    let changed = 0;
    upsertDemoHouseholdMembers(db, accountIds, householdId, options);
    upsertDemoMhaSettings(db, categories, householdId);
    transactions.forEach((transaction, index) => {
      const category = categories.get(transaction.categoryName) || uncategorized || null;
      const isTransfer = category?.is_transfer ? 1 : 0;
      const params = [
        accountIds[transaction.accountKey],
        transaction.date,
        dollarsToCents(transaction.amount),
        transaction.merchant,
        transaction.originalDescription,
        category?.id ?? null,
        transaction.note,
        isTransfer,
        transaction.externalId,
        householdId
      ];
      if (findTxn.get(householdId, transaction.externalId)) {
        updateTxn.run(...params);
      } else {
        insertTxn.run(...params);
      }
      changed++;
    });

    const rules = upsertDemoRules(db, categories, householdId);
    return {
      transactions: changed,
      rules,
      accounts: Object.keys(accountIds).length,
      householdMembers: DEMO_HOUSEHOLD_MEMBERS.length
    };
  });

  const result = run();
  reapplyRulesToAllTransactions(db, householdId);
  return { refreshed: true, ...result };
}

export function seedDemoDataForHousehold(db, householdId = 1, options = {}) {
  const existing = db
    .prepare(
      `SELECT
         (SELECT COUNT(*) FROM accounts WHERE household_id = ?) AS accounts,
         (SELECT COUNT(*) FROM transactions WHERE household_id = ?) AS transactions`
    )
    .get(householdId, householdId);

  const categories = loadCategoryMap(db, householdId);

  if (existing.accounts > 0 || existing.transactions > 0) {
    const refreshed = refreshExistingDemoData(db, categories, householdId, options);
    if (refreshed.refreshed) {
      console.log(`Demo seed: refreshed ${refreshed.transactions} showcase transactions and ${refreshed.rules} rules.`);
      return { seeded: false, ...refreshed };
    }
    console.log('Demo seed: skipped because local data already exists.');
    return { seeded: false };
  }

  const uncategorized = categories.get('Uncategorized');

  const insertTxn = db.prepare(`
    INSERT INTO transactions
      (account_id, date, amount, original_merchant, original_description,
       category_id, notes, is_transfer, is_ignored, source, external_id,
       updated_at, household_id)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'manual', ?, datetime('now'), ?)
  `);

  const insertBudget = db.prepare(`
    INSERT OR IGNORE INTO budgets (household_id, category_id, amount, rollover)
    VALUES (?, ?, ?, 0)
  `);

  const run = db.transaction(() => {
    const accountIds = upsertDemoAccounts(db, householdId);
    const transactions = buildDemoTransactions(options.now);
    const householdMembers = upsertDemoHouseholdMembers(db, accountIds, householdId, options);
    upsertDemoMhaSettings(db, categories, householdId);

    let inserted = 0;
    transactions.forEach((transaction) => {
      const category = categories.get(transaction.categoryName) || uncategorized || null;
      const isTransfer = category?.is_transfer ? 1 : 0;
      insertTxn.run(
        accountIds[transaction.accountKey],
        transaction.date,
        dollarsToCents(transaction.amount),
        transaction.merchant,
        transaction.originalDescription,
        category?.id ?? null,
        transaction.note,
        isTransfer,
        transaction.externalId,
        householdId
      );
      inserted++;
    });

    const rulesCreated = upsertDemoRules(db, categories, householdId);

    for (const [categoryName, amount] of BUDGET_SEED) {
      const category = categories.get(categoryName);
      if (category) insertBudget.run(householdId, category.id, dollarsToCents(amount));
    }

    return { inserted, rulesCreated, householdMembers };
  });

  const result = run();
  reapplyRulesToAllTransactions(db, householdId);
  console.log(
    `Demo seed: created ${ACCOUNT_SEED.length} accounts, ${result.householdMembers} household members, ${result.inserted} transactions, and ${result.rulesCreated} rules.`
  );
  return {
    seeded: true,
    accounts: ACCOUNT_SEED.length,
    householdMembers: result.householdMembers,
    transactions: result.inserted,
    rules: result.rulesCreated
  };
}

export function seedDemoData(db) {
  return seedDemoDataForHousehold(db, 1);
}
