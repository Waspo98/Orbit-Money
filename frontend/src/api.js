import {
  cacheGetResponse,
  readCachedAuthPayload,
  readCachedGetResponse,
  saveValidatedSession
} from './offlineCache.js';

// Thin fetch wrapper. Sends cookies (credentials: 'same-origin') and parses JSON.
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

  let res;
  try {
    res = await fetch(path, {
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

  // Tolerate empty bodies (e.g. 204 responses).
  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    const err = new Error(data.error || `Request failed: ${res.status}`);
    err.status = res.status;
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
