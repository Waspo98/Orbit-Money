const DB_NAME = 'orbit-money-offline';
const DB_VERSION = 1;
const RESPONSES_STORE = 'responses';
const META_STORE = 'meta';
const SESSION_KEY = 'current-session';
const SHARED_ACCESS_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

let dbPromise = null;
let activeSession = null;

function canUseIndexedDb() {
  return typeof indexedDB !== 'undefined';
}

function openDb() {
  if (!canUseIndexedDb()) return Promise.resolve(null);
  if (dbPromise) return dbPromise;

  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(RESPONSES_STORE)) {
        db.createObjectStore(RESPONSES_STORE, { keyPath: 'key' });
      }
      if (!db.objectStoreNames.contains(META_STORE)) {
        db.createObjectStore(META_STORE, { keyPath: 'key' });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  }).catch((err) => {
    console.warn('Offline cache unavailable:', err);
    dbPromise = null;
    return null;
  });

  return dbPromise;
}

async function withStore(storeName, mode, callback) {
  const db = await openDb();
  if (!db) return undefined;

  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, mode);
    const store = tx.objectStore(storeName);
    let request;

    try {
      request = callback(store);
    } catch (err) {
      reject(err);
      return;
    }

    if (!request) {
      tx.oncomplete = () => resolve(undefined);
      tx.onerror = () => reject(tx.error);
      return;
    }

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  }).catch((err) => {
    console.warn('Offline cache operation failed:', err);
    return undefined;
  });
}

function userKeyFromAuth(auth) {
  return String(auth?.email || auth?.username || auth?.displayName || 'unknown');
}

function buildSession(auth, validatedAt = Date.now()) {
  if (!auth?.authenticated) return null;
  const householdId = auth.household?.id ?? 1;
  const role = auth.household?.role || 'owner';
  return {
    key: SESSION_KEY,
    authenticated: true,
    userKey: userKeyFromAuth(auth),
    householdId,
    householdRole: role,
    householdAccessLevel: auth.household?.accessLevel || 'write',
    validatedAt,
    scope: `user:${userKeyFromAuth(auth)}|household:${householdId}`,
    auth
  };
}

function isSharedMemberSession(session) {
  return !!session && session.householdRole !== 'owner';
}

function isSessionUsableOffline(session, now = Date.now()) {
  if (!session?.authenticated) return false;
  if (!isSharedMemberSession(session)) return true;
  return now - Number(session.validatedAt || 0) <= SHARED_ACCESS_MAX_AGE_MS;
}

function offlineAccessExpiredError() {
  const err = new Error('Reconnect to verify household access before viewing cached shared data.');
  err.offlineAccessExpired = true;
  err.status = 0;
  return err;
}

export function currentOfflineSession() {
  return activeSession;
}

export async function saveValidatedSession(auth, validatedAt = Date.now()) {
  const session = buildSession(auth, validatedAt);
  if (!session) {
    await clearOfflineFinancialCache();
    return null;
  }

  activeSession = session;
  await withStore(META_STORE, 'readwrite', (store) => store.put(session));
  return session;
}

export async function readCachedSession({ enforceSharedExpiry = true } = {}) {
  if (activeSession) {
    if (!enforceSharedExpiry || isSessionUsableOffline(activeSession)) {
      return activeSession;
    }
    throw offlineAccessExpiredError();
  }

  const session = await withStore(META_STORE, 'readonly', (store) => store.get(SESSION_KEY));
  if (!session) return null;
  if (enforceSharedExpiry && !isSessionUsableOffline(session)) {
    throw offlineAccessExpiredError();
  }
  activeSession = session;
  return session;
}

export async function readCachedAuthPayload() {
  const session = await readCachedSession();
  if (!session?.auth) return null;
  return {
    ...session.auth,
    offline: true,
    cachedAt: session.validatedAt
  };
}

function responseCacheKey(session, path) {
  return `${session.scope}::${path}`;
}

export async function cacheGetResponse(path, data) {
  const session = activeSession;
  if (!session?.scope || path === '/api/auth/me') return;
  await withStore(RESPONSES_STORE, 'readwrite', (store) =>
    store.put({
      key: responseCacheKey(session, path),
      scope: session.scope,
      path,
      savedAt: Date.now(),
      accessValidatedAt: session.validatedAt,
      data
    })
  );
}

export async function readCachedGetResponse(path) {
  const session = await readCachedSession();
  if (!session?.scope) return null;
  const cached = await withStore(RESPONSES_STORE, 'readonly', (store) =>
    store.get(responseCacheKey(session, path))
  );
  return cached?.data ?? null;
}

export async function clearOfflineFinancialCache() {
  activeSession = null;
  const db = await openDb();
  if (!db) return;

  await Promise.all([
    withStore(RESPONSES_STORE, 'readwrite', (store) => store.clear()),
    withStore(META_STORE, 'readwrite', (store) => store.delete(SESSION_KEY))
  ]);
}
