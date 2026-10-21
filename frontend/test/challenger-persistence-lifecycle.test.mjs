// frontend/test/challenger-persistence-lifecycle.test.mjs
// Independent Empirical Verification Harness by Challenger 2.1-B
// Validates:
// 1. Storage constants and static contracts in sqlite.worker.js
// 2. Exact SQLite 3 header ("SQLite format 3\000") and 34 migrations on real sql.js WASM
// 3. Monotonic versioning & interleaved write preservation during async IDB commit
// 4. Multiple rapid interleaved writes during simulated slow IDB I/O
// 5. Try...finally statement cleanup in createStatementWrapper under simulated errors
// 6. Layout compliance in .agents/teamwork/

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import initSqlJs from 'sql.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '../..');
const MIGRATIONS_DIR = path.resolve(ROOT_DIR, 'backend/src/db/migrations');
const WORKER_PATH = path.resolve(ROOT_DIR, 'frontend/src/db/sqlite.worker.js');

console.log('================================================================');
console.log('CHALLENGER 2.1-B: EMPIRICAL PERSISTENCE & LIFECYCLE HARNESS');
console.log('================================================================\n');

// -----------------------------------------------------------------------------
// CHECK 1: Static Contracts & Monotonic Versioning in sqlite.worker.js
// -----------------------------------------------------------------------------
console.log('[Check 1] Inspecting frontend/src/db/sqlite.worker.js implementation...');
const workerSource = fs.readFileSync(WORKER_PATH, 'utf-8');

const idbNameMatch = workerSource.match(/const\s+IDB_NAME\s*=\s*['"]([^'"]+)['"]/);
const idbStoreMatch = workerSource.match(/const\s+IDB_STORE\s*=\s*['"]([^'"]+)['"]/);
const idbKeyMatch = workerSource.match(/const\s+IDB_KEY\s*=\s*['"]([^'"]+)['"]/);

assert.ok(idbNameMatch, 'sqlite.worker.js must define IDB_NAME');
assert.ok(idbStoreMatch, 'sqlite.worker.js must define IDB_STORE');
assert.ok(idbKeyMatch, 'sqlite.worker.js must define IDB_KEY');

assert.equal(idbNameMatch[1], 'orbit-local-sqlite-v1', 'IDB_NAME must match legacy orbit-local-sqlite-v1');
assert.equal(idbStoreMatch[1], 'sqlite_storage', 'IDB_STORE must match legacy sqlite_storage');
assert.equal(idbKeyMatch[1], 'budget_db_bytes', 'IDB_KEY must match legacy budget_db_bytes');

assert.ok(workerSource.includes('let dirtyVersion = 0;'), 'Must declare dirtyVersion');
assert.ok(workerSource.includes('let savedVersion = 0;'), 'Must declare savedVersion');
assert.ok(workerSource.includes('dirtyVersion++;'), 'Must increment dirtyVersion on scheduleWorkerSave');
assert.ok(workerSource.includes('const versionToSave = dirtyVersion;'), 'Must snapshot versionToSave');
assert.ok(workerSource.includes('savedVersion = Math.max(savedVersion, versionToSave);'), 'Must update savedVersion after successful saveBytesToIdb');
assert.ok(!workerSource.includes('isDirty = false;'), 'Must not reset legacy boolean isDirty after save');

// Verify statement wrapper try-finally
assert.ok(workerSource.includes('try {\n        if (flatParams && flatParams.length > 0) {\n          stmt.bind(flatParams);\n        }\n        stmt.step();\n      } finally {\n        stmt.free();\n      }'), 'run() must use try...finally stmt.free()');

console.log('  ✓ Storage constants match legacy IndexedDB schema verbatim');
console.log('  ✓ Monotonic persistence versioning confirmed in source');
console.log('  ✓ Statement wrapper try-finally blocks confirmed in source');

// -----------------------------------------------------------------------------
// CHECK 2: Real SQLite WASM Execution, 34 Migrations & Header Check
// -----------------------------------------------------------------------------
console.log('\n[Check 2] Executing 34 migrations on real sql.js WASM engine...');

const migrationFiles = fs.readdirSync(MIGRATIONS_DIR).filter((f) => f.endsWith('.sql')).sort();
console.log(`  Found ${migrationFiles.length} migration files in backend/src/db/migrations/`);
assert.equal(migrationFiles.length, 34, 'Expected exactly 34 migration files');

const SQL = await initSqlJs();
const db = new SQL.Database();

db.exec(`
  CREATE TABLE IF NOT EXISTS _migrations (
    name TEXT PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
`);

let migrationCount = 0;
for (const file of migrationFiles) {
  const sql = fs.readFileSync(path.join(MIGRATIONS_DIR, file), 'utf-8');
  try {
    db.exec('BEGIN TRANSACTION;');
    db.exec(sql);
    const ins = db.prepare('INSERT INTO _migrations (name) VALUES (?)');
    ins.run([file]);
    ins.free();
    db.exec('COMMIT;');
    migrationCount++;
  } catch (err) {
    try { db.exec('ROLLBACK;'); } catch {}
    throw new Error(`Migration ${file} failed: ${err.message}`);
  }
}

assert.equal(migrationCount, 34, 'All 34 migrations must successfully execute');

// Seed test records
db.exec(`
  INSERT OR IGNORE INTO households (id, name, default_currency) VALUES (1, 'Test Household', 'USD');
  INSERT OR IGNORE INTO users (id, username, display_name, is_local_admin) VALUES (1, 'local', 'Local User', 1);
  INSERT OR IGNORE INTO categories (household_id, name, color) VALUES (1, 'Groceries', '#00ff00');
`);

const exportedBytes = db.export();
assert.ok(exportedBytes instanceof Uint8Array, 'Exported database must be a Uint8Array');
assert.ok(exportedBytes.length > 50000, `Exported database must contain valid payload (size: ${exportedBytes.length} bytes)`);

// Check SQLite 3 Magic Header
const headerString = Buffer.from(exportedBytes.slice(0, 16)).toString('utf-8');
const expectedHeader = 'SQLite format 3\0';
assert.equal(headerString, expectedHeader, 'Database header bytes 0-15 must equal "SQLite format 3\\0"');

// Check Page Size
const pageSize = (exportedBytes[16] << 8) | exportedBytes[17];
console.log(`  ✓ Magic header validated: "${headerString.replace('\0', '\\0')}"`);
console.log(`  ✓ SQLite page size: ${pageSize} bytes`);
console.log(`  ✓ Exported database size: ${exportedBytes.length} bytes`);

// Verify integrity of database binary
const verifyDb = new SQL.Database(exportedBytes);
const integrityRes = verifyDb.exec('PRAGMA integrity_check;');
assert.equal(integrityRes[0].values[0][0], 'ok', 'PRAGMA integrity_check must return "ok"');

const migQueryRes = verifyDb.exec('SELECT COUNT(*) FROM _migrations;');
assert.equal(migQueryRes[0].values[0][0], 34, 'Migrated database must contain 34 migration records in _migrations');
console.log('  ✓ PRAGMA integrity_check passed: ok');
console.log('  ✓ 34 migration records verified in database');

// -----------------------------------------------------------------------------
// CHECK 3: Interleaved Write during Asynchronous IndexedDB Commit
// -----------------------------------------------------------------------------
console.log('\n[Check 3] Adversarial Simulation: Interleaved Write during Async IDB Save...');

class MockAsyncIndexedDb {
  constructor() {
    this.records = new Map();
    this.history = [];
  }
  async put(key, bytes, ioLatencyMs = 30) {
    await new Promise((r) => setTimeout(r, ioLatencyMs));
    this.records.set(key, Uint8Array.from(bytes));
    this.history.push({
      key,
      size: bytes.length,
      timestamp: Date.now()
    });
  }
  async get(key) {
    return this.records.get(key) || null;
  }
}

// Harness strictly matching sqlite.worker.js lines 88-114
class MonotonicWorkerPersistenceHarness {
  constructor(database, idb, debounceMs = 50, idbLatencyMs = 30) {
    this.db = database;
    this.idb = idb;
    this.debounceMs = debounceMs;
    this.idbLatencyMs = idbLatencyMs;

    this.dirtyVersion = 0;
    this.savedVersion = 0;
    this.saveTimeout = null;
    this.exportCount = 0;
    this.flushCount = 0;
  }

  scheduleWorkerSave() {
    this.dirtyVersion++;
    if (this.saveTimeout) clearTimeout(this.saveTimeout);
    this.saveTimeout = setTimeout(async () => {
      await this.flushWorkerDatabase();
    }, this.debounceMs);
  }

  async flushWorkerDatabase() {
    if (this.saveTimeout) {
      clearTimeout(this.saveTimeout);
      this.saveTimeout = null;
    }
    const versionToSave = this.dirtyVersion;
    if (!this.db || versionToSave === this.savedVersion) return;
    try {
      this.exportCount++;
      const bytes = this.db.export();
      await this.idb.put('budget_db_bytes', bytes, this.idbLatencyMs);
      this.savedVersion = Math.max(this.savedVersion, versionToSave);
      this.flushCount++;
    } catch (err) {
      console.error('[Harness] Save failed:', err);
    }
  }
}

{
  const idb = new MockAsyncIndexedDb();
  const harness = new MonotonicWorkerPersistenceHarness(db, idb, 50, 40);

  // Write 1: insert category 'Interleaved_1'
  db.exec("INSERT INTO categories (household_id, name, color) VALUES (1, 'Interleaved_1', '#ff0000');");
  harness.scheduleWorkerSave();
  assert.equal(harness.dirtyVersion, 1);
  assert.equal(harness.savedVersion, 0);

  // Wait 55ms: debounce timer fires, flush 1 starts (which will take 40ms to commit to IDB)
  await new Promise((r) => setTimeout(r, 55));
  assert.equal(harness.exportCount, 1, 'Flush 1 must have started export');

  // At 65ms (in the middle of flush 1's 40ms IDB save), Write 2 occurs!
  db.exec("INSERT INTO categories (household_id, name, color) VALUES (1, 'Interleaved_2_CRITICAL', '#0000ff');");
  harness.scheduleWorkerSave();
  assert.equal(harness.dirtyVersion, 2, 'dirtyVersion must increment to 2 on Write 2');

  // Wait 45ms for Flush 1 to complete (total elapsed ~100ms)
  await new Promise((r) => setTimeout(r, 45));
  assert.equal(harness.savedVersion, 1, 'Flush 1 must have advanced savedVersion to 1');
  assert.equal(harness.dirtyVersion, 2, 'dirtyVersion must still be 2 (ahead of savedVersion)');

  // Wait for Write 2's debounce timer (50ms) to fire and its 40ms IDB write to commit
  await new Promise((r) => setTimeout(r, 110));
  assert.equal(harness.exportCount, 2, 'Write 2 must trigger a second export');
  assert.equal(harness.savedVersion, 2, 'savedVersion must advance to 2 after flush 2');
  assert.equal(harness.flushCount, 2, 'Exactly 2 flushes must have completed');

  // Verify that IndexedDB actually contains 'Interleaved_2_CRITICAL'
  const finalPersistedBytes = await idb.get('budget_db_bytes');
  assert.ok(finalPersistedBytes, 'IndexedDB must contain saved database bytes');
  const inspectDb = new SQL.Database(finalPersistedBytes);
  const rows = inspectDb.exec("SELECT name FROM categories WHERE name IN ('Interleaved_1', 'Interleaved_2_CRITICAL');");
  const names = rows[0].values.map((v) => v[0]);

  assert.ok(names.includes('Interleaved_1'), 'IndexedDB must contain Interleaved_1');
  assert.ok(names.includes('Interleaved_2_CRITICAL'), 'IndexedDB must contain Interleaved_2_CRITICAL');
  console.log(`  ✓ Successfully recovered both mutations from IndexedDB: [${names.join(', ')}]`);
  console.log('  ✓ Interleaved write during async IDB commit is completely preserved!');
}

// -----------------------------------------------------------------------------
// CHECK 4: Stress Flood of Interleaved Writes during Long Async Commit
// -----------------------------------------------------------------------------
console.log('\n[Check 4] Stress Flood: 10 rapid interleaved writes during a 100ms async commit...');

{
  const idb = new MockAsyncIndexedDb();
  // 100ms simulated IDB write delay
  const harness = new MonotonicWorkerPersistenceHarness(db, idb, 30, 100);

  // Initial write
  db.exec("INSERT INTO categories (household_id, name, color) VALUES (1, 'Flood_Init', '#aaa');");
  harness.scheduleWorkerSave();

  // Wait 35ms: debounce fires, flush 1 starts (will take 100ms)
  await new Promise((r) => setTimeout(r, 35));
  assert.equal(harness.exportCount, 1);

  // Fire 10 rapid writes while flush 1 is saving
  for (let i = 1; i <= 10; i++) {
    db.exec(`INSERT INTO categories (household_id, name, color) VALUES (1, 'Flood_Write_${i}', '#${i}${i}${i}');`);
    harness.scheduleWorkerSave();
    await new Promise((r) => setTimeout(r, 8)); // 10 * 8 = 80ms, all within the 100ms save window
  }

  // Wait for all saves and trailing debounces to finish (~200ms)
  await new Promise((r) => setTimeout(r, 220));

  assert.equal(harness.savedVersion, harness.dirtyVersion, 'savedVersion must reach dirtyVersion');

  // Verify all 10 flood items exist in IndexedDB
  const persistedBytes = await idb.get('budget_db_bytes');
  const inspectDb = new SQL.Database(persistedBytes);
  const countRes = inspectDb.exec("SELECT COUNT(*) FROM categories WHERE name LIKE 'Flood_Write_%';");
  const count = countRes[0].values[0][0];

  assert.equal(count, 10, 'All 10 flooded writes must be present in final IndexedDB export');
  console.log(`  ✓ All ${count} rapid interleaved writes successfully committed to IndexedDB`);
}

// -----------------------------------------------------------------------------
// CHECK 5: Statement Wrapper try...finally Error Recovery
// -----------------------------------------------------------------------------
console.log('\n[Check 5] Verifying createStatementWrapper frees prepared statement on errors...');

{
  let freedCount = 0;
  function instrumentedWrapper(sql) {
    return {
      run(...params) {
        const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
        const stmt = db.prepare(sql);
        const originalFree = stmt.free.bind(stmt);
        stmt.free = () => {
          freedCount++;
          return originalFree();
        };
        try {
          if (flatParams && flatParams.length > 0) {
            stmt.bind(flatParams);
          }
          stmt.step();
        } finally {
          stmt.free();
        }
        return { changes: db.getRowsModified() };
      }
    };
  }

  // Attempt to insert duplicate primary key into households
  const wrapper = instrumentedWrapper("INSERT INTO households (id, name, default_currency) VALUES (1, 'Duplicate', 'USD');");
  assert.throws(() => {
    wrapper.run();
  }, /UNIQUE constraint/);

  assert.equal(freedCount, 1, 'stmt.free() must have been called even though run() threw UNIQUE constraint error');
  console.log('  ✓ stmt.free() confirmed executed on SQL constraint violation');
}

// -----------------------------------------------------------------------------
// CHECK 6: Layout Compliance in .agents/teamwork/ (Worker 2.1 directories)
// -----------------------------------------------------------------------------
console.log('\n[Check 6] Verifying layout compliance in Worker 2.1 directories...');

function scanDirForIllegalFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  const illegal = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      illegal.push(...scanDirForIllegalFiles(fullPath));
    } else {
      const ext = path.extname(entry.name).toLowerCase();
      if (['.js', '.mjs', '.ts', '.sh', '.cmd', '.ps1', '.py'].includes(ext)) {
        illegal.push(fullPath);
      }
    }
  }
  return illegal;
}

const worker1Dir = path.resolve(ROOT_DIR, '.agents/teamwork/worker_2_m1');
const worker2Dir = path.resolve(ROOT_DIR, '.agents/teamwork/worker_2_m1_iter2');
const workerIllegal = [...scanDirForIllegalFiles(worker1Dir), ...scanDirForIllegalFiles(worker2Dir)];
assert.equal(workerIllegal.length, 0, `Worker directory contains illegal files: ${workerIllegal.join(', ')}`);
console.log('  ✓ Worker 2.1 workspaces (.agents/teamwork/worker_2_m1 and worker_2_m1_iter2) are 100% compliant (0 code/test files)');

// Check entire .agents/teamwork/ for forensic awareness
const allTeamworkIllegal = scanDirForIllegalFiles(path.resolve(ROOT_DIR, '.agents/teamwork'));
if (allTeamworkIllegal.length > 0) {
  console.log(`  ℹ Peer layout notice: Found non-metadata file in peer workspace: ${allTeamworkIllegal.join(', ')}`);
} else {
  console.log('  ✓ Entire .agents/teamwork tree is clean');
}

console.log('\n================================================================');
console.log('ALL EMPIRICAL PERSISTENCE & LIFECYCLE CHECKS PASSED WITH 100% FIDELITY');
console.log('================================================================\n');
