import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const migrationsDir = path.join(__dirname, 'migrations');

/**
 * Discovers all `*.sql` files in `migrations/`, applies them in sorted filename
 * order, and records each in the `_migrations` table. Already-applied files are
 * skipped. Each migration runs inside a transaction — if any statement fails,
 * the whole file rolls back and the process exits.
 *
 * Convention: name files `NNN_description.sql` (e.g. `003_add_tags.sql`). Never
 * edit an already-applied migration — always create a new one.
 */
export function runMigrations(db) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS _migrations (
      name TEXT PRIMARY KEY,
      applied_at TEXT NOT NULL DEFAULT (datetime('now'))
    )
  `);

  const applied = new Set(
    db.prepare('SELECT name FROM _migrations').all().map((r) => r.name)
  );

  const files = fs
    .readdirSync(migrationsDir)
    .filter((f) => f.endsWith('.sql'))
    .sort();

  let appliedCount = 0;

  for (const file of files) {
    if (applied.has(file)) continue;

    console.log(`  Applying migration: ${file}`);
    const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf-8');

    try {
      const trx = db.transaction(() => {
        db.exec(sql);
        db.prepare('INSERT INTO _migrations (name) VALUES (?)').run(file);
      });
      trx();
      appliedCount++;
    } catch (err) {
      console.error(`FATAL: Migration ${file} failed:`, err.message);
      process.exit(1);
    }
  }

  if (appliedCount === 0) {
    console.log('Migrations: up to date.');
  } else {
    console.log(`Migrations: applied ${appliedCount} new file(s).`);
  }
}
