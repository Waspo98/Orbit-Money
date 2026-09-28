import {
  cacheGetResponse,
  readCachedAuthPayload,
  readCachedGetResponse,
  saveValidatedSession
} from './offlineCache.js';

import { CapacitorHttp } from '@capacitor/core';
import { isNativeApp, resolveApiUrl } from './serverConfig.js';

const NATIVE_COOKIE_KEY = 'orbit_native_session_cookie';

export function getStoredSessionCookie() {
  if (typeof window === 'undefined') return '';
  return window.localStorage.getItem(NATIVE_COOKIE_KEY) || '';
}

export function saveSessionCookieFromHeader(headerValue) {
  if (!headerValue || typeof window === 'undefined') return;
  const str = Array.isArray(headerValue) ? headerValue.join('; ') : String(headerValue);
  const match = str.match(/([a-zA-Z0-9_.-]+=[^;,\s]+)/);
  if (match) {
    window.localStorage.setItem(NATIVE_COOKIE_KEY, match[1]);
  }
}

export function clearStoredSessionCookie() {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(NATIVE_COOKIE_KEY);
}

// Thin fetch wrapper. Sends cookies (credentials: 'same-origin' on web, 'include' on native) and parses JSON.
// Throws an Error with .status and .data populated for non-2xx responses.

function isUnsafeMethod(method) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(String(method || 'GET').toUpperCase());
}

function isOffline() {
  return typeof navigator !== 'undefined' && navigator.onLine === false;
}

function offlineReadOnlyError() {
  const err = new Error('You are offline. Orbit Money is read-only until you reconnect.');
  err.status = 0;
  err.offlineReadOnly = true;
  return err;
}

function isNetworkFailure(err) {
  return err instanceof TypeError || err?.name === 'TypeError';
}

async function request(path, options = {}) {
  const method = String(options.method || 'GET').toUpperCase();
  const unsafe = isUnsafeMethod(method);

  if (unsafe && isOffline()) {
    throw offlineReadOnlyError();
  }

  const url = resolveApiUrl(path);

  let data;
  let status = 200;
  let ok = true;

  if (isNativeApp()) {
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };
    const sessionCookie = getStoredSessionCookie();
    if (sessionCookie) {
      headers['Cookie'] = sessionCookie;
    }

    let reqData = options.body;
    if (typeof reqData === 'string') {
      try {
        reqData = JSON.parse(reqData);
      } catch {
        // preserve string
      }
    }

    try {
      const res = await CapacitorHttp.request({
        url,
        method,
        headers,
        data: reqData
      });

      status = res.status;
      ok = status >= 200 && status < 300;
      data = res.data ?? {};

      const setCookie = res.headers?.['Set-Cookie'] || res.headers?.['set-cookie'];
      if (setCookie) {
        saveSessionCookieFromHeader(setCookie);
      }

      if (status === 401 && path !== '/api/auth/login') {
        clearStoredSessionCookie();
      }
      if (path === '/api/auth/logout') {
        clearStoredSessionCookie();
      }
    } catch (err) {
      if (unsafe && isNetworkFailure(err)) {
        throw offlineReadOnlyError();
      }
      if (method === 'GET' && isNetworkFailure(err)) {
        const cached = path === '/api/auth/me'
          ? await readCachedAuthPayload()
          : await readCachedGetResponse(path);
        if (cached) return cached;
      }
      throw err;
    }
  } else {
    let res;
    try {
      res = await fetch(url, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          ...(options.headers || {})
        },
        credentials: 'same-origin'
      });
    } catch (err) {
      if (unsafe && isNetworkFailure(err)) {
        throw offlineReadOnlyError();
      }
      if (method === 'GET' && isNetworkFailure(err)) {
        const cached = path === '/api/auth/me'
          ? await readCachedAuthPayload()
          : await readCachedGetResponse(path);
        if (cached) return cached;
      }
      throw err;
    }

    status = res.status;
    ok = res.ok;
    data = await res.json().catch(() => ({}));
  }

  if (!ok) {
    const err = new Error(data?.error || `Request failed: ${status}`);
    err.status = status;
    err.data = data;
    throw err;
  }

  if (method === 'GET') {
    if (path === '/api/auth/me') {
      await saveValidatedSession(data);
    } else {
      await cacheGetResponse(path, data);
    }
  }

  return data;
}

export const api = {
  get: (path) => request(path),
  post: (path, body = {}) =>
    request(path, { method: 'POST', body: JSON.stringify(body) }),
  put: (path, body = {}) =>
    request(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: (path, body = {}) =>
    request(path, { method: 'PATCH', body: JSON.stringify(body) }),
  del: (path) => request(path, { method: 'DELETE' })
};
