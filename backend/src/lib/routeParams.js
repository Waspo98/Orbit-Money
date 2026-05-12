import { sendBadRequest } from './http.js';

const INTEGER_RE = /^-?\d+$/;

export function parseInteger(value) {
  if (typeof value === 'number') {
    return Number.isInteger(value) ? value : null;
  }
  if (typeof value !== 'string') return null;
  const text = value.trim();
  if (!INTEGER_RE.test(text)) return null;
  const parsed = Number(text);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

export function parseId(value) {
  const parsed = parseInteger(value);
  return parsed !== null && parsed > 0 ? parsed : null;
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
