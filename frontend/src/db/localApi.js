import { getLocalDatabase, createStatementWrapper, flushDatabase } from './localDb.js';
import { dollarsToCents, centsToDollars, moneyFieldsToDollars } from '../lib/money.js';
import { effectiveCategoryIdSql } from '../lib/effectiveSql.js';

const EFFECTIVE_CATEGORY_ID_SQL = effectiveCategoryIdSql('t');

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

export async function handleLocalApiRequest(url, options = {}) {
  const db = await getLocalDatabase();
  const method = String(options.method || 'GET').toUpperCase();
  const pathname = getPathname(url);
  const params = parseQueryParams(url);
  const body = safeJson(options.body);

  const wrap = (sql) => createStatementWrapper(db, sql);

  // -------------------------------------------------------------------------
  // Auth
  // -------------------------------------------------------------------------
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

  // -------------------------------------------------------------------------
  // Accounts
  // -------------------------------------------------------------------------
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

  // -------------------------------------------------------------------------
  // Categories
  // -------------------------------------------------------------------------
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

  // -------------------------------------------------------------------------
  // Transactions
  // -------------------------------------------------------------------------
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

      return {
        transactions: rows,
        items: rows,
        totalCount,
        page,
        pageCount: Math.ceil(totalCount / limit)
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

  // -------------------------------------------------------------------------
  // Budgets
  // -------------------------------------------------------------------------
  if (pathname === '/api/budgets') {
    if (method === 'GET') {
      const month = params.get('month');
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

  // -------------------------------------------------------------------------
  // Goals
  // -------------------------------------------------------------------------
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

  // -------------------------------------------------------------------------
  // Rules
  // -------------------------------------------------------------------------
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

  // -------------------------------------------------------------------------
  // Upcoming / Recurring Items
  // -------------------------------------------------------------------------
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

  // -------------------------------------------------------------------------
  // Preferences
  // -------------------------------------------------------------------------
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

  // -------------------------------------------------------------------------
  // MHA (Military Housing Allowance)
  // -------------------------------------------------------------------------
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

  // -------------------------------------------------------------------------
  // SimpleFIN
  // -------------------------------------------------------------------------
  if (pathname === '/api/simplefin/status') {
    const cfg = wrap('SELECT * FROM simplefin_config WHERE household_id = 1').get();
    return {
      configured: Boolean(cfg?.access_url),
      lastSync: cfg?.last_sync || null
    };
  }

  // -------------------------------------------------------------------------
  // Full Backup & Restore
  // -------------------------------------------------------------------------
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
        console.warn(`Local restore table ${table} partial error:`, err);
      }
    }
    await flushDatabase();
    return { success: true };
  }

  // Fallback for unmatched local endpoint
  console.warn(`[localApi] Unhandled local endpoint: ${method} ${pathname}`);
  return { success: true, items: [] };
}
