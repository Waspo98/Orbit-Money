import express from 'express';
import multer from 'multer';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import { sendBadRequest, sendOk } from '../lib/http.js';
import { importRocketMoneyCSV } from '../services/csvImport.js';

const router = express.Router();

// Multer keeps files in memory. 20MB is enough for large Rocket Money exports
// without writing private financial CSVs to disk.
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
  const householdId = requireHouseholdId(req);
  if (!req.file) {
    return sendBadRequest(res, 'No file uploaded.');
  }

  try {
    const summary = importRocketMoneyCSV(db, req.file.buffer, householdId);
    sendOk(res, { success: true, ...summary });
  } catch (err) {
    console.error('CSV import failed:', err);
    sendBadRequest(res, err.message || 'Import failed');
  }
});

export default router;
