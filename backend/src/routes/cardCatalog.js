import express from 'express';
import { requireAuth } from '../auth.js';
import { db } from '../db/index.js';
import { sendBadRequest, sendNotFound, sendOk, sendServerError } from '../lib/http.js';
import {
  fetchAndCacheCardArtwork,
  listCatalogTemplates,
  syncCreditCardCatalogFromUpstream
} from '../services/creditCardCatalogSync.js';

const router = express.Router();
const SAFE_ID_RE = /^[a-z0-9_-]{1,80}$/i;

router.get('/', requireAuth, (req, res) => {
  try {
    const items = listCatalogTemplates(db);
    sendOk(res, { items });
  } catch (err) {
    console.error('Get credit card catalog failed:', err);
    sendServerError(res, err);
  }
});

router.get('/:id/artwork', requireAuth, async (req, res) => {
  const cardId = String(req.params.id || '').trim();
  if (!SAFE_ID_RE.test(cardId)) {
    return sendBadRequest(res, 'Invalid card catalog id.');
  }

  try {
    const artwork = await fetchAndCacheCardArtwork(db, cardId);
    if (!artwork || !artwork.buffer) {
      return sendNotFound(res, 'Card artwork not available.');
    }
    res.setHeader('Cache-Control', 'public, max-age=604800, immutable');
    res.type(artwork.contentType || 'image/png');
    res.send(artwork.buffer);
  } catch (err) {
    console.error('Fetch card catalog artwork failed:', err);
    sendServerError(res, err);
  }
});

router.post('/sync', requireAuth, async (req, res) => {
  try {
    const summary = await syncCreditCardCatalogFromUpstream(db);
    const items = listCatalogTemplates(db);
    sendOk(res, { success: true, summary, items });
  } catch (err) {
    console.error('Sync credit card catalog failed:', err);
    sendServerError(res, err);
  }
});

export default router;
