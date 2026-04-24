import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import {
  ensureMerchantLogoEntryForTransaction,
  overrideMerchantLogo,
  reportMerchantLogoStatus,
  searchMerchantLogoBrands
} from '../services/merchantLogos.js';
import {
  sendBadRequest,
  sendNotFound,
  sendOk,
  sendServerError
} from '../lib/http.js';
import { parseId } from '../lib/routeParams.js';

const router = express.Router();

router.post('/report', requireAuth, (req, res) => {
  const merchantKey = String(req.body?.merchant_key || '').trim();
  const status = String(req.body?.status || '').trim();

  if (!merchantKey || !['loaded', 'failed'].includes(status)) {
    return sendBadRequest(res, 'merchant_key and valid status are required.');
  }

  try {
    const result = reportMerchantLogoStatus(db, { merchantKey, status });
    sendOk(res, { success: true, ...result });
  } catch (err) {
    console.error('Report merchant logo status failed:', err);
    sendServerError(res, err);
  }
});

router.post('/override', requireAuth, (req, res) => {
  const merchantKey = String(req.body?.merchant_key || '').trim();
  const useCategoryIcon = req.body?.use_category_icon === true;
  const rawLogoUrl = String(req.body?.logo_url || '').trim();

  if (!merchantKey) {
    return sendBadRequest(res, 'merchant_key is required.');
  }
  if (!useCategoryIcon && !rawLogoUrl) {
    return sendBadRequest(res, 'logo_url is required unless using the category icon.');
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
      return sendBadRequest(res, err.message || 'Logo URL is invalid.');
    }
  }

  try {
    const result = overrideMerchantLogo(db, {
      merchantKey,
      logoUrl,
      hide: useCategoryIcon
    });
    if (!result.updated) {
      return sendNotFound(res, 'Merchant logo entry not found.');
    }
    sendOk(res, { success: true, ...result });
  } catch (err) {
    console.error('Override merchant logo failed:', err);
    sendServerError(res, err);
  }
});

router.post('/ensure', requireAuth, (req, res) => {
  const transactionId = parseId(req.body?.transaction_id);
  if (transactionId === null || transactionId <= 0) {
    return sendBadRequest(res, 'transaction_id is required.');
  }

  try {
    const result = ensureMerchantLogoEntryForTransaction(db, transactionId);
    if (!result.found) {
      return sendNotFound(res, 'Transaction not found.');
    }
    if (!result.merchant_logo) {
      return sendBadRequest(res, 'This transaction does not have a merchant name to override.');
    }
    sendOk(res, { success: true, merchant_logo: result.merchant_logo });
  } catch (err) {
    console.error('Ensure merchant logo failed:', err);
    sendServerError(res, err);
  }
});

router.post('/search', requireAuth, async (req, res) => {
  const transactionId = parseId(req.body?.transaction_id);
  const query = String(req.body?.query || '').trim();
  if (transactionId === null || transactionId <= 0) {
    return sendBadRequest(res, 'transaction_id is required.');
  }
  if (!query) {
    return sendBadRequest(res, 'query is required.');
  }

  try {
    const result = await searchMerchantLogoBrands(db, { transactionId, query });
    if (!result.found) {
      return sendNotFound(res, 'Transaction not found.');
    }
    sendOk(res, {
      success: true,
      configured: result.configured,
      merchant_logo: result.merchant_logo,
      candidates: result.candidates
    });
  } catch (err) {
    console.error('Search merchant logos failed:', err);
    sendServerError(res, err);
  }
});

export default router;
