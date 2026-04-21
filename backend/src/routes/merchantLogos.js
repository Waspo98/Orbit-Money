import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import { reportMerchantLogoStatus } from '../services/merchantLogos.js';

const router = express.Router();

router.post('/report', requireAuth, (req, res) => {
  const merchantKey = String(req.body?.merchant_key || '').trim();
  const status = String(req.body?.status || '').trim();

  if (!merchantKey || !['loaded', 'failed'].includes(status)) {
    return res.status(400).json({ error: 'merchant_key and valid status are required.' });
  }

  try {
    const result = reportMerchantLogoStatus(db, { merchantKey, status });
    res.json({ success: true, ...result });
  } catch (err) {
    console.error('Report merchant logo status failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
