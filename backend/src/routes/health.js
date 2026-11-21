import fs from 'fs';
import express from 'express';
import { db } from '../db/index.js';
import { sendOk, sendStatusPayload } from '../lib/http.js';

const pkg = JSON.parse(
  fs.readFileSync(new URL('../../package.json', import.meta.url), 'utf8')
);
export const appVersion = pkg.version || '0.0.0';

/**
 * Builds the /api/health router backed by the given SQLite database handle.
 */
export function createHealthRouter(database, version = appVersion) {
  const router = express.Router();

  /**
   * GET /api/health
   * Returns app version and basic DB counts so clients (and Docker healthcheck)
   * can confirm the DB is reachable and migrations ran. Public — no auth required.
   */
  router.get('/', (req, res) => {
    try {
      const transactions = database.prepare('SELECT COUNT(*) AS c FROM transactions').get().c;
      const categories   = database.prepare('SELECT COUNT(*) AS c FROM categories').get().c;
      const accounts     = database.prepare('SELECT COUNT(*) AS c FROM accounts').get().c;
      const rules        = database.prepare('SELECT COUNT(*) AS c FROM rules').get().c;

      sendOk(res, {
        status: 'ok',
        version,
        counts: { transactions, categories, accounts, rules }
      });
    } catch (err) {
      sendStatusPayload(res, 500, { status: 'error', error: err.message });
    }
  });

  return router;
}

export default createHealthRouter(db);
