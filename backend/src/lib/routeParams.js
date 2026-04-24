import { sendBadRequest } from './http.js';

export function parseInteger(value) {
  const parsed = parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : null;
}

export function parseId(value) {
  return parseInteger(value);
}

export function parseBoundedInteger(value, { fallback, min = -Infinity, max = Infinity }) {
  const parsed = parseInteger(value);
  const next = parsed === null ? fallback : parsed;
  return Math.min(max, Math.max(min, next));
}

export function parseBooleanField(body, key) {
  const value = body?.[key];
  return typeof value === 'boolean' ? value : null;
}

export function readIdParam(req, res, key, label) {
  const id = parseId(req.params[key]);
  if (id === null) {
    sendBadRequest(res, `Invalid ${label} id.`);
    return null;
  }
  return id;
}
