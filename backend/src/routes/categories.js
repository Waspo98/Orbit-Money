import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';

const router = express.Router();

/**
 * GET /api/categories
 * Returns all categories ordered by sort_order. Same rationale as /accounts:
 * frontend caches this, uses it for lookup when rendering transactions.
 */
router.get('/', requireAuth, (req, res) => {
  try {
    const items = db
      .prepare(
        `
        SELECT id, name, color, icon, is_transfer, is_income, sort_order
        FROM categories
        ORDER BY sort_order ASC, name ASC
      `
      )
      .all();
    res.json({ items });
  } catch (err) {
    console.error('List categories failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
