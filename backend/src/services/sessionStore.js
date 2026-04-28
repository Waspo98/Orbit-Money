import fs from 'fs';
import path from 'path';
import session from 'express-session';
import Database from 'better-sqlite3';

const ONE_DAY_MS = 24 * 60 * 60 * 1000;

function validateIdentifier(value, fallback) {
  const text = String(value || fallback);
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(text) ? text : fallback;
}

function callbackLater(fn, err, value) {
  if (!fn) return;
  queueMicrotask(() => fn(err, value));
}

function expirationFromSession(sess) {
  const now = Date.now();
  const expires = sess?.cookie?.expires ? new Date(sess.cookie.expires).getTime() : NaN;
  if (Number.isFinite(expires)) return expires;
  const maxAge = Number(sess?.cookie?.maxAge);
  return Number.isFinite(maxAge) ? now + maxAge : now + ONE_DAY_MS;
}

export default class BetterSqliteSessionStore extends session.Store {
  constructor(options = {}) {
    super(options);

    this.table = validateIdentifier(options.table, 'sessions');
    const dbName = String(options.db || 'sessions.db');
    const dbPath = dbName.includes(':memory:')
      ? dbName
      : path.join(options.dir || '.', dbName);

    if (options.dir) {
      fs.mkdirSync(options.dir, { recursive: true });
    }

    this.db = new Database(dbPath);
    if (options.wal !== false) {
      this.db.pragma('journal_mode = WAL');
    }
    this.db.exec(
      `CREATE TABLE IF NOT EXISTS ${this.table} (
        sid TEXT PRIMARY KEY,
        expired INTEGER,
        sess TEXT
      )`
    );

    this.statements = {
      get: this.db.prepare(`SELECT sess FROM ${this.table} WHERE sid = ? AND ? <= expired`),
      set: this.db.prepare(
        `INSERT OR REPLACE INTO ${this.table} (sid, expired, sess) VALUES (?, ?, ?)`
      ),
      destroy: this.db.prepare(`DELETE FROM ${this.table} WHERE sid = ?`),
      all: this.db.prepare(`SELECT sess FROM ${this.table}`),
      length: this.db.prepare(`SELECT COUNT(*) AS count FROM ${this.table}`),
      clear: this.db.prepare(`DELETE FROM ${this.table}`),
      cleanup: this.db.prepare(`DELETE FROM ${this.table} WHERE ? > expired`),
      touch: this.db.prepare(`UPDATE ${this.table} SET expired = ? WHERE sid = ? AND ? <= expired`)
    };

    this.cleanup();
    this.cleanupTimer = setInterval(() => this.cleanup(), ONE_DAY_MS);
    this.cleanupTimer.unref?.();
  }

  cleanup() {
    this.statements.cleanup.run(Date.now());
  }

  get(sid, fn) {
    try {
      const row = this.statements.get.get(sid, Date.now());
      callbackLater(fn, null, row ? JSON.parse(row.sess) : null);
    } catch (err) {
      callbackLater(fn, err);
    }
  }

  set(sid, sess, fn) {
    try {
      this.statements.set.run(sid, expirationFromSession(sess), JSON.stringify(sess));
      callbackLater(fn, null, true);
    } catch (err) {
      callbackLater(fn, err);
    }
  }

  destroy(sid, fn) {
    try {
      this.statements.destroy.run(sid);
      callbackLater(fn, null, true);
    } catch (err) {
      callbackLater(fn, err);
    }
  }

  touch(sid, sess, fn) {
    try {
      this.statements.touch.run(expirationFromSession(sess), sid, Date.now());
      callbackLater(fn, null, true);
    } catch (err) {
      callbackLater(fn, err);
    }
  }

  all(fn) {
    try {
      const rows = this.statements.all.all();
      callbackLater(fn, null, rows.map((row) => JSON.parse(row.sess)));
    } catch (err) {
      callbackLater(fn, err);
    }
  }

  length(fn) {
    try {
      const row = this.statements.length.get();
      callbackLater(fn, null, row.count);
    } catch (err) {
      callbackLater(fn, err);
    }
  }

  clear(fn) {
    try {
      this.statements.clear.run();
      callbackLater(fn, null, true);
    } catch (err) {
      callbackLater(fn, err);
    }
  }
}
