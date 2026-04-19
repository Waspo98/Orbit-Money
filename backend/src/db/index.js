import Database from 'better-sqlite3';
import path from 'path';
import fs from 'fs';
import { config } from '../config.js';
import { runMigrations as runMigrationsImpl } from './migrations.js';

// Ensure the data directory exists (matters on first run with a fresh volume).
if (!fs.existsSync(config.dataDir)) {
  fs.mkdirSync(config.dataDir, { recursive: true });
}

const dbPath = path.join(config.dataDir, 'budget.db');
export const db = new Database(dbPath);

// Performance + integrity settings.
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');
db.pragma('synchronous = NORMAL');

console.log(`SQLite opened at ${dbPath}`);

export function runMigrations() {
  runMigrationsImpl(db);
}
