import express from 'express';
import { requireHouseholdId } from '../auth.js';
import { db } from '../db/index.js';
import { sendBadRequest, sendOk, sendRouteError } from '../lib/http.js';

const router = express.Router();

const KEY_RE = /^[a-z][a-zA-Z0-9_.-]{0,80}$/;
const MAX_VALUE_BYTES = 100 * 1024;

function requireUserId(req) {
  const id = Number(req.user?.id);
  if (!Number.isInteger(id) || id <= 0) {
    throw new Error('Preferences require a signed-in user account.');
  }
  return id;
}

function assertPreferenceKey(key) {
  const normalized = String(key || '').trim();
  if (!KEY_RE.test(normalized)) {
    throw new Error('Invalid preference key.');
  }
  return normalized;
}

function serializePreferenceValue(value) {
  const valueJson = JSON.stringify(value ?? null);
  if (Buffer.byteLength(valueJson, 'utf8') > MAX_VALUE_BYTES) {
    throw new Error('Preference value is too large.');
  }
  return valueJson;
}

function parsePreferenceRow(row) {
  try {
    return JSON.parse(row.value_json);
  } catch {
    return null;
  }
}

function readPreferences(householdId, userId) {
  const rows = db
    .prepare(
      `SELECT key, value_json
         FROM user_preferences
        WHERE household_id = ?
          AND user_id = ?
        ORDER BY key ASC`
    )
    .all(householdId, userId);

  return Object.fromEntries(rows.map((row) => [row.key, parsePreferenceRow(row)]));
}

function upsertPreferenceStatement() {
  return db.prepare(
    `INSERT INTO user_preferences (household_id, user_id, key, value_json)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(household_id, user_id, key) DO UPDATE SET
       value_json = excluded.value_json,
       updated_at = datetime('now')`
  );
}

router.get('/', (req, res) => {
  try {
    const householdId = requireHouseholdId(req);
    const userId = requireUserId(req);
    sendOk(res, { preferences: readPreferences(householdId, userId) });
  } catch (err) {
    sendRouteError(res, err);
  }
});

router.put('/', (req, res) => {
  try {
    const householdId = requireHouseholdId(req);
    const userId = requireUserId(req);
    const preferences = req.body?.preferences;
    if (!preferences || typeof preferences !== 'object' || Array.isArray(preferences)) {
      return sendBadRequest(res, 'Preferences payload is required.');
    }

    const entries = Object.entries(preferences).map(([key, value]) => [
      assertPreferenceKey(key),
      serializePreferenceValue(value)
    ]);

    const run = db.transaction(() => {
      const upsertPreference = upsertPreferenceStatement();
      for (const [key, valueJson] of entries) {
        upsertPreference.run(householdId, userId, key, valueJson);
      }
    });
    run();

    return sendOk(res, { preferences: readPreferences(householdId, userId) });
  } catch (err) {
    return sendBadRequest(res, err.message || 'Could not save preferences.');
  }
});

router.put('/:key', (req, res) => {
  try {
    const householdId = requireHouseholdId(req);
    const userId = requireUserId(req);
    const key = assertPreferenceKey(req.params.key);
    const valueJson = serializePreferenceValue(req.body?.value);

    upsertPreferenceStatement().run(householdId, userId, key, valueJson);
    return sendOk(res, { key, value: parsePreferenceRow({ value_json: valueJson }) });
  } catch (err) {
    return sendBadRequest(res, err.message || 'Could not save preference.');
  }
});

export default router;
