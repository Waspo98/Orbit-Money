import express from 'express';
import multer from 'multer';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import { sendBadRequest, sendOk } from '../lib/http.js';
import { importRocketMoneyCSV } from '../services/csvImport.js';

const router = express.Router();

// Multer — keep file in memory. 20MB limit is ~5x Neal's ~2MB CSV, plenty.
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 20 * 1024 * 1024 }
});

/**
 * POST /api/import/rocket-money
 * multipart/form-data with a single file field named 'file'
 *
 * Returns:
 *   200 { inserted, skipped, accountsCreated, rulesCreated, totalRows, parseWarnings }
 *   400 { error } for malformed CSV / missing columns / etc.
 */
router.post('/rocket-money', requireAuth, upload.single('file'), (req, res) => {
  if (!req.file) {
    return sendBadRequest(res, 'No file uploaded.');
  }

  try {
    const summary = importRocketMoneyCSV(db, req.file.buffer);
    sendOk(res, { success: true, ...summary });
  } catch (err) {
    console.error('CSV import failed:', err);
    sendBadRequest(res, err.message || 'Import failed');
  }
});

export default router;
