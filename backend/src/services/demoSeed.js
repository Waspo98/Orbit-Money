import { dollarsToCents } from '../lib/money.js';
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

const TXN_SEED = [
  ['checking', -86.42, 'WAL-MART SUPERCENTER #5402', 'Groceries', 1],
  ['credit', -14.99, 'Streamly', 'Entertainment', 2],
  ['checking', -52.18, 'MCDONALDS F3421', 'Dining & Drinks', 3],
  ['checking', 3150.0, 'Acme Design Payroll', 'Income', 4],
  ['credit', -37.7, 'PORTILLOS HOT DOGS 12', 'Dining & Drinks', 5],
  ['checking', -118.22, 'Electric Utility Co.', 'Bills & Utilities', 6],
  ['credit', -64.5, 'TARGET T-2201', 'Shopping', 7],
  ['checking', -24.0, 'Downtown Parking', 'Auto & Transport', 8],
  ['savings', 500.0, 'Transfer from Checking', 'Internal Transfers', 9],
  ['checking', -500.0, 'Transfer to Savings', 'Internal Transfers', 9],
  ['credit', -42.13, 'Pet Pantry', 'Pets', 10],
  ['checking', -18.75, 'City Pharmacy', 'Health & Wellness', 11],
  ['credit', -93.2, 'WALMART GROCERY', 'Groceries', 12],
  ['checking', -9.99, 'WENDYS APP', 'Dining & Drinks', 13],
  ['credit', -128.64, 'OLD NAVY 4421', 'Shopping', 14],
  ['checking', -68.0, 'Mobile Wireless', 'Bills & Utilities', 15],
  ['checking', 245.0, 'Side Project Client', 'Income', 16],
  ['credit', -22.4, 'JIMMY JOHNS #1904', 'Dining & Drinks', 17],
  ['checking', -33.25, 'Community Books', 'Books', 18],
  ['credit', -74.3, 'WENDYS #1187', 'Dining & Drinks', 19],
  ['checking', -121.44, 'Water Utility', 'Bills & Utilities', 20],
  ['credit', -16.0, 'Yoga Studio', 'Health & Wellness', 21],
  ['checking', -38.9, 'CULVERS OF MADISON', 'Dining & Drinks', 22],
  ['credit', -210.15, 'Weekend Hotel', 'Travel', 23],
  ['checking', -72.55, 'TARGET.COM', 'Shopping', 24],
  ['credit', -44.99, 'OLD NAVY ONLINE', 'Shopping', 25],
  ['checking', -29.5, 'Laundromat Plus', 'Personal Care', 26],
  ['checking', 3150.0, 'Acme Design Payroll', 'Income', 27],
  ['credit', -58.62, 'Garden Supply', 'Home Improvement', 28],
  ['checking', -19.0, 'Bus Pass', 'Auto & Transport', 29],
  ['credit', -39.8, 'MCDONALDS MOBILE ORDER', 'Dining & Drinks', 30],
  ['savings', 250.0, 'Transfer from Checking', 'Savings Transfer', 31],
  ['checking', -250.0, 'Transfer to Savings', 'Savings Transfer', 31],
  ['credit', -83.47, 'WALMART.COM', 'Shopping', 32],
  ['checking', -12.0, 'CULVERS #330', 'Dining & Drinks', 33],
  ['credit', -31.49, 'Office Depot', 'Office Supplies', 34],
  ['checking', -96.0, 'Internet Provider', 'Bills & Utilities', 35],
  ['credit', -27.65, 'Pet Pantry', 'Pets', 36],
  ['checking', -15.25, 'Charity Roundup', 'Charitable Donations', 37],
  ['credit', -46.11, 'PORTILLOS DRIVE THRU', 'Dining & Drinks', 38],
  ['checking', -140.0, 'Medical Clinic', 'Health & Wellness', 39],
  ['credit', -67.88, 'TARGET STORE 0874', 'Shopping', 40],
  ['checking', -110.0, 'Credit Card Payment', 'Credit Card Payment', 41],
  ['credit', 110.0, 'Payment Thank You', 'Credit Card Payment', 41],
  ['credit', -23.99, 'PhotoCloud', 'Software', 42],
  ['checking', 180.0, 'Marketplace Sale', 'Income', 43],
  ['credit', -54.76, 'OLDNAVY.COM', 'Shopping', 44],
  ['checking', -88.1, 'Gas & Heat Utility', 'Bills & Utilities', 45],
  ['credit', -36.42, 'JIMMY JOHN SANDWICHES', 'Dining & Drinks', 46],
  ['checking', -102.7, 'WAL-MART NEIGHBORHOOD MARKET', 'Groceries', 47],
  ['credit', -19.84, 'WENDYS MOBILE', 'Dining & Drinks', 48],
  ['checking', -28.67, 'CULVERS BUTTERBURGER', 'Dining & Drinks', 49],
  ['credit', -82.13, 'OLD NAVY STORE 0912', 'Shopping', 50],
  ['checking', -17.45, 'MCDONALDS #9081', 'Dining & Drinks', 51],
  ['credit', -47.28, 'TARGET GROCERY', 'Groceries', 52]
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
  ['Entertainment', 180],
  ['Auto & Transport', 220],
  ['Shopping', 240],
  ['Health & Wellness', 200],
  ['Pets', 90]
];

function isoDaysAgo(daysAgo) {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() - daysAgo);
  return d.toISOString().slice(0, 10);
}

function loadCategoryMap(db) {
  const rows = db.prepare('SELECT id, name, is_transfer FROM categories').all();
  return new Map(rows.map((c) => [c.name, c]));
}

function upsertDemoAccounts(db) {
  const findByName = db.prepare('SELECT id FROM accounts WHERE name = ?');
  const insertAccount = db.prepare(`
    INSERT INTO accounts
      (name, type, institution, account_number_last4, current_balance,
       estimated_value, is_manual, is_archived, sort_order)
    VALUES (?, ?, ?, ?, ?, ?, 1, 0, ?)
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
    const existing = findByName.get(account.name);
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

function upsertDemoRules(db, categories) {
  const findRule = db.prepare('SELECT id FROM rules WHERE name = ?');
  const insertRule = db.prepare(`
    INSERT INTO rules (name, conditions, actions, priority, enabled)
    VALUES (?, ?, ?, ?, 1)
  `);
  const updateRule = db.prepare(`
    UPDATE rules
       SET conditions = ?,
           actions = ?,
           priority = ?,
           enabled = 1,
           updated_at = datetime('now')
     WHERE name = ?
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
    const existing = findRule.get(name);
    if (existing) {
      updateRule.run(conditions, actionsJson, priority, name);
    } else {
      insertRule.run(name, conditions, actionsJson, priority);
    }
    changed++;
  }
  return changed;
}

function refreshExistingDemoData(db, categories) {
  const hasDemoTransactions = db
    .prepare("SELECT COUNT(*) AS c FROM transactions WHERE external_id LIKE 'demo-txn-%'")
    .get().c > 0;

  if (!hasDemoTransactions) {
    return { refreshed: false, transactions: 0, rules: 0 };
  }

  const accountIds = upsertDemoAccounts(db);
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
  `);
  const insertTxn = db.prepare(`
    INSERT INTO transactions
      (account_id, date, amount, original_merchant, original_description,
       category_id, notes, is_transfer, is_ignored, source, external_id,
       updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'manual', ?, datetime('now'))
  `);
  const findTxn = db.prepare('SELECT id FROM transactions WHERE external_id = ?');

  const run = db.transaction(() => {
    let changed = 0;
    TXN_SEED.forEach(([accountKey, amount, merchant, categoryName, daysAgo], index) => {
      const category = categories.get(categoryName) || uncategorized || null;
      const isTransfer = category?.is_transfer ? 1 : 0;
      const externalId = `demo-txn-${String(index + 1).padStart(3, '0')}`;
      const params = [
        accountIds[accountKey],
        isoDaysAgo(daysAgo),
        dollarsToCents(amount),
        merchant,
        `${merchant} demo transaction`,
        category?.id ?? null,
        index % 9 === 0 ? 'Demo note for showcase mode.' : null,
        isTransfer,
        externalId
      ];
      if (findTxn.get(externalId)) {
        updateTxn.run(...params);
      } else {
        insertTxn.run(...params);
      }
      changed++;
    });

    const rules = upsertDemoRules(db, categories);
    return { transactions: changed, rules, accounts: Object.keys(accountIds).length };
  });

  const result = run();
  reapplyRulesToAllTransactions(db);
  return { refreshed: true, ...result };
}

export function seedDemoData(db) {
  const existing = db
    .prepare(
      `SELECT
         (SELECT COUNT(*) FROM accounts) AS accounts,
         (SELECT COUNT(*) FROM transactions) AS transactions`
    )
    .get();

  const categories = loadCategoryMap(db);

  if (existing.accounts > 0 || existing.transactions > 0) {
    const refreshed = refreshExistingDemoData(db, categories);
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
       updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'manual', ?, datetime('now'))
  `);

  const insertBudget = db.prepare(`
    INSERT OR IGNORE INTO budgets (category_id, amount, rollover)
    VALUES (?, ?, 0)
  `);

  const run = db.transaction(() => {
    const accountIds = upsertDemoAccounts(db);

    let inserted = 0;
    TXN_SEED.forEach(([accountKey, amount, merchant, categoryName, daysAgo], index) => {
      const category = categories.get(categoryName) || uncategorized || null;
      const isTransfer = category?.is_transfer ? 1 : 0;
      insertTxn.run(
        accountIds[accountKey],
        isoDaysAgo(daysAgo),
        dollarsToCents(amount),
        merchant,
        `${merchant} demo transaction`,
        category?.id ?? null,
        index % 9 === 0 ? 'Demo note for showcase mode.' : null,
        isTransfer,
        `demo-txn-${String(index + 1).padStart(3, '0')}`
      );
      inserted++;
    });

    const rulesCreated = upsertDemoRules(db, categories);

    for (const [categoryName, amount] of BUDGET_SEED) {
      const category = categories.get(categoryName);
      if (category) insertBudget.run(category.id, dollarsToCents(amount));
    }

    return { inserted, rulesCreated };
  });

  const result = run();
  reapplyRulesToAllTransactions(db);
  console.log(
    `Demo seed: created ${ACCOUNT_SEED.length} accounts, ${result.inserted} transactions, and ${result.rulesCreated} rules.`
  );
  return { seeded: true, accounts: ACCOUNT_SEED.length, transactions: result.inserted, rules: result.rulesCreated };
}
