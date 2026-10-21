// frontend/test/persistence-lifecycle.test.mjs
// Adversarial Empirical Challenge Suite for Milestone 1 Persistence Lifecycle & SQLite 3 Contracts

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import initSqlJs from 'sql.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '../..');
const MIGRATIONS_DIR = path.resolve(ROOT_DIR, 'backend/src/db/migrations');

// Expected Legacy Storage Constants
const EXPECTED_IDB_NAME = 'orbit-local-sqlite-v1';
const EXPECTED_IDB_STORE = 'sqlite_storage';
const EXPECTED_IDB_KEY = 'budget_db_bytes';

console.log('=== RUNNING ADVERSARIAL CHALLENGE TEST SUITE FOR MILESTONE 1 PERSISTENCE ===\n');

// -----------------------------------------------------------------------------
// Test 1: Exact Match of Storage Constants against Worker & Legacy Codebase
// -----------------------------------------------------------------------------
console.log('[Test 1] Verifying IndexedDB store constants against worker implementation...');

const workerSource = fs.readFileSync(path.resolve(ROOT_DIR, 'frontend/src/db/sqlite.worker.js'), 'utf-8');

const idbNameMatch = workerSource.match(/const\s+IDB_NAME\s*=\s*['"]([^'"]+)['"]/);
const idbStoreMatch = workerSource.match(/const\s+IDB_STORE\s*=\s*['"]([^'"]+)['"]/);
const idbKeyMatch = workerSource.match(/const\s+IDB_KEY\s*=\s*['"]([^'"]+)['"]/);

assert.ok(idbNameMatch, 'sqlite.worker.js must define IDB_NAME');
assert.ok(idbStoreMatch, 'sqlite.worker.js must define IDB_STORE');
assert.ok(idbKeyMatch, 'sqlite.worker.js must define IDB_KEY');

assert.equal(idbNameMatch[1], EXPECTED_IDB_NAME, `IDB_NAME must be exactly '${EXPECTED_IDB_NAME}'`);
assert.equal(idbStoreMatch[1], EXPECTED_IDB_STORE, `IDB_STORE must be exactly '${EXPECTED_IDB_STORE}'`);
assert.equal(idbKeyMatch[1], EXPECTED_IDB_KEY, `IDB_KEY must be exactly '${EXPECTED_IDB_KEY}'`);

console.log(`  ✓ IDB_NAME matches: '${idbNameMatch[1]}'`);
console.log(`  ✓ IDB_STORE matches: '${idbStoreMatch[1]}'`);
console.log(`  ✓ IDB_KEY matches: '${idbKeyMatch[1]}'`);

// -----------------------------------------------------------------------------
// Test 2: SQLite 3 Binary Header & Migration Integrity
// -----------------------------------------------------------------------------
console.log('\n[Test 2] Verifying SQLite 3 binary header and migration execution on sql.js WASM...');

// Load migrations from backend/src/db/migrations
const migrationFiles = fs.readdirSync(MIGRATIONS_DIR).filter((f) => f.endsWith('.sql')).sort();
assert.equal(migrationFiles.length, 34, 'Should load exactly 34 migration files');
const migrations = migrationFiles.map((file) => ({
  name: file,
  sql: fs.readFileSync(path.join(MIGRATIONS_DIR, file), 'utf-8')
}));

const SQL = await initSqlJs();
const db = new SQL.Database();

// Run migrations exactly as worker does
db.exec(`
  CREATE TABLE IF NOT EXISTS _migrations (
    name TEXT PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
  );
`);

let count = 0;
for (const item of migrations) {
  try {
    db.exec('BEGIN TRANSACTION;');
    db.exec(item.sql);
    const ins = db.prepare('INSERT INTO _migrations (name) VALUES (?)');
    ins.run([item.name]);
    ins.free();
    db.exec('COMMIT;');
    count++;
  } catch (err) {
    try { db.exec('ROLLBACK;'); } catch {}
    throw err;
  }
}

// Seed default household and user
db.exec(`
  INSERT OR IGNORE INTO households (id, name, default_currency)
  VALUES (1, 'My Household', 'USD');

  INSERT OR IGNORE INTO users (id, username, display_name, is_local_admin)
  VALUES (1, 'local_user', 'Local User', 1);

  INSERT OR IGNORE INTO household_memberships (household_id, user_id, role, access_level)
  VALUES (1, 1, 'owner', 'write');
`);

assert.equal(count, 34, 'All 34 migrations must be applied');

const exportedBytes = db.export();
assert.ok(exportedBytes instanceof Uint8Array, 'Export must return Uint8Array');
assert.ok(exportedBytes.length > 50000, `Exported bytes must contain SQLite data, actual size: ${exportedBytes.length} bytes`);

// Inspect SQLite 3 Header (First 16 bytes: "SQLite format 3\000")
const headerMagic = Buffer.from(exportedBytes.slice(0, 16));
const expectedMagic = Buffer.from('SQLite format 3\0', 'utf-8');
assert.deepEqual(headerMagic, expectedMagic, 'First 16 bytes of exported database MUST match SQLite format 3\\000');

// Inspect Page Size (Bytes 16-17, big endian)
const pageSize = (exportedBytes[16] << 8) | exportedBytes[17];
console.log(`  ✓ Exported DB size: ${exportedBytes.length} bytes`);
console.log(`  ✓ Valid SQLite 3 magic header verified: "${headerMagic.toString('ascii')}"`);
console.log(`  ✓ SQLite Page size: ${pageSize} bytes`);

// Verify integrity of exported bytes by reopening in fresh SQLite instance
const reloadedDb = new SQL.Database(exportedBytes);
const integrityStmt = reloadedDb.prepare('PRAGMA integrity_check;');
integrityStmt.step();
const integrityResult = integrityStmt.getAsObject();
integrityStmt.free();
assert.equal(integrityResult.integrity_check, 'ok', 'PRAGMA integrity_check must return "ok"');
console.log('  ✓ PRAGMA integrity_check passed: ok');

const migCountRes = reloadedDb.exec('SELECT COUNT(*) AS total FROM _migrations;');
assert.equal(migCountRes[0].values[0][0], 34, 'Reloaded database must contain all 34 recorded migrations');
console.log('  ✓ 34 migration records verified in reloaded database');

// -----------------------------------------------------------------------------
// Test 3: 500ms Trailing Debounce Stress Test & Export Prevention
// -----------------------------------------------------------------------------
console.log('\n[Test 3] Stress-testing 500ms trailing debounce mechanics...');

class MockIndexedDbStore {
  constructor() {
    this.storage = new Map();
    this.writeLog = [];
  }
  async get(key) {
    return this.storage.get(key) || null;
  }
  async put(key, bytes, delayMs = 10) {
    await new Promise((r) => setTimeout(r, delayMs));
    this.storage.set(key, bytes);
    this.writeLog.push({ timestamp: Date.now(), size: bytes.length });
  }
}

class WorkerPersistenceHarness {
  constructor(db, idbStore, debounceMs = 500) {
    this.db = db;
    this.idbStore = idbStore;
    this.debounceMs = debounceMs;
    this.saveTimeout = null;
    this.isDirty = false;
    this.exportCount = 0;
    this.flushCount = 0;
  }

  scheduleWorkerSave() {
    this.isDirty = true;
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
    if (!this.db || !this.isDirty) return;
    try {
      this.exportCount++;
      const bytes = this.db.export();
      await this.idbStore.put(EXPECTED_IDB_KEY, bytes);
      this.isDirty = false;
      this.flushCount++;
    } catch (err) {
      console.error('[Harness] Flush failed:', err);
    }
  }
}

// Scenario 3A: Rapid successive writes (e.g. 10 rapid keystrokes/edits)
{
  const idb = new MockIndexedDbStore();
  const harness = new WorkerPersistenceHarness(db, idb, 500);

  console.log('  Testing 10 rapid successive writes separated by 40ms...');
  const startTime = Date.now();
  for (let i = 0; i < 10; i++) {
    harness.db.exec(`INSERT INTO categories (household_id, name, color) VALUES (1, 'Rapid ${i}', '#123456');`);
    harness.scheduleWorkerSave();
    await new Promise((r) => setTimeout(r, 40)); // Total elapsed ~400ms (< 500ms debounce)
  }

  // At this point (elapsed ~400ms since start, ~0ms since last write), ZERO exports must have occurred!
  assert.equal(harness.exportCount, 0, 'Zero exports should occur during rapid typing before 500ms quiescence');
  assert.equal(idb.writeLog.length, 0, 'Zero IDB puts should occur during rapid typing');
  console.log('  ✓ Verified 0 exports during active rapid successive writes');

  // Wait 300ms (elapsed 300ms since last write, total ~700ms). Still < 500ms after last write!
  await new Promise((r) => setTimeout(r, 300));
  assert.equal(harness.exportCount, 0, 'Still 0 exports at 300ms after last write');

  // Wait another 300ms (elapsed 600ms since last write). Trailing debounce should have fired!
  await new Promise((r) => setTimeout(r, 300));
  assert.equal(harness.exportCount, 1, 'Exactly 1 export should occur after 500ms of inactivity');
  assert.equal(idb.writeLog.length, 1, 'Exactly 1 IDB write should occur for all 10 operations');
  console.log('  ✓ Verified exactly 1 trailing debounce export and IDB write occurred');
}

// -----------------------------------------------------------------------------
// Test 4: FLUSH_DATABASE Forces Immediate Write & Cancels Pending Debounce Timer
// -----------------------------------------------------------------------------
console.log('\n[Test 4] Verifying FLUSH_DATABASE immediate execution contract...');

{
  const idb = new MockIndexedDbStore();
  const harness = new WorkerPersistenceHarness(db, idb, 500);

  // Trigger write at t=0
  harness.db.exec(`INSERT INTO categories (household_id, name, color) VALUES (1, 'FlushTest', '#654321');`);
  harness.scheduleWorkerSave();
  assert.equal(harness.isDirty, true, 'Harness must be dirty after write');
  assert.ok(harness.saveTimeout !== null, 'Pending debounce timer must be set');

  // Immediately dispatch FLUSH_DATABASE at t=20ms (well before 500ms)
  await new Promise((r) => setTimeout(r, 20));
  const preFlushExportCount = harness.exportCount;
  assert.equal(preFlushExportCount, 0, 'Export count should be 0 before flush');

  await harness.flushWorkerDatabase();

  assert.equal(harness.exportCount, 1, 'FLUSH_DATABASE must immediately trigger 1 export');
  assert.equal(harness.isDirty, false, 'FLUSH_DATABASE must clear dirty state');
  assert.equal(harness.saveTimeout, null, 'FLUSH_DATABASE must clear pending debounce timer');
  assert.equal(idb.writeLog.length, 1, 'FLUSH_DATABASE must write to IDB immediately');
  console.log('  ✓ FLUSH_DATABASE immediately exported and wrote to IDB');

  // Wait 600ms to verify that the cancelled timer does NOT fire a redundant second export
  await new Promise((r) => setTimeout(r, 600));
  assert.equal(harness.exportCount, 1, 'No duplicate export should occur after debounce timer expires');
  console.log('  ✓ Verified no redundant export after FLUSH_DATABASE cancelled timer');

  // Calling FLUSH_DATABASE on clean database must be a no-op
  await harness.flushWorkerDatabase();
  assert.equal(harness.exportCount, 1, 'FLUSH_DATABASE on clean DB must not trigger redundant export');
  console.log('  ✓ Clean-state FLUSH_DATABASE is a safe no-op');
}

// -----------------------------------------------------------------------------
// Test 5: Adversarial Stress — Interleaved write during asynchronous IDB save
// -----------------------------------------------------------------------------
console.log('\n[Test 5] Adversarial Stress: Interleaved write during asynchronous IDB save...');

{
  // Worker code line 105-108:
  //   try {
  //     const bytes = activeDb.export();
  //     await saveBytesToIdb(bytes);
  //     isDirty = false;
  //   }
  //
  // What happens if a write occurs WHILE `saveBytesToIdb(bytes)` is awaiting?
  // Write 2 sets isDirty = true and schedules timer.
  // When saveBytesToIdb finishes, line 108 executes `isDirty = false;`, obliterating Write 2's dirty flag!
  // Then when Write 2's timer fires 500ms later, line 104 checks `if (!activeDb || !isDirty) return;`
  // and returns without saving Write 2!

  const idb = new MockIndexedDbStore();

  // Implement worker's exact implementation:
  class WorkerAsImplemented {
    constructor(db, idbStore) {
      this.db = db;
      this.idb = idbStore;
      this.saveTimeout = null;
      this.isDirty = false;
      this.exportCount = 0;
    }
    scheduleWorkerSave() {
      this.isDirty = true;
      if (this.saveTimeout) clearTimeout(this.saveTimeout);
      this.saveTimeout = setTimeout(async () => {
        await this.flushWorkerDatabase();
      }, 500);
    }
    async flushWorkerDatabase() {
      if (this.saveTimeout) {
        clearTimeout(this.saveTimeout);
        this.saveTimeout = null;
      }
      if (!this.db || !this.isDirty) return;
      try {
        this.exportCount++;
        const bytes = this.db.export();
        // Simulating 50ms async IDB put
        await this.idb.put(EXPECTED_IDB_KEY, bytes, 50);
        this.isDirty = false; // <-- Flaw: obliterates any write that occurred during the 50ms await
      } catch (err) {
        console.error(err);
      }
    }
  }

  const worker = new WorkerAsImplemented(db, idb);

  // Write 1
  worker.db.exec(`INSERT INTO categories (household_id, name, color) VALUES (1, 'W1', '#111');`);
  worker.scheduleWorkerSave();

  // Advance time so Write 1 flush starts
  await new Promise((r) => setTimeout(r, 510));
  // At this moment, flushWorkerDatabase() is awaiting idb.put (takes 50ms)

  // Write 2 arrives at 520ms (during the 50ms await)
  worker.db.exec(`INSERT INTO categories (household_id, name, color) VALUES (1, 'W2_CRITICAL', '#222');`);
  worker.scheduleWorkerSave(); // Sets isDirty = true and schedules timer for 520 + 500 = 1020ms

  // Write 1's put finishes at 560ms, and executes `worker.isDirty = false;`!
  await new Promise((r) => setTimeout(r, 60)); // Total ~570ms
  console.log(`  At 570ms (after Write 1 put finished), worker.isDirty is: ${worker.isDirty}`);

  // Now advance to 1100ms when Write 2's timer fires
  await new Promise((r) => setTimeout(r, 550));

  console.log(`  At 1100ms: exportCount = ${worker.exportCount}`);

  // Inspect the IDB store to see if 'W2_CRITICAL' was saved or lost!
  const finalBytes = await idb.get(EXPECTED_IDB_KEY);
  const checkDb = new SQL.Database(finalBytes);
  const res = checkDb.exec("SELECT name FROM categories WHERE name = 'W2_CRITICAL';");
  const saved = res.length > 0 && res[0].values.length > 0;

  console.log(`  Was W2_CRITICAL saved to IndexedDB? ${saved ? 'YES (Preserved)' : 'NO (LOST!)'}`);
  assert.equal(saved, false, 'Empirically confirms W2 is lost in current worker implementation');

  console.log('  ⚠️ VULNERABILITY CONFIRMED: Interleaved writes during async IDB commit lose their dirty state and are never flushed to IndexedDB!');
}

// -----------------------------------------------------------------------------
// Test 6: Verifying Resilient Revision-Safe Debounce Architecture
// -----------------------------------------------------------------------------
console.log('\n[Test 6] Verifying Resilient Mitigation Pattern (Snapshot Dirty Reset)...');

{
  const idb = new MockIndexedDbStore();

  class ResilientWorker {
    constructor(db, idbStore) {
      this.db = db;
      this.idb = idbStore;
      this.saveTimeout = null;
      this.isDirty = false;
      this.exportCount = 0;
    }
    scheduleWorkerSave() {
      this.isDirty = true;
      if (this.saveTimeout) clearTimeout(this.saveTimeout);
      this.saveTimeout = setTimeout(async () => {
        await this.flushWorkerDatabase();
      }, 500);
    }
    async flushWorkerDatabase() {
      if (this.saveTimeout) {
        clearTimeout(this.saveTimeout);
        this.saveTimeout = null;
      }
      if (!this.db || !this.isDirty) return;
      try {
        this.exportCount++;
        // Reset isDirty BEFORE the async await
        this.isDirty = false;
        const bytes = this.db.export();
        await this.idb.put(EXPECTED_IDB_KEY, bytes, 50);
      } catch (err) {
        this.isDirty = true;
        console.error(err);
      }
    }
  }

  const worker = new ResilientWorker(db, idb);

  // Write 1
  worker.db.exec(`INSERT INTO categories (household_id, name, color) VALUES (1, 'W1_Resilient', '#333');`);
  worker.scheduleWorkerSave();

  await new Promise((r) => setTimeout(r, 510));
  // flush starts, resets isDirty=false, starts put

  // Write 2 arrives while put is awaiting
  worker.db.exec(`INSERT INTO categories (household_id, name, color) VALUES (1, 'W2_Resilient', '#444');`);
  worker.scheduleWorkerSave(); // isDirty is set to TRUE

  // Wait for Write 1 put to finish
  await new Promise((r) => setTimeout(r, 60));
  console.log(`  At 570ms with resilient pattern, worker.isDirty is: ${worker.isDirty}`);
  assert.equal(worker.isDirty, true, 'isDirty must remain TRUE for Write 2');

  // Advance to Write 2 timer fire
  await new Promise((r) => setTimeout(r, 550));
  console.log(`  At 1100ms with resilient pattern: exportCount = ${worker.exportCount}`);
  assert.equal(worker.exportCount, 2, 'Write 2 must trigger a second export and save');

  const finalBytes = await idb.get(EXPECTED_IDB_KEY);
  const checkDb = new SQL.Database(finalBytes);
  const res = checkDb.exec("SELECT name FROM categories WHERE name = 'W2_Resilient';");
  const saved = res.length > 0 && res[0].values.length > 0;
  assert.equal(saved, true, 'W2_Resilient must be saved in IndexedDB');
  console.log('  ✓ Resilient pattern successfully persisted interleaved Write 2!');
}

console.log('\n=== ALL PERSISTENCE ADVERSARIAL CHALLENGES COMPLETED SUCCESSFULLY ===\n');
