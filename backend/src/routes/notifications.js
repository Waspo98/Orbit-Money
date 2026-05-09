import express from 'express';
import { requireHouseholdId } from '../auth.js';
import {
  getNotificationRuntimeConfig,
  sendNotificationToUser,
  subscriptionSummary,
  upsertPushSubscription
} from '../services/notifications.js';
import { sendBadRequest, sendOk, sendRouteError } from '../lib/http.js';

const router = express.Router();

function requireUserId(req) {
  const id = Number(req.user?.id);
  if (!Number.isInteger(id) || id <= 0) {
    throw new Error('Notifications require a signed-in user account.');
  }
  return id;
}

function readSubscription(body) {
  const subscription = body?.subscription || body || {};
  const endpoint = String(subscription.endpoint || '').trim();
  const keys = subscription.keys || {};
  const p256dh = String(keys.p256dh || '').trim();
  const auth = String(keys.auth || '').trim();
  if (!endpoint || !p256dh || !auth) {
    throw new Error('Push subscription is incomplete.');
  }
  return {
    endpoint,
    p256dh,
    auth,
    expirationTime: Number.isFinite(Number(subscription.expirationTime))
      ? Number(subscription.expirationTime)
      : null
  };
}

router.get('/config', (req, res) => {
  try {
    const householdId = requireHouseholdId(req);
    const userId = requireUserId(req);
    sendOk(res, {
      ...getNotificationRuntimeConfig(),
      subscriptions: subscriptionSummary(householdId, userId)
    });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.post('/subscriptions', (req, res) => {
  try {
    const householdId = requireHouseholdId(req);
    const userId = requireUserId(req);
    const subscription = readSubscription(req.body);
    upsertPushSubscription({
      householdId,
      userId,
      ...subscription,
      userAgent: req.get('user-agent') || null
    });
    sendOk(res, {
      success: true,
      subscriptions: subscriptionSummary(householdId, userId)
    });
  } catch (err) {
    sendBadRequest(res, err.message || 'Could not save push subscription.');
  }
});

router.post('/test', async (req, res) => {
  try {
    const householdId = requireHouseholdId(req);
    const userId = requireUserId(req);
    const result = await sendNotificationToUser({
      householdId,
      userId,
      prefs: { enabled: true },
      notification: {
        type: 'test',
        dedupeKey: `test:${Date.now()}`,
        title: 'Orbit Notifications',
        body: 'Notifications are ready on this device.',
        url: '/settings/preferences',
        tag: 'test'
      }
    });
    sendOk(res, { success: Number(result.sent || 0) > 0, ...result });
  } catch (err) {
    sendRouteError(res, err);
  }
});

export default router;
