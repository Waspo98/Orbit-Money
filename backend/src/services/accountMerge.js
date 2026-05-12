function cents(value) {
  return Number.isFinite(Number(value)) ? Number(value) : 0;
}

function profileValue(value) {
  return value !== null && value !== undefined && value !== '' ? value : null;
}

function preferTarget(targetValue, sourceValue) {
  const target = profileValue(targetValue);
  return target !== null ? target : profileValue(sourceValue);
}

function parseJsonArray(value) {
  if (typeof value !== 'string') return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function mergeJsonArrayText(targetValue, sourceValue) {
  const items = [];
  const seen = new Set();
  for (const item of [...parseJsonArray(targetValue), ...parseJsonArray(sourceValue)]) {
    const key = JSON.stringify(item);
    if (seen.has(key)) continue;
    seen.add(key);
    items.push(item);
  }
  return JSON.stringify(items);
}

function balanceSourceRank(source) {
  if (source === 'manual') return 2;
  if (source) return 1;
  return 0;
}

function shouldReplaceBalanceRecord(targetRecord, sourceRecord) {
  return balanceSourceRank(sourceRecord.source) > balanceSourceRank(targetRecord.source);
}

function allocationAmount(row, account) {
  if (row.allocation_type === 'percent') {
    const available = Math.max(0, cents(account.current_balance) - cents(row.reserve_amount));
    return Math.round((available * Number(row.allocation_percent || 0)) / 100);
  }
  return cents(row.allocation_amount);
}

function mergeAllocation(existing, sourceRow, targetAccount, sourceAccount) {
  const totalAmount =
    allocationAmount(existing, targetAccount) + allocationAmount(sourceRow, sourceAccount);
  const reserveAmount = Math.max(cents(existing.reserve_amount), cents(sourceRow.reserve_amount));
  const targetBasis = Math.max(0, cents(targetAccount.current_balance) - reserveAmount);

  if (
    existing.allocation_type === 'percent' &&
    sourceRow.allocation_type === 'percent' &&
    targetBasis > 0 &&
    totalAmount <= targetBasis
  ) {
    return {
      allocation_type: 'percent',
      allocation_percent: (totalAmount / targetBasis) * 100,
      allocation_amount: 0,
      reserve_amount: reserveAmount
    };
  }

  return {
    allocation_type: 'fixed',
    allocation_percent: 0,
    allocation_amount: totalAmount,
    reserve_amount: reserveAmount
  };
}

function mergeBalanceRecords(db, { householdId, sourceId, targetId }) {
  const summary = {
    balanceRecordsMoved: 0,
    balanceRecordConflicts: 0,
    balanceRecordsReplaced: 0
  };
  const sourceRecords = db
    .prepare(
      `SELECT *
         FROM account_balance_records
        WHERE household_id = ?
          AND account_id = ?
        ORDER BY record_date ASC, id ASC`
    )
    .all(householdId, sourceId);

  const findTargetRecord = db.prepare(
    `SELECT *
       FROM account_balance_records
      WHERE household_id = ?
        AND account_id = ?
        AND record_date = ?`
  );
  const moveRecord = db.prepare(
    `UPDATE account_balance_records
        SET account_id = ?,
            updated_at = datetime('now')
      WHERE id = ?
        AND household_id = ?`
  );
  const replaceTargetRecord = db.prepare(
    `UPDATE account_balance_records
        SET balance = ?,
            source = ?,
            updated_at = datetime('now')
      WHERE id = ?
        AND household_id = ?`
  );
  const deleteRecord = db.prepare(
    `DELETE FROM account_balance_records
      WHERE id = ?
        AND household_id = ?`
  );

  for (const sourceRecord of sourceRecords) {
    const targetRecord = findTargetRecord.get(householdId, targetId, sourceRecord.record_date);
    if (!targetRecord) {
      summary.balanceRecordsMoved += moveRecord.run(targetId, sourceRecord.id, householdId).changes;
      continue;
    }

    summary.balanceRecordConflicts += 1;
    if (shouldReplaceBalanceRecord(targetRecord, sourceRecord)) {
      replaceTargetRecord.run(
        sourceRecord.balance,
        sourceRecord.source || 'simplefin',
        targetRecord.id,
        householdId
      );
      summary.balanceRecordsReplaced += 1;
    }
    deleteRecord.run(sourceRecord.id, householdId);
  }

  const latestRecord = db
    .prepare(
      `SELECT record_date, balance, source
         FROM account_balance_records
        WHERE household_id = ?
          AND account_id = ?
        ORDER BY record_date DESC, id DESC
        LIMIT 1`
    )
    .get(householdId, targetId);

  if (latestRecord) {
    db.prepare(
      `UPDATE accounts
          SET current_balance = ?,
              is_manual = CASE WHEN ? = 'manual' THEN 1 ELSE is_manual END,
              updated_at = datetime('now')
        WHERE id = ?
          AND household_id = ?`
    ).run(latestRecord.balance, latestRecord.source, targetId, householdId);
  }

  return summary;
}

function mergeGoalAllocations(db, { householdId, sourceId, targetId, sourceAccount, targetAccount }) {
  const summary = {
    goalAllocationsMoved: 0,
    goalAllocationConflicts: 0
  };
  const sourceRows = db
    .prepare(
      `SELECT *
         FROM goal_account_allocations
        WHERE household_id = ?
          AND account_id = ?
        ORDER BY id ASC`
    )
    .all(householdId, sourceId);
  const findTarget = db.prepare(
    `SELECT *
       FROM goal_account_allocations
      WHERE household_id = ?
        AND goal_id = ?
        AND account_id = ?`
  );
  const moveAllocation = db.prepare(
    `UPDATE goal_account_allocations
        SET account_id = ?,
            updated_at = datetime('now')
      WHERE id = ?
        AND household_id = ?`
  );
  const updateAllocation = db.prepare(
    `UPDATE goal_account_allocations
        SET allocation_type = ?,
            allocation_percent = ?,
            allocation_amount = ?,
            reserve_amount = ?,
            updated_at = datetime('now')
      WHERE id = ?
        AND household_id = ?`
  );
  const deleteAllocation = db.prepare(
    `DELETE FROM goal_account_allocations
      WHERE id = ?
        AND household_id = ?`
  );

  for (const sourceRow of sourceRows) {
    const existing = findTarget.get(householdId, sourceRow.goal_id, targetId);
    if (!existing) {
      summary.goalAllocationsMoved += moveAllocation.run(targetId, sourceRow.id, householdId).changes;
      continue;
    }

    const merged = mergeAllocation(existing, sourceRow, targetAccount, sourceAccount);
    updateAllocation.run(
      merged.allocation_type,
      merged.allocation_percent,
      merged.allocation_amount,
      merged.reserve_amount,
      existing.id,
      householdId
    );
    deleteAllocation.run(sourceRow.id, householdId);
    summary.goalAllocationConflicts += 1;
  }

  return summary;
}

function mergeRetirementLinks(db, { householdId, sourceId, targetId }) {
  const summary = {
    retirementLinksMoved: 0,
    retirementLinkDuplicates: 0
  };
  const links = db
    .prepare(
      `SELECT *
         FROM household_retirement_accounts
        WHERE household_id = ?
          AND account_id = ?
        ORDER BY id ASC`
    )
    .all(householdId, sourceId);
  const findDuplicate = db.prepare(
    `SELECT id
       FROM household_retirement_accounts
      WHERE household_id = ?
        AND member_id = ?
        AND account_id = ?`
  );
  const moveLink = db.prepare(
    `UPDATE household_retirement_accounts
        SET account_id = ?,
            updated_at = datetime('now')
      WHERE id = ?
        AND household_id = ?`
  );
  const deleteLink = db.prepare(
    `DELETE FROM household_retirement_accounts
      WHERE id = ?
        AND household_id = ?`
  );

  for (const link of links) {
    const duplicate = findDuplicate.get(householdId, link.member_id, targetId);
    if (duplicate) {
      deleteLink.run(link.id, householdId);
      summary.retirementLinkDuplicates += 1;
      continue;
    }
    summary.retirementLinksMoved += moveLink.run(targetId, link.id, householdId).changes;
  }

  return summary;
}

function mergeCreditCardProfiles(db, { householdId, sourceId, targetId }) {
  const summary = {
    creditCardProfileMoved: false,
    creditCardProfileMerged: false
  };
  const findProfile = db.prepare(
    `SELECT *
       FROM credit_card_profiles
      WHERE household_id = ?
        AND account_id = ?`
  );
  const sourceProfile = findProfile.get(householdId, sourceId);
  if (!sourceProfile) return summary;

  const targetProfile = findProfile.get(householdId, targetId);
  if (!targetProfile) {
    db.prepare(
      `UPDATE credit_card_profiles
          SET account_id = ?,
              updated_at = datetime('now')
        WHERE id = ?
          AND household_id = ?`
    ).run(targetId, sourceProfile.id, householdId);
    summary.creditCardProfileMoved = true;
    return summary;
  }

  db.prepare(
    `UPDATE credit_card_profiles
        SET card_name = ?,
            issuer_name = ?,
            network = ?,
            image_url = ?,
            annual_fee = ?,
            annual_fee_post_date = ?,
            credit_limit = ?,
            authorized_users_json = ?,
            reward_categories_json = ?,
            benefits_json = ?,
            notes = ?,
            updated_at = datetime('now')
      WHERE id = ?
        AND household_id = ?`
  ).run(
    preferTarget(targetProfile.card_name, sourceProfile.card_name),
    preferTarget(targetProfile.issuer_name, sourceProfile.issuer_name),
    preferTarget(targetProfile.network, sourceProfile.network),
    preferTarget(targetProfile.image_url, sourceProfile.image_url),
    preferTarget(targetProfile.annual_fee, sourceProfile.annual_fee),
    preferTarget(targetProfile.annual_fee_post_date, sourceProfile.annual_fee_post_date),
    preferTarget(targetProfile.credit_limit, sourceProfile.credit_limit),
    mergeJsonArrayText(targetProfile.authorized_users_json, sourceProfile.authorized_users_json),
    mergeJsonArrayText(targetProfile.reward_categories_json, sourceProfile.reward_categories_json),
    mergeJsonArrayText(targetProfile.benefits_json, sourceProfile.benefits_json),
    preferTarget(targetProfile.notes, sourceProfile.notes),
    targetProfile.id,
    householdId
  );
  db.prepare(
    `DELETE FROM credit_card_profiles
      WHERE id = ?
        AND household_id = ?`
  ).run(sourceProfile.id, householdId);
  summary.creditCardProfileMerged = true;
  return summary;
}

export function mergeAccounts(db, { householdId, sourceId, targetId }) {
  const source = db
    .prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?')
    .get(sourceId, householdId);
  const target = db
    .prepare('SELECT * FROM accounts WHERE id = ? AND household_id = ?')
    .get(targetId, householdId);

  if (!source) {
    const err = new Error('Source account not found.');
    err.status = 404;
    throw err;
  }
  if (!target) {
    const err = new Error('Target account not found.');
    err.status = 404;
    throw err;
  }

  const run = db.transaction(() => {
    const summary = {
      transactionsMoved: db
        .prepare(
          `UPDATE transactions
              SET account_id = ?,
                  updated_at = datetime('now')
            WHERE account_id = ?
              AND household_id = ?`
        )
        .run(targetId, sourceId, householdId).changes,
      linkedGoalsMoved: db
        .prepare(
          `UPDATE goals
              SET linked_account_id = ?,
                  updated_at = datetime('now')
            WHERE linked_account_id = ?
              AND household_id = ?`
        )
        .run(targetId, sourceId, householdId).changes,
      upcomingItemsMoved: db
        .prepare(
          `UPDATE upcoming_items
              SET account_id = ?,
                  updated_at = datetime('now')
            WHERE account_id = ?
              AND household_id = ?`
        )
        .run(targetId, sourceId, householdId).changes
    };

    Object.assign(summary, mergeBalanceRecords(db, { householdId, sourceId, targetId }));
    Object.assign(
      summary,
      mergeGoalAllocations(db, {
        householdId,
        sourceId,
        targetId,
        sourceAccount: source,
        targetAccount: target
      })
    );
    Object.assign(summary, mergeRetirementLinks(db, { householdId, sourceId, targetId }));
    Object.assign(summary, mergeCreditCardProfiles(db, { householdId, sourceId, targetId }));

    db.prepare('DELETE FROM accounts WHERE id = ? AND household_id = ?').run(sourceId, householdId);

    const accountUpdates = [];
    const accountValues = [];
    if (source.estimated_value != null && target.estimated_value == null) {
      accountUpdates.push('estimated_value = ?');
      accountValues.push(source.estimated_value);
    }
    if (source.simplefin_account_id && !target.simplefin_account_id) {
      accountUpdates.push('simplefin_account_id = ?');
      accountValues.push(source.simplefin_account_id);
    }
    if (accountUpdates.length > 0) {
      accountUpdates.push("updated_at = datetime('now')");
      db.prepare(
        `UPDATE accounts
            SET ${accountUpdates.join(', ')}
          WHERE id = ?
            AND household_id = ?`
      ).run(...accountValues, targetId, householdId);
    }

    return summary;
  });

  return {
    ...run(),
    mergedSourceName: source.name,
    intoTargetName: target.name
  };
}
