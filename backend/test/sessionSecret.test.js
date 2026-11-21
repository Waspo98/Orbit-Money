import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'fs';
import os from 'os';
import path from 'path';
import test from 'node:test';
import Database from 'better-sqlite3';
import express from 'express';

import { looksLikePlaceholder, resolveSessionSecret } from '../src/lib/sessionSecret.js';
import { runMigrations } from '../src/db/migrations.js';
import { appVersion, createHealthRouter } from '../src/routes/health.js';

test('resolveSessionSecret uses explicit non-placeholder env secret without touching disk', () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'orbit-secret-env-'));
  try {
    const result = resolveSessionSecret({
      envSecret: 'explicit-production-secret-value-1234',
      dataDir: tempDir
    });
    assert.equal(result.secret, 'explicit-production-secret-value-1234');
    assert.equal(result.source, 'env');
    assert.equal(fs.existsSync(path.join(tempDir, '.session_secret')), false);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test('resolveSessionSecret rejects placeholder env secrets', () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'orbit-secret-ph-'));
  try {
    assert.throws(
      () => resolveSessionSecret({ envSecret: 'change_me_to_random_64_char_hex_string', dataDir: tempDir }),
      (err) => err?.code === 'PLACEHOLDER_SECRET'
    );
    assert.equal(looksLikePlaceholder('change_this_password'), true);
    assert.equal(looksLikePlaceholder('valid-random-secret'), false);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test('resolveSessionSecret generates and persists a 64-char hex secret when omitted', () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'orbit-secret-gen-'));
  try {
    const first = resolveSessionSecret({ envSecret: '', dataDir: tempDir });
    assert.equal(first.source, 'generated');
    assert.match(first.secret, /^[0-9a-f]{64}$/);
    assert.equal(fs.existsSync(path.join(tempDir, '.session_secret')), true);

    const second = resolveSessionSecret({ envSecret: undefined, dataDir: tempDir });
    assert.equal(second.source, 'file');
    assert.equal(second.secret, first.secret);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test('config.js boots with only ADMIN_PASSWORD and persists SESSION_SECRET in DATA_DIR', () => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'orbit-config-boot-'));
  try {
    const configUrl = new URL('../src/config.js', import.meta.url).href;
    const script = `import { config } from ${JSON.stringify(configUrl)}; console.log(JSON.stringify({ secret: config.sessionSecret, dataDir: config.dataDir }));`;
    const env = {
      ...process.env,
      DATA_DIR: tempDir,
      AUTH_PROVIDER: 'local',
      ADMIN_PASSWORD: 'test-strong-password',
      SESSION_SECRET: '',
      SIMPLEFIN_ENCRYPTION_KEY: '',
      API_KEY: ''
    };

    const first = spawnSync(process.execPath, ['--input-type=module', '-e', script], {
      cwd: tempDir,
      env,
      encoding: 'utf8'
    });
    assert.equal(first.status, 0, first.stderr);
    const firstOut = JSON.parse(first.stdout.trim().split(/\r?\n/).pop());
    assert.match(firstOut.secret, /^[0-9a-f]{64}$/);

    const second = spawnSync(process.execPath, ['--input-type=module', '-e', script], {
      cwd: tempDir,
      env,
      encoding: 'utf8'
    });
    assert.equal(second.status, 0, second.stderr);
    const secondOut = JSON.parse(second.stdout.trim().split(/\r?\n/).pop());
    assert.equal(secondOut.secret, firstOut.secret);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
});

test('GET /api/health returns package version and DB counts', async () => {
  const db = new Database(':memory:');
  runMigrations(db);

  const app = express();
  app.use('/api/health', createHealthRouter(db));
  const server = app.listen(0);
  try {
    const port = server.address().port;
    const res = await fetch(`http://127.0.0.1:${port}/api/health`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.status, 'ok');
    assert.equal(body.version, appVersion);
    assert.equal(typeof body.counts.categories, 'number');
  } finally {
    server.close();
    db.close();
  }
});
