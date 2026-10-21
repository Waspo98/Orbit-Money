// frontend/src/db/localDb.js
// Main-thread Web Worker proxy for Orbit Money's SQLite WASM engine.

let workerInstance = null;
let nextId = 1;
const pendingRequests = new Map();

function getWorker() {
  if (!workerInstance) {
    workerInstance = new Worker(
      new URL('./sqlite.worker.js', import.meta.url),
      { type: 'module' }
    );

    workerInstance.onmessage = (event) => {
      const { id, success, result, bytes, error, status } = event.data || {};
      const pending = pendingRequests.get(id);
      if (!pending) return;
      pendingRequests.delete(id);

      if (success) {
        pending.resolve(bytes !== undefined ? bytes : result);
      } else {
        const err = new Error(error || 'Worker request failed');
        if (status) err.status = status;
        pending.reject(err);
      }
    };

    workerInstance.onerror = (err) => {
      console.error('[localDb] SQLite worker error:', err);
      for (const [, pending] of pendingRequests.entries()) {
        pending.reject(new Error(err.message || 'SQLite worker crashed'));
      }
      pendingRequests.clear();
      try {
        workerInstance.terminate();
      } catch {}
      workerInstance = null;
    };
  }
  return workerInstance;
}

export function callWorker(type, payload = {}, transferList = []) {
  return new Promise((resolve, reject) => {
    try {
      const w = getWorker();
      const id = nextId++;
      pendingRequests.set(id, { resolve, reject });
      w.postMessage({ id, type, ...payload }, transferList);
    } catch (err) {
      reject(err);
    }
  });
}

export async function exportDatabaseBytes() {
  return callWorker('EXPORT_DATABASE');
}

export async function importDatabaseBytes(bytes) {
  let uint8;
  if (bytes instanceof Uint8Array) {
    if (bytes.byteOffset !== 0 || bytes.byteLength !== bytes.buffer.byteLength) {
      uint8 = new Uint8Array(bytes);
    } else {
      uint8 = bytes;
    }
  } else if (bytes instanceof ArrayBuffer) {
    uint8 = new Uint8Array(bytes);
  } else {
    uint8 = new Uint8Array(bytes || []);
  }

  // Zero-copy transfer of the ArrayBuffer to worker
  return callWorker('IMPORT_DATABASE', { bytes: uint8 }, [uint8.buffer]);
}

export async function flushDatabase() {
  return callWorker('FLUSH_DATABASE');
}

export async function resetLocalDatabase() {
  return callWorker('RESET_DATABASE');
}

export async function getLocalDatabase() {
  await callWorker('PING');
  return {
    exec: (sql, params = []) => callWorker('API_REQUEST', { url: '/api/exec', options: { method: 'POST', body: { sql, params } } }),
    export: () => exportDatabaseBytes()
  };
}

export function scheduleSave() {
  // Worker automatically debounces writes with a 500ms trailing timer
  return flushDatabase();
}

export function createStatementWrapper() {
  console.warn('[localDb] createStatementWrapper is deprecated on main thread. All queries are offloaded to sqlite.worker.js.');
  return {
    all: () => [],
    get: () => null,
    run: () => ({ changes: 0, lastInsertRowid: 0 })
  };
}

// Lifecycle listeners for immediate flush on exit or tab hide
if (typeof window !== 'undefined' && typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      void flushDatabase();
    }
  });
  window.addEventListener('pagehide', () => {
    void flushDatabase();
  });
}
