import { dollarsToCents } from '../lib/money.js';
import { parseId } from '../lib/routeParams.js';
import {
  FREQUENCY_UNITS,
  findRuleDateOnOrAfter,
  isoDate,
  parseRecurrenceRule,
  recurrenceRuleToStorage
} from '../lib/upcomingSchedule.js';

export const KINDS = new Set(['bill', 'subscription', 'income', 'giving']);
export const FREQUENCY_TYPES = new Set([
  'weekly',
  'biweekly',
  'semimonthly',
  'monthly',
  'bimonthly',
  'quarterly',
  'yearly',
  'custom'
]);
export const AMOUNT_STRATEGIES = new Set(['fixed', 'history_average']);
export const BILL_CATEGORY_HINTS = ['bill', 'utilit', 'insurance', 'loan', 'mortgage', 'rent'];
export const MAX_LOOKBACK_MONTHS = 24;

export function upcomingError(message, status = 400) {
  const err = new Error(message);
  err.status = status;
  return err;
}

export function clampInteger(value, min, max, fallback) {
  const parsed = Math.trunc(Number(value));
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(min, Math.min(max, parsed));
}

export function normalizeRecurrenceRule(value) {
  if (!value) return null;
  const rule = parseRecurrenceRule(value);
  if (!rule) throw upcomingError('recurrence_rule is invalid.');
  return rule;
}

export function normalizeKind(value, direction = 'expense', categoryName = '') {
  if (KINDS.has(value)) return value;
  if (direction === 'income') return 'income';
  const text = categoryName.toLowerCase();
  if (BILL_CATEGORY_HINTS.some((hint) => text.includes(hint))) return 'bill';
  return 'subscription';
}

export function extractKindOverride(rawRecurrenceRule) {
  if (!rawRecurrenceRule) return null;
  let parsed = rawRecurrenceRule;
  if (typeof rawRecurrenceRule === 'string') {
    try {
      parsed = JSON.parse(rawRecurrenceRule);
    } catch {
      return null;
    }
  }
  return parsed && typeof parsed === 'object' && parsed.kind_override === 'giving' ? 'giving' : null;
}

export function encodeRecurrenceStorage(rule, isGiving) {
  if (!rule && !isGiving) return null;
  if (rule && isGiving) return JSON.stringify({ ...rule, kind_override: 'giving' });
  if (isGiving) return JSON.stringify({ kind_override: 'giving' });
  return recurrenceRuleToStorage(rule);
}

function validateLinkedRow(db, table, id, householdId, label) {
  if (id === null) return;
  const row = db.prepare(`SELECT id FROM ${table} WHERE id = ? AND household_id = ?`).get(id, householdId);
  if (!row) throw upcomingError(`${label} was not found in this household.`, 400);
}

export function normalizeUpcomingBody(db, body, householdId) {
  const name = String(body?.name || body?.merchant || '').trim();
  if (!name) throw upcomingError('Name is required.');
  const direction = body?.direction === 'income' ? 'income' : 'expense';
  const category = body?.category_name || '';
  const requestedKind = normalizeKind(body?.kind, direction, category);
  const isGiving = direction === 'expense' && requestedKind === 'giving';
  const sqlKind = isGiving ? 'bill' : requestedKind;
  const rawFreq = FREQUENCY_TYPES.has(body?.frequency_type) ? body.frequency_type : 'monthly';
  const isQuarterly = rawFreq === 'quarterly';
  const frequencyType = isQuarterly ? 'custom' : rawFreq;
  const frequencyInterval = isQuarterly ? 3 : Math.max(1, Math.min(365, Number(body?.frequency_interval) || 1));
  const frequencyUnit = isQuarterly
    ? 'months'
    : (FREQUENCY_UNITS.has(body?.frequency_unit) ? body.frequency_unit : 'months');
  const recurrenceRule = normalizeRecurrenceRule(body?.recurrence_rule);
  let nextDate = isoDate(body?.next_date);
  if (!nextDate) throw upcomingError('next_date must use YYYY-MM-DD.');
  if (recurrenceRule) nextDate = findRuleDateOnOrAfter(nextDate, recurrenceRule) || nextDate;
  const amount = Number(body?.amount);
  if (!Number.isFinite(amount)) throw upcomingError('amount must be a number.');
  const amountStrategy = AMOUNT_STRATEGIES.has(body?.amount_strategy) ? body.amount_strategy : 'fixed';
  const amountLookbackMonths = clampInteger(body?.amount_lookback_months, 1, MAX_LOOKBACK_MONTHS, 6);
  const categoryId = parseId(body?.category_id);
  const accountId = parseId(body?.account_id);
  const sourceTransactionId = parseId(body?.source_transaction_id);
  validateLinkedRow(db, 'categories', categoryId, householdId, 'Category');
  validateLinkedRow(db, 'accounts', accountId, householdId, 'Account');
  validateLinkedRow(db, 'transactions', sourceTransactionId, householdId, 'Source transaction');

  return {
    name: name.slice(0, 120),
    kind: sqlKind,
    source: body?.source === 'transaction' || body?.source === 'suggestion' ? body.source : 'manual',
    merchant: String(body?.merchant || name).trim().slice(0, 160),
    amount: dollarsToCents(Math.abs(amount)),
    direction,
    frequency_type: frequencyType,
    frequency_interval: frequencyInterval,
    frequency_unit: frequencyUnit,
    recurrence_rule: encodeRecurrenceStorage(recurrenceRule, isGiving),
    amount_strategy: amountStrategy,
    amount_lookback_months: amountLookbackMonths,
    next_date: nextDate,
    category_id: categoryId,
    account_id: accountId,
    source_transaction_id: sourceTransactionId,
    notes: body?.notes == null ? null : String(body.notes).trim().slice(0, 500)
  };
}
