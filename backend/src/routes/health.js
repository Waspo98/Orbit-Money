import express from 'express';
import { db } from '../db/index.js';
import { sendOk, sendStatusPayload } from '../lib/http.js';

const router = express.Router();

/**
 * GET /api/health
 * Returns basic DB counts so the frontend (and Docker healthcheck) can confirm
 * the DB is reachable and migrations ran. Public — no auth required.
 */
router.get('/', (req, res) => {
  try {
    const transactions = db.prepare('SELECT COUNT(*) AS c FROM transactions').get().c;
    const categories   = db.prepare('SELECT COUNT(*) AS c FROM categories').get().c;
    const accounts     = db.prepare('SELECT COUNT(*) AS c FROM accounts').get().c;
    const rules        = db.prepare('SELECT COUNT(*) AS c FROM rules').get().c;

    sendOk(res, {
      status: 'ok',
      counts: { transactions, categories, accounts, rules }
    });
  } catch (err) {
    sendStatusPayload(res, 500, { status: 'error', error: err.message });
  }
});

export default router;
