// =============================================================================
// scheduler.js - tiny hourly tick for background household jobs.
// =============================================================================
// Design goals:
//   - No new npm dependency (no node-cron)
//   - Survives container restarts - idempotence based on DB state
//   - Runs background household work from one simple hourly loop
//   - Only runs the daily sync once per local day, regardless of tick count
//
// The check runs every 60 minutes (plus once at startup after a 30s warmup).
// Each tick:
//   - cleans up expired sample households
//   - reconciles upcoming transaction occurrences
//   - sends due scheduled push notifications
//   - triggers the SimpleFIN scheduled sync after 6 AM local if it has not run today
// =============================================================================

import { db } from './db/index.js';
import { cleanupExpiredSampleHouseholds } from './services/sampleHouseholds.js';
import { runSync } from './services/simplefinSync.js';
import { reconcileUpcomingTransactions } from './services/upcomingReconciliation.js';
import { runScheduledNotificationChecks } from './services/notifications.js';

const TICK_MS = 60 * 60 * 1000; // 60 minutes
const STARTUP_WARMUP_MS = 30 * 1000;
const SCHEDULED_HOUR = 6; // 6 AM local

function localTodayIsoDate() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

async function tick() {
  try {
    const result = cleanupExpiredSampleHouseholds(db);
    if (result.householdsDeleted > 0 || result.usersDeleted > 0) {
      console.log('[scheduler] Sample cleanup done:', result);
    }
  } catch (err) {
    console.error('[scheduler] Sample cleanup failed:', err.message || err);
  }

  try {
    const result = reconcileUpcomingTransactions(db);
    if (result.occurrences_created > 0 || result.matched > 0 || result.missed > 0) {
      console.log('[scheduler] Upcoming reconciliation done:', result);
    }
  } catch (err) {
    console.error('[scheduler] Upcoming reconciliation failed:', err.message || err);
  }

  try {
    const result = await runScheduledNotificationChecks();
    if (result.sent > 0) {
      console.log('[scheduler] Notifications sent:', result);
    }
  } catch (err) {
    console.error('[scheduler] Notification check failed:', err.message || err);
  }

  const now = new Date();
  if (now.getHours() < SCHEDULED_HOUR) return;

  const configs = db
    .prepare(
      `SELECT household_id, last_sync_at
         FROM simplefin_config
        WHERE access_url_encrypted IS NOT NULL
          AND sync_enabled = 1`
    )
    .all();
  if (configs.length === 0) return;

  for (const cfg of configs) {
    // Check the most recent successful sync's local date.
    const lastSuccess = db
      .prepare(
        `SELECT finished_at FROM sync_log
          WHERE household_id = ?
            AND status = 'success'
          ORDER BY finished_at DESC
          LIMIT 1`
      )
      .get(cfg.household_id);

    if (lastSuccess?.finished_at) {
      // sqlite `datetime('now')` returns UTC text. Convert to local date.
      const lastLocalDate = new Date(lastSuccess.finished_at + 'Z')
        .toLocaleDateString('en-CA'); // yyyy-mm-dd in local tz
      if (lastLocalDate === localTodayIsoDate()) continue; // already ran today
    }

    try {
      console.log(`[scheduler] Triggering daily sync for household ${cfg.household_id}...`);
      const result = await runSync({ trigger: 'scheduled', householdId: cfg.household_id });
      console.log('[scheduler] Sync done:', result);
    } catch (err) {
      console.error('[scheduler] Sync failed:', err.message || err);
    }
  }
}

export function startScheduler() {
  setTimeout(() => {
    tick();
    setInterval(tick, TICK_MS);
  }, STARTUP_WARMUP_MS);
  console.log(`Scheduler started (checks hourly, runs daily at ${SCHEDULED_HOUR}:00 local).`);
}
