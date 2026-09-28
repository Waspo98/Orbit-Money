import { Capacitor } from '@capacitor/core';

const SERVER_URL_KEY = 'orbit_server_url';

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
  if (!isNativeApp()) {
    return '';
  }
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

export function resolveApiUrl(path) {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path;
  }
  const base = getServerUrl();
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return base ? `${base}${normalizedPath}` : normalizedPath;
}
