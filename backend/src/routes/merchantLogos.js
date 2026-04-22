import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import {
  ensureMerchantLogoEntryForTransaction,
  overrideMerchantLogo,
  reportMerchantLogoStatus,
  searchMerchantLogoBrands
} from '../services/merchantLogos.js';

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

router.post('/ensure', requireAuth, (req, res) => {
  const transactionId = Number(req.body?.transaction_id);
  if (!Number.isInteger(transactionId) || transactionId <= 0) {
    return res.status(400).json({ error: 'transaction_id is required.' });
  }

  try {
    const result = ensureMerchantLogoEntryForTransaction(db, transactionId);
    if (!result.found) {
      return res.status(404).json({ error: 'Transaction not found.' });
    }
    if (!result.merchant_logo) {
      return res.status(400).json({ error: 'This transaction does not have a merchant name to override.' });
    }
    res.json({ success: true, merchant_logo: result.merchant_logo });
  } catch (err) {
    console.error('Ensure merchant logo failed:', err);
    res.status(500).json({ error: err.message });
  }
});

router.post('/search', requireAuth, async (req, res) => {
  const transactionId = Number(req.body?.transaction_id);
  const query = String(req.body?.query || '').trim();
  if (!Number.isInteger(transactionId) || transactionId <= 0) {
    return res.status(400).json({ error: 'transaction_id is required.' });
  }
  if (!query) {
    return res.status(400).json({ error: 'query is required.' });
  }

  try {
    const result = await searchMerchantLogoBrands(db, { transactionId, query });
    if (!result.found) {
      return res.status(404).json({ error: 'Transaction not found.' });
    }
    res.json({
      success: true,
      configured: result.configured,
      merchant_logo: result.merchant_logo,
      candidates: result.candidates
    });
  } catch (err) {
    console.error('Search merchant logos failed:', err);
    res.status(500).json({ error: err.message });
  }
});

export default router;
