import {
  NAVIGATION_PREFS_STORAGE_KEY,
  normalizeNavigationPreferences
} from './navigation.js';

export const USER_PREFERENCE_KEYS = {
  navigationPreferences: 'navigationPreferences',
  dashboardLayout: 'dashboardLayout',
  dashboardHiddenBiggestTransactions: 'dashboardHiddenBiggestTransactions',
  dashboardHandledReviewTransactions: 'dashboardHandledReviewTransactions',
  dashboardGoalFocusId: 'dashboardGoalFocusId',
  dashboardRetirement: 'dashboardRetirement',
  settingsCardOrder: 'settingsCardOrder',
  budgetedSort: 'budgetedSort'
};

const DASHBOARD_LAYOUT_STORAGE_KEY = 'orbit-money-dashboard-layout-v2';
const BIGGEST_TRANSACTIONS_HIDDEN_KEY = 'orbit-money-biggest-transactions-hidden-v1';
const TRANSACTION_REVIEW_HANDLED_KEY = 'orbit-money-transaction-review-handled-v1';
const GOAL_FOCUS_STORAGE_KEY = 'orbit-money-dashboard-goal-focus-v1';
const RETIREMENT_PREFS_STORAGE_KEY = 'orbit-money-retirement-preferences-v1';
const SETTINGS_CARD_ORDER_STORAGE_KEY = 'orbit-money-settings-card-order';
const BUDGETED_SORT_STORAGE_KEY = 'orbit-money-budgeted-sort';

const BUDGETED_SORT_OPTIONS = new Set([
  'variance_desc',
  'remaining_asc',
  'spent_desc',
  'name_asc'
]);
const LEGACY_BUDGETED_SORT_OPTIONS = {
  pct_desc: 'variance_desc',
  pct_asc: 'remaining_asc',
  spent_asc: 'name_asc'
};
const RETIREMENT_PRESETS = new Set(['conservative', 'balanced', 'aggressive']);

function readJson(key, fallback = null) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* ignore */
  }
}

function readString(key, fallback = '') {
  try {
    return localStorage.getItem(key) || fallback;
  } catch {
    return fallback;
  }
}

function writeString(key, value) {
  try {
    if (value === null || value === undefined || value === '') localStorage.removeItem(key);
    else localStorage.setItem(key, String(value));
  } catch {
    /* ignore */
  }
}

function normalizeIdArray(value) {
  return Array.isArray(value)
    ? Array.from(new Set(value.map(Number).filter(Number.isFinite))).slice(-1000)
    : [];
}

function normalizeGoalFocusId(value) {
  const id = Number(value);
  return Number.isFinite(id) && id > 0 ? id : null;
}

function normalizeRetirementPreferences(value) {
  const prefs = value && typeof value === 'object' ? value : {};
  return {
    retirementAge: Number(prefs.retirementAge) || 67,
    presetKey: RETIREMENT_PRESETS.has(prefs.presetKey) ? prefs.presetKey : 'balanced'
  };
}

function normalizeBudgetedSort(value) {
  if (BUDGETED_SORT_OPTIONS.has(value)) return value;
  return LEGACY_BUDGETED_SORT_OPTIONS[value] || 'variance_desc';
}

export function normalizePreferenceValue(key, value) {
  switch (key) {
    case USER_PREFERENCE_KEYS.navigationPreferences:
      return normalizeNavigationPreferences(value);
    case USER_PREFERENCE_KEYS.dashboardLayout:
      return Array.isArray(value) ? value : null;
    case USER_PREFERENCE_KEYS.dashboardHiddenBiggestTransactions:
    case USER_PREFERENCE_KEYS.dashboardHandledReviewTransactions:
      return normalizeIdArray(value);
    case USER_PREFERENCE_KEYS.dashboardGoalFocusId:
      return normalizeGoalFocusId(value);
    case USER_PREFERENCE_KEYS.dashboardRetirement:
      return normalizeRetirementPreferences(value);
    case USER_PREFERENCE_KEYS.settingsCardOrder:
      return Array.isArray(value) ? value : [];
    case USER_PREFERENCE_KEYS.budgetedSort:
      return normalizeBudgetedSort(value);
    default:
      return value ?? null;
  }
}

export function defaultUserPreferences() {
  return {
    [USER_PREFERENCE_KEYS.navigationPreferences]: normalizeNavigationPreferences(),
    [USER_PREFERENCE_KEYS.dashboardLayout]: null,
    [USER_PREFERENCE_KEYS.dashboardHiddenBiggestTransactions]: [],
    [USER_PREFERENCE_KEYS.dashboardHandledReviewTransactions]: [],
    [USER_PREFERENCE_KEYS.dashboardGoalFocusId]: null,
    [USER_PREFERENCE_KEYS.dashboardRetirement]: normalizeRetirementPreferences(),
    [USER_PREFERENCE_KEYS.settingsCardOrder]: [],
    [USER_PREFERENCE_KEYS.budgetedSort]: 'variance_desc'
  };
}

export function readLocalUserPreferences() {
  return {
    [USER_PREFERENCE_KEYS.navigationPreferences]: normalizeNavigationPreferences(
      readJson(NAVIGATION_PREFS_STORAGE_KEY, {})
    ),
    [USER_PREFERENCE_KEYS.dashboardLayout]: readJson(DASHBOARD_LAYOUT_STORAGE_KEY, null),
    [USER_PREFERENCE_KEYS.dashboardHiddenBiggestTransactions]: normalizeIdArray(
      readJson(BIGGEST_TRANSACTIONS_HIDDEN_KEY, [])
    ),
    [USER_PREFERENCE_KEYS.dashboardHandledReviewTransactions]: normalizeIdArray(
      readJson(TRANSACTION_REVIEW_HANDLED_KEY, [])
    ),
    [USER_PREFERENCE_KEYS.dashboardGoalFocusId]: normalizeGoalFocusId(
      readString(GOAL_FOCUS_STORAGE_KEY, '')
    ),
    [USER_PREFERENCE_KEYS.dashboardRetirement]: normalizeRetirementPreferences(
      readJson(RETIREMENT_PREFS_STORAGE_KEY, {})
    ),
    [USER_PREFERENCE_KEYS.settingsCardOrder]: readJson(SETTINGS_CARD_ORDER_STORAGE_KEY, []),
    [USER_PREFERENCE_KEYS.budgetedSort]: normalizeBudgetedSort(
      readString(BUDGETED_SORT_STORAGE_KEY, 'variance_desc')
    )
  };
}

export function mergeUserPreferences(serverPreferences = {}, localPreferences = readLocalUserPreferences()) {
  const defaults = defaultUserPreferences();
  const merged = {};
  for (const key of Object.values(USER_PREFERENCE_KEYS)) {
    const source = Object.prototype.hasOwnProperty.call(serverPreferences, key)
      ? serverPreferences[key]
      : localPreferences[key] ?? defaults[key];
    merged[key] = normalizePreferenceValue(key, source);
  }
  return merged;
}

export function missingServerPreferencePatch(serverPreferences = {}, localPreferences = readLocalUserPreferences()) {
  const defaults = defaultUserPreferences();
  const patch = {};
  for (const key of Object.values(USER_PREFERENCE_KEYS)) {
    if (Object.prototype.hasOwnProperty.call(serverPreferences, key)) continue;
    const value = normalizePreferenceValue(key, localPreferences[key] ?? defaults[key]);
    if (JSON.stringify(value) !== JSON.stringify(defaults[key])) patch[key] = value;
  }
  return patch;
}

export function writeLocalPreference(key, value) {
  const normalized = normalizePreferenceValue(key, value);
  switch (key) {
    case USER_PREFERENCE_KEYS.navigationPreferences:
      writeJson(NAVIGATION_PREFS_STORAGE_KEY, normalized);
      break;
    case USER_PREFERENCE_KEYS.dashboardLayout:
      writeJson(DASHBOARD_LAYOUT_STORAGE_KEY, normalized);
      break;
    case USER_PREFERENCE_KEYS.dashboardHiddenBiggestTransactions:
      writeJson(BIGGEST_TRANSACTIONS_HIDDEN_KEY, normalized);
      break;
    case USER_PREFERENCE_KEYS.dashboardHandledReviewTransactions:
      writeJson(TRANSACTION_REVIEW_HANDLED_KEY, normalized);
      break;
    case USER_PREFERENCE_KEYS.dashboardGoalFocusId:
      writeString(GOAL_FOCUS_STORAGE_KEY, normalized);
      break;
    case USER_PREFERENCE_KEYS.dashboardRetirement:
      writeJson(RETIREMENT_PREFS_STORAGE_KEY, normalized);
      break;
    case USER_PREFERENCE_KEYS.settingsCardOrder:
      writeJson(SETTINGS_CARD_ORDER_STORAGE_KEY, normalized);
      break;
    case USER_PREFERENCE_KEYS.budgetedSort:
      writeString(BUDGETED_SORT_STORAGE_KEY, normalized);
      break;
    default:
      break;
  }
  return normalized;
}
