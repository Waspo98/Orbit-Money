import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import { overrideMerchantLogo, reportMerchantLogoStatus } from '../services/merchantLogos.js';

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

router.post('/override', requireAuth, (req, res) => {
  const merchantKey = String(req.body?.merchant_key || '').trim();
  const useCategoryIcon = req.body?.use_category_icon === true;
  const rawLogoUrl = String(req.body?.logo_url || '').trim();

  if (!merchantKey) {
    return res.status(400).json({ error: 'merchant_key is required.' });
  }
  if (!useCategoryIcon && !rawLogoUrl) {
    return res.status(400).json({ error: 'logo_url is required unless using the category icon.' });
  }

  let logoUrl = null;
  if (rawLogoUrl) {
    try {
      const parsed = new URL(rawLogoUrl);
      if (!['http:', 'https:'].includes(parsed.protocol)) {
        throw new Error('Logo URL must start with http:// or https://.');
      }
      logoUrl = parsed.toString();
    } catch (err) {
      return res.status(400).json({ error: err.message || 'Logo URL is invalid.' });
    }
  }

  try {
    const result = overrideMerchantLogo(db, {
      merchantKey,
      logoUrl,
      hide: useCategoryIcon
    });
    if (!result.updated) {
      return res.status(404).json({ error: 'Merchant logo entry not found.' });
    }
    res.json({ success: true, ...result });
  } catch (err) {
    console.error('Override merchant logo failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
