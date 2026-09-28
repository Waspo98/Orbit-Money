import initSqlJs from 'sql.js';
import sqlWasmUrl from 'sql.js/dist/sql-wasm.wasm?url';
import migrations from 'virtual:sqlite-migrations';

const IDB_NAME = 'orbit-local-sqlite-v1';
const IDB_STORE = 'sqlite_storage';
const IDB_KEY = 'budget_db_bytes';

let sqlEnginePromise = null;
let activeDb = null;
let saveTimeout = null;
let isDirty = false;

function openIdb() {
  if (typeof indexedDB === 'undefined') return Promise.resolve(null);
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(IDB_NAME, 1);
    request.onupgradeneeded = () => {
      const idb = request.result;
      if (!idb.objectStoreNames.contains(IDB_STORE)) {
        idb.createObjectStore(IDB_STORE);
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function loadPersistedBytes() {
  const idb = await openIdb();
  if (!idb) return null;
  return new Promise((resolve) => {
    try {
      const tx = idb.transaction(IDB_STORE, 'readonly');
      const store = tx.objectStore(IDB_STORE);
      const req = store.get(IDB_KEY);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
}

async function saveBytesToIdb(bytes) {
  const idb = await openIdb();
  if (!idb) return;
  return new Promise((resolve, reject) => {
    try {
      const tx = idb.transaction(IDB_STORE, 'readwrite');
      const store = tx.objectStore(IDB_STORE);
      store.put(bytes, IDB_KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    } catch (err) {
      reject(err);
    }
  });
}

export function scheduleSave() {
  isDirty = true;
  if (saveTimeout) clearTimeout(saveTimeout);
  saveTimeout = setTimeout(async () => {
    await flushDatabase();
  }, 350);
}

export async function flushDatabase() {
  if (saveTimeout) {
    clearTimeout(saveTimeout);
    saveTimeout = null;
  }
  if (!activeDb || !isDirty) return;
  try {
    const bytes = activeDb.export();
    await saveBytesToIdb(bytes);
    isDirty = false;
  } catch (err) {
    console.error('Failed to flush local SQLite database:', err);
  }
}

function runDatabaseMigrations(db) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS _migrations (
      name TEXT PRIMARY KEY,
      applied_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  const stmt = db.prepare('SELECT name FROM _migrations');
  const applied = new Set();
  while (stmt.step()) {
    const row = stmt.getAsObject();
    applied.add(row.name);
  }
  stmt.free();

  let count = 0;
  for (const item of migrations) {
    if (applied.has(item.name)) continue;
    try {
      db.exec('BEGIN TRANSACTION;');
      db.exec(item.sql);
      const ins = db.prepare('INSERT INTO _migrations (name) VALUES (?)');
      ins.run([item.name]);
      ins.free();
      db.exec('COMMIT;');
      count++;
    } catch (err) {
      try {
        db.exec('ROLLBACK;');
      } catch {}
      console.error(`Migration ${item.name} failed:`, err);
      throw err;
    }
  }

  // Ensure household 1 and user 1 exist with default settings
  db.exec(`
    INSERT OR IGNORE INTO households (id, name, default_currency)
    VALUES (1, 'My Household', 'USD');

    INSERT OR IGNORE INTO users (id, username, display_name, is_local_admin)
    VALUES (1, 'local_user', 'Local User', 1);

    INSERT OR IGNORE INTO household_memberships (household_id, user_id, role, access_level)
    VALUES (1, 1, 'owner', 'write');
  `);

  return count;
}

export async function getLocalDatabase() {
  if (activeDb) return activeDb;
  if (sqlEnginePromise) return sqlEnginePromise;

  sqlEnginePromise = (async () => {
    const SQL = await initSqlJs({
      locateFile: () => sqlWasmUrl
    });

    const persisted = await loadPersistedBytes();
    let db;
    if (persisted && persisted.length > 0) {
      try {
        db = new SQL.Database(persisted);
      } catch (err) {
        console.warn('Failed to load persisted SQLite bytes, initializing fresh db:', err);
        db = new SQL.Database();
      }
    } else {
      db = new SQL.Database();
    }

    // Set performance / integrity pragmas
    try {
      db.exec('PRAGMA foreign_keys = ON;');
    } catch {}

    const appliedCount = runDatabaseMigrations(db);
    if (appliedCount > 0 || !persisted) {
      const bytes = db.export();
      await saveBytesToIdb(bytes);
    }

    activeDb = db;
    return activeDb;
  })();

  return sqlEnginePromise;
}

export function createStatementWrapper(db, sql) {
  return {
    all(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      if (flatParams && flatParams.length > 0) {
        stmt.bind(flatParams);
      }
      const rows = [];
      while (stmt.step()) {
        rows.push(stmt.getAsObject());
      }
      stmt.free();
      return rows;
    },
    get(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      if (flatParams && flatParams.length > 0) {
        stmt.bind(flatParams);
      }
      let row = null;
      if (stmt.step()) {
        row = stmt.getAsObject();
      }
      stmt.free();
      return row;
    },
    run(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      if (flatParams && flatParams.length > 0) {
        stmt.bind(flatParams);
      }
      stmt.step();
      stmt.free();

      const lastIdRes = db.exec('SELECT last_insert_rowid() AS id;');
      const lastInsertRowid = lastIdRes[0]?.values[0]?.[0] ?? 0;
      const changes = db.getRowsModified();

      scheduleSave();
      return { changes, lastInsertRowid };
    }
  };
}

export async function exportDatabaseBytes() {
  const db = await getLocalDatabase();
  await flushDatabase();
  return db.export();
}

export async function importDatabaseBytes(bytes) {
  const SQL = await initSqlJs({
    locateFile: () => sqlWasmUrl
  });

  const db = new SQL.Database(bytes);
  runDatabaseMigrations(db);
  const exported = db.export();
  await saveBytesToIdb(exported);
  activeDb = db;
  return activeDb;
}

export async function resetLocalDatabase() {
  if (saveTimeout) {
    clearTimeout(saveTimeout);
    saveTimeout = null;
  }
  const idb = await openIdb();
  if (idb) {
    const tx = idb.transaction(IDB_STORE, 'readwrite');
    tx.objectStore(IDB_STORE).delete(IDB_KEY);
  }
  activeDb = null;
  sqlEnginePromise = null;
  return getLocalDatabase();
}
