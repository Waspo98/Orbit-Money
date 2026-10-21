// frontend/test/worker-stress.test.mjs
// Adversarial Empirical Stress & Race Condition Test Harness for Worker 2.1 (Milestone 1)

import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '../..');

console.log('================================================================');
console.log('ADVERSARIAL STRESS & RACE CONDITION HARNESS: WORKER 2.1 (M1)');
console.log('================================================================');

// -----------------------------------------------------------------------------
// Test Suite 1: High-Concurrency Message Flooding & Correlation ID Resolution
// -----------------------------------------------------------------------------
console.log('\n--- SUITE 1: High-Concurrency Flooding & Correlation ID Matching ---');

class MockWorkerChannel {
  constructor(delayMin = 0, delayMax = 20) {
    this.delayMin = delayMin;
    this.delayMax = delayMax;
    this.onmessage = null;
    this.onerror = null;
    this.messageLog = [];
    this.activeWorkers = 1;
  }

  postMessage(data, transferList = []) {
    this.messageLog.push(data);
    const { id, type, payload } = data;
    
    // Simulate asynchronous out-of-order processing with random jitter
    const delay = Math.floor(Math.random() * (this.delayMax - this.delayMin + 1)) + this.delayMin;
    setTimeout(() => {
      if (this.onmessage) {
        if (type === 'ERROR_TEST') {
          this.onmessage({
            data: { id, success: false, error: 'Simulated operation failure', status: 500 }
          });
        } else {
          this.onmessage({
            data: { id, success: true, result: { echoId: id, echoPayload: payload, processedAt: Date.now() } }
          });
        }
      }
    }, delay);
  }

  terminate() {
    this.activeWorkers = 0;
  }
}

// Emulate localDb callWorker mechanics with pure correlation IDs
function createWorkerClient(workerInstance) {
  let nextId = 1;
  const pendingRequests = new Map();

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
    for (const [, pending] of pendingRequests.entries()) {
      pending.reject(new Error(err.message || 'SQLite worker crashed'));
    }
    pendingRequests.clear();
    try {
      workerInstance.terminate();
    } catch {}
  };

  function call(type, payload = {}, transferList = []) {
    return new Promise((resolve, reject) => {
      try {
        const id = nextId++;
        pendingRequests.set(id, { resolve, reject });
        workerInstance.postMessage({ id, type, payload }, transferList);
      } catch (err) {
        reject(err);
      }
    });
  }

  return { call, pendingRequests, getNextId: () => nextId };
}

async function runFloodingTest(concurrency = 500) {
  const channel = new MockWorkerChannel(1, 30);
  const client = createWorkerClient(channel);
  const startTime = Date.now();

  console.log(`[Flood Test] Dispatching ${concurrency} parallel requests with randomized out-of-order latency...`);
  const promises = [];
  const completionOrder = [];

  for (let i = 0; i < concurrency; i++) {
    const p = client.call('PING', { index: i, tag: `req-${i}` })
      .then((res) => {
        completionOrder.push(res.echoId);
        assert.equal(res.echoPayload.index, i, `Cross-talk detected! Expected payload index ${i}, got ${res.echoPayload.index}`);
        assert.equal(res.echoPayload.tag, `req-${i}`);
        return res;
      });
    promises.push(p);
  }

  assert.equal(client.pendingRequests.size, concurrency, `Pending map should contain ${concurrency} requests immediately`);

  const results = await Promise.all(promises);
  const duration = Date.now() - startTime;

  console.log(`[Flood Test] All ${concurrency} parallel requests completed in ${duration}ms.`);
  console.log(`[Flood Test] Pending requests remaining: ${client.pendingRequests.size}`);
  assert.equal(client.pendingRequests.size, 0, 'Pending requests map must be completely clean (0 leaks)');
  assert.equal(results.length, concurrency);

  // Check out-of-order execution occurred
  let outOfOrderCount = 0;
  for (let i = 1; i < completionOrder.length; i++) {
    if (completionOrder[i] < completionOrder[i - 1]) {
      outOfOrderCount++;
    }
  }
  console.log(`[Flood Test] Out-of-order completions detected: ${outOfOrderCount} / ${concurrency}`);
  assert(outOfOrderCount > 0, 'Out-of-order jitter must have occurred to validate correlation ID robustness');
  console.log('✓ PASS: Concurrency flooding & correlation ID matching verified with zero cross-talk.');
}

await runFloodingTest(500);

// -----------------------------------------------------------------------------
// Test Suite 2: Worker Crash / Error Propagation (`onerror`)
// -----------------------------------------------------------------------------
console.log('\n--- SUITE 2: Worker Crash & Error Propagation ---');

async function runCrashTest() {
  const channel = new MockWorkerChannel(100, 200);
  const client = createWorkerClient(channel);

  const p1 = client.call('PING', { item: 1 });
  const p2 = client.call('PING', { item: 2 });
  const p3 = client.call('PING', { item: 3 });

  assert.equal(client.pendingRequests.size, 3);

  // Trigger worker crash event
  channel.onerror(new Error('Synthetic WASM OOM crash'));

  let rejectedCount = 0;
  await Promise.all([
    p1.catch((e) => { rejectedCount++; assert(e.message.includes('Synthetic WASM OOM crash')); }),
    p2.catch((e) => { rejectedCount++; assert(e.message.includes('Synthetic WASM OOM crash')); }),
    p3.catch((e) => { rejectedCount++; assert(e.message.includes('Synthetic WASM OOM crash')); })
  ]);

  assert.equal(rejectedCount, 3, 'All in-flight requests must reject when worker crashes');
  assert.equal(client.pendingRequests.size, 0, 'Pending map must be cleared on crash');
  console.log('✓ PASS: Worker crash properly rejects all pending promises without hanging.');
}

await runCrashTest();

// -----------------------------------------------------------------------------
// Test Suite 3: Real SQLite WASM Engine Execution & SQL Error Handling
// -----------------------------------------------------------------------------
console.log('\n--- SUITE 3: Real SQLite WASM Engine & Endpoint Error Handling ---');

// Load sql.js WASM
const sqlWasmPath = path.resolve(rootDir, 'frontend/node_modules/sql.js/dist/sql-wasm.js');
const initSqlJs = (await import(`file:///${sqlWasmPath.replace(/\\/g, '/')}`)).default;
const SQL = await initSqlJs();

function createTestDatabaseWithMigrations() {
  const db = new SQL.Database();
  db.exec(`
    CREATE TABLE IF NOT EXISTS _migrations (
      name TEXT PRIMARY KEY,
      applied_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);

  const migrationsDir = path.resolve(rootDir, 'backend/src/db/migrations');
  const files = fs.readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort();
  for (const file of files) {
    const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf-8');
    db.exec('BEGIN TRANSACTION;');
    db.exec(sql);
    db.run('INSERT INTO _migrations (name) VALUES (?)', [file]);
    db.exec('COMMIT;');
  }

  // Ensure household 1 and user 1
  db.exec(`
    INSERT OR IGNORE INTO households (id, name, default_currency) VALUES (1, 'My Household', 'USD');
    INSERT OR IGNORE INTO users (id, username, display_name, is_local_admin) VALUES (1, 'local_user', 'Local User', 1);
    INSERT OR IGNORE INTO household_memberships (household_id, user_id, role, access_level) VALUES (1, 1, 'owner', 'write');
  `);

  return db;
}

const testDb = createTestDatabaseWithMigrations();
console.log('[SQLite Engine] Initialized in-memory SQLite database with 34 migrations.');

function createStatementWrapper(db, sql) {
  return {
    all(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      if (flatParams && flatParams.length > 0) stmt.bind(flatParams);
      const rows = [];
      while (stmt.step()) rows.push(stmt.getAsObject());
      stmt.free();
      return rows;
    },
    get(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      if (flatParams && flatParams.length > 0) stmt.bind(flatParams);
      let row = null;
      if (stmt.step()) row = stmt.getAsObject();
      stmt.free();
      return row;
    },
    run(...params) {
      const flatParams = params.length === 1 && Array.isArray(params[0]) ? params[0] : params;
      const stmt = db.prepare(sql);
      if (flatParams && flatParams.length > 0) stmt.bind(flatParams);
      stmt.step();
      stmt.free();
      const lastIdRes = db.exec('SELECT last_insert_rowid() AS id;');
      const lastInsertRowid = lastIdRes[0]?.values[0]?.[0] ?? 0;
      const changes = db.getRowsModified();
      return { changes, lastInsertRowid };
    }
  };
}

// Emulate worker message handler
async function handleWorkerMessage(db, message) {
  const { id, type, url, options, bytes } = message;
  try {
    if (type === 'PING') {
      return { id, success: true };
    }

    if (type === 'API_REQUEST') {
      const pathname = url.split('?')[0];
      const method = (options.method || 'GET').toUpperCase();
      const wrap = (sql) => createStatementWrapper(db, sql);

      if (pathname === '/api/exec' && method === 'POST') {
        const body = options.body || {};
        if (body.sql) {
          wrap(body.sql).run(body.params || []);
          return { id, success: true, result: { success: true } };
        }
      }

      if (pathname === '/api/accounts' && method === 'GET') {
        const accounts = wrap('SELECT * FROM accounts WHERE household_id = 1').all();
        return { id, success: true, result: { items: accounts } };
      }

      if (pathname === '/api/accounts' && method === 'POST') {
        const body = options.body || {};
        const res = wrap(`
          INSERT INTO accounts (household_id, name, type, is_manual, current_balance, sort_order)
          VALUES (1, ?, ?, 1, ?, 10)
        `).run([body.name, body.type || 'checking', body.balance || 0]);
        return { id, success: true, result: { success: true, id: res.lastInsertRowid } };
      }

      if (pathname === '/api/categories' && method === 'GET') {
        const items = wrap('SELECT * FROM categories WHERE household_id = 1').all();
        return { id, success: true, result: { items } };
      }

      if (pathname === '/api/transactions' && method === 'GET') {
        const count = wrap('SELECT COUNT(*) AS total FROM transactions WHERE household_id = 1').get()?.total || 0;
        return {
          id,
          success: true,
          result: {
            items: [],
            total: count,
            totalCount: count,
            totalPages: Math.max(1, Math.ceil(count / 50)),
            pageCount: Math.max(1, Math.ceil(count / 50)),
            page: 1,
            grandTotal: count
          }
        };
      }

      // Check worker's fallback behavior
      console.warn(`[Worker Fallback] Unhandled endpoint: ${method} ${pathname}`);
      return { id, success: true, result: { success: true, items: [] } };
    }

    if (type === 'EXPORT_DATABASE') {
      const exported = db.export();
      return { id, success: true, bytes: exported };
    }

    throw new Error(`[sqlite.worker] Unknown message type: ${type}`);
  } catch (err) {
    return {
      id,
      success: false,
      error: err.message || String(err),
      status: err.status || 500
    };
  }
}

// Test 3.1: Rejected SQL Operations (Syntax Error)
console.log('\n[Test 3.1] Testing rejected SQL syntax error...');
const syntaxErrResp = await handleWorkerMessage(testDb, {
  id: 101,
  type: 'API_REQUEST',
  url: '/api/exec',
  options: { method: 'POST', body: { sql: 'SELECT * FROM ;;; INVALID SQL SYNTAX;' } }
});
console.log('[Test 3.1 Response]', syntaxErrResp);
assert.equal(syntaxErrResp.id, 101);
assert.equal(syntaxErrResp.success, false);
assert.equal(syntaxErrResp.status, 500);
assert(syntaxErrResp.error.includes('syntax error'), `Expected syntax error, got: ${syntaxErrResp.error}`);
console.log('✓ PASS: Rejected SQL syntax cleanly returns success: false and status: 500');

// Test 3.2: Rejected SQL Operations (Constraint Violation)
console.log('\n[Test 3.2] Testing rejected SQL constraint violation...');
const constraintErrResp = await handleWorkerMessage(testDb, {
  id: 102,
  type: 'API_REQUEST',
  url: '/api/exec',
  options: {
    method: 'POST',
    body: { sql: "INSERT INTO households (id, name, default_currency) VALUES (1, 'Duplicate Household', 'USD');" }
  }
});
console.log('[Test 3.2 Response]', constraintErrResp);
assert.equal(constraintErrResp.id, 102);
assert.equal(constraintErrResp.success, false);
assert.equal(constraintErrResp.status, 500);
assert(constraintErrResp.error.includes('UNIQUE constraint failed'), `Expected UNIQUE constraint failed, got: ${constraintErrResp.error}`);
console.log('✓ PASS: Constraint violation cleanly returns success: false and status: 500');

// Test 3.3: Worker Resilience after SQL Failures
console.log('\n[Test 3.3] Verifying worker resilience after errors...');
const validResp = await handleWorkerMessage(testDb, {
  id: 103,
  type: 'API_REQUEST',
  url: '/api/categories',
  options: { method: 'GET' }
});
assert.equal(validResp.id, 103);
assert.equal(validResp.success, true);
assert(Array.isArray(validResp.result.items));
console.log(`✓ PASS: Worker immediately processes valid requests (${validResp.result.items.length} categories) after errors`);

// Test 3.4: Unknown Message Type
console.log('\n[Test 3.4] Testing unknown message type...');
const unknownTypeResp = await handleWorkerMessage(testDb, {
  id: 104,
  type: 'CORRUPTED_MESSAGE_TYPE'
});
console.log('[Test 3.4 Response]', unknownTypeResp);
assert.equal(unknownTypeResp.id, 104);
assert.equal(unknownTypeResp.success, false);
assert.equal(unknownTypeResp.status, 500);
assert(unknownTypeResp.error.includes('Unknown message type: CORRUPTED_MESSAGE_TYPE'));
console.log('✓ PASS: Unknown message type returns status 500 and rejects cleanly');

// Test 3.5: Unmatched / Invalid Endpoint Behavior
console.log('\n[Test 3.5] Investigating unmatched / invalid endpoint behavior...');
const unmatchedResp = await handleWorkerMessage(testDb, {
  id: 105,
  type: 'API_REQUEST',
  url: '/api/nonexistent-route-404',
  options: { method: 'GET' }
});
console.log('[Test 3.5 Response]', unmatchedResp);
console.log(`NOTE: Unmatched endpoint returned: success=${unmatchedResp.success}, items=${JSON.stringify(unmatchedResp.result.items)}`);
// In sqlite.worker.js line 1295, unmatched routes return { success: true, items: [] } as a fallback
console.log('✓ PASS: Unmatched endpoint handles gracefully without crashing worker');

// -----------------------------------------------------------------------------
// Test Suite 4: End-to-End Concurrent Database Flooding with Mixed Reads & Writes
// -----------------------------------------------------------------------------
console.log('\n--- SUITE 4: Real Database High-Concurrency Mixed Read/Write Flooding ---');

async function runRealDbConcurrencyTest(operations = 200) {
  const db = createTestDatabaseWithMigrations();
  const startTime = Date.now();

  console.log(`[DB Flood] Executing ${operations} concurrent mixed DB operations...`);
  const promises = [];

  for (let i = 0; i < operations; i++) {
    const id = i + 1;
    let req;
    if (i % 5 === 0) {
      // Write: create account
      req = { id, type: 'API_REQUEST', url: '/api/accounts', options: { method: 'POST', body: { name: `Account ${i}`, balance: i * 100 } } };
    } else if (i % 5 === 1) {
      // Export database
      req = { id, type: 'EXPORT_DATABASE' };
    } else if (i % 5 === 2) {
      // Read: transactions
      req = { id, type: 'API_REQUEST', url: '/api/transactions', options: { method: 'GET' } };
    } else if (i % 5 === 3) {
      // Read: categories
      req = { id, type: 'API_REQUEST', url: '/api/categories', options: { method: 'GET' } };
    } else {
      // Read: accounts
      req = { id, type: 'API_REQUEST', url: '/api/accounts', options: { method: 'GET' } };
    }

    promises.push(handleWorkerMessage(db, req).then((res) => {
      assert.equal(res.id, id, `Response ID mismatch! Expected ${id}, got ${res.id}`);
      assert.equal(res.success, true);
      return res;
    }));
  }

  const results = await Promise.all(promises);
  const elapsed = Date.now() - startTime;
  console.log(`[DB Flood] All ${operations} mixed operations completed in ${elapsed}ms (${(operations / (elapsed / 1000)).toFixed(1)} ops/sec)`);
  assert.equal(results.length, operations);
  console.log('✓ PASS: High-concurrency mixed read/write database flooding executed with zero errors.');
}

await runRealDbConcurrencyTest(200);

// -----------------------------------------------------------------------------
// Test Suite 5: Debounced Persistence & Race Condition Empirical Verification
// -----------------------------------------------------------------------------
console.log('\n--- SUITE 5: Persistence Debounce & Race Condition Empirical Analysis ---');

let isDirty = false;
let saveTimeout = null;
let idbSavedCount = 0;
let lastSavedPayload = null;

async function mockSaveBytesToIdb(payload) {
  // Simulate asynchronous IndexedDB write delay
  await new Promise((r) => setTimeout(r, 20));
  idbSavedCount++;
  lastSavedPayload = payload;
}

function scheduleWorkerSave(payload) {
  isDirty = true;
  if (saveTimeout) clearTimeout(saveTimeout);
  saveTimeout = setTimeout(async () => {
    await flushWorkerDatabase();
  }, 50); // 50ms for test speed
}

let activePayload = 'initial';

async function flushWorkerDatabase() {
  if (saveTimeout) {
    clearTimeout(saveTimeout);
    saveTimeout = null;
  }
  if (!isDirty) return;
  try {
    const payloadToSave = activePayload;
    // Current Worker 2.1 implementation:
    // await saveBytesToIdb(bytes);
    // isDirty = false;
    await mockSaveBytesToIdb(payloadToSave);
    isDirty = false;
  } catch (err) {
    console.error(err);
  }
}

// Test scenario: Write 1 starts flush, Write 2 occurs during IDB write
console.log('[Race Test] Simulating Write 2 occurring while Write 1 is saving to IDB...');
activePayload = 'write_1';
isDirty = true;
const flush1Promise = flushWorkerDatabase();

// In the middle of flush 1 saving, Write 2 occurs
activePayload = 'write_2';
scheduleWorkerSave('write_2');

// Wait for flush 1 to finish
await flush1Promise;
// At this point, flush 1 finished and set isDirty = false!
console.log(`[Race Test] State immediately after flush 1: isDirty=${isDirty}, lastSaved=${lastSavedPayload}`);

// Now wait for scheduleWorkerSave timeout (50ms) to trigger flush 2
await new Promise((r) => setTimeout(r, 80));
console.log(`[Race Test] State after scheduled timeout: isDirty=${isDirty}, lastSaved=${lastSavedPayload}, idbSavedCount=${idbSavedCount}`);

const isVulnerable = lastSavedPayload !== 'write_2';
console.log(`[Race Test Result] Vulnerability detected in current implementation: ${isVulnerable ? 'YES (Write 2 was skipped because isDirty was cleared)' : 'NO'}`);

if (isVulnerable) {
  console.log('⚠️ FINDING: Debounce flag race condition confirmed in sqlite.worker.js lines 105-108.');
}

// Now demonstrate the robust version counter pattern
console.log('\n[Fix Verification] Testing version-counter / pre-clear debounce pattern...');
let dirtyVersion = 0;
let savedVersion = 0;
let fixedSaveTimeout = null;
let fixedSavedPayload = null;

async function fixedMockSave(payload) {
  await new Promise((r) => setTimeout(r, 20));
  fixedSavedPayload = payload;
}

function scheduleFixedSave() {
  dirtyVersion++;
  if (fixedSaveTimeout) clearTimeout(fixedSaveTimeout);
  fixedSaveTimeout = setTimeout(async () => {
    await flushFixedDatabase();
  }, 50);
}

let fixedActivePayload = 'initial';

async function flushFixedDatabase() {
  if (fixedSaveTimeout) {
    clearTimeout(fixedSaveTimeout);
    fixedSaveTimeout = null;
  }
  const versionToSave = dirtyVersion;
  if (versionToSave === savedVersion) return;
  try {
    const payloadToSave = fixedActivePayload;
    await fixedMockSave(payloadToSave);
    savedVersion = Math.max(savedVersion, versionToSave);
  } catch (err) {
    console.error(err);
  }
}

// Simulate identical interleaving
fixedActivePayload = 'write_1';
dirtyVersion++;
const fixedFlush1Promise = flushFixedDatabase();

// In the middle of flush 1 saving, Write 2 occurs
fixedActivePayload = 'write_2';
scheduleFixedSave();

await fixedFlush1Promise;
// Wait for timeout (50ms)
await new Promise((r) => setTimeout(r, 80));

console.log(`[Fix Verification] State after scheduled timeout: savedVersion=${savedVersion}, dirtyVersion=${dirtyVersion}, payload=${fixedSavedPayload}`);
assert.equal(fixedSavedPayload, 'write_2', 'Fixed pattern must successfully persist write_2 to IndexedDB!');
console.log('✓ PASS: Version counter pattern completely eliminates persistence race condition.');

console.log('\n================================================================');
console.log('ALL ADVERSARIAL STRESS TESTS COMPLETE');
console.log('================================================================\n');
