import webpush from 'web-push';
import { config } from '../config.js';
import { db } from '../db/index.js';
import { effectiveCategoryIdSql } from '../lib/effectiveSql.js';
import { formatLocalDate, formatLocalMonth } from '../lib/localDate.js';
import { buildMonthlyBudgetOverview } from './monthlyBudgetOverview.js';
import {
  NOTIFICATION_PREFERENCE_KEY,
  notificationTimeFor,
  normalizeNotificationPreferences
} from './notificationPreferences.js';

const EFFECTIVE_CATEGORY_ID_SQL = effectiveCategoryIdSql('t');
const PUSH_TTL_SECONDS = 60 * 60 * 24;

const pushReady = Boolean(
  config.webPushPublicKey &&
  config.webPushPrivateKey &&
  config.webPushSubject
);

if (pushReady) {
  webpush.setVapidDetails(
    config.webPushSubject,
    config.webPushPublicKey,
    config.webPushPrivateKey
  );
}

function parsePreferenceRow(row) {
  if (!row?.value_json) return null;
  try {
    return JSON.parse(row.value_json);
  } catch {
    return null;
  }
}

function readNotificationPreferences(householdId, userId) {
  const row = db
    .prepare(
      `SELECT value_json
         FROM user_preferences
        WHERE household_id = ?
          AND user_id = ?
          AND key = ?`
    )
    .get(householdId, userId, NOTIFICATION_PREFERENCE_KEY);
  return normalizeNotificationPreferences(parsePreferenceRow(row));
}

function activeHouseholdUsers(householdId) {
  return db
    .prepare(
      `SELECT hm.household_id, hm.user_id
         FROM household_memberships hm
         JOIN users u ON u.id = hm.user_id
        WHERE hm.household_id = ?
        ORDER BY hm.user_id ASC`
    )
    .all(householdId);
}

function activeSubscriptions(householdId, userId) {
  return db
    .prepare(
      `SELECT id, endpoint, p256dh, auth, expiration_time
         FROM push_subscriptions
        WHERE household_id = ?
          AND user_id = ?
          AND disabled_at IS NULL
        ORDER BY last_seen_at DESC`
    )
    .all(householdId, userId);
}

function alreadySent(householdId, userId, type, dedupeKey) {
  return !!db
    .prepare(
      `SELECT 1
         FROM notification_delivery_log
        WHERE household_id = ?
          AND user_id = ?
          AND type = ?
          AND dedupe_key = ?
        LIMIT 1`
    )
    .get(householdId, userId, type, dedupeKey);
}

function markSent(householdId, userId, { type, dedupeKey, title, url }) {
  db.prepare(
    `INSERT OR IGNORE INTO notification_delivery_log (
       household_id, user_id, type, dedupe_key, title, target_url, status
     ) VALUES (?, ?, ?, ?, ?, ?, 'sent')`
  ).run(householdId, userId, type, dedupeKey, title, url);
}

function disableSubscription(id) {
  db.prepare(
    `UPDATE push_subscriptions
        SET disabled_at = datetime('now'),
            updated_at = datetime('now')
      WHERE id = ?`
  ).run(id);
}

function payloadFor(notification) {
  return JSON.stringify({
    title: notification.title,
    body: notification.body,
    tag: notification.tag || `${notification.type}:${notification.dedupeKey}`,
    data: {
      type: notification.type,
      url: notification.url || '/dashboard'
    }
  });
}

async function sendToSubscription(subscription, payload) {
  try {
    await webpush.sendNotification(
      {
        endpoint: subscription.endpoint,
        expirationTime: subscription.expiration_time || null,
        keys: {
          p256dh: subscription.p256dh,
          auth: subscription.auth
        }
      },
      payload,
      { TTL: PUSH_TTL_SECONDS }
    );
    return { sent: true };
  } catch (err) {
    if (err.statusCode === 404 || err.statusCode === 410) {
      disableSubscription(subscription.id);
    }
    return { sent: false, error: err.message || String(err) };
  }
}

export function getNotificationRuntimeConfig() {
  return {
    configured: pushReady,
    vapidPublicKey: pushReady ? config.webPushPublicKey : null
  };
}

export function upsertPushSubscription({
  householdId,
  userId,
  endpoint,
  p256dh,
  auth,
  expirationTime = null,
  userAgent = null
}) {
  if (!endpoint || !p256dh || !auth) {
    throw new Error('Push subscription is incomplete.');
  }

  db.prepare(
    `INSERT INTO push_subscriptions (
       household_id, user_id, endpoint, p256dh, auth, expiration_time, user_agent
     ) VALUES (?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(endpoint) DO UPDATE SET
       household_id = excluded.household_id,
       user_id = excluded.user_id,
       p256dh = excluded.p256dh,
       auth = excluded.auth,
       expiration_time = excluded.expiration_time,
       user_agent = excluded.user_agent,
       disabled_at = NULL,
       last_seen_at = datetime('now'),
       updated_at = datetime('now')`
  ).run(householdId, userId, endpoint, p256dh, auth, expirationTime, userAgent);
}

export function removePushSubscription({ householdId, userId, endpoint }) {
  if (!endpoint) return;
  db.prepare(
    `UPDATE push_subscriptions
        SET disabled_at = datetime('now'),
            updated_at = datetime('now')
      WHERE household_id = ?
        AND user_id = ?
        AND endpoint = ?`
  ).run(householdId, userId, endpoint);
}

export function subscriptionSummary(householdId, userId) {
  const activeCount = db
    .prepare(
      `SELECT COUNT(*) AS count
         FROM push_subscriptions
        WHERE household_id = ?
          AND user_id = ?
          AND disabled_at IS NULL`
    )
    .get(householdId, userId).count;
  return { activeCount: Number(activeCount) || 0 };
}

export async function sendNotificationToUser({
  householdId,
  userId,
  prefs,
  notification
}) {
  if (!pushReady) return { sent: 0, skipped: 'not_configured' };

  const normalizedPrefs = normalizeNotificationPreferences(
    prefs || readNotificationPreferences(householdId, userId)
  );
  if (!normalizedPrefs.enabled) return { sent: 0, skipped: 'disabled' };
  if (alreadySent(householdId, userId, notification.type, notification.dedupeKey)) {
    return { sent: 0, skipped: 'duplicate' };
  }

  const subscriptions = activeSubscriptions(householdId, userId);
  if (subscriptions.length === 0) return { sent: 0, skipped: 'no_subscriptions' };

  const payload = payloadFor(notification);
  const results = await Promise.all(
    subscriptions.map((subscription) => sendToSubscription(subscription, payload))
  );
  const sent = results.filter((result) => result.sent).length;
  if (sent > 0) {
    markSent(householdId, userId, notification);
  }
  return { sent, failed: results.length - sent };
}

async function sendNotificationToHousehold(householdId, buildNotification, acceptsPrefs) {
  const users = activeHouseholdUsers(householdId);
  let sent = 0;
  for (const user of users) {
    const prefs = readNotificationPreferences(user.household_id, user.user_id);
    if (!prefs.enabled || !acceptsPrefs(prefs)) continue;
    const notification = await buildNotification(prefs, user.user_id);
    if (!notification) continue;
    const result = await sendNotificationToUser({
      householdId: user.household_id,
      userId: user.user_id,
      prefs,
      notification
    });
    sent += Number(result.sent || 0);
  }
  return sent;
}

function money(value) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0
  }).format(Number(value) || 0);
}

function addDays(date, days) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function localTime(now = new Date()) {
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

function isDueByTime(time, now = new Date()) {
  return localTime(now) >= time;
}

function weekKey(now = new Date()) {
  const day = now.getDay();
  const monday = addDays(now, day === 0 ? -6 : 1 - day);
  return formatLocalDate(monday);
}

function monthStart(monthKey) {
  return `${monthKey}-01`;
}

function quarterInfo(now = new Date()) {
  const monthIndex = now.getMonth();
  const quarter = Math.floor(monthIndex / 3) + 1;
  const startMonth = String((quarter - 1) * 3 + 1).padStart(2, '0');
  return {
    key: `${now.getFullYear()}-Q${quarter}`,
    start: `${now.getFullYear()}-${startMonth}-01`
  };
}

function countReviewCandidates(householdId, sinceDate) {
  return Number(
    db
      .prepare(
        `SELECT COUNT(*) AS count
           FROM transactions t
           LEFT JOIN categories c
             ON c.id = ${EFFECTIVE_CATEGORY_ID_SQL}
            AND c.household_id = t.household_id
          WHERE t.household_id = ?
            AND t.date >= ?
            AND COALESCE(t.edited_is_ignored, t.is_ignored) = 0
            AND COALESCE(t.edited_is_transfer, t.is_transfer) = 0
            AND (c.id IS NULL OR lower(c.name) = 'uncategorized')`
      )
      .get(householdId, sinceDate).count || 0
  );
}

function countUpcomingOccurrences(householdId, today) {
  const endDate = formatLocalDate(addDays(new Date(`${today}T00:00:00`), 7));
  return Number(
    db
      .prepare(
        `SELECT COUNT(*) AS count
           FROM upcoming_occurrences
          WHERE household_id = ?
            AND status = 'pending'
            AND expected_date >= ?
            AND expected_date <= ?`
      )
      .get(householdId, today, endDate).count || 0
  );
}

function buildWeeklySnapshot(householdId, prefs, now = new Date()) {
  const today = formatLocalDate(now);
  const month = formatLocalMonth(now);
  const overview = buildMonthlyBudgetOverview(db, { householdId, month });
  const budgeted = overview.budgeted || [];
  const overCount = budgeted.filter((item) => Number(item.amount) > 0 && Number(item.spent) > Number(item.amount)).length;
  const watchCount = budgeted.filter((item) => {
    const amount = Number(item.amount) || 0;
    const spent = Number(item.spent) || 0;
    return amount > 0 && spent <= amount && spent / amount >= 0.85;
  }).length;
  const reviewCount = countReviewCandidates(householdId, formatLocalDate(addDays(now, -30)));
  const upcomingCount = countUpcomingOccurrences(householdId, today);
  const totalSpent = Number(overview.summary?.total_spent || 0);

  const parts = [];
  if (overCount > 0) parts.push(`${overCount} over budget`);
  if (watchCount > 0) parts.push(`${watchCount} to watch`);
  if (reviewCount > 0) parts.push(`${reviewCount} to review`);
  if (upcomingCount > 0) parts.push(`${upcomingCount} upcoming`);
  if (parts.length === 0) parts.push('everything looks calm');

  return {
    type: 'weekly_snapshot',
    dedupeKey: `weekly:${weekKey(now)}`,
    title: 'Your Orbit Weekly Snapshot',
    body: prefs.showAmounts
      ? `${money(totalSpent)} spent this month; ${parts.join(', ')}.`
      : `This week: ${parts.join(', ')}.`,
    url: '/dashboard',
    tag: 'weekly_snapshot'
  };
}

function incomeNotificationBody(transaction, showAmounts) {
  const merchant = transaction.merchant || 'Income';
  if (showAmounts) {
    return `Cha-ching! ${merchant} just sent ${money(transaction.amount)} to your account.`;
  }
  return `Cha-ching! It's pay day. ${merchant} just hit your bank account.`;
}

function incomeUrl(transaction) {
  const params = new URLSearchParams({
    type: 'income',
    date_from: transaction.date,
    date_to: transaction.date,
    sort: 'date_desc'
  });
  return `/transactions?${params.toString()}`;
}

function fetchIncomeTransactions(householdId, { transactionIds = null, importedSince = null } = {}) {
  const args = [householdId];
  const filters = [
    't.household_id = ?',
    't.amount > 0',
    'c.is_income = 1',
    'COALESCE(t.edited_is_transfer, t.is_transfer) = 0',
    'COALESCE(t.edited_is_ignored, t.is_ignored) = 0'
  ];

  if (Array.isArray(transactionIds)) {
    if (transactionIds.length === 0) return [];
    filters.push(`t.id IN (${transactionIds.map(() => '?').join(',')})`);
    args.push(...transactionIds);
  }
  if (importedSince) {
    filters.push('t.imported_at >= ?');
    args.push(importedSince);
  }

  return db
    .prepare(
      `SELECT t.id,
              t.date,
              t.amount / 100.0 AS amount,
              COALESCE(t.edited_merchant, t.original_merchant, 'Income') AS merchant
         FROM transactions t
         JOIN categories c
           ON c.id = ${EFFECTIVE_CATEGORY_ID_SQL}
          AND c.household_id = t.household_id
        WHERE ${filters.join(' AND ')}
        ORDER BY t.date DESC, t.id DESC
        LIMIT 25`
    )
    .all(...args);
}

export async function sendIncomeNotificationsForTransactions({
  householdId,
  transactionIds,
  timing = 'after_sync'
}) {
  const transactions = fetchIncomeTransactions(householdId, { transactionIds });
  if (transactions.length === 0) return 0;

  let sent = 0;
  for (const transaction of transactions) {
    sent += await sendNotificationToHousehold(
      householdId,
      (prefs) => ({
        type: 'income',
        dedupeKey: `transaction:${transaction.id}`,
        title: 'Income Arrived',
        body: incomeNotificationBody(transaction, prefs.showAmounts),
        url: incomeUrl(transaction),
        tag: `income:${transaction.id}`
      }),
      (prefs) => prefs.income.enabled && prefs.income.timing === timing
    );
  }
  return sent;
}

async function sendScheduledIncomeNotifications(householdId, prefs, userId, now) {
  if (!prefs.income.enabled || prefs.income.timing === 'after_sync') return 0;
  const time = prefs.income.time;
  if (!isDueByTime(time, now)) return 0;

  const today = formatLocalDate(now);
  const transactions = fetchIncomeTransactions(householdId, {
    importedSince: `${today} 00:00:00`
  });
  let sent = 0;
  for (const transaction of transactions) {
    const result = await sendNotificationToUser({
      householdId,
      userId,
      prefs,
      notification: {
        type: 'income',
        dedupeKey: `transaction:${transaction.id}`,
        title: 'Income Arrived',
        body: incomeNotificationBody(transaction, prefs.showAmounts),
        url: incomeUrl(transaction),
        tag: `income:${transaction.id}`
      }
    });
    sent += Number(result.sent || 0);
  }
  return sent;
}

export async function notifySyncIssue({ householdId, logId, message, partial = false }) {
  return sendNotificationToHousehold(
    householdId,
    () => ({
      type: 'sync_issue',
      dedupeKey: `sync:${logId}`,
      title: partial ? 'Partial Sync' : 'Sync Issue',
      body: partial
        ? 'Orbit synced some accounts, but one connection needs attention.'
        : 'Orbit had trouble syncing your accounts.',
      url: '/settings/data-management',
      tag: `sync:${logId}`
    }),
    (prefs) => prefs.syncIssues.enabled
  );
}

function snapshotPeriod(now, cadence) {
  if (cadence === 'quarterly') {
    const quarter = quarterInfo(now);
    return quarter;
  }
  const month = formatLocalMonth(now);
  return { key: month, start: monthStart(month) };
}

function accountHasSnapshotThisPeriod(householdId, accountId, startDate, today) {
  return !!db
    .prepare(
      `SELECT 1
         FROM account_balance_records
        WHERE household_id = ?
          AND account_id = ?
          AND record_date >= ?
          AND record_date <= ?
        LIMIT 1`
    )
    .get(householdId, accountId, startDate, today);
}

async function sendSnapshotReminders(householdId, prefs, userId, now) {
  if (!prefs.accountSnapshots.enabled || prefs.accountSnapshots.accountIds.length === 0) {
    return 0;
  }
  if (now.getDate() !== prefs.accountSnapshots.dayOfMonth) return 0;
  const time = notificationTimeFor(prefs, 'accountSnapshots');
  if (!isDueByTime(time, now)) return 0;

  const today = formatLocalDate(now);
  const period = snapshotPeriod(now, prefs.accountSnapshots.cadence);
  const accounts = db
    .prepare(
      `SELECT id, name
         FROM accounts
        WHERE household_id = ?
          AND is_archived = 0
          AND id IN (${prefs.accountSnapshots.accountIds.map(() => '?').join(',')})
        ORDER BY sort_order ASC, name ASC`
    )
    .all(householdId, ...prefs.accountSnapshots.accountIds);

  let sent = 0;
  for (const account of accounts) {
    if (accountHasSnapshotThisPeriod(householdId, account.id, period.start, today)) {
      continue;
    }
    const result = await sendNotificationToUser({
      householdId,
      userId,
      prefs,
      notification: {
        type: 'account_snapshot',
        dedupeKey: `account:${account.id}:${period.key}`,
        title: 'Snapshot Time',
        body: `Time to update your ${account.name} snapshot.`,
        url: `/accounts?action=add-snapshot&accountId=${account.id}`,
        tag: `account-snapshot:${account.id}`
      }
    });
    sent += Number(result.sent || 0);
  }
  return sent;
}

export async function runScheduledNotificationChecks(now = new Date()) {
  if (!pushReady) return { sent: 0, skipped: 'not_configured' };
  const users = db
    .prepare(
      `SELECT hm.household_id, hm.user_id
         FROM household_memberships hm
         JOIN user_preferences up
           ON up.household_id = hm.household_id
          AND up.user_id = hm.user_id
          AND up.key = ?
        ORDER BY hm.household_id ASC, hm.user_id ASC`
    )
    .all(NOTIFICATION_PREFERENCE_KEY);

  let sent = 0;
  for (const user of users) {
    const prefs = readNotificationPreferences(user.household_id, user.user_id);
    if (!prefs.enabled) continue;

    if (
      prefs.weeklySnapshot.enabled &&
      now.getDay() === prefs.weeklySnapshot.dayOfWeek &&
      isDueByTime(notificationTimeFor(prefs, 'weeklySnapshot'), now)
    ) {
      const result = await sendNotificationToUser({
        householdId: user.household_id,
        userId: user.user_id,
        prefs,
        notification: buildWeeklySnapshot(user.household_id, prefs, now)
      });
      sent += Number(result.sent || 0);
    }

    sent += await sendScheduledIncomeNotifications(user.household_id, prefs, user.user_id, now);
    sent += await sendSnapshotReminders(user.household_id, prefs, user.user_id, now);
  }
  return { sent };
}
