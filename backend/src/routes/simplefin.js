import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import { encrypt } from '../crypto.js';
import {
  sendBadRequest,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { parseBoundedInteger } from '../lib/routeParams.js';
import { claimAccessUrl } from '../services/simplefinClient.js';
import { runSync } from '../services/simplefinSync.js';

const router = express.Router();

/**
 * GET /api/simplefin/status
 * High-level connection + last-sync status for the Settings page + banner.
 */
router.get('/status', requireAuth, (req, res) => {
  try {
    const cfg = db.prepare('SELECT * FROM simplefin_config WHERE id = 1').get();
    const connected = !!(cfg && cfg.access_url_encrypted);

    const lastSync = db
      .prepare(
        `SELECT id, started_at, finished_at, status, error_message,
                transactions_inserted, accounts_unmatched
           FROM sync_log
          ORDER BY started_at DESC
          LIMIT 1`
      )
      .get() || null;

    sendOk(res, {
      connected,
      syncEnabled: connected ? !!cfg.sync_enabled : false,
      cutoverDate: cfg?.cutover_date || null,
      lastSyncAt: cfg?.last_sync_at || null,
      lastSync
    });
  } catch (err) {
    console.error('SimpleFIN status failed:', err);
    sendServerError(res, err);
  }
});

/**
 * POST /api/simplefin/setup
 * Body: { setupToken, cutoverDate }  (cutoverDate: 'YYYY-MM-DD')
 *
 * Exchanges the setup token for an access URL, encrypts it, stores it, and
 * records the cutover date. Does NOT run a sync — Neal clicks "Sync Now" or
 * waits for the 6 AM scheduler.
 */
router.post('/setup', requireAuth, async (req, res) => {
  const { setupToken, cutoverDate } = req.body || {};
  if (!setupToken || !cutoverDate) {
    return sendBadRequest(res, 'setupToken and cutoverDate are required.');
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(cutoverDate)) {
    return sendBadRequest(res, 'cutoverDate must be in YYYY-MM-DD format.');
  }

  try {
    const accessUrl = await claimAccessUrl(setupToken);
    const encrypted = encrypt(accessUrl);

    db.prepare(
      `
      INSERT INTO simplefin_config (id, access_url_encrypted, cutover_date, sync_enabled, updated_at)
      VALUES (1, ?, ?, 1, datetime('now'))
      ON CONFLICT(id) DO UPDATE SET
        access_url_encrypted = excluded.access_url_encrypted,
        cutover_date = excluded.cutover_date,
        sync_enabled = 1,
        updated_at = datetime('now')
    `
    ).run(encrypted, cutoverDate);

    sendOk(res, { success: true });
  } catch (err) {
    console.error('SimpleFIN setup failed:', err);
    sendBadRequest(res, err.message || 'Setup failed');
  }
});

/**
 * POST /api/simplefin/sync
 * Kicks off a manual sync. Returns the summary when complete.
 */
router.post('/sync', requireAuth, async (req, res) => {
  try {
    const result = await runSync({ trigger: 'manual' });
    sendOk(res, { success: true, ...result });
  } catch (err) {
    console.error('Manual sync failed:', err);
    sendServerError(res, new Error(err.message || 'Sync failed'));
  }
});

/**
 * GET /api/simplefin/sync-log?limit=20
 * Returns recent sync log rows for display in Settings.
 */
router.get('/sync-log', requireAuth, (req, res) => {
  const limit = parseBoundedInteger(req.query.limit, { fallback: 20, min: 1, max: 100 });
  try {
    const items = db
      .prepare(
        `SELECT id, started_at, finished_at, status, trigger,
                transactions_inserted, transactions_skipped,
                rm_rows_deleted, accounts_created, accounts_unmatched,
                transfers_matched, error_message
           FROM sync_log
          ORDER BY started_at DESC
          LIMIT ?`
      )
      .all(limit);
    sendOk(res, { items });
  } catch (err) {
    console.error('Sync log fetch failed:', err);
    sendServerError(res, err);
  }
});

/**
 * POST /api/simplefin/disconnect
 * Clears the encrypted access URL and disables sync. Does NOT delete any
 * transactions or accounts — those stay for history.
 */
router.post('/disconnect', requireAuth, (req, res) => {
  try {
    db.prepare(
      `UPDATE simplefin_config
          SET access_url_encrypted = NULL,
              sync_enabled = 0,
              updated_at = datetime('now')
        WHERE id = 1`
    ).run();
    sendOk(res, { success: true });
  } catch (err) {
    console.error('Disconnect failed:', err);
    sendServerError(res, err);
  }
});

export default router;
