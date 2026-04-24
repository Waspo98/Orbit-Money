// =============================================================================
// simplefinSync.js — orchestrates a full SimpleFIN sync
// =============================================================================
// Pipeline:
//   1. Load encrypted access URL from DB, decrypt
//   2. Determine date range:
//        start = cutover_date  (or last_sync_at - 7 days, whichever is later)
//        end   = today
//   3. Delete any CSV-imported rows after cutover_date (cutover enforcement)
//   4. Fetch accounts + transactions from SimpleFIN
//   5. For each SimpleFIN account, match to existing by (institution, last4).
//        match → link simplefin_account_id (if not already linked)
//        no match → create new account (is_manual=0), flag as unmatched
//   6. For each transaction:
//        build in-memory row → run rule matcher → INSERT OR IGNORE
//        (dedupe via UNIQUE index on (source='simplefin', external_id=SimpleFIN id))
//   7. Update each account's current_balance from SimpleFIN's latest figure
//   8. Run transfer matcher
//   9. Write sync_log row with full summary
//
// Concurrency: we log to sync_log with status='running' at the start to prevent
// overlapping runs. If a previous 'running' row exists and is <15 min old, we
// bail. Older than that, we assume it crashed and proceed.
// =============================================================================

import { db } from '../db/index.js';
import { decrypt } from '../crypto.js';
import { fetchAccounts } from './simplefinClient.js';
import { loadRules, applyRulesToDraft } from './ruleMatcher.js';
import { matchTransfers } from './transferMatcher.js';
import { dollarsToCents } from '../lib/money.js';

const STALE_RUN_MINUTES = 15;
const LOOKBACK_BUFFER_DAYS = 7; // re-fetch the last N days on every sync
const UNIX_EPOCH_ISO_DATE = '1970-01-01';

function toIsoDate(d) {
  return d.toISOString().slice(0, 10);
}

function simpleFinPostedToIsoDate(posted) {
  const seconds = Number(posted);
  if (!Number.isFinite(seconds) || seconds <= 0) {
    return null;
  }

  const date = new Date(seconds * 1000);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  return toIsoDate(date);
}

/**
 * Find a previous sync_log row that's still 'running'. If it's recent, throws
 * to prevent overlap. If it's stale, marks it as error and allows the new run.
 */
function guardAgainstOverlap() {
  const stale = db
    .prepare(
      `
      SELECT id, started_at
      FROM sync_log
      WHERE status = 'running'
      ORDER BY started_at DESC
      LIMIT 1
    `
    )
    .get();

  if (!stale) return;

  const ageMs = Date.now() - new Date(stale.started_at + 'Z').getTime();
  if (ageMs < STALE_RUN_MINUTES * 60 * 1000) {
    throw new Error(
      'Another sync is already running. Wait for it to finish, or try again ' +
        `in ${STALE_RUN_MINUTES} minutes.`
    );
  }

  db.prepare(
    `UPDATE sync_log
       SET status = 'error',
           finished_at = datetime('now'),
           error_message = 'Sync appeared to crash (marked stale after 15 min).'
       WHERE id = ?`
  ).run(stale.id);
}

/**
 * Guess our internal account type from SimpleFIN metadata. SimpleFIN doesn't
 * have an explicit type field — we rely on the account name and the org.
 */
function guessAccountTypeFromSimpleFin(sfAccount) {
  const hay = `${sfAccount.name || ''} ${sfAccount.org?.name || ''}`.toLowerCase();
  if (/credit|card/.test(hay)) return 'credit';
  if (/ira|401k|roth|brokerage|invest|betterment|vanguard|fidelity|schwab/.test(hay)) {
    return 'investment';
  }
  if (/loan|mortgage/.test(hay)) return 'loan';
  if (/savings|reserve/.test(hay)) return 'savings';
  return 'checking';
}

/**
 * Extract a last-4 from SimpleFIN's account. Not all providers expose an
 * account number. Returns null if unavailable.
 */
function extractLast4(sfAccount) {
  // SimpleFIN occasionally puts a masked number in the name (e.g., "...1234").
  const fromName = (sfAccount.name || '').match(/\b(\d{4})\s*$/);
  if (fromName) return fromName[1];
  return null;
}

/**
 * Run a full sync. `trigger` is 'manual' | 'scheduled'.
 * Returns a summary object; errors throw and are logged to sync_log.
 */
export async function runSync({ trigger = 'manual' } = {}) {
  guardAgainstOverlap();

  // Load config
  const cfg = db.prepare('SELECT * FROM simplefin_config WHERE id = 1').get();
  if (!cfg || !cfg.access_url_encrypted) {
    throw new Error('SimpleFIN is not configured. Connect it in Settings first.');
  }
  if (!cfg.sync_enabled) {
    throw new Error('SimpleFIN sync is disabled.');
  }
  const cutoverDate = cfg.cutover_date;
  if (!cutoverDate) {
    throw new Error(
      'Cutover date is not set. Reconnect SimpleFIN from Settings to set one.'
    );
  }

  const accessUrl = decrypt(cfg.access_url_encrypted);

  // Open a sync_log row
  const logInsert = db.prepare(`
    INSERT INTO sync_log (status, trigger) VALUES ('running', ?)
  `);
  const logId = logInsert.run(trigger).lastInsertRowid;

  const markLog = db.prepare(`
    UPDATE sync_log
       SET status = ?,
           finished_at = datetime('now'),
           transactions_inserted = ?,
           transactions_skipped = ?,
           rm_rows_deleted = ?,
           accounts_created = ?,
           accounts_unmatched = ?,
           transfers_matched = ?,
           error_message = ?
     WHERE id = ?
  `);

  try {
    // Date range: cutover → today (capped by a small lookback on re-runs
    // to avoid fetching 5 years on a normal daily pull).
    const today = new Date();
    const cutover = new Date(cutoverDate + 'T00:00:00Z');

    let start;
    if (cfg.last_sync_at) {
      const lastSync = new Date(cfg.last_sync_at + 'Z');
      const lookback = new Date(lastSync.getTime() - LOOKBACK_BUFFER_DAYS * 86400000);
      start = lookback > cutover ? lookback : cutover;
    } else {
      start = cutover;
    }

    // --- Step 1: Enforce cutover — delete post-cutover CSV rows. ------------
    const deletion = db
      .prepare(
        `DELETE FROM transactions
          WHERE source = 'csv_import' AND date > ?`
      )
      .run(cutoverDate);
    const rmDeleted = deletion.changes;

    // --- Step 2: Fetch from SimpleFIN. ---------------------------------------
    const payload = await fetchAccounts(accessUrl, { startDate: start, endDate: today });

    // --- Step 3: Upsert accounts. --------------------------------------------
    const findAccountByInstLast4 = db.prepare(`
      SELECT id FROM accounts
       WHERE institution IS ? AND account_number_last4 IS ?
    `);
    const findAccountBySfId = db.prepare(`
      SELECT id FROM accounts WHERE simplefin_account_id = ?
    `);
    const linkSfId = db.prepare(`
      UPDATE accounts SET simplefin_account_id = ?, is_manual = 0, updated_at = datetime('now')
       WHERE id = ?
    `);
    const updateBalance = db.prepare(`
      UPDATE accounts
         SET current_balance = ?, updated_at = datetime('now')
       WHERE id = ?
    `);
    const insertAccount = db.prepare(`
      INSERT INTO accounts (name, type, institution, account_number_last4,
                            simplefin_account_id, current_balance, is_manual)
      VALUES (?, ?, ?, ?, ?, ?, 0)
    `);

    let accountsCreated = 0;
    let accountsUnmatched = 0;

    // Map of SimpleFIN account id → our account id, so we can attribute
    // transactions correctly below.
    const sfToLocalId = new Map();

    for (const sf of payload.accounts) {
      // Already linked by simplefin_account_id?
      const linked = findAccountBySfId.get(sf.id);
      if (linked) {
        sfToLocalId.set(sf.id, linked.id);
        updateBalance.run(dollarsToCents(parseFloat(sf.balance) || 0), linked.id);
        continue;
      }

      // Try to match by (institution, last4).
      const institution = sf.org?.name || null;
      const last4 = extractLast4(sf);

      if (institution && last4) {
        const existing = findAccountByInstLast4.get(institution, last4);
        if (existing) {
          linkSfId.run(sf.id, existing.id);
          updateBalance.run(dollarsToCents(parseFloat(sf.balance) || 0), existing.id);
          sfToLocalId.set(sf.id, existing.id);
          continue;
        }
      }

      // No match → create new, flag unmatched.
      const type = guessAccountTypeFromSimpleFin(sf);
      const result = insertAccount.run(
        sf.name || 'SimpleFIN Account',
        type,
        institution,
        last4,
        sf.id,
        dollarsToCents(parseFloat(sf.balance) || 0)
      );
      sfToLocalId.set(sf.id, result.lastInsertRowid);
      accountsCreated++;
      accountsUnmatched++;
    }

    // --- Step 4: Insert transactions (with rule matcher). --------------------
    const rules = loadRules(db);
    const categoriesUncat =
      db
        .prepare(`SELECT id FROM categories WHERE lower(name) = 'uncategorized'`)
        .get()?.id ?? null;

    // v12: original_merchant holds the raw bank merchant name. Rule-driven
    // edits (if any) are pre-populated into the edited_* columns via
    // applyRulesToDraft before INSERT, so newly-synced rows already reflect
    // any matching rules without needing a post-sync reapply.
    const insertTxn = db.prepare(`
      INSERT OR IGNORE INTO transactions
        (account_id, date, amount,
         original_merchant, original_description,
         category_id, notes, is_transfer, is_ignored,
         edited_merchant, edited_merchant_source,
         edited_category_id, edited_category_id_source,
         edited_is_transfer, edited_is_transfer_source,
         edited_is_ignored, edited_is_ignored_source,
         source, external_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'simplefin', ?)
    `);
    const repairEpochTxn = db.prepare(`
      UPDATE transactions
         SET account_id = ?,
             date = ?,
             amount = ?,
             original_merchant = ?,
             original_description = ?
       WHERE source = 'simplefin'
         AND external_id = ?
         AND date = ?
    `);

    let inserted = 0;
    let skipped = 0;

    const insertRun = db.transaction(() => {
      for (const sf of payload.accounts) {
        const accountId = sfToLocalId.get(sf.id);
        if (!accountId) continue;

        const txns = Array.isArray(sf.transactions) ? sf.transactions : [];
        for (const tx of txns) {
          const isoDate = simpleFinPostedToIsoDate(tx.posted);
          if (!isoDate) {
            skipped++;
            continue;
          }

          const amount = parseFloat(tx.amount);
          if (!Number.isFinite(amount)) {
            skipped++;
            continue;
          }
          const amountCents = dollarsToCents(amount);

          const rawMerchant = (tx.payee || tx.description || 'Unknown').trim();
          const originalDesc = (tx.description || tx.payee || '').trim();

          // Build the draft in the shape computeEdits expects (original_* keys)
          // and run it through the rule engine. This sets edited_* with
          // source='rule:{id}' for any rule that matches.
          const draft = {
            account_id: accountId,
            date: isoDate,
            amount: amountCents,
            original_merchant: rawMerchant,
            original_description: originalDesc,
            original_category_id: categoriesUncat,
            original_is_transfer: 0,
            original_is_ignored: 0
          };
          const hydrated = applyRulesToDraft(draft, rules);

          const result = insertTxn.run(
            accountId,
            isoDate,
            amountCents,
            rawMerchant,
            originalDesc,
            categoriesUncat,
            null,  // notes
            0,     // is_transfer (original)
            0,     // is_ignored (original)
            hydrated.edited_merchant,
            hydrated.edited_merchant_source,
            hydrated.edited_category_id,
            hydrated.edited_category_id_source,
            hydrated.edited_is_transfer,
            hydrated.edited_is_transfer_source,
            hydrated.edited_is_ignored,
            hydrated.edited_is_ignored_source,
            tx.id  // SimpleFIN transaction id → external_id
          );
          if (result.changes === 1) {
            inserted++;
          } else {
            repairEpochTxn.run(
              accountId,
              isoDate,
              amountCents,
              rawMerchant,
              originalDesc,
              tx.id,
              UNIX_EPOCH_ISO_DATE
            );
            skipped++; // duplicate (already synced previously)
          }
        }
      }
    });
    insertRun();

    // --- Step 5: Transfer matcher. ------------------------------------------
    const transfersPaired = matchTransfers(db);

    // --- Step 6: Persist last_sync_at. --------------------------------------
    db.prepare(`
      UPDATE simplefin_config
         SET last_sync_at = datetime('now'), updated_at = datetime('now')
       WHERE id = 1
    `).run();

    // --- Step 7: Finalize log row. ------------------------------------------
    // SimpleFIN may have returned per-institution errors. Surface them but
    // don't fail the overall sync — partial data is still useful.
    const sfErrors = Array.isArray(payload.errors) ? payload.errors : [];
    const errorMsg = sfErrors.length > 0
      ? `Partial sync — SimpleFIN reported: ${sfErrors.slice(0, 3).join('; ')}`
      : null;
    const finalStatus = sfErrors.length > 0 ? 'error' : 'success';

    markLog.run(
      finalStatus,
      inserted,
      skipped,
      rmDeleted,
      accountsCreated,
      accountsUnmatched,
      transfersPaired,
      errorMsg,
      logId
    );

    return {
      status: finalStatus,
      inserted,
      skipped,
      rmDeleted,
      accountsCreated,
      accountsUnmatched,
      transfersPaired,
      error: errorMsg
    };
  } catch (err) {
    markLog.run('error', 0, 0, 0, 0, 0, 0, err.message || String(err), logId);
    throw err;
  }
}
