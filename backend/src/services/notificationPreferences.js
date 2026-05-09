export const NOTIFICATION_PREFERENCE_KEY = 'notificationPreferences';

const WEEK_DAYS = new Set([0, 1, 2, 3, 4, 5, 6]);
const TIME_RE = /^([01]\d|2[0-3]):[0-5]\d$/;
const INCOME_TIMING_MODES = new Set(['after_sync', 'scheduled']);
const SNAPSHOT_CADENCES = new Set(['monthly', 'quarterly']);

function normalizeTime(value, fallback = '09:00') {
  return TIME_RE.test(String(value || '')) ? String(value) : fallback;
}

function normalizeWeekDay(value, fallback = 1) {
  const day = Number(value);
  return WEEK_DAYS.has(day) ? day : fallback;
}

function normalizeIncomeTiming(value, fallback = 'after_sync') {
  if (value === 'default' || value === 'custom') return 'scheduled';
  return INCOME_TIMING_MODES.has(value) ? value : fallback;
}

function normalizeNotificationSectionTime(section, defaultTime, fallback = '09:00') {
  if (section?.timeMode === 'default' || section?.timing === 'default') {
    return normalizeTime(defaultTime, fallback);
  }
  return normalizeTime(section?.time, normalizeTime(defaultTime, fallback));
}

function normalizeAccountIds(value) {
  return Array.isArray(value)
    ? Array.from(new Set(value.map(Number).filter((id) => Number.isInteger(id) && id > 0))).slice(0, 200)
    : [];
}

export function defaultNotificationPreferences() {
  return {
    enabled: false,
    showAmounts: false,
    defaultTime: '09:00',
    weeklySnapshot: {
      enabled: true,
      dayOfWeek: 1,
      time: '09:00'
    },
    income: {
      enabled: true,
      timing: 'after_sync',
      time: '09:00'
    },
    syncIssues: {
      enabled: true
    },
    accountSnapshots: {
      enabled: false,
      accountIds: [],
      cadence: 'monthly',
      dayOfMonth: 1,
      time: '09:00'
    }
  };
}

export function normalizeNotificationPreferences(value = {}) {
  const defaults = defaultNotificationPreferences();
  const prefs = value && typeof value === 'object' ? value : {};
  const weekly = prefs.weeklySnapshot && typeof prefs.weeklySnapshot === 'object'
    ? prefs.weeklySnapshot
    : {};
  const income = prefs.income && typeof prefs.income === 'object' ? prefs.income : {};
  const syncIssues = prefs.syncIssues && typeof prefs.syncIssues === 'object'
    ? prefs.syncIssues
    : {};
  const snapshots = prefs.accountSnapshots && typeof prefs.accountSnapshots === 'object'
    ? prefs.accountSnapshots
    : {};
  const defaultTime = normalizeTime(prefs.defaultTime, defaults.defaultTime);

  return {
    enabled: prefs.enabled === true,
    showAmounts: prefs.showAmounts === true,
    defaultTime,
    weeklySnapshot: {
      enabled: weekly.enabled !== false,
      dayOfWeek: normalizeWeekDay(weekly.dayOfWeek, defaults.weeklySnapshot.dayOfWeek),
      time: normalizeNotificationSectionTime(weekly, defaultTime, defaults.weeklySnapshot.time)
    },
    income: {
      enabled: income.enabled !== false,
      timing: normalizeIncomeTiming(income.timing, defaults.income.timing),
      time: normalizeNotificationSectionTime(income, defaultTime, defaults.income.time)
    },
    syncIssues: {
      enabled: syncIssues.enabled !== false
    },
    accountSnapshots: {
      enabled: snapshots.enabled === true,
      accountIds: normalizeAccountIds(snapshots.accountIds),
      cadence: SNAPSHOT_CADENCES.has(snapshots.cadence)
        ? snapshots.cadence
        : defaults.accountSnapshots.cadence,
      dayOfMonth: Math.max(1, Math.min(28, Math.trunc(Number(snapshots.dayOfMonth) || 1))),
      time: normalizeNotificationSectionTime(snapshots, defaultTime, defaults.accountSnapshots.time)
    }
  };
}

export function notificationTimeFor(prefs, section) {
  const normalized = normalizeNotificationPreferences(prefs);
  const row = normalized[section] || {};
  return normalizeTime(row.time, normalized.defaultTime);
}
