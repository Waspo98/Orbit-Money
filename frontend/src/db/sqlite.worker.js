import initSqlJs from 'sql.js';
import sqlWasmUrl from 'sql.js/dist/sql-wasm.wasm?url';
import migrations from 'virtual:sqlite-migrations';
import { dollarsToCents, centsToDollars, moneyFieldsToDollars } from '../lib/money.js';
import { effectiveCategoryIdSql } from '../lib/effectiveSql.js';

const EFFECTIVE_CATEGORY_ID_SQL = effectiveCategoryIdSql('t');

const IDB_NAME = 'orbit-local-sqlite-v1';
const IDB_STORE = 'sqlite_storage';
const IDB_KEY = 'budget_db_bytes';

let idbPromise = null;

function openIdb() {
  const idbFactory = typeof self !== 'undefined' && self.indexedDB ? self.indexedDB : (typeof indexedDB !== 'undefined' ? indexedDB : null);
  if (!idbFactory) return Promise.resolve(null);
  if (!idbPromise) {
    idbPromise = new Promise((resolve, reject) => {
      const request = idbFactory.open(IDB_NAME, 1);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(IDB_STORE)) {
          db.createObjectStore(IDB_STORE);
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => {
        idbPromise = null;
        reject(request.error);
      };
    });
  }
  return idbPromise;
}

async function loadPersistedBytes() {
  const idb = await openIdb();
  if (!idb) return null;
  return new Promise((resolve) => {
    try {
      const tx = idb.transaction(IDB_STORE, 'readonly');
      const store = tx.objectStore(IDB_STORE);
      const req = store.get(IDB_KEY);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}

async function saveBytesToIdb(bytes) {
  const idb = await openIdb();
  if (!idb) return;
  return new Promise((resolve, reject) => {
    try {
      const tx = idb.transaction(IDB_STORE, 'readwrite');
      const store = tx.objectStore(IDB_STORE);
      store.put(bytes, IDB_KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    } catch (err) {
      reject(err);
    }
  });
}

async function deletePersistedBytes() {
  const idb = await openIdb();
  if (!idb) return;
  return new Promise((resolve, reject) => {
    try {
      const tx = idb.transaction(IDB_STORE, 'readwrite');
      const store = tx.objectStore(IDB_STORE);
      store.delete(IDB_KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    } catch (err) {
      reject(err);
    }
  });
}

// -----------------------------------------------------------------------------
// Debounced persistence (500ms trailing debounce)
// -----------------------------------------------------------------------------
let dirtyVersion = 0;
let savedVersion = 0;
let saveTimeout = null;

function scheduleWorkerSave() {
  dirtyVersion++;
  if (saveTimeout) clearTimeout(saveTimeout);
  saveTimeout = setTimeout(async () => {
    await flushWorkerDatabase();
  }, 500);
}

async function flushWorkerDatabase() {
  if (saveTimeout) {
    clearTimeout(saveTimeout);
    saveTimeout = null;
  }
  const versionToSave = dirtyVersion;
  if (!activeDb || versionToSave === savedVersion) return;
  try {
    const bytes = activeDb.export();
    await saveBytesToIdb(bytes);
    savedVersion = Math.max(savedVersion, versionToSave);
  } catch (err) {
    console.error('[sqlite.worker] Failed to flush database:', err);
  }
}

// -----------------------------------------------------------------------------
// Database Lifecycle & Migrations
// -----------------------------------------------------------------------------
let sqlEnginePromise = null;
let activeDb = null;
let initPromise = null;

function getSqlEngine() {
  if (!sqlEnginePromise) {
    sqlEnginePromise = initSqlJs({
      locateFile: () => sqlWasmUrl
    });
  }
  return sqlEnginePromise;
}

function runDatabaseMigrations(db) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS _migrations (
      name TEXT PRIMARY KEY,
      applied_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  const stmt = db.prepare('SELECT name FROM _migrations');
  const applied = new Set();
  while (stmt.step()) {
    const row = stmt.getAsObject();
    applied.add(row.name);
  }
  stmt.free();

  let count = 0;
  for (const item of migrations) {
    if (applied.has(item.name)) continue;
    try {
      db.exec('BEGIN TRANSACTION;');
      db.exec(item.sql);
      const ins = db.prepare('INSERT INTO _migrations (name) VALUES (?)');
      ins.run([item.name]);
      ins.free();
      db.exec('COMMIT;');
      count++;
    } catch (err) {
      try {
        db.exec('ROLLBACK;');
      } catch {}
      console.error(`[sqlite.worker] Migration ${item.name} failed:`, err);
      throw err;
    }
  }

  // Ensure household 1 and user 1 exist with default settings
  db.exec(`
    INSERT OR IGNORE INTO households (id, name, default_currency)
    VALUES (1, 'My Household', 'USD');

    INSERT OR IGNORE INTO users (id, username, display_name, is_local_admin)
    VALUES (1, 'local_user', 'Local User', 1);

    INSERT OR IGNORE INTO household_memberships (household_id, user_id, role, access_level)
    VALUES (1, 1, 'owner', 'write');
  `);

  return count;
}

async function getLocalDatabase() {
  if (activeDb) return activeDb;
  if (initPromise) return initPromise;

  initPromise = (async () => {
    const SQL = await getSqlEngine();
    const persisted = await loadPersistedBytes();
    let db;
    if (persisted && persisted.length > 0) {
      try {
        db = new SQL.Database(persisted);
      } catch (err) {
        console.warn('[sqlite.worker] Failed to load persisted SQLite bytes, initializing fresh db:', err);
        db = new SQL.Database();
      }
    } else {
      db = new SQL.Database();
    }

    try {
      db.exec('PRAGMA foreign_keys = ON;');
    } catch {}

    const appliedCount = runDatabaseMigrations(db);
    if (appliedCount > 0 || !persisted) {
      const bytes = db.export();
      await saveBytesToIdb(bytes);
    }

    activeDb = db;
    return activeDb;
  })();

  return initPromise;
}

function createStatementWrapper(db, sql) {
  return {
    all(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      try {
        if (flatParams && flatParams.length > 0) {
          stmt.bind(flatParams);
        }
        const rows = [];
        while (stmt.step()) {
          rows.push(stmt.getAsObject());
        }
        return rows;
      } finally {
        stmt.free();
      }
    },
    get(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      try {
        if (flatParams && flatParams.length > 0) {
          stmt.bind(flatParams);
        }
        let row = null;
        if (stmt.step()) {
          row = stmt.getAsObject();
        }
        return row;
      } finally {
        stmt.free();
      }
    },
    run(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      try {
        if (flatParams && flatParams.length > 0) {
          stmt.bind(flatParams);
        }
        stmt.step();
      } finally {
        stmt.free();
      }

      const lastIdRes = db.exec('SELECT last_insert_rowid() AS id;');
      const lastInsertRowid = lastIdRes[0]?.values[0]?.[0] ?? 0;
      const changes = db.getRowsModified();

      scheduleWorkerSave();
      return { changes, lastInsertRowid };
    }
  };
}

// -----------------------------------------------------------------------------
// Request Helpers
// -----------------------------------------------------------------------------
function parseQueryParams(url) {
  const queryIndex = url.indexOf('?');
  if (queryIndex === -1) return new URLSearchParams();
  return new URLSearchParams(url.slice(queryIndex));
}

function getPathname(url) {
  const queryIndex = url.indexOf('?');
  return queryIndex === -1 ? url : url.slice(0, queryIndex);
}

function safeJson(body) {
  if (!body) return {};
  if (typeof body === 'object') return body;
  try {
    return JSON.parse(body);
  } catch {
    return {};
  }
}

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

function getHouseholdPayload(wrap) {
  let members = [];
  try {
    members = wrap('SELECT * FROM household_members WHERE household_id = 1 ORDER BY id ASC')
      .all()
      .map((m) => moneyFieldsToDollars(m, MEMBER_MONEY_FIELDS));
  } catch (err) {
    console.warn('[sqlite.worker] Failed to load household members:', err);
  }

  let retirementAccounts = [];
  try {
    retirementAccounts = wrap(`
      SELECT hra.id, hra.member_id, hra.account_id, hra.account_kind,
             a.name AS account_name, a.type AS account_type, a.institution,
             a.current_balance, a.estimated_value, a.is_archived
        FROM household_retirement_accounts hra
        JOIN accounts a ON a.id = hra.account_id
       WHERE hra.household_id = 1
         AND a.household_id = 1
         AND a.is_archived = 0
       ORDER BY hra.member_id ASC, hra.account_kind ASC, a.sort_order ASC, a.name ASC
    `).all().map((row) => {
      row.current_balance = centsToDollars(row.current_balance);
      row.estimated_value = row.estimated_value != null ? centsToDollars(row.estimated_value) : null;
      const est = Number(row.estimated_value);
      row.balance = (Number.isFinite(est) && est > 0) ? est : (Number(row.current_balance) || 0);
      return row;
    });
  } catch (err) {
    console.warn('[sqlite.worker] Failed to load retirement accounts:', err);
  }

  const linkedByMember = new Map();
  for (const acc of retirementAccounts) {
    if (!linkedByMember.has(acc.member_id)) linkedByMember.set(acc.member_id, []);
    linkedByMember.get(acc.member_id).push(acc);
  }

  const decoratedMembers = members.map((member) => {
    const netPayAnnual = (Number(member.net_pay_per_period) || 0) * (Number(member.pay_periods_per_year) || 0);
    const employeeRetirementAnnual = Number(member.employee_contribution_annual) ||
      ((Number(member.gross_income_annual) || 0) * ((Number(member.employee_contribution_percent) || 0) / 100));
    const gross = Number(member.gross_income_annual) || 0;
    const empPct = Number(member.employee_contribution_percent) || 0;
    const matchPct = Number(member.employer_match_percent) || 0;
    const limitPct = Number(member.employer_match_limit_percent) || empPct;
    const cap = Number(member.employer_match_annual_cap) || 0;
    const matchedBase = gross * (Math.min(empPct, limitPct) / 100);
    const estMatch = matchedBase * (matchPct / 100);
    const employerRetirementAnnual = cap > 0 ? Math.min(estMatch, cap) : estMatch;
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
      household_value_annual: netPayAnnual + totalBenefitsAnnual,
      retirement_accounts: linkedByMember.get(member.id) || []
    };
  });

  let records = [];
  try {
    records = wrap(`
      SELECT r.*, m.name AS member_name
        FROM household_income_records r
        JOIN household_members m ON m.id = r.member_id
       WHERE r.household_id = 1 AND m.household_id = 1
       ORDER BY r.effective_date DESC, r.id DESC
       LIMIT 80
    `).all().map((r) => moneyFieldsToDollars(r, MEMBER_MONEY_FIELDS));
  } catch {}

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

  summary.retirement_account_balance = retirementAccounts.reduce((sum, a) => sum + a.balance, 0);
  summary.hsa_account_balance = retirementAccounts
    .filter((a) => a.account_kind === 'hsa')
    .reduce((sum, a) => sum + a.balance, 0);
  summary.linked_retirement_account_count = retirementAccounts.length;

  return { summary, members: decoratedMembers, records, retirement_accounts: retirementAccounts };
}

// -----------------------------------------------------------------------------
// Endpoint Implementation
// -----------------------------------------------------------------------------
async function handleWorkerApiRequest(db, rawUrl, options = {}) {
  const method = String(options.method || 'GET').toUpperCase();
  let pathname = getPathname(rawUrl);
  const params = parseQueryParams(rawUrl);
  const body = safeJson(options.body);

  // Alias /api/user/preferences to /api/preferences
  if (pathname === '/api/user/preferences') {
    pathname = '/api/preferences';
  } else if (pathname.startsWith('/api/user/preferences/')) {
    pathname = pathname.replace('/api/user/preferences/', '/api/preferences/');
  }

  const wrap = (sql) => createStatementWrapper(db, sql);

  // ---------------------------------------------------------------------------
  // Auth
  // ---------------------------------------------------------------------------
  if (pathname === '/api/auth/me') {
    return {
      authenticated: true,
      mode: 'local',
      user: {
        id: 1,
        email: 'local@device',
        username: 'local_user',
        display_name: 'Local Device',
        is_local_admin: 1
      },
      household: {
        id: 1,
        name: 'My Household',
        role: 'owner',
        accessLevel: 'write'
      }
    };
  }

  if (pathname === '/api/auth/config') {
    return {
      mode: 'local',
      isOidcConfigured: false
    };
  }

  if (pathname === '/api/auth/logout') {
    return { success: true };
  }

  // ---------------------------------------------------------------------------
  // Accounts
  // ---------------------------------------------------------------------------
  if (pathname === '/api/accounts') {
    if (method === 'GET') {
      const includeArchived = params.get('includeArchived') === '1';
      const sql = `
        SELECT a.id, a.name, a.type, a.institution, a.account_number_last4,
               a.current_balance, a.estimated_value,
               a.is_manual, a.is_archived, a.sort_order, a.mha_default_eligible,
               a.simplefin_account_id, a.created_at, a.updated_at,
               COUNT(t.id) AS transaction_count
          FROM accounts a
          LEFT JOIN transactions t ON t.account_id = a.id AND t.household_id = 1
         WHERE a.household_id = 1
           ${includeArchived ? '' : 'AND a.is_archived = 0'}
         GROUP BY a.id
         ORDER BY a.is_archived ASC, a.sort_order ASC, a.name ASC
      `;
      const accounts = wrap(sql).all().map((a) => {
        a.current_balance = centsToDollars(a.current_balance);
        a.estimated_value = a.estimated_value != null ? centsToDollars(a.estimated_value) : null;
        return a;
      });

      const profiles = wrap('SELECT * FROM credit_card_profiles WHERE household_id = 1').all();
      const profileMap = new Map();
      for (const p of profiles) {
        p.annual_fee = p.annual_fee != null ? centsToDollars(p.annual_fee) : null;
        p.credit_limit = p.credit_limit != null ? centsToDollars(p.credit_limit) : null;
        try { p.authorized_users = JSON.parse(p.authorized_users_json || '[]'); } catch { p.authorized_users = []; }
        try { p.reward_categories = JSON.parse(p.reward_categories_json || '[]'); } catch { p.reward_categories = []; }
        try { p.benefits = JSON.parse(p.benefits_json || '[]'); } catch { p.benefits = []; }
        profileMap.set(p.account_id, p);
      }

      const items = accounts.map((acc) => ({
        ...acc,
        credit_card_profile: profileMap.get(acc.id) || null
      }));

      return { items };
    }

    if (method === 'POST') {
      const name = String(body.name || '').trim();
      const type = String(body.type || 'other');
      const balanceCents = dollarsToCents(body.balance || 0);
      const estCents = body.estimated_value != null ? dollarsToCents(body.estimated_value) : null;

      const maxSortRes = wrap('SELECT MAX(sort_order) AS max_sort FROM accounts WHERE household_id = 1').get();
      const nextSort = (maxSortRes?.max_sort || 0) + 10;

      const res = wrap(`
        INSERT INTO accounts (household_id, name, type, institution, account_number_last4, is_manual, current_balance, estimated_value, sort_order)
        VALUES (1, ?, ?, ?, ?, 1, ?, ?, ?)
      `).run([name, type, body.institution || null, body.account_number_last4 || null, balanceCents, estCents, nextSort]);

      const inserted = wrap('SELECT * FROM accounts WHERE id = ?').get([res.lastInsertRowid]);
      inserted.current_balance = centsToDollars(inserted.current_balance);
      inserted.estimated_value = inserted.estimated_value != null ? centsToDollars(inserted.estimated_value) : null;
      return { success: true, account: inserted };
    }
  }

  if (pathname === '/api/accounts/reorder' && method === 'POST') {
    const orderedIds = body.orderedIds || [];
    orderedIds.forEach((id, index) => {
      wrap('UPDATE accounts SET sort_order = ?, updated_at = datetime("now") WHERE id = ? AND household_id = 1')
        .run([index * 10, id]);
    });
    return { success: true, reordered: orderedIds.length };
  }

  const accountIdMatch = pathname.match(/^\/api\/accounts\/(\d+)(?:\/(.*))?$/);
  if (accountIdMatch) {
    const accId = Number(accountIdMatch[1]);
    const subRoute = accountIdMatch[2];

    if (!subRoute && method === 'PUT') {
      const fields = [];
      const values = [];
      if (body.name !== undefined) { fields.push('name = ?'); values.push(body.name); }
      if (body.type !== undefined) { fields.push('type = ?'); values.push(body.type); }
      if (body.institution !== undefined) { fields.push('institution = ?'); values.push(body.institution); }
      if (body.account_number_last4 !== undefined) { fields.push('account_number_last4 = ?'); values.push(body.account_number_last4); }
      if (body.current_balance !== undefined) { fields.push('current_balance = ?'); values.push(dollarsToCents(body.current_balance)); }
      if (body.estimated_value !== undefined) { fields.push('estimated_value = ?'); values.push(body.estimated_value != null ? dollarsToCents(body.estimated_value) : null); }
      if (body.is_archived !== undefined) { fields.push('is_archived = ?'); values.push(body.is_archived ? 1 : 0); }
      if (body.mha_default_eligible !== undefined) { fields.push('mha_default_eligible = ?'); values.push(body.mha_default_eligible ? 1 : 0); }

      if (fields.length > 0) {
        fields.push("updated_at = datetime('now')");
        values.push(accId);
        wrap(`UPDATE accounts SET ${fields.join(', ')} WHERE id = ? AND household_id = 1`).run(values);
      }
      const updated = wrap('SELECT * FROM accounts WHERE id = ?').get([accId]);
      if (updated) {
        updated.current_balance = centsToDollars(updated.current_balance);
        updated.estimated_value = updated.estimated_value != null ? centsToDollars(updated.estimated_value) : null;
      }
      return { success: true, account: updated };
    }

    if (!subRoute && method === 'DELETE') {
      wrap('DELETE FROM transactions WHERE account_id = ? AND household_id = 1').run([accId]);
      wrap('DELETE FROM credit_card_profiles WHERE account_id = ? AND household_id = 1').run([accId]);
      wrap('DELETE FROM account_balance_records WHERE account_id = ? AND household_id = 1').run([accId]);
      wrap('DELETE FROM accounts WHERE id = ? AND household_id = 1').run([accId]);
      return { success: true };
    }

    if (subRoute === 'archive' && method === 'POST') {
      wrap('UPDATE accounts SET is_archived = 1, updated_at = datetime("now") WHERE id = ? AND household_id = 1').run([accId]);
      return { success: true };
    }

    if (subRoute === 'unarchive' && method === 'POST') {
      wrap('UPDATE accounts SET is_archived = 0, updated_at = datetime("now") WHERE id = ? AND household_id = 1').run([accId]);
      return { success: true };
    }

    if (subRoute === 'records' && method === 'POST') {
      const recDate = body.recordDate || new Date().toISOString().slice(0, 10);
      const balCents = dollarsToCents(body.balance || 0);
      wrap(`
        INSERT INTO account_balance_records (household_id, account_id, record_date, balance, source)
        VALUES (1, ?, ?, ?, 'manual')
      `).run([accId, recDate, balCents]);
      return { success: true };
    }

    if (subRoute === 'credit-card-profile' && method === 'PUT') {
      const annualFee = body.annual_fee != null ? dollarsToCents(body.annual_fee) : null;
      const creditLimit = body.credit_limit != null ? dollarsToCents(body.credit_limit) : null;
      const authUsersJson = JSON.stringify(body.authorized_users || []);
      const rewardCatJson = JSON.stringify(body.reward_categories || []);
      const benefitsJson = JSON.stringify(body.benefits || []);

      wrap(`
        INSERT INTO credit_card_profiles (
          household_id, account_id, card_name, issuer_name, network, image_url,
          annual_fee, annual_fee_post_date, credit_limit, authorized_users_json,
          reward_categories_json, benefits_json, updated_at
        ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
        ON CONFLICT(household_id, account_id) DO UPDATE SET
          card_name = excluded.card_name,
          issuer_name = excluded.issuer_name,
          network = excluded.network,
          image_url = excluded.image_url,
          annual_fee = excluded.annual_fee,
          annual_fee_post_date = excluded.annual_fee_post_date,
          credit_limit = excluded.credit_limit,
          authorized_users_json = excluded.authorized_users_json,
          reward_categories_json = excluded.reward_categories_json,
          benefits_json = excluded.benefits_json,
          updated_at = datetime('now')
      `).run([
        accId,
        body.card_name || null,
        body.issuer_name || null,
        body.network || null,
        body.image_url || null,
        annualFee,
        body.annual_fee_post_date || null,
        creditLimit,
        authUsersJson,
        rewardCatJson,
        benefitsJson
      ]);
      return { success: true };
    }
  }

  // ---------------------------------------------------------------------------
  // Categories
  // ---------------------------------------------------------------------------
  if (pathname === '/api/categories') {
    if (method === 'GET') {
      const items = wrap('SELECT * FROM categories WHERE household_id = 1 ORDER BY sort_order ASC, name ASC').all();
      return { items };
    }
    if (method === 'POST') {
      const name = String(body.name || '').trim();
      const color = String(body.color || '#888888');
      const icon = body.icon || null;
      const mhaDefaultEligible = body.mha_default_eligible ? 1 : 0;
      const mhaDefaultIgnored = body.mha_default_ignored ? 1 : 0;
      const res = wrap(`
        INSERT INTO categories (household_id, name, color, icon, mha_default_eligible, mha_default_ignored)
        VALUES (1, ?, ?, ?, ?, ?)
      `).run([name, color, icon, mhaDefaultEligible, mhaDefaultIgnored]);
      const created = wrap('SELECT * FROM categories WHERE id = ?').get([res.lastInsertRowid]);
      return { success: true, category: created };
    }
  }

  const categoryIdMatch = pathname.match(/^\/api\/categories\/(\d+)$/);
  if (categoryIdMatch) {
    const catId = Number(categoryIdMatch[1]);
    if (method === 'PUT') {
      const fields = [];
      const values = [];
      if (body.name !== undefined) { fields.push('name = ?'); values.push(body.name); }
      if (body.color !== undefined) { fields.push('color = ?'); values.push(body.color); }
      if (body.icon !== undefined) { fields.push('icon = ?'); values.push(body.icon); }
      if (body.mha_default_eligible !== undefined) { fields.push('mha_default_eligible = ?'); values.push(body.mha_default_eligible ? 1 : 0); }
      if (body.mha_default_ignored !== undefined) { fields.push('mha_default_ignored = ?'); values.push(body.mha_default_ignored ? 1 : 0); }
      if (fields.length > 0) {
        fields.push("updated_at = datetime('now')");
        values.push(catId);
        wrap(`UPDATE categories SET ${fields.join(', ')} WHERE id = ? AND household_id = 1`).run(values);
      }
      const updated = wrap('SELECT * FROM categories WHERE id = ?').get([catId]);
      return { success: true, category: updated };
    }
    if (method === 'DELETE') {
      wrap('UPDATE transactions SET category_id = NULL WHERE category_id = ? AND household_id = 1').run([catId]);
      wrap('UPDATE transactions SET edited_category_id = NULL WHERE edited_category_id = ? AND household_id = 1').run([catId]);
      wrap('DELETE FROM budgets WHERE category_id = ? AND household_id = 1').run([catId]);
      wrap('DELETE FROM categories WHERE id = ? AND household_id = 1').run([catId]);
      return { success: true };
    }
  }

  // ---------------------------------------------------------------------------
  // Transactions
  // ---------------------------------------------------------------------------
  if (pathname === '/api/transactions') {
    if (method === 'GET') {
      const limit = Math.max(1, Math.min(200, Number(params.get('limit') || 50)));
      const page = Math.max(1, Number(params.get('page') || 1));
      const offset = (page - 1) * limit;

      const where = ['t.household_id = 1'];
      const args = [];

      const search = params.get('search');
      if (search) {
        where.push('(t.original_merchant LIKE ? OR t.original_description LIKE ? OR t.edited_merchant LIKE ? OR t.notes LIKE ?)');
        const q = `%${search}%`;
        args.push(q, q, q, q);
      }

      const accountId = params.get('account_id') || params.get('accountId');
      if (accountId) {
        where.push('t.account_id = ?');
        args.push(Number(accountId));
      }

      const categoryId = params.get('category_id') || params.get('categoryId');
      if (categoryId) {
        where.push(`${EFFECTIVE_CATEGORY_ID_SQL} = ?`);
        args.push(Number(categoryId));
      }

      const startDate = params.get('start_date') || params.get('startDate');
      if (startDate) {
        where.push('t.date >= ?');
        args.push(startDate);
      }

      const endDate = params.get('end_date') || params.get('endDate');
      if (endDate) {
        where.push('t.date <= ?');
        args.push(endDate);
      }

      const includeIgnored = params.get('include_ignored') === '1';
      if (!includeIgnored) {
        where.push('COALESCE(t.edited_is_ignored, t.is_ignored) = 0');
      }

      const whereClause = where.join(' AND ');

      const countSql = `SELECT COUNT(*) AS total FROM transactions t WHERE ${whereClause}`;
      const totalCount = wrap(countSql).get(args)?.total || 0;

      const sort = params.get('sort') || 'date_desc';
      const orderClause = sort === 'date_asc' ? 't.date ASC, t.id ASC' : 't.date DESC, t.id DESC';

      const dataSql = `
        SELECT t.id, t.account_id, t.date, t.amount,
               COALESCE(t.edited_merchant, t.original_merchant) AS merchant,
               COALESCE(t.edited_is_transfer, t.is_transfer) AS is_transfer,
               COALESCE(t.edited_is_ignored, t.is_ignored) AS is_ignored,
               t.original_merchant, t.original_description,
               t.category_id AS original_category_id,
               ${EFFECTIVE_CATEGORY_ID_SQL} AS category_id,
               t.edited_merchant, t.edited_merchant_source,
               t.edited_category_id, t.edited_category_id_source,
               t.edited_is_transfer, t.edited_is_transfer_source,
               t.edited_is_ignored, t.edited_is_ignored_source,
               t.notes, t.transfer_pair_id,
               a.name AS account_name,
               c.name AS category_name, c.color AS category_color, c.icon AS category_icon
          FROM transactions t
          LEFT JOIN accounts a ON a.id = t.account_id AND a.household_id = 1
          LEFT JOIN categories c ON c.id = ${EFFECTIVE_CATEGORY_ID_SQL}
         WHERE ${whereClause}
         ORDER BY ${orderClause}
         LIMIT ? OFFSET ?
      `;

      const rows = wrap(dataSql).all([...args, limit, offset]).map((r) => {
        r.amount = centsToDollars(r.amount);
        r.has_edits = Boolean(
          r.edited_merchant_source ||
          r.edited_category_id_source ||
          r.edited_is_transfer_source ||
          r.edited_is_ignored_source
        );
        return r;
      });

      const monthCountSql = `
        SELECT substr(t.date, 1, 7) AS month, COUNT(*) AS count
          FROM transactions t
         WHERE ${whereClause}
         GROUP BY substr(t.date, 1, 7)
         ORDER BY month DESC
      `;
      const monthCounts = wrap(monthCountSql).all(args);

      const totalPages = Math.max(1, Math.ceil(totalCount / limit));

      return {
        transactions: rows,
        items: rows,
        total: totalCount,
        totalCount,
        totalPages,
        pageCount: totalPages,
        page,
        grandTotal: totalCount,
        monthlyTotal: 0,
        monthCounts
      };
    }

    if (method === 'POST') {
      const accountId = Number(body.account_id || body.accountId);
      const date = String(body.date || new Date().toISOString().slice(0, 10));
      const amountCents = dollarsToCents(body.amount || 0);
      const merchant = String(body.merchant || body.original_merchant || '').trim();
      const description = String(body.description || body.original_description || merchant).trim();
      const categoryId = body.category_id != null ? Number(body.category_id) : null;
      const isTransfer = body.is_transfer ? 1 : 0;
      const isIgnored = body.is_ignored ? 1 : 0;
      const notes = body.notes || null;

      const res = wrap(`
        INSERT INTO transactions (
          household_id, account_id, date, amount,
          original_merchant, original_description, category_id,
          is_transfer, is_ignored, notes
        ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run([accountId, date, amountCents, merchant, description, categoryId, isTransfer, isIgnored, notes]);

      // Update account balance
      wrap('UPDATE accounts SET current_balance = current_balance + ?, updated_at = datetime("now") WHERE id = ? AND household_id = 1')
        .run([amountCents, accountId]);

      const inserted = wrap('SELECT * FROM transactions WHERE id = ?').get([res.lastInsertRowid]);
      inserted.amount = centsToDollars(inserted.amount);
      return { success: true, transaction: inserted };
    }
  }

  const txnIdMatch = pathname.match(/^\/api\/transactions\/(\d+)(?:\/(.*))?$/);
  if (txnIdMatch) {
    const txnId = Number(txnIdMatch[1]);
    const subRoute = txnIdMatch[2];

    if (!subRoute && method === 'PATCH') {
      const fields = [];
      const values = [];

      if (body.merchant !== undefined) {
        fields.push('edited_merchant = ?', 'edited_merchant_source = ?');
        values.push(body.merchant, 'user');
      }
      if (body.category_id !== undefined) {
        fields.push('edited_category_id = ?', 'edited_category_id_source = ?');
        values.push(body.category_id, 'user');
      }
      if (body.is_transfer !== undefined) {
        fields.push('edited_is_transfer = ?', 'edited_is_transfer_source = ?');
        values.push(body.is_transfer ? 1 : 0, 'user');
      }
      if (body.is_ignored !== undefined) {
        fields.push('edited_is_ignored = ?', 'edited_is_ignored_source = ?');
        values.push(body.is_ignored ? 1 : 0, 'user');
      }
      if (body.notes !== undefined) {
        fields.push('notes = ?');
        values.push(body.notes);
      }

      if (fields.length > 0) {
        fields.push("updated_at = datetime('now')");
        values.push(txnId);
        wrap(`UPDATE transactions SET ${fields.join(', ')} WHERE id = ? AND household_id = 1`).run(values);
      }

      const updated = wrap('SELECT * FROM transactions WHERE id = ?').get([txnId]);
      if (updated) updated.amount = centsToDollars(updated.amount);
      return { success: true, transaction: updated };
    }

    if (!subRoute && method === 'DELETE') {
      const txn = wrap('SELECT * FROM transactions WHERE id = ? AND household_id = 1').get([txnId]);
      if (txn) {
        wrap('UPDATE accounts SET current_balance = current_balance - ?, updated_at = datetime("now") WHERE id = ? AND household_id = 1')
          .run([txn.amount, txn.account_id]);
        wrap('DELETE FROM transactions WHERE id = ? AND household_id = 1').run([txnId]);
      }
      return { success: true };
    }

    if (subRoute === 'reset' && method === 'POST') {
      wrap(`
        UPDATE transactions
           SET edited_merchant = NULL, edited_merchant_source = NULL,
               edited_category_id = NULL, edited_category_id_source = NULL,
               edited_is_transfer = NULL, edited_is_transfer_source = NULL,
               edited_is_ignored = NULL, edited_is_ignored_source = NULL,
               updated_at = datetime('now')
         WHERE id = ? AND household_id = 1
      `).run([txnId]);
      return { success: true };
    }
  }

  // ---------------------------------------------------------------------------
  // Budgets
  // ---------------------------------------------------------------------------
  if (pathname === '/api/budgets') {
    if (method === 'GET') {
      const rows = wrap('SELECT * FROM budgets WHERE household_id = 1').all().map((b) => {
        b.amount = centsToDollars(b.amount);
        return b;
      });
      return { items: rows };
    }

    if (method === 'PUT') {
      const items = Array.isArray(body.items) ? body.items : Array.isArray(body) ? body : [];
      for (const item of items) {
        const catId = Number(item.category_id || item.categoryId);
        const amtCents = dollarsToCents(item.amount || 0);
        wrap(`
          INSERT INTO budgets (household_id, category_id, amount, updated_at)
          VALUES (1, ?, ?, datetime('now'))
          ON CONFLICT(household_id, category_id) DO UPDATE SET
            amount = excluded.amount,
            updated_at = datetime('now')
        `).run([catId, amtCents]);
      }
      return { success: true };
    }
  }

  if (pathname === '/api/budgets/months' && method === 'GET') {
    const rows = wrap('SELECT DISTINCT substr(date, 1, 7) AS month FROM transactions WHERE household_id = 1 ORDER BY month DESC').all();
    const months = rows.map((r) => r.month).filter(Boolean);
    const currentMonth = new Date().toISOString().slice(0, 7);
    if (!months.includes(currentMonth)) months.unshift(currentMonth);
    return { months };
  }

  // ---------------------------------------------------------------------------
  // Goals
  // ---------------------------------------------------------------------------
  if (pathname === '/api/goals') {
    if (method === 'GET') {
      const goals = wrap('SELECT * FROM goals WHERE household_id = 1 ORDER BY sort_order ASC, id ASC').all().map((g) => {
        g.target_amount = centsToDollars(g.target_amount);
        g.current_amount = centsToDollars(g.current_amount);
        return g;
      });
      return { items: goals, goals };
    }

    if (method === 'POST') {
      const name = String(body.name || '').trim();
      const targetCents = dollarsToCents(body.target_amount || 0);
      const targetDate = body.target_date || null;
      const res = wrap(`
        INSERT INTO goals (household_id, name, target_amount, current_amount, target_date)
        VALUES (1, ?, ?, 0, ?)
      `).run([name, targetCents, targetDate]);
      const created = wrap('SELECT * FROM goals WHERE id = ?').get([res.lastInsertRowid]);
      created.target_amount = centsToDollars(created.target_amount);
      created.current_amount = centsToDollars(created.current_amount);
      return { success: true, goal: created };
    }
  }

  const goalIdMatch = pathname.match(/^\/api\/goals\/(\d+)$/);
  if (goalIdMatch) {
    const goalId = Number(goalIdMatch[1]);
    if (method === 'PUT') {
      const fields = [];
      const values = [];
      if (body.name !== undefined) { fields.push('name = ?'); values.push(body.name); }
      if (body.target_amount !== undefined) { fields.push('target_amount = ?'); values.push(dollarsToCents(body.target_amount)); }
      if (body.current_amount !== undefined) { fields.push('current_amount = ?'); values.push(dollarsToCents(body.current_amount)); }
      if (body.target_date !== undefined) { fields.push('target_date = ?'); values.push(body.target_date); }
      if (fields.length > 0) {
        fields.push("updated_at = datetime('now')");
        values.push(goalId);
        wrap(`UPDATE goals SET ${fields.join(', ')} WHERE id = ? AND household_id = 1`).run(values);
      }
      return { success: true };
    }
    if (method === 'DELETE') {
      wrap('DELETE FROM goals WHERE id = ? AND household_id = 1').run([goalId]);
      return { success: true };
    }
  }

  // ---------------------------------------------------------------------------
  // Rules
  // ---------------------------------------------------------------------------
  if (pathname === '/api/rules') {
    if (method === 'GET') {
      const rules = wrap('SELECT * FROM rules WHERE household_id = 1 ORDER BY priority DESC, id ASC').all().map((r) => {
        try { r.conditions = JSON.parse(r.conditions_json || '[]'); } catch { r.conditions = []; }
        try { r.actions = JSON.parse(r.actions_json || '[]'); } catch { r.actions = []; }
        r.enabled = Boolean(r.enabled);
        return r;
      });
      return { items: rules, rules };
    }

    if (method === 'POST') {
      const name = String(body.name || '').trim();
      const conditionsJson = JSON.stringify(body.conditions || []);
      const actionsJson = JSON.stringify(body.actions || []);
      const priority = Number(body.priority || 0);

      const res = wrap(`
        INSERT INTO rules (household_id, name, conditions_json, actions_json, priority, enabled)
        VALUES (1, ?, ?, ?, ?, 1)
      `).run([name, conditionsJson, actionsJson, priority]);
      const created = wrap('SELECT * FROM rules WHERE id = ?').get([res.lastInsertRowid]);
      return { success: true, rule: created };
    }
  }

  // ---------------------------------------------------------------------------
  // Upcoming / Recurring Items
  // ---------------------------------------------------------------------------
  if (pathname === '/api/upcoming') {
    if (method === 'GET') {
      const items = wrap('SELECT * FROM upcoming_items WHERE household_id = 1 ORDER BY next_date ASC').all().map((u) => {
        u.amount = centsToDollars(u.amount);
        return u;
      });
      const occurrences = wrap(`
        SELECT o.*, u.name, u.amount, u.account_id
          FROM upcoming_occurrences o
          JOIN upcoming_items u ON u.id = o.upcoming_item_id
         WHERE o.household_id = 1
         ORDER BY o.expected_date ASC
      `).all().map((o) => {
        o.amount = centsToDollars(o.amount);
        return o;
      });
      return { items, occurrences, suggestions: [] };
    }

    if (method === 'POST') {
      const name = String(body.name || '').trim();
      const amountCents = dollarsToCents(body.amount || 0);
      const cadence = body.cadence || 'monthly';
      const nextDate = body.next_date || new Date().toISOString().slice(0, 10);
      const accountId = body.account_id ? Number(body.account_id) : null;
      const categoryId = body.category_id ? Number(body.category_id) : null;

      const res = wrap(`
        INSERT INTO upcoming_items (household_id, name, amount, cadence, next_date, account_id, category_id)
        VALUES (1, ?, ?, ?, ?, ?, ?)
      `).run([name, amountCents, cadence, nextDate, accountId, categoryId]);
      const created = wrap('SELECT * FROM upcoming_items WHERE id = ?').get([res.lastInsertRowid]);
      return { success: true, item: created };
    }
  }

  // ---------------------------------------------------------------------------
  // Preferences
  // ---------------------------------------------------------------------------
  if (pathname === '/api/preferences') {
    if (method === 'GET') {
      const rows = wrap('SELECT key, value_json FROM user_preferences WHERE household_id = 1 AND user_id = 1').all();
      const prefs = {};
      for (const r of rows) {
        try { prefs[r.key] = JSON.parse(r.value_json); } catch { prefs[r.key] = r.value_json; }
      }
      return { preferences: prefs };
    }

    if (method === 'PUT') {
      const prefs = body.preferences || body;
      for (const [key, val] of Object.entries(prefs)) {
        const json = JSON.stringify(val);
        wrap(`
          INSERT INTO user_preferences (household_id, user_id, key, value_json, updated_at)
          VALUES (1, 1, ?, ?, datetime('now'))
          ON CONFLICT(household_id, user_id, key) DO UPDATE SET
            value_json = excluded.value_json,
            updated_at = datetime('now')
        `).run([key, json]);
      }
      return { success: true };
    }
  }

  const prefKeyMatch = pathname.match(/^\/api\/preferences\/(.+)$/);
  if (prefKeyMatch && method === 'PUT') {
    const key = decodeURIComponent(prefKeyMatch[1]);
    const json = JSON.stringify(body.value);
    wrap(`
      INSERT INTO user_preferences (household_id, user_id, key, value_json, updated_at)
      VALUES (1, 1, ?, ?, datetime('now'))
      ON CONFLICT(household_id, user_id, key) DO UPDATE SET
        value_json = excluded.value_json,
        updated_at = datetime('now')
    `).run([key, json]);
    return { success: true };
  }

  // ---------------------------------------------------------------------------
  // Household
  // ---------------------------------------------------------------------------
  if (pathname === '/api/household') {
    if (method === 'GET') {
      return getHouseholdPayload(wrap);
    }
  }

  if (pathname === '/api/household/retirement-history') {
    return { accounts: [], history: [] };
  }

  if (pathname === '/api/household/members' && method === 'POST') {
    const name = String(body.name || '').trim();
    const role = body.role || 'adult';
    const birthDate = body.birth_date || null;
    const employmentStatus = body.employment_status || 'employed';
    const employer = body.employer || null;
    const jobTitle = body.job_title || null;
    const grossIncome = dollarsToCents(body.gross_income_annual || 0);
    const netPay = dollarsToCents(body.net_pay_per_period || 0);
    const payFreq = body.pay_frequency || 'biweekly';
    const periods = Number(body.pay_periods_per_year) || 26;
    const retType = body.retirement_account_type || 'none';
    const empContribPct = Number(body.employee_contribution_percent) || 0;
    const empContribAnnual = dollarsToCents(body.employee_contribution_annual || 0);
    const matchPct = Number(body.employer_match_percent) || 0;
    const matchLimitPct = Number(body.employer_match_limit_percent) || 0;
    const matchCap = dollarsToCents(body.employer_match_annual_cap || 0);
    const healthPrem = dollarsToCents(body.health_premium_per_month || 0);
    const hsaContrib = dollarsToCents(body.hsa_contribution_annual || 0);
    const depCareFsa = dollarsToCents(body.dependent_care_fsa_annual || 0);
    const otherBen = dollarsToCents(body.other_benefits_annual || 0);
    const notes = body.notes || null;

    wrap(`
      INSERT INTO household_members (
        household_id, name, role, birth_date, employment_status, employer, job_title,
        gross_income_annual, net_pay_per_period, pay_frequency, pay_periods_per_year,
        retirement_account_type, employee_contribution_percent, employee_contribution_annual,
        employer_match_percent, employer_match_limit_percent, employer_match_annual_cap,
        health_premium_per_month, hsa_contribution_annual, dependent_care_fsa_annual,
        other_benefits_annual, notes
      ) VALUES (
        1, ?, ?, ?, ?, ?, ?,
        ?, ?, ?, ?,
        ?, ?, ?,
        ?, ?, ?,
        ?, ?, ?,
        ?, ?
      )
    `).run([
      name, role, birthDate, employmentStatus, employer, jobTitle,
      grossIncome, netPay, payFreq, periods,
      retType, empContribPct, empContribAnnual,
      matchPct, matchLimitPct, matchCap,
      healthPrem, hsaContrib, depCareFsa,
      otherBen, notes
    ]);

    return getHouseholdPayload(wrap);
  }

  const memberIdMatch = pathname.match(/^\/api\/household\/members\/(\d+)$/);
  if (memberIdMatch) {
    const memberId = Number(memberIdMatch[1]);
    if (method === 'PUT') {
      const fields = [];
      const values = [];
      if (body.name !== undefined) { fields.push('name = ?'); values.push(body.name); }
      if (body.role !== undefined) { fields.push('role = ?'); values.push(body.role); }
      if (body.birth_date !== undefined) { fields.push('birth_date = ?'); values.push(body.birth_date); }
      if (body.employment_status !== undefined) { fields.push('employment_status = ?'); values.push(body.employment_status); }
      if (body.employer !== undefined) { fields.push('employer = ?'); values.push(body.employer); }
      if (body.job_title !== undefined) { fields.push('job_title = ?'); values.push(body.job_title); }
      if (body.gross_income_annual !== undefined) { fields.push('gross_income_annual = ?'); values.push(dollarsToCents(body.gross_income_annual)); }
      if (body.net_pay_per_period !== undefined) { fields.push('net_pay_per_period = ?'); values.push(dollarsToCents(body.net_pay_per_period)); }
      if (body.pay_frequency !== undefined) { fields.push('pay_frequency = ?'); values.push(body.pay_frequency); }
      if (body.pay_periods_per_year !== undefined) { fields.push('pay_periods_per_year = ?'); values.push(Number(body.pay_periods_per_year)); }
      if (body.retirement_account_type !== undefined) { fields.push('retirement_account_type = ?'); values.push(body.retirement_account_type); }
      if (body.employee_contribution_percent !== undefined) { fields.push('employee_contribution_percent = ?'); values.push(Number(body.employee_contribution_percent)); }
      if (body.employee_contribution_annual !== undefined) { fields.push('employee_contribution_annual = ?'); values.push(dollarsToCents(body.employee_contribution_annual)); }
      if (body.employer_match_percent !== undefined) { fields.push('employer_match_percent = ?'); values.push(Number(body.employer_match_percent)); }
      if (body.employer_match_limit_percent !== undefined) { fields.push('employer_match_limit_percent = ?'); values.push(Number(body.employer_match_limit_percent)); }
      if (body.employer_match_annual_cap !== undefined) { fields.push('employer_match_annual_cap = ?'); values.push(dollarsToCents(body.employer_match_annual_cap)); }
      if (body.health_premium_per_month !== undefined) { fields.push('health_premium_per_month = ?'); values.push(dollarsToCents(body.health_premium_per_month)); }
      if (body.hsa_contribution_annual !== undefined) { fields.push('hsa_contribution_annual = ?'); values.push(dollarsToCents(body.hsa_contribution_annual)); }
      if (body.dependent_care_fsa_annual !== undefined) { fields.push('dependent_care_fsa_annual = ?'); values.push(dollarsToCents(body.dependent_care_fsa_annual)); }
      if (body.other_benefits_annual !== undefined) { fields.push('other_benefits_annual = ?'); values.push(dollarsToCents(body.other_benefits_annual)); }
      if (body.notes !== undefined) { fields.push('notes = ?'); values.push(body.notes); }

      if (fields.length > 0) {
        fields.push("updated_at = datetime('now')");
        values.push(memberId);
        wrap(`UPDATE household_members SET ${fields.join(', ')} WHERE id = ? AND household_id = 1`).run(values);
      }
      return getHouseholdPayload(wrap);
    }
    if (method === 'DELETE') {
      wrap('DELETE FROM household_members WHERE id = ? AND household_id = 1').run([memberId]);
      return getHouseholdPayload(wrap);
    }
  }

  // ---------------------------------------------------------------------------
  // MHA (Military Housing Allowance)
  // ---------------------------------------------------------------------------
  if (pathname === '/api/mha/settings') {
    if (method === 'GET') {
      const row = wrap('SELECT value FROM app_settings WHERE key = "mha_tracker_enabled" AND household_id = 1').get();
      return { enabled: row ? row.value === '1' : false };
    }
    if (method === 'PUT') {
      const val = body.enabled ? '1' : '0';
      wrap(`
        INSERT INTO app_settings (household_id, key, value, updated_at)
        VALUES (1, 'mha_tracker_enabled', ?, datetime('now'))
        ON CONFLICT(household_id, key) DO UPDATE SET
          value = excluded.value,
          updated_at = datetime('now')
      `).run([val]);
      return { success: true, enabled: body.enabled };
    }
  }

  // ---------------------------------------------------------------------------
  // SimpleFIN
  // ---------------------------------------------------------------------------
  if (pathname === '/api/simplefin/status') {
    const cfg = wrap('SELECT * FROM simplefin_config WHERE household_id = 1').get();
    return {
      configured: Boolean(cfg?.access_url),
      lastSync: cfg?.last_sync || null
    };
  }

  // ---------------------------------------------------------------------------
  // Full Backup & Restore
  // ---------------------------------------------------------------------------
  if (pathname === '/api/data/orbit-backup') {
    const household = wrap('SELECT id, name, default_currency, created_at, updated_at FROM households WHERE id = 1').get();
    const backupTables = [
      'accounts', 'categories', 'credit_card_profiles', 'transactions', 'rules',
      'budgets', 'goals', 'goal_account_allocations', 'account_balance_records',
      'household_members', 'household_income_records', 'household_retirement_accounts',
      'app_settings', 'user_preferences', 'upcoming_items', 'upcoming_occurrences'
    ];
    const tables = {};
    for (const tbl of backupTables) {
      try {
        tables[tbl] = wrap(`SELECT * FROM ${tbl} WHERE household_id = 1`).all();
      } catch {
        tables[tbl] = [];
      }
    }
    return {
      type: 'orbit_money_backup',
      version: 1,
      exportedAt: new Date().toISOString(),
      household: household || { id: 1, name: 'My Household', default_currency: 'USD' },
      tables
    };
  }

  if (pathname === '/api/data/orbit-restore' && method === 'POST') {
    const backup = body.backup || body;
    if (!backup?.tables) {
      throw new Error('Invalid Orbit backup JSON format.');
    }
    for (const [table, rows] of Object.entries(backup.tables)) {
      if (!Array.isArray(rows) || rows.length === 0) continue;
      try {
        wrap(`DELETE FROM ${table} WHERE household_id = 1`).run();
        for (const row of rows) {
          const cols = Object.keys(row).filter((c) => c !== 'id');
          const placeholders = cols.map(() => '?').join(', ');
          const colNames = cols.map((c) => `"${c}"`).join(', ');
          const vals = cols.map((c) => (c === 'household_id' ? 1 : row[c]));
          wrap(`INSERT INTO ${table} (${colNames}) VALUES (${placeholders})`).run(vals);
        }
      } catch (err) {
        console.warn(`[sqlite.worker] Local restore table ${table} partial error:`, err);
      }
    }
    await flushWorkerDatabase();
    return { success: true };
  }

  // ---------------------------------------------------------------------------
  // Raw Execute (for database migration / bridge commands)
  // ---------------------------------------------------------------------------
  if (pathname === '/api/exec' && method === 'POST') {
    if (body.sql) {
      wrap(body.sql).run(body.params || []);
      return { success: true };
    }
  }

  // Fallback for unmatched local endpoint
  console.warn(`[sqlite.worker] Unhandled local endpoint: ${method} ${pathname}`);
  return { success: true, items: [] };
}

// -----------------------------------------------------------------------------
// Worker Message Dispatcher
// -----------------------------------------------------------------------------
self.onmessage = async (e) => {
  const { id, type, url, options, bytes } = e.data || {};
  try {
    if (type === 'PING') {
      await getLocalDatabase();
      self.postMessage({ id, success: true });
      return;
    }

    if (type === 'API_REQUEST') {
      const db = await getLocalDatabase();
      const result = await handleWorkerApiRequest(db, url, options || {});
      self.postMessage({ id, success: true, result });
      return;
    }

    if (type === 'EXPORT_DATABASE') {
      await getLocalDatabase();
      await flushWorkerDatabase();
      const exportedBytes = activeDb.export();
      self.postMessage({ id, success: true, bytes: exportedBytes }, [exportedBytes.buffer]);
      return;
    }

    if (type === 'IMPORT_DATABASE') {
      if (saveTimeout) {
        clearTimeout(saveTimeout);
        saveTimeout = null;
      }
      const SQL = await getSqlEngine();
      const uint8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
      const db = new SQL.Database(uint8);
      runDatabaseMigrations(db);
      const exported = db.export();
      await saveBytesToIdb(exported);
      activeDb = db;
      savedVersion = dirtyVersion;
      self.postMessage({ id, success: true });
      return;
    }

    if (type === 'RESET_DATABASE') {
      if (saveTimeout) {
        clearTimeout(saveTimeout);
        saveTimeout = null;
      }
      await deletePersistedBytes();
      activeDb = null;
      initPromise = null;
      savedVersion = dirtyVersion;
      await getLocalDatabase();
      self.postMessage({ id, success: true });
      return;
    }

    if (type === 'FLUSH_DATABASE') {
      await flushWorkerDatabase();
      self.postMessage({ id, success: true });
      return;
    }

    throw new Error(`[sqlite.worker] Unknown message type: ${type}`);
  } catch (err) {
    console.error(`[sqlite.worker] Error handling message ${type}:`, err);
    self.postMessage({
      id,
      success: false,
      error: err.message || String(err),
      status: err.status || 500
    });
  }
};
