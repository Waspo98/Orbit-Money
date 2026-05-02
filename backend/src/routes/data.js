import express from 'express';
import multer from 'multer';
import Papa from 'papaparse';
import { requireAuth, requireHouseholdId, requireHouseholdWrite } from '../auth.js';
import { db } from '../db/index.js';
import { centsToDollars } from '../lib/money.js';
import { sendBadRequest, sendOk, sendRouteError } from '../lib/http.js';

const router = express.Router();

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 50 * 1024 * 1024 }
});

const BACKUP_TABLES = [
  'accounts',
  'categories',
  'transactions',
  'rules',
  'budgets',
  'goals',
  'goal_account_allocations',
  'account_balance_records',
  'household_members',
  'household_income_records',
  'household_retirement_accounts',
  'app_settings',
  'upcoming_items',
  'upcoming_dismissed_suggestions',
  'upcoming_occurrences',
  'import_batches',
  'import_batch_items'
];

const RESTORE_DELETE_ORDER = [
  'upcoming_occurrences',
  'upcoming_dismissed_suggestions',
  'upcoming_items',
  'import_batch_items',
  'import_batches',
  'simplefin_config',
  'app_settings',
  'household_retirement_accounts',
  'household_income_records',
  'household_members',
  'account_balance_records',
  'goal_account_allocations',
  'goals',
  'budgets',
  'rules',
  'transactions',
  'categories',
  'accounts'
];

const RESTORE_INSERT_ORDER = [
  'accounts',
  'categories',
  'transactions',
  'rules',
  'budgets',
  'goals',
  'goal_account_allocations',
  'account_balance_records',
  'household_members',
  'household_income_records',
  'household_retirement_accounts',
  'app_settings',
  'upcoming_items',
  'upcoming_dismissed_suggestions',
  'upcoming_occurrences',
  'import_batches',
  'import_batch_items'
];

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

function sendDownload(res, filename, contentType, body) {
  res.setHeader('Content-Type', contentType);
  res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
  res.send(body);
}

function tableColumns(table) {
  return db.pragma(`table_info(${table})`).map((column) => column.name);
}

function rowsForTable(table, householdId) {
  return db.prepare(`SELECT * FROM ${table} WHERE household_id = ?`).all(householdId);
}

function withDollars(row, fields) {
  const copy = { ...row };
  for (const field of fields) {
    if (copy[field] !== null && copy[field] !== undefined) {
      copy[field] = centsToDollars(copy[field]);
    }
  }
  return copy;
}

function buildFinancialExport(householdId) {
  return {
    type: 'orbit_money_financial_export',
    version: 1,
    exportedAt: new Date().toISOString(),
    accounts: rowsForTable('accounts', householdId).map((row) =>
      withDollars(row, ['current_balance', 'estimated_value'])
    ),
    categories: rowsForTable('categories', householdId),
    transactions: rowsForTable('transactions', householdId).map((row) =>
      withDollars(row, ['amount'])
    ),
    budgets: rowsForTable('budgets', householdId).map((row) => withDollars(row, ['amount'])),
    goals: rowsForTable('goals', householdId).map((row) =>
      withDollars(row, ['target_amount', 'current_amount'])
    ),
    goalAccountAllocations: rowsForTable('goal_account_allocations', householdId).map((row) =>
      withDollars(row, ['allocation_amount', 'reserve_amount'])
    ),
    accountBalanceRecords: rowsForTable('account_balance_records', householdId).map((row) =>
      withDollars(row, ['balance'])
    ),
    upcomingItems: rowsForTable('upcoming_items', householdId).map((row) =>
      withDollars(row, ['amount'])
    )
  };
}

function buildOrbitBackup(householdId) {
  const household = db
    .prepare('SELECT id, name, default_currency, created_at, updated_at FROM households WHERE id = ?')
    .get(householdId);
  const memberships = db
    .prepare(
      `SELECT hm.user_id, hm.role, hm.access_level, hm.created_at, hm.updated_at,
              u.email, u.username, u.display_name
         FROM household_memberships hm
         JOIN users u ON u.id = hm.user_id
        WHERE hm.household_id = ?`
    )
    .all(householdId);

  const tables = {};
  for (const table of BACKUP_TABLES) {
    tables[table] = rowsForTable(table, householdId);
  }

  return {
    type: 'orbit_money_backup',
    version: 1,
    exportedAt: new Date().toISOString(),
    notice: 'SimpleFIN connection information is not included. Generate a new SimpleFIN API key and reconnect SimpleFIN after restore.',
    household,
    memberships,
    tables
  };
}

function parseBackupFile(file) {
  if (!file) throw new Error('No backup file uploaded.');
  let parsed;
  try {
    parsed = JSON.parse(file.buffer.toString('utf-8'));
  } catch {
    throw new Error('Backup file must be valid JSON.');
  }
  if (parsed?.type !== 'orbit_money_backup' || parsed?.version !== 1 || !parsed.tables) {
    throw new Error('This is not a supported Orbit Money backup file.');
  }
  return parsed;
}

function backupSummary(backup) {
  const tables = backup.tables || {};
  return {
    householdName: backup.household?.name || 'Orbit Household',
    exportedAt: backup.exportedAt || null,
    accounts: tables.accounts?.length || 0,
    transactions: tables.transactions?.length || 0,
    categories: tables.categories?.length || 0,
    rules: tables.rules?.length || 0,
    budgets: tables.budgets?.length || 0,
    goals: tables.goals?.length || 0,
    upcomingItems: tables.upcoming_items?.length || 0,
    simplefinExcluded: true
  };
}

function insertRows(table, rows, householdId) {
  if (!Array.isArray(rows) || rows.length === 0) return 0;
  const columns = tableColumns(table);
  const available = new Set(columns);
  let inserted = 0;

  for (const row of rows) {
    const next = { ...row };
    if (available.has('household_id')) next.household_id = householdId;
    const rowColumns = columns.filter((column) => Object.prototype.hasOwnProperty.call(next, column));
    if (rowColumns.length === 0) continue;
    const quoted = rowColumns.map((column) => `"${column}"`).join(', ');
    const placeholders = rowColumns.map(() => '?').join(', ');
    const values = rowColumns.map((column) => next[column]);
    db.prepare(`INSERT INTO ${table} (${quoted}) VALUES (${placeholders})`).run(...values);
    inserted += 1;
  }

  return inserted;
}

function restoreMemberships(backup, householdId, currentUserId) {
  const memberships = Array.isArray(backup.memberships) ? backup.memberships : [];
  const findUser = db.prepare(
    `SELECT id FROM users
      WHERE (? IS NOT NULL AND lower(email) = lower(?))
         OR (? IS NOT NULL AND username = ?)
      LIMIT 1`
  );
  const upsert = db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role, access_level)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(household_id, user_id) DO UPDATE SET
       role = excluded.role,
       access_level = excluded.access_level,
       updated_at = datetime('now')`
  );

  let restored = 0;
  for (const membership of memberships) {
    const matched = findUser.get(
      membership.email || null,
      membership.email || null,
      membership.username || null,
      membership.username || null
    );
    if (!matched) continue;
    if (currentUserId && matched.id === currentUserId) continue;
    const role = membership.role === 'admin' ? 'admin' : membership.role === 'owner' ? 'admin' : 'member';
    const accessLevel = membership.access_level === 'read' ? 'read' : 'write';
    upsert.run(householdId, matched.id, role, accessLevel);
    restored += 1;
  }

  if (currentUserId) {
    db.prepare(
      `INSERT INTO household_memberships (household_id, user_id, role, access_level)
       VALUES (?, ?, 'owner', 'write')
       ON CONFLICT(household_id, user_id) DO UPDATE SET
         role = 'owner',
         access_level = 'write',
         updated_at = datetime('now')`
    ).run(householdId, currentUserId);
  }

  return restored;
}

function restoreOrbitBackup(backup, householdId, currentUserId) {
  const run = db.transaction(() => {
    db.exec('PRAGMA defer_foreign_keys = ON');

    for (const table of RESTORE_DELETE_ORDER) {
      db.prepare(`DELETE FROM ${table} WHERE household_id = ?`).run(householdId);
    }

    db.prepare(
      `UPDATE households
          SET name = ?,
              default_currency = ?,
              updated_at = datetime('now')
        WHERE id = ?`
    ).run(
      backup.household?.name || 'Orbit Household',
      backup.household?.default_currency || 'USD',
      householdId
    );

    const inserted = {};
    for (const table of RESTORE_INSERT_ORDER) {
      inserted[table] = insertRows(table, backup.tables?.[table] || [], householdId);
    }
    const membershipsRestored = restoreMemberships(backup, householdId, currentUserId);

    return { inserted, membershipsRestored };
  });

  return run();
}

router.get('/budgeting-export', requireAuth, requireHouseholdWrite, (req, res) => {
  const householdId = requireHouseholdId(req);
  const format = String(req.query.format || 'json').toLowerCase();

  try {
    if (format === 'csv') {
      const rows = db
        .prepare(
          `SELECT t.date,
                  t.amount,
                  COALESCE(t.edited_merchant, t.original_merchant) AS merchant,
                  t.original_merchant,
                  t.original_description,
                  t.notes,
                  COALESCE(t.edited_is_transfer, t.is_transfer) AS is_transfer,
                  COALESCE(t.edited_is_ignored, t.is_ignored) AS is_ignored,
                  a.name AS account,
                  a.type AS account_type,
                  c.name AS category
             FROM transactions t
             LEFT JOIN accounts a ON a.id = t.account_id AND a.household_id = t.household_id
             LEFT JOIN categories c ON c.id = COALESCE(t.edited_category_id, t.category_id)
            WHERE t.household_id = ?
            ORDER BY t.date DESC, t.id DESC`
        )
        .all(householdId)
        .map((row) => ({ ...row, amount: centsToDollars(row.amount) }));
      return sendDownload(
        res,
        `orbit-money-transactions-${timestamp()}.csv`,
        'text/csv; charset=utf-8',
        Papa.unparse(rows)
      );
    }

    return sendDownload(
      res,
      `orbit-money-financial-export-${timestamp()}.json`,
      'application/json; charset=utf-8',
      JSON.stringify(buildFinancialExport(householdId), null, 2)
    );
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.get('/orbit-backup', requireAuth, requireHouseholdWrite, (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    sendDownload(
      res,
      `orbit-money-backup-${timestamp()}.json`,
      'application/json; charset=utf-8',
      JSON.stringify(buildOrbitBackup(householdId), null, 2)
    );
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.post('/orbit-restore/preview', requireAuth, requireHouseholdWrite, upload.single('file'), (req, res) => {
  try {
    const backup = parseBackupFile(req.file);
    sendOk(res, { success: true, ...backupSummary(backup) });
  } catch (err) {
    sendBadRequest(res, err.message || 'Restore preview failed.');
  }
});

router.post('/orbit-restore', requireAuth, requireHouseholdWrite, upload.single('file'), (req, res) => {
  const householdId = requireHouseholdId(req);
  try {
    const backup = parseBackupFile(req.file);
    const result = restoreOrbitBackup(backup, householdId, req.user?.id || null);
    sendOk(res, {
      success: true,
      ...backupSummary(backup),
      ...result,
      simplefinReconnectRequired: true
    });
  } catch (err) {
    sendBadRequest(res, err.message || 'Restore failed.');
  }
});

export default router;
