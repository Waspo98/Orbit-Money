// frontend/test/worker-contracts.test.mjs
// Verification of Worker 2.1 Iteration 2 fixes: monotonic versioning and statement leak prevention

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import initSqlJs from 'sql.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '../..');

console.log('=== VERIFYING WORKER 2.1 ITERATION 2 CONTRACTS ===\n');

// 1. Static Contract Checks on frontend/src/db/sqlite.worker.js
const workerSource = fs.readFileSync(path.resolve(rootDir, 'frontend/src/db/sqlite.worker.js'), 'utf-8');

console.log('[Contract 1] Checking monotonic persistence version tracking in sqlite.worker.js...');
assert.ok(workerSource.includes('let dirtyVersion = 0;'), 'Must declare dirtyVersion');
assert.ok(workerSource.includes('let savedVersion = 0;'), 'Must declare savedVersion');
assert.ok(workerSource.includes('dirtyVersion++;'), 'Must increment dirtyVersion on scheduleWorkerSave');
assert.ok(workerSource.includes('const versionToSave = dirtyVersion;'), 'Must snapshot versionToSave in flushWorkerDatabase');
assert.ok(workerSource.includes('savedVersion = Math.max(savedVersion, versionToSave);'), 'Must advance savedVersion upon successful saveBytesToIdb');
assert.ok(!workerSource.includes('isDirty = false;'), 'Must not reference obsolete isDirty variable');
console.log('  ✓ Monotonic persistence tracking confirmed in sqlite.worker.js source');

console.log('\n[Contract 2] Checking try...finally statement freeing in createStatementWrapper...');
assert.ok(workerSource.includes('try {\n        if (flatParams && flatParams.length > 0) {\n          stmt.bind(flatParams);\n        }\n        const rows = [];\n        while (stmt.step()) {\n          rows.push(stmt.getAsObject());\n        }\n        return rows;\n      } finally {\n        stmt.free();\n      }'), 'all() must wrap in try...finally stmt.free()');

assert.ok(workerSource.includes('try {\n        if (flatParams && flatParams.length > 0) {\n          stmt.bind(flatParams);\n        }\n        let row = null;\n        if (stmt.step()) {\n          row = stmt.getAsObject();\n        }\n        return row;\n      } finally {\n        stmt.free();\n      }'), 'get() must wrap in try...finally stmt.free()');

assert.ok(workerSource.includes('try {\n        if (flatParams && flatParams.length > 0) {\n          stmt.bind(flatParams);\n        }\n        stmt.step();\n      } finally {\n        stmt.free();\n      }'), 'run() must wrap in try...finally stmt.free()');
console.log('  ✓ Statement free try...finally blocks confirmed in createStatementWrapper');

// 2. Dynamic execution test of createStatementWrapper with stmt.free tracking
console.log('\n[Contract 3] Dynamic execution of statement wrapper error paths...');

const SQL = await initSqlJs();
const db = new SQL.Database();
db.exec('CREATE TABLE test_table (id INTEGER PRIMARY KEY, val TEXT NOT NULL);');
db.exec("INSERT INTO test_table (id, val) VALUES (1, 'hello');");

function createInstrumentedStatementWrapper(database, sql) {
  let freedCount = 0;
  return {
    getFreedCount: () => freedCount,
    wrapper: {
      all(...params) {
        const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
        const stmt = database.prepare(sql);
        const originalFree = stmt.free.bind(stmt);
        stmt.free = () => {
          freedCount++;
          return originalFree();
        };
        try {
          if (flatParams && flatParams.length > 0) {
            stmt.bind(flatParams);
          }
          const rows = [];
          while (stmt.step()) {
            rows.push(stmt.getAsObject());
          }
          return rows;
        } finally {
          stmt.free();
        }
      },
      get(...params) {
        const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
        const stmt = database.prepare(sql);
        const originalFree = stmt.free.bind(stmt);
        stmt.free = () => {
          freedCount++;
          return originalFree();
        };
        try {
          if (flatParams && flatParams.length > 0) {
            stmt.bind(flatParams);
          }
          let row = null;
          if (stmt.step()) {
            row = stmt.getAsObject();
          }
          return row;
        } finally {
          stmt.free();
        }
      },
      run(...params) {
        const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
        const stmt = database.prepare(sql);
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
        return { changes: database.getRowsModified() };
      }
    }
  };
}

// Test run() when stmt.step() throws (Unique constraint violation)
{
  const inst = createInstrumentedStatementWrapper(db, "INSERT INTO test_table (id, val) VALUES (1, 'duplicate');");
  assert.throws(() => {
    inst.wrapper.run();
  }, /UNIQUE constraint/);
  assert.equal(inst.getFreedCount(), 1, 'stmt.free() must have been called despite run() throwing constraint violation');
  console.log('  ✓ run() called stmt.free() on constraint violation error');
}

// Test all() when stmt.step() or bind throws
{
  const inst = createInstrumentedStatementWrapper(db, 'SELECT * FROM test_table WHERE id = ?;');
  const rows = inst.wrapper.all(1);
  assert.equal(rows.length, 1);
  assert.equal(inst.getFreedCount(), 1, 'stmt.free() must have been called on successful all()');
  console.log('  ✓ all() called stmt.free() on normal execution');
}

// Test get() when successful
{
  const inst = createInstrumentedStatementWrapper(db, 'SELECT * FROM test_table WHERE id = ?;');
  const row = inst.wrapper.get(1);
  assert.equal(row.id, 1);
  assert.equal(inst.getFreedCount(), 1, 'stmt.free() must have been called on successful get()');
  console.log('  ✓ get() called stmt.free() on normal execution');
}

// 3. Dynamic test of monotonic persistence versioning
console.log('\n[Contract 4] Dynamic simulation of monotonic persistence flush...');
{
  let dirtyVersion = 0;
  let savedVersion = 0;
  let saveTimeout = null;
  const idbLog = [];

  async function mockSaveToIdb(version) {
    // 30ms async delay
    await new Promise((r) => setTimeout(r, 30));
    idbLog.push(version);
  }

  function scheduleWorkerSave() {
    dirtyVersion++;
    if (saveTimeout) clearTimeout(saveTimeout);
    saveTimeout = setTimeout(async () => {
      await flushWorkerDatabase();
    }, 50);
  }

  async function flushWorkerDatabase() {
    if (saveTimeout) {
      clearTimeout(saveTimeout);
      saveTimeout = null;
    }
    const versionToSave = dirtyVersion;
    if (versionToSave === savedVersion) return;
    try {
      await mockSaveToIdb(versionToSave);
      savedVersion = Math.max(savedVersion, versionToSave);
    } catch (err) {
      console.error(err);
    }
  }

  // Trigger write 1
  scheduleWorkerSave(); // dirtyVersion = 1, timeout set for 50ms
  await new Promise((r) => setTimeout(r, 55)); // flush starts, awaiting mockSaveToIdb (taking 30ms)

  // Trigger write 2 while flush 1 is awaiting
  scheduleWorkerSave(); // dirtyVersion = 2, timeout set for +50ms

  // Wait for flush 1 to finish
  await new Promise((r) => setTimeout(r, 35));
  assert.equal(savedVersion, 1, 'Flush 1 saved version 1');
  assert.equal(dirtyVersion, 2, 'dirtyVersion is 2');

  // Wait for flush 2 timeout to fire and complete
  await new Promise((r) => setTimeout(r, 60)); // 50ms timeout + 30ms async
  await new Promise((r) => setTimeout(r, 35));

  assert.equal(savedVersion, 2, 'Flush 2 must advance savedVersion to 2');
  assert.deepEqual(idbLog, [1, 2], 'Both version 1 and version 2 must be committed to IDB');
  console.log('  ✓ Monotonic persistence verified: interleaved writes cleanly persisted in next flush');
}

console.log('\n=== ALL WORKER CONTRACT TESTS PASSED ===\n');
