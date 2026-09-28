import { Capacitor } from '@capacitor/core';

export const STORAGE_MODE_LOCAL = 'local';
export const STORAGE_MODE_SELF_HOSTED = 'self-hosted';

const SERVER_URL_KEY = 'orbit_server_url';
const STORAGE_MODE_KEY = 'orbit_storage_mode';
const ONBOARDING_COMPLETED_KEY = 'orbit_onboarding_completed';

export function isNativeApp() {
  return Capacitor.isNativePlatform();
}

export function normalizeServerUrl(rawUrl) {
  let url = String(rawUrl || '').trim().replace(/\/+$/, '');
  if (!url) return '';
  if (!/^https?:\/\//i.test(url)) {
    if (/^(localhost|127\.0\.0\.1|10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)/i.test(url)) {
      url = `http://${url}`;
    } else {
      url = `https://${url}`;
    }
  }
  return url;
}

export function getServerUrl() {
  return localStorage.getItem(SERVER_URL_KEY) || '';
}

export function setServerUrl(url) {
  const normalized = normalizeServerUrl(url);
  if (!normalized) {
    localStorage.removeItem(SERVER_URL_KEY);
  } else {
    localStorage.setItem(SERVER_URL_KEY, normalized);
  }
}

export function getStorageMode() {
  const stored = localStorage.getItem(STORAGE_MODE_KEY);
  if (stored === STORAGE_MODE_LOCAL || stored === STORAGE_MODE_SELF_HOSTED) {
    return stored;
  }
  return STORAGE_MODE_LOCAL;
}

export function setStorageMode(mode) {
  if (mode === STORAGE_MODE_SELF_HOSTED) {
    localStorage.setItem(STORAGE_MODE_KEY, STORAGE_MODE_SELF_HOSTED);
  } else {
    localStorage.setItem(STORAGE_MODE_KEY, STORAGE_MODE_LOCAL);
  }
}

export function hasCompletedOnboarding() {
  return localStorage.getItem(ONBOARDING_COMPLETED_KEY) === 'true';
}

export function setCompletedOnboarding(completed = true) {
  if (completed) {
    localStorage.setItem(ONBOARDING_COMPLETED_KEY, 'true');
  } else {
    localStorage.removeItem(ONBOARDING_COMPLETED_KEY);
  }
}

export function resolveApiUrl(path) {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path;
  }
  const base = getServerUrl();
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return base ? `${base}${normalizedPath}` : normalizedPath;
}
