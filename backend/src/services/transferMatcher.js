// =============================================================================
// transferMatcher.js — pair opposite-sign same-day transfer transactions
// =============================================================================
// Rocket Money (and bank statements in general) represent an internal transfer
// as two rows: e.g., -$500 from checking and +$500 to savings. We want those
// collapsed into a single conceptual "Transfer" so budget math is correct.
//
// Strategy (safe heuristic): only consider transactions whose displayed category
// has is_transfer=1 (Credit Card Payment, Internal Transfers, Savings Transfer).
// This avoids false pairs like "paid $50 on card" + "got $50 from a friend".
//
// Pair criteria:
//   - Same date
//   - Opposite-sign amounts with matching absolute value (in cents)
//   - Different accounts
//   - Neither already paired
//
// Runs after every SimpleFIN sync. Idempotent — re-running produces the same
// result.
// =============================================================================

/**
 * Scan all unpaired transfer-category transactions and pair any that have an
 * exact opposite-sign same-day match on another account.
 *
 * Returns the number of pairs created (= number of transactions updated / 2).
 */
export function matchTransfers(db) {
  // Only consider transactions that:
  //   - display in a transfer-flagged category
  //   - aren't already paired
  //   - don't display as ignored
  const rows = db
    .prepare(
      `
      SELECT t.id, t.account_id, t.date, t.amount
      FROM transactions t
      JOIN categories c ON c.id = COALESCE(t.edited_category_id, t.category_id)
      WHERE c.is_transfer = 1
        AND t.transfer_pair_id IS NULL
        AND COALESCE(t.edited_is_ignored, t.is_ignored) = 0
      ORDER BY t.date DESC, t.id ASC
    `
    )
    .all();

  // Group by date, then look for opposite-sign matches within each date group.
  const byDate = new Map();
  for (const r of rows) {
    if (!byDate.has(r.date)) byDate.set(r.date, []);
    byDate.get(r.date).push(r);
  }

  const pairTxn = db.prepare(`
    UPDATE transactions
    SET edited_is_transfer = 1,
        edited_is_transfer_source = 'system:transfer_matcher',
        transfer_pair_id = ?,
        updated_at = datetime('now')
    WHERE id = ?
  `);

  let pairsCreated = 0;

  const run = db.transaction(() => {
    for (const [, group] of byDate) {
      const consumed = new Set();

      for (let i = 0; i < group.length; i++) {
        if (consumed.has(group[i].id)) continue;
        const a = group[i];

        for (let j = i + 1; j < group.length; j++) {
          if (consumed.has(group[j].id)) continue;
          const b = group[j];

          // Opposite sign + equal magnitude + different accounts
          const sameSize = Math.round(Math.abs(a.amount) * 100) ===
                            Math.round(Math.abs(b.amount) * 100);
          const oppositeSigns = (a.amount > 0) !== (b.amount > 0);
          const differentAccounts = a.account_id !== b.account_id;

          if (sameSize && oppositeSigns && differentAccounts) {
            pairTxn.run(b.id, a.id);
            pairTxn.run(a.id, b.id);
            consumed.add(a.id);
            consumed.add(b.id);
            pairsCreated++;
            break; // done with a — move on
          }
        }
      }
    }
  });

  run();
  return pairsCreated;
}
