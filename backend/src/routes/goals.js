import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import {
  formatLocalDate,
  formatLocalMonth,
  isValidOptionalDateOnly
} from '../lib/localDate.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendRouteError,
  sendServerError
} from '../lib/http.js';
import { centsToDollars, dollarsToCents } from '../lib/money.js';
import { parseId, parseInteger, readIdParam } from '../lib/routeParams.js';

const router = express.Router();

const ELIGIBLE_ACCOUNT_TYPES = new Set(['checking', 'savings', 'cash', 'investment', 'other']);
const GOAL_KINDS = new Set(['retirement', 'college', 'car', 'home', 'emergency', 'travel', 'custom']);
const ALLOCATION_TYPES = new Set(['percent', 'fixed']);

function monthKey(date) {
  return String(date || '').slice(0, 7);
}

function currentMonth() {
  return formatLocalMonth();
}

function addMonths(key, amount) {
  const [year, month] = key.split('-').map(Number);
  const d = new Date(year, month - 1 + amount, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function addMonthsToDate(date, amount) {
  const next = new Date(date);
  next.setMonth(next.getMonth() + amount);
  return formatLocalDate(next);
}

function validDate(value) {
  return isValidOptionalDateOnly(value);
}

function goalError(message, status = 400) {
  const err = new Error(message);
  err.status = status;
  return err;
}

function accountBalance(account, balancesByAccount = null) {
  const raw = balancesByAccount ? balancesByAccount.get(account.id) : account.current_balance;
  return Math.max(0, Number(raw) || 0);
}

function reserveByAccount(allocations) {
  const reserves = new Map();
  for (const allocation of allocations) {
    const current = reserves.get(allocation.account_id) || 0;
    reserves.set(allocation.account_id, Math.max(current, Number(allocation.reserve_amount) || 0));
  }
  return reserves;
}

function allocatableBasis(account, reserves, balancesByAccount = null) {
  return Math.max(0, accountBalance(account, balancesByAccount) - (reserves.get(account.id) || 0));
}

function allocationAmount(allocation, account, reserves, balancesByAccount = null) {
  const basis = allocatableBasis(account, reserves, balancesByAccount);
  if (allocation.allocation_type === 'percent') {
    return Math.round(basis * ((Number(allocation.allocation_percent) || 0) / 100));
  }
  return Math.min(Number(allocation.allocation_amount) || 0, basis);
}

function monthlyPace(history) {
  if (!history || history.length < 2) return 0;
  const recent = history.slice(-7);
  const first = recent[0];
  const last = recent[recent.length - 1];
  const months = Math.max(1, recent.length - 1);
  return ((Number(last.amount) || 0) - (Number(first.amount) || 0)) / months;
}

function estimateEta(currentAmount, targetAmount, monthlyAmount) {
  if (currentAmount >= targetAmount) return { date: null, months: 0, status: 'complete' };
  if (monthlyAmount <= 0) return { date: null, months: null, status: 'stalled' };
  const months = Math.ceil((targetAmount - currentAmount) / monthlyAmount);
  return {
    date: addMonthsToDate(new Date(), months),
    months,
    status: 'projected'
  };
}

function fetchGoals() {
  return db
    .prepare(
      `SELECT id, name, target_amount, current_amount, target_date,
              linked_account_id, kind, icon, notes, sort_order, created_at, updated_at
         FROM goals
        ORDER BY sort_order ASC, id ASC`
    )
    .all();
}

function fetchEligibleAccounts() {
  return db
    .prepare(
      `SELECT id, name, type, institution, account_number_last4,
              current_balance, estimated_value, is_archived, sort_order
         FROM accounts
        WHERE is_archived = 0
        ORDER BY sort_order ASC, name ASC`
    )
    .all()
    .filter((account) => ELIGIBLE_ACCOUNT_TYPES.has(account.type));
}

function fetchAllocations() {
  return db
    .prepare(
      `SELECT ga.id, ga.goal_id, ga.account_id, ga.allocation_type,
              ga.allocation_percent, ga.allocation_amount,
              ga.reserve_amount, ga.created_at, ga.updated_at,
              g.name AS goal_name
         FROM goal_account_allocations ga
         JOIN goals g ON g.id = ga.goal_id
        ORDER BY ga.created_at ASC, ga.id ASC`
    )
    .all();
}

function buildHistories(accounts, goals, allocations, months) {
  const latestTransaction = db
    .prepare('SELECT MAX(date) AS latest, MIN(date) AS earliest FROM transactions')
    .get();
  const latestRecord = db
    .prepare(
      `SELECT MAX(record_date) AS latest, MIN(record_date) AS earliest
         FROM account_balance_records`
    )
    .get();

  const todayMonth = currentMonth();
  const latestDataMonth = [
    monthKey(latestTransaction.latest),
    monthKey(latestRecord.latest),
    todayMonth
  ].filter(Boolean).sort().at(-1);
  const latestMonth = latestDataMonth > todayMonth ? latestDataMonth : todayMonth;
  const floorMonth = addMonths(latestMonth, -(months - 1));
  const earliestMonth = [
    monthKey(latestTransaction.earliest),
    monthKey(latestRecord.earliest)
  ].filter(Boolean).sort()[0] || latestMonth;
  const firstMonth = earliestMonth > floorMonth ? earliestMonth : floorMonth;

  const monthlyDeltas = db
    .prepare(
      `SELECT account_id, substr(date, 1, 7) AS month, SUM(amount) AS amount
         FROM transactions
        WHERE date >= ?
        GROUP BY account_id, substr(date, 1, 7)
        ORDER BY month ASC`
    )
    .all(`${firstMonth}-01`);
  const balanceRecords = db
    .prepare(
      `SELECT account_id, record_date, balance
         FROM account_balance_records
        WHERE record_date <= ?
        ORDER BY account_id ASC, record_date DESC`
    )
    .all(`${latestMonth}-31`);

  const accountById = new Map(accounts.map((account) => [account.id, account]));
  const balancesByAccount = new Map(accounts.map((account) => [account.id, Number(account.current_balance) || 0]));
  const allocationsByGoal = new Map();
  const reserves = reserveByAccount(allocations);

  for (const allocation of allocations) {
    if (!allocationsByGoal.has(allocation.goal_id)) allocationsByGoal.set(allocation.goal_id, []);
    allocationsByGoal.get(allocation.goal_id).push(allocation);
  }

  const deltasByMonth = new Map();
  for (const row of monthlyDeltas) {
    if (!deltasByMonth.has(row.month)) deltasByMonth.set(row.month, []);
    deltasByMonth.get(row.month).push(row);
  }

  const recordsByAccount = new Map();
  for (const record of balanceRecords) {
    if (!recordsByAccount.has(record.account_id)) recordsByAccount.set(record.account_id, []);
    recordsByAccount.get(record.account_id).push({
      month: monthKey(record.record_date),
      date: record.record_date,
      balance: Number(record.balance) || 0
    });
  }
  for (const records of recordsByAccount.values()) {
    records.sort((a, b) => b.date.localeCompare(a.date));
  }

  function snapshotBalanceForMonth(accountId, month) {
    const records = recordsByAccount.get(accountId);
    if (!records) return null;
    const record = records.find((item) => item.month <= month);
    return record ? record.balance : null;
  }

  const histories = new Map(goals.map((goal) => [goal.id, []]));
  let cursor = latestMonth;

  while (cursor >= firstMonth) {
    const monthBalances = new Map();
    for (const account of accounts) {
      const snapshotBalance = snapshotBalanceForMonth(account.id, cursor);
      monthBalances.set(account.id, snapshotBalance ?? (balancesByAccount.get(account.id) || 0));
    }

    for (const goal of goals) {
      const total = (allocationsByGoal.get(goal.id) || []).reduce((sum, allocation) => {
        const account = accountById.get(allocation.account_id);
        if (!account) return sum;
        return sum + allocationAmount(allocation, account, reserves, monthBalances);
      }, 0);
      histories.get(goal.id).push({ month: cursor, amount: total });
    }

    for (const delta of deltasByMonth.get(cursor) || []) {
      balancesByAccount.set(
        delta.account_id,
        (balancesByAccount.get(delta.account_id) || 0) - (Number(delta.amount) || 0)
      );
    }

    cursor = addMonths(cursor, -1);
  }

  for (const rows of histories.values()) rows.reverse();
  return histories;
}

function buildGoalsPayload(monthCount = 24) {
  const months = Math.min(60, Math.max(3, Number.isFinite(monthCount) ? monthCount : 24));
  const goals = fetchGoals();
  const accounts = fetchEligibleAccounts();
  const allocations = fetchAllocations();
  const accountById = new Map(accounts.map((account) => [account.id, account]));
  const allocationsByGoal = new Map();
  const allocationsByAccount = new Map();
  const reserves = reserveByAccount(allocations);

  for (const allocation of allocations) {
    if (!allocationsByGoal.has(allocation.goal_id)) allocationsByGoal.set(allocation.goal_id, []);
    allocationsByGoal.get(allocation.goal_id).push(allocation);
    if (!allocationsByAccount.has(allocation.account_id)) allocationsByAccount.set(allocation.account_id, []);
    allocationsByAccount.get(allocation.account_id).push(allocation);
  }

  const histories = buildHistories(accounts, goals, allocations, months);

  const hydratedGoals = goals.map((goal) => {
    const goalAllocations = allocationsByGoal.get(goal.id) || [];
    const history = histories.get(goal.id) || [];
    const currentAmount = goalAllocations.reduce((sum, allocation) => {
      const account = accountById.get(allocation.account_id);
      if (!account) return sum;
      return sum + allocationAmount(allocation, account, reserves);
    }, 0);
    const targetAmount = Number(goal.target_amount) || 0;
    const pace = monthlyPace(history);
    const eta = estimateEta(currentAmount, targetAmount, pace);

    return {
      ...goal,
      target_amount: centsToDollars(targetAmount),
      current_amount: centsToDollars(currentAmount),
      progress_percent: targetAmount > 0 ? Math.min(100, (currentAmount / targetAmount) * 100) : 0,
      monthly_pace: centsToDollars(pace),
      eta,
      history: history.map((point) => ({
        ...point,
        amount: centsToDollars(point.amount)
      })),
      allocations: goalAllocations.map((allocation) => {
        const account = accountById.get(allocation.account_id);
        return {
          id: allocation.id,
          goal_id: allocation.goal_id,
          account_id: allocation.account_id,
          account_name: account?.name || 'Deleted account',
          allocation_type: allocation.allocation_type,
          allocation_value: allocation.allocation_type === 'percent'
            ? Number(allocation.allocation_percent) || 0
            : centsToDollars(allocation.allocation_amount),
          reserve_amount: centsToDollars(allocation.reserve_amount),
          current_amount: account ? centsToDollars(allocationAmount(allocation, account, reserves)) : 0
        };
      })
    };
  });

  const accountSummaries = accounts.map((account) => {
    const accountAllocations = allocationsByAccount.get(account.id) || [];
    const reserveAmount = reserves.get(account.id) || 0;
    const basis = allocatableBasis(account, reserves);
    const allocatedAmount = accountAllocations.reduce(
      (sum, allocation) => sum + allocationAmount(allocation, account, reserves),
      0
    );
    const allocationItems = accountAllocations.map((allocation) => ({
      id: allocation.id,
      goal_id: allocation.goal_id,
      goal_name: allocation.goal_name,
      allocation_type: allocation.allocation_type,
      allocation_value: allocation.allocation_type === 'percent'
        ? Number(allocation.allocation_percent) || 0
        : centsToDollars(allocation.allocation_amount),
      reserve_amount: centsToDollars(allocation.reserve_amount),
      current_amount: centsToDollars(allocationAmount(allocation, account, reserves))
    }));

    return {
      ...account,
      current_balance: centsToDollars(account.current_balance),
      estimated_value: account.estimated_value == null ? null : centsToDollars(account.estimated_value),
      reserve_amount: centsToDollars(reserveAmount),
      allocatable_amount: centsToDollars(basis),
      allocated_amount: centsToDollars(allocatedAmount),
      remaining_amount: centsToDollars(Math.max(0, basis - allocatedAmount)),
      allocated_percent: basis > 0 ? (allocatedAmount / basis) * 100 : 0,
      allocations: allocationItems
    };
  });

  const totalTarget = goals.reduce((sum, goal) => sum + (Number(goal.target_amount) || 0), 0);
  const totalSaved = hydratedGoals.reduce((sum, goal) => sum + dollarsToCents(goal.current_amount), 0);
  const projectedGoals = hydratedGoals.filter((goal) => goal.eta.status === 'projected').length;

  return {
    summary: {
      goal_count: hydratedGoals.length,
      total_target: centsToDollars(totalTarget),
      total_saved: centsToDollars(totalSaved),
      progress_percent: totalTarget > 0 ? Math.min(100, (totalSaved / totalTarget) * 100) : 0,
      projected_goals: projectedGoals
    },
    goals: hydratedGoals,
    accounts: accountSummaries
  };
}

function normalizeGoalBody(body) {
  const name = String(body?.name || '').trim();
  if (!name) throw goalError('Goal name is required.');
  if (name.length > 90) throw goalError('Goal name must be 90 characters or fewer.');

  const targetAmount = Number(body?.target_amount);
  if (!Number.isFinite(targetAmount) || targetAmount <= 0) {
    throw goalError('target_amount must be a positive number.');
  }
  const targetAmountCents = dollarsToCents(targetAmount);

  const targetDate = body?.target_date ? String(body.target_date) : null;
  if (!validDate(targetDate)) throw goalError('target_date must use YYYY-MM-DD.');

  const kind = GOAL_KINDS.has(body?.kind) ? body.kind : 'custom';
  const icon = String(body?.icon || kind || 'target').trim().slice(0, 32);
  const notes = body?.notes == null ? null : String(body.notes).trim().slice(0, 500);

  const rawAllocations = Array.isArray(body?.allocations) ? body.allocations : [];
  const seen = new Set();
  const allocations = rawAllocations.map((row) => {
    const accountId = parseId(row?.account_id);
    if (accountId === null) throw goalError('Each allocation needs a valid account_id.');
    if (seen.has(accountId)) throw goalError('Each account can only be allocated once per goal.');
    seen.add(accountId);

    const allocationType = ALLOCATION_TYPES.has(row?.allocation_type) ? row.allocation_type : 'percent';
    const allocationValue = Number(row?.allocation_value);
    const reserveAmount = Number(row?.reserve_amount || 0);
    if (!Number.isFinite(allocationValue) || allocationValue < 0) {
      throw goalError('allocation_value must be a non-negative number.');
    }
    if (allocationType === 'percent' && allocationValue > 100) {
      throw goalError('Percent allocations cannot exceed 100%.');
    }
    if (!Number.isFinite(reserveAmount) || reserveAmount < 0) {
      throw goalError('reserve_amount must be a non-negative number.');
    }

    return {
      account_id: accountId,
      allocation_type: allocationType,
      allocation_percent: allocationType === 'percent' ? allocationValue : 0,
      allocation_amount: allocationType === 'fixed' ? dollarsToCents(allocationValue) : 0,
      reserve_amount: dollarsToCents(reserveAmount)
    };
  }).filter((row) =>
    row.allocation_type === 'percent'
      ? row.allocation_percent > 0
      : row.allocation_amount > 0
  );

  return {
    name,
    target_amount: targetAmountCents,
    target_date: targetDate,
    kind,
    icon,
    notes,
    allocations,
    stealFromOthers: !!body?.stealFromOthers && allocations.some((row) => row.allocation_type === 'fixed')
  };
}

function validateAccounts(allocations) {
  if (allocations.length === 0) {
    throw goalError('Connect at least one account and add an allocation.');
  }
  const placeholders = allocations.map(() => '?').join(',');
  const rows = db
    .prepare(`SELECT id, type, is_archived FROM accounts WHERE id IN (${placeholders})`)
    .all(...allocations.map((row) => row.account_id));
  const byId = new Map(rows.map((row) => [row.id, row]));

  for (const allocation of allocations) {
    const account = byId.get(allocation.account_id);
    if (!account || account.is_archived) {
      throw goalError('Allocations can only use active accounts.');
    }
    if (!ELIGIBLE_ACCOUNT_TYPES.has(account.type)) {
      throw goalError('Goals can use checking, savings, cash, investment, or other asset accounts.');
    }
  }
}

function accountAllocationTotal(rows, account, reserves) {
  return rows.reduce((sum, row) => sum + allocationAmount(row, account, reserves), 0);
}

function reduceOtherAllocations(accountId, protectedGoalId, overflow, basis, rows) {
  const candidates = rows
    .filter((row) => row.goal_id !== protectedGoalId)
    .map((row) => ({
      ...row,
      amount:
        row.allocation_type === 'percent'
          ? Math.round(basis * ((Number(row.allocation_percent) || 0) / 100))
          : Number(row.allocation_amount) || 0
    }))
    .filter((row) => row.amount > 0);

  const reducible = candidates.reduce((sum, row) => sum + row.amount, 0);
  if (reducible < overflow) {
    throw goalError('This allocation is larger than the account has available to steal.');
  }

  const update = db.prepare(
    `UPDATE goal_account_allocations
        SET allocation_percent = ?, allocation_amount = ?, updated_at = datetime('now')
      WHERE id = ?`
  );
  const remove = db.prepare('DELETE FROM goal_account_allocations WHERE id = ?');

  for (const row of candidates) {
    const removeAmount = Math.min(row.amount, Math.round(overflow * (row.amount / reducible)));
    let nextPercent = Number(row.allocation_percent) || 0;
    let nextAmount = Number(row.allocation_amount) || 0;
    if (row.allocation_type === 'percent') {
      nextPercent = Math.max(0, nextPercent - ((removeAmount / basis) * 100));
    } else {
      nextAmount = Math.max(0, nextAmount - removeAmount);
    }
    const isEmpty = row.allocation_type === 'percent'
      ? nextPercent < 0.01
      : nextAmount < 1;
    if (isEmpty) {
      remove.run(row.id);
    } else {
      update.run(nextPercent, nextAmount, row.id);
    }
  }

  return db
    .prepare('SELECT * FROM goal_account_allocations WHERE account_id = ?')
    .all(accountId);
}

function rebalanceAccounts(accountIds, protectedGoalId, stealFromOthers) {
  const uniqueIds = Array.from(new Set(accountIds));
  if (uniqueIds.length === 0) return;

  for (const accountId of uniqueIds) {
    const account = db
      .prepare('SELECT id, current_balance FROM accounts WHERE id = ?')
      .get(accountId);
    if (!account) continue;

    let rows = db
      .prepare('SELECT * FROM goal_account_allocations WHERE account_id = ?')
      .all(accountId);
    let reserves = reserveByAccount(rows);
    let basis = allocatableBasis(account, reserves);
    let total = accountAllocationTotal(rows, account, reserves);

    if (total <= basis) continue;
    if (!stealFromOthers) {
      throw goalError('This account does not have enough open balance for that allocation.');
    }
    if (basis <= 0) {
      throw goalError('This account has no allocatable balance after reserve.');
    }

    rows = reduceOtherAllocations(accountId, protectedGoalId, total - basis, basis, rows);
    reserves = reserveByAccount(rows);
    basis = allocatableBasis(account, reserves);
    total = accountAllocationTotal(rows, account, reserves);

    if (total > basis) {
      throw goalError('Allocation is still above the account balance after stealing from other goals.');
    }
  }
}

function saveGoal(existingId, body) {
  const normalized = normalizeGoalBody(body);
  validateAccounts(normalized.allocations);

  const run = db.transaction(() => {
    let goalId = existingId;
    if (goalId) {
      const existing = db.prepare('SELECT id FROM goals WHERE id = ?').get(goalId);
      if (!existing) throw goalError('Goal not found.', 404);
      db.prepare(
        `UPDATE goals
            SET name = ?, target_amount = ?, target_date = ?, kind = ?, icon = ?,
                notes = ?, updated_at = datetime('now')
          WHERE id = ?`
      ).run(
        normalized.name,
        normalized.target_amount,
        normalized.target_date,
        normalized.kind,
        normalized.icon,
        normalized.notes,
        goalId
      );
      db.prepare('DELETE FROM goal_account_allocations WHERE goal_id = ?').run(goalId);
    } else {
      const nextOrder = db.prepare('SELECT COALESCE(MAX(sort_order), -1) + 1 AS next_order FROM goals').get().next_order;
      const result = db
        .prepare(
          `INSERT INTO goals (name, target_amount, current_amount, target_date, kind, icon, notes, sort_order)
           VALUES (?, ?, 0, ?, ?, ?, ?, ?)`
        )
        .run(
          normalized.name,
          normalized.target_amount,
          normalized.target_date,
          normalized.kind,
          normalized.icon,
          normalized.notes,
          nextOrder
        );
      goalId = result.lastInsertRowid;
    }

    const insertAllocation = db.prepare(
      `INSERT INTO goal_account_allocations
         (goal_id, account_id, allocation_type, allocation_percent, allocation_amount, reserve_amount)
       VALUES (?, ?, ?, ?, ?, ?)`
    );
    for (const allocation of normalized.allocations) {
      insertAllocation.run(
        goalId,
        allocation.account_id,
        allocation.allocation_type,
        allocation.allocation_percent,
        allocation.allocation_amount,
        allocation.reserve_amount
      );
    }

    rebalanceAccounts(
      normalized.allocations.map((row) => row.account_id),
      goalId,
      normalized.stealFromOthers
    );

    return goalId;
  });

  return run();
}

router.get('/', requireAuth, (req, res) => {
  try {
    const requestedMonths = parseInteger(req.query.months);
    sendOk(res, buildGoalsPayload(requestedMonths));
  } catch (err) {
    console.error('List goals failed:', err);
    sendServerError(res, err);
  }
});

router.post('/', requireAuth, (req, res) => {
  try {
    const id = saveGoal(null, req.body || {});
    sendOk(res, { success: true, id, ...buildGoalsPayload(24) });
  } catch (err) {
    console.error('Create goal failed:', err);
    sendRouteError(res, err);
  }
});

router.put('/reorder', requireAuth, (req, res) => {
  const ids = Array.isArray(req.body?.ids)
    ? req.body.ids.map(parseId).filter((id) => id !== null)
    : [];

  if (ids.length === 0 || new Set(ids).size !== ids.length) {
    return sendBadRequest(res, 'ids must be a non-empty list of unique goal ids.');
  }

  try {
    const existing = db.prepare('SELECT id FROM goals').all().map((row) => row.id);
    const existingSet = new Set(existing);
    if (ids.length !== existing.length || ids.some((id) => !existingSet.has(id))) {
      return sendBadRequest(res, 'ids must include every goal exactly once.');
    }

    const update = db.prepare('UPDATE goals SET sort_order = ? WHERE id = ?');
    const run = db.transaction(() => {
      ids.forEach((id, index) => update.run(index, id));
    });
    run();

    sendOk(res, { success: true, ...buildGoalsPayload(24) });
  } catch (err) {
    console.error('Reorder goals failed:', err);
    sendServerError(res, err);
  }
});

router.put('/:id', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'goal');
  if (id === null) return;
  try {
    const savedId = saveGoal(id, req.body || {});
    sendOk(res, { success: true, id: savedId, ...buildGoalsPayload(24) });
  } catch (err) {
    console.error('Update goal failed:', err);
    sendRouteError(res, err);
  }
});

router.delete('/:id', requireAuth, (req, res) => {
  const id = readIdParam(req, res, 'id', 'goal');
  if (id === null) return;
  try {
    const result = db.prepare('DELETE FROM goals WHERE id = ?').run(id);
    if (result.changes === 0) {
      return sendNotFound(res, 'Goal not found.');
    }
    sendOk(res, { success: true });
  } catch (err) {
    console.error('Delete goal failed:', err);
    sendServerError(res, err);
  }
});

export default router;
