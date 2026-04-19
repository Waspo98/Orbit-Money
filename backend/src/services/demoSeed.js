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
    key: 'demo-credit',
    name: 'Demo Rewards Card',
    type: 'credit',
    institution: 'Northstar Card',
    last4: '4096',
    balance: -842.37,
    sortOrder: 20
  }
];

const TXN_SEED = [
  ['checking', -86.42, 'Green Basket Market', 'Groceries', 1],
  ['credit', -14.99, 'Streamly', 'Entertainment', 2],
  ['checking', -52.18, 'Fuel Stop', 'Auto & Transport', 3],
  ['checking', 3150.0, 'Acme Design Payroll', 'Income', 4],
  ['credit', -37.7, 'Bluebird Cafe', 'Dining & Drinks', 5],
  ['checking', -118.22, 'Electric Utility Co.', 'Bills & Utilities', 6],
  ['credit', -64.5, 'Home Harbor', 'Home Improvement', 7],
  ['checking', -24.0, 'Downtown Parking', 'Auto & Transport', 8],
  ['savings', 500.0, 'Transfer from Checking', 'Internal Transfers', 9],
  ['checking', -500.0, 'Transfer to Savings', 'Internal Transfers', 9],
  ['credit', -42.13, 'Pet Pantry', 'Pets', 10],
  ['checking', -18.75, 'City Pharmacy', 'Health & Wellness', 11],
  ['credit', -93.2, 'FreshCart', 'Groceries', 12],
  ['checking', -9.99, 'Cloud Notes', 'Software', 13],
  ['credit', -128.64, 'Style Loft', 'Shopping', 14],
  ['checking', -68.0, 'Mobile Wireless', 'Bills & Utilities', 15],
  ['checking', 245.0, 'Side Project Client', 'Income', 16],
  ['credit', -22.4, 'Morning Roast', 'Dining & Drinks', 17],
  ['checking', -33.25, 'Community Books', 'Books', 18],
  ['credit', -74.3, 'Cinema House', 'Entertainment', 19],
  ['checking', -121.44, 'Water Utility', 'Bills & Utilities', 20],
  ['credit', -16.0, 'Yoga Studio', 'Health & Wellness', 21],
  ['checking', -38.9, 'Neighborhood Deli', 'Dining & Drinks', 22],
  ['credit', -210.15, 'Weekend Hotel', 'Travel', 23],
  ['checking', -72.55, 'Green Basket Market', 'Groceries', 24],
  ['credit', -44.99, 'GameHub', 'Entertainment', 25],
  ['checking', -29.5, 'Laundromat Plus', 'Personal Care', 26],
  ['checking', 3150.0, 'Acme Design Payroll', 'Income', 27],
  ['credit', -58.62, 'Garden Supply', 'Home Improvement', 28],
  ['checking', -19.0, 'Bus Pass', 'Auto & Transport', 29],
  ['credit', -39.8, 'Taco Grove', 'Dining & Drinks', 30],
  ['savings', 250.0, 'Transfer from Checking', 'Savings Transfer', 31],
  ['checking', -250.0, 'Transfer to Savings', 'Savings Transfer', 31],
  ['credit', -83.47, 'FreshCart', 'Groceries', 32],
  ['checking', -12.0, 'Museum Tickets', 'Entertainment', 33],
  ['credit', -31.49, 'Office Depot', 'Office Supplies', 34],
  ['checking', -96.0, 'Internet Provider', 'Bills & Utilities', 35],
  ['credit', -27.65, 'Pet Pantry', 'Pets', 36],
  ['checking', -15.25, 'Charity Roundup', 'Charitable Donations', 37],
  ['credit', -46.11, 'Bluebird Cafe', 'Dining & Drinks', 38],
  ['checking', -140.0, 'Medical Clinic', 'Health & Wellness', 39],
  ['credit', -67.88, 'Green Basket Market', 'Groceries', 40],
  ['checking', -110.0, 'Credit Card Payment', 'Credit Card Payment', 41],
  ['credit', 110.0, 'Payment Thank You', 'Credit Card Payment', 41],
  ['credit', -23.99, 'PhotoCloud', 'Software', 42],
  ['checking', 180.0, 'Marketplace Sale', 'Income', 43],
  ['credit', -54.76, 'Style Loft', 'Shopping', 44],
  ['checking', -88.1, 'Gas & Heat Utility', 'Bills & Utilities', 45],
  ['credit', -36.42, 'Sandwich Square', 'Dining & Drinks', 46],
  ['checking', -102.7, 'Green Basket Market', 'Groceries', 47]
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

export function seedDemoData(db) {
  const existing = db
    .prepare(
      `SELECT
         (SELECT COUNT(*) FROM accounts) AS accounts,
         (SELECT COUNT(*) FROM transactions) AS transactions`
    )
    .get();

  if (existing.accounts > 0 || existing.transactions > 0) {
    console.log('Demo seed: skipped because local data already exists.');
    return { seeded: false };
  }

  const categories = loadCategoryMap(db);
  const uncategorized = categories.get('Uncategorized');

  const insertAccount = db.prepare(`
    INSERT INTO accounts
      (name, type, institution, account_number_last4, current_balance,
       is_manual, is_archived, sort_order)
    VALUES (?, ?, ?, ?, ?, 1, 0, ?)
  `);

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
    const accountIds = {};

    for (const account of ACCOUNT_SEED) {
      const result = insertAccount.run(
        account.name,
        account.type,
        account.institution,
        account.last4,
        account.balance,
        account.sortOrder
      );
      accountIds[account.key.replace('demo-', '')] = result.lastInsertRowid;
    }

    let inserted = 0;
    TXN_SEED.forEach(([accountKey, amount, merchant, categoryName, daysAgo], index) => {
      const category = categories.get(categoryName) || uncategorized || null;
      const isTransfer = category?.is_transfer ? 1 : 0;
      insertTxn.run(
        accountIds[accountKey],
        isoDaysAgo(daysAgo),
        amount,
        merchant,
        `${merchant} demo transaction`,
        category?.id ?? null,
        index % 9 === 0 ? 'Demo note for showcase mode.' : null,
        isTransfer,
        `demo-txn-${String(index + 1).padStart(3, '0')}`
      );
      inserted++;
    });

    for (const [categoryName, amount] of BUDGET_SEED) {
      const category = categories.get(categoryName);
      if (category) insertBudget.run(category.id, amount);
    }

    return inserted;
  });

  const inserted = run();
  console.log(`Demo seed: created ${ACCOUNT_SEED.length} accounts and ${inserted} transactions.`);
  return { seeded: true, accounts: ACCOUNT_SEED.length, transactions: inserted };
}
