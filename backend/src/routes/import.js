import express from 'express';
import { requireAuth, requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import { sendBadRequest, sendOk } from '../lib/http.js';
import { readIdParam } from '../lib/routeParams.js';
import { createMemoryUpload } from '../lib/uploads.js';
import {
  applyRocketMoneyImportBatch,
  createRocketMoneyImportBatch,
  importRocketMoneyCSV,
  undoImportBatch
} from '../services/csvImport.js';

const router = express.Router();

// Multer keeps files in memory. 20MB is enough for large Rocket Money exports
// without writing private financial CSVs to disk.
const upload = createMemoryUpload({ fileSizeMb: 20 });

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

router.post('/rocket-money/preview', requireAuth, upload.single('file'), (req, res) => {
  const householdId = requireHouseholdId(req);
  if (!req.file) {
    return sendBadRequest(res, 'No file uploaded.');
  }

  try {
    const summary = createRocketMoneyImportBatch(
      db,
      req.file.buffer,
      householdId,
      req.file.originalname || null
    );
    sendOk(res, { success: true, ...summary });
  } catch (err) {
    console.error('CSV preview failed:', err);
    sendBadRequest(res, err.message || 'Preview failed');
  }
});

router.post('/rocket-money/:id/commit', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'import preview');
  if (id === null) return;

  try {
    const summary = applyRocketMoneyImportBatch(db, id, householdId);
    sendOk(res, { success: true, ...summary });
  } catch (err) {
    console.error('CSV import commit failed:', err);
    sendBadRequest(res, err.message || 'Import failed');
  }
});

router.post('/batches/:id/undo', requireAuth, (req, res) => {
  const householdId = requireHouseholdId(req);
  const id = readIdParam(req, res, 'id', 'import batch');
  if (id === null) return;

  try {
    const summary = undoImportBatch(db, id, householdId);
    sendOk(res, { success: true, ...summary });
  } catch (err) {
    console.error('CSV import undo failed:', err);
    sendBadRequest(res, err.message || 'Undo failed');
  }
});

export default router;
