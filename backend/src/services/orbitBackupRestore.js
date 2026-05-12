const RESTORE_DELETE_ORDER = [
  'upcoming_occurrences',
  'upcoming_dismissed_suggestions',
  'upcoming_items',
  'import_batch_items',
  'import_batches',
  'household_shares',
  'household_memberships',
  'simplefin_config',
  'sync_log',
  'app_settings',
  'user_preferences',
  'household_retirement_accounts',
  'household_income_records',
  'household_members',
  'account_balance_records',
  'goal_account_allocations',
  'goals',
  'budgets',
  'rules',
  'transactions',
  'credit_card_profiles',
  'categories',
  'accounts'
];

const RESTORE_INSERT_ORDER = [
  'accounts',
  'credit_card_profiles',
  'categories',
  'rules',
  'transactions',
  'budgets',
  'goals',
  'goal_account_allocations',
  'account_balance_records',
  'household_members',
  'household_income_records',
  'household_retirement_accounts',
  'app_settings',
  'upcoming_items',
  'upcoming_dismissed_suggestions',
  'upcoming_occurrences',
  'import_batches',
  'import_batch_items'
];

const RULE_SOURCE_FIELDS = [
  'edited_merchant_source',
  'edited_category_id_source',
  'edited_is_transfer_source',
  'edited_is_ignored_source',
  'edited_mha_eligible_source'
];

const IMPORT_BATCH_ROW_TABLES = new Set([
  'accounts',
  'credit_card_profiles',
  'categories',
  'transactions',
  'rules',
  'budgets',
  'goals',
  'goal_account_allocations',
  'account_balance_records',
  'household_members',
  'household_income_records',
  'household_retirement_accounts',
  'upcoming_items',
  'upcoming_occurrences'
]);

function tableColumns(db, table) {
  return db.pragma(`table_info(${table})`).map((column) => column.name);
}

function tableExists(db, table) {
  return Boolean(
    db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").get(table)
  );
}

function asPositiveInteger(value) {
  const number = Number(value);
  return Number.isInteger(number) && number > 0 ? number : null;
}

function hasOwn(row, column) {
  return Object.prototype.hasOwnProperty.call(row, column);
}

function createRestoreContext() {
  return {
    columnCache: new Map(),
    idMaps: new Map(),
    deferredTransactionPairs: []
  };
}

function columnsFor(db, context, table) {
  if (!context.columnCache.has(table)) {
    context.columnCache.set(table, tableColumns(db, table));
  }
  return context.columnCache.get(table);
}

function idMapFor(context, table) {
  if (!context.idMaps.has(table)) context.idMaps.set(table, new Map());
  return context.idMaps.get(table);
}

function rememberMappedId(context, table, oldId, newId) {
  const sourceId = asPositiveInteger(oldId);
  const targetId = asPositiveInteger(newId);
  if (!sourceId || !targetId) return;
  idMapFor(context, table).set(sourceId, targetId);
}

function mappedId(context, table, oldId) {
  const sourceId = asPositiveInteger(oldId);
  if (!sourceId) return null;
  return idMapFor(context, table).get(sourceId) ?? null;
}

function mapOptionalId(context, table, oldId) {
  if (oldId === null || oldId === undefined || oldId === '') return null;
  return mappedId(context, table, oldId);
}

function mapRequiredId(context, table, oldId) {
  return mappedId(context, table, oldId);
}

function safeJsonParse(value) {
  if (typeof value !== 'string') return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function remapRuleScalarValue(context, table, value) {
  const next = mappedId(context, table, value);
  return next ?? null;
}

function remapRuleConditions(value, context) {
  const conditions = safeJsonParse(value);
  if (!Array.isArray(conditions)) return value;

  const remapped = conditions.map((condition) => {
    if (!condition || typeof condition !== 'object') return condition;
    if (condition.field === 'account_id') {
      return { ...condition, value: remapRuleScalarValue(context, 'accounts', condition.value) };
    }
    if (condition.field === 'category_id') {
      return { ...condition, value: remapRuleScalarValue(context, 'categories', condition.value) };
    }
    return condition;
  });

  return JSON.stringify(remapped);
}

function remapRuleActions(value, context) {
  const actions = safeJsonParse(value);
  if (!Array.isArray(actions)) return value;

  const remapped = actions.map((action) => {
    if (!action || typeof action !== 'object' || action.type !== 'categorize') return action;
    return { ...action, value: remapRuleScalarValue(context, 'categories', action.value) };
  });

  return JSON.stringify(remapped);
}

function remapRuleSource(value, context) {
  if (typeof value !== 'string' || !value.startsWith('rule:')) return value ?? null;
  const nextRuleId = mappedId(context, 'rules', value.slice('rule:'.length));
  return nextRuleId ? `rule:${nextRuleId}` : null;
}

function remapImportPreviewJson(value, context) {
  const preview = safeJsonParse(value);
  if (!preview || typeof preview !== 'object') return value;

  const next = { ...preview };

  if (Array.isArray(next.accounts)) {
    next.accounts = next.accounts.map((account) => {
      if (!account || typeof account !== 'object') return account;
      if (!hasOwn(account, 'existingAccountId')) return account;
      return {
        ...account,
        existingAccountId: account.existingAccountId
          ? mappedId(context, 'accounts', account.existingAccountId)
          : null
      };
    });
  }

  if (Array.isArray(next.rules)) {
    next.rules = next.rules.map((rule) => {
      if (!rule || typeof rule !== 'object') return rule;
      if (!hasOwn(rule, 'existingRuleId')) return rule;
      return {
        ...rule,
        existingRuleId: rule.existingRuleId ? mappedId(context, 'rules', rule.existingRuleId) : null
      };
    });
  }

  if (Array.isArray(next.transactions)) {
    next.transactions = next.transactions.map((transaction) => {
      if (!transaction || typeof transaction !== 'object') return transaction;
      if (!hasOwn(transaction, 'categoryId')) return transaction;
      return {
        ...transaction,
        categoryId: transaction.categoryId
          ? mappedId(context, 'categories', transaction.categoryId)
          : null
      };
    });
  }

  return JSON.stringify(next);
}

function remapImportBatchItem(row, context) {
  const importBatchId = mapRequiredId(context, 'import_batches', row.import_batch_id);
  if (!importBatchId) return null;

  if (!IMPORT_BATCH_ROW_TABLES.has(row.table_name)) {
    return null;
  }

  const rowId = mapRequiredId(context, row.table_name, row.row_id);
  if (!rowId) return null;

  return {
    ...row,
    import_batch_id: importBatchId,
    row_id: rowId
  };
}

function transformRestoreRow(table, row, householdId, context) {
  const next = { ...row };

  switch (table) {
    case 'rules':
      if (hasOwn(next, 'conditions')) next.conditions = remapRuleConditions(next.conditions, context);
      if (hasOwn(next, 'actions')) next.actions = remapRuleActions(next.actions, context);
      break;

    case 'transactions': {
      const accountId = mapRequiredId(context, 'accounts', next.account_id);
      if (!accountId) return null;
      next.account_id = accountId;
      if (hasOwn(next, 'category_id')) next.category_id = mapOptionalId(context, 'categories', next.category_id);
      if (hasOwn(next, 'edited_category_id')) {
        next.edited_category_id = mapOptionalId(context, 'categories', next.edited_category_id);
      }
      for (const field of RULE_SOURCE_FIELDS) {
        if (hasOwn(next, field)) next[field] = remapRuleSource(next[field], context);
      }
      if (hasOwn(next, 'transfer_pair_id')) {
        context.deferredTransactionPairs.push({
          oldId: next.id,
          oldPairId: next.transfer_pair_id
        });
        next.transfer_pair_id = null;
      }
      break;
    }

    case 'budgets': {
      const categoryId = mapRequiredId(context, 'categories', next.category_id);
      if (!categoryId) return null;
      next.category_id = categoryId;
      break;
    }

    case 'goals':
      if (hasOwn(next, 'linked_account_id')) {
        next.linked_account_id = mapOptionalId(context, 'accounts', next.linked_account_id);
      }
      break;

    case 'goal_account_allocations': {
      const goalId = mapRequiredId(context, 'goals', next.goal_id);
      const accountId = mapRequiredId(context, 'accounts', next.account_id);
      if (!goalId || !accountId) return null;
      next.goal_id = goalId;
      next.account_id = accountId;
      break;
    }

    case 'account_balance_records': {
      const accountId = mapRequiredId(context, 'accounts', next.account_id);
      if (!accountId) return null;
      next.account_id = accountId;
      break;
    }

    case 'credit_card_profiles': {
      const accountId = mapRequiredId(context, 'accounts', next.account_id);
      if (!accountId) return null;
      next.account_id = accountId;
      break;
    }

    case 'household_income_records': {
      const memberId = mapRequiredId(context, 'household_members', next.member_id);
      if (!memberId) return null;
      next.member_id = memberId;
      break;
    }

    case 'household_retirement_accounts': {
      const memberId = mapRequiredId(context, 'household_members', next.member_id);
      const accountId = mapRequiredId(context, 'accounts', next.account_id);
      if (!memberId || !accountId) return null;
      next.member_id = memberId;
      next.account_id = accountId;
      break;
    }

    case 'upcoming_items':
      if (hasOwn(next, 'category_id')) next.category_id = mapOptionalId(context, 'categories', next.category_id);
      if (hasOwn(next, 'account_id')) next.account_id = mapOptionalId(context, 'accounts', next.account_id);
      if (hasOwn(next, 'source_transaction_id')) {
        next.source_transaction_id = mapOptionalId(
          context,
          'transactions',
          next.source_transaction_id
        );
      }
      break;

    case 'upcoming_occurrences': {
      const itemId = mapRequiredId(context, 'upcoming_items', next.upcoming_item_id);
      if (!itemId) return null;
      next.upcoming_item_id = itemId;
      if (hasOwn(next, 'matched_transaction_id')) {
        next.matched_transaction_id = mapOptionalId(
          context,
          'transactions',
          next.matched_transaction_id
        );
      }
      break;
    }

    case 'import_batches':
      if (hasOwn(next, 'preview_json')) {
        next.preview_json = remapImportPreviewJson(next.preview_json, context);
      }
      break;

    case 'import_batch_items':
      return remapImportBatchItem(next, context);

    default:
      break;
  }

  if (hasOwn(next, 'household_id')) next.household_id = householdId;
  return next;
}

function insertRestoreRows(db, table, rows, householdId, context) {
  if (!Array.isArray(rows) || rows.length === 0) return 0;
  if (!tableExists(db, table)) return 0;
  const columns = columnsFor(db, context, table);
  const available = new Set(columns);
  const hasIdColumn = available.has('id');
  let inserted = 0;

  for (const row of rows) {
    const transformed = transformRestoreRow(table, row, householdId, context);
    if (!transformed) continue;
    if (available.has('household_id')) transformed.household_id = householdId;

    const rowColumns = columns.filter((column) => {
      if (column === 'id' && hasIdColumn) return false;
      return hasOwn(transformed, column);
    });
    if (rowColumns.length === 0) continue;

    const quoted = rowColumns.map((column) => `"${column}"`).join(', ');
    const placeholders = rowColumns.map(() => '?').join(', ');
    const values = rowColumns.map((column) => transformed[column]);
    const result = db.prepare(`INSERT INTO ${table} (${quoted}) VALUES (${placeholders})`).run(...values);
    inserted += 1;

    if (hasIdColumn) {
      rememberMappedId(context, table, row.id, result.lastInsertRowid);
    }
  }

  return inserted;
}

function applyDeferredTransactionPairs(db, householdId, context) {
  if (context.deferredTransactionPairs.length === 0) return;
  const update = db.prepare(
    `UPDATE transactions
        SET transfer_pair_id = ?
      WHERE id = ?
        AND household_id = ?`
  );

  for (const pair of context.deferredTransactionPairs) {
    const newId = mappedId(context, 'transactions', pair.oldId);
    const newPairId = mapOptionalId(context, 'transactions', pair.oldPairId);
    if (!newId || !newPairId) continue;
    update.run(newPairId, newId, householdId);
  }
}

function restoreMemberships(db, backup, householdId, currentUserId) {
  const memberships = Array.isArray(backup.memberships) ? backup.memberships : [];
  const userIdMap = new Map();
  const findUser = db.prepare(
    `SELECT id FROM users
      WHERE (? IS NOT NULL AND lower(email) = lower(?))
         OR (? IS NOT NULL AND username = ?)
      LIMIT 1`
  );
  const upsert = db.prepare(
    `INSERT INTO household_memberships (household_id, user_id, role, access_level)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(household_id, user_id) DO UPDATE SET
       role = excluded.role,
       access_level = excluded.access_level,
       updated_at = datetime('now')`
  );

  let restored = 0;
  for (const membership of memberships) {
    const matched = findUser.get(
      membership.email || null,
      membership.email || null,
      membership.username || null,
      membership.username || null
    );
    if (!matched) continue;

    const oldUserId = asPositiveInteger(membership.user_id);
    if (oldUserId) userIdMap.set(oldUserId, matched.id);

    if (currentUserId && matched.id === currentUserId) continue;
    const role = membership.role === 'admin' ? 'admin' : membership.role === 'owner' ? 'admin' : 'member';
    const accessLevel = membership.access_level === 'read' ? 'read' : 'write';
    upsert.run(householdId, matched.id, role, accessLevel);
    restored += 1;
  }

  if (currentUserId) {
    db.prepare(
      `INSERT INTO household_memberships (household_id, user_id, role, access_level)
       VALUES (?, ?, 'owner', 'write')
       ON CONFLICT(household_id, user_id) DO UPDATE SET
         role = 'owner',
         access_level = 'write',
         updated_at = datetime('now')`
    ).run(householdId, currentUserId);
  }

  return { restored, userIdMap };
}

function restoreUserPreferences(db, backup, householdId, userIdMap) {
  const rows = Array.isArray(backup.tables?.user_preferences)
    ? backup.tables.user_preferences
    : [];
  if (rows.length === 0) return 0;

  const upsert = db.prepare(
    `INSERT INTO user_preferences (household_id, user_id, key, value_json, updated_at)
     VALUES (?, ?, ?, ?, COALESCE(?, datetime('now')))
     ON CONFLICT(household_id, user_id, key) DO UPDATE SET
       value_json = excluded.value_json,
       updated_at = excluded.updated_at`
  );

  let restored = 0;
  for (const row of rows) {
    const userId = userIdMap.get(asPositiveInteger(row.user_id));
    if (!userId) continue;
    if (!row.key || typeof row.value_json !== 'string') continue;
    upsert.run(householdId, userId, row.key, row.value_json, row.updated_at || null);
    restored += 1;
  }

  return restored;
}

export function restoreOrbitBackup(db, backup, householdId, currentUserId) {
  const ownerUserId = asPositiveInteger(currentUserId);
  if (!ownerUserId) {
    throw new Error('Restore requires a current household owner user.');
  }

  const run = db.transaction(() => {
    db.exec('PRAGMA defer_foreign_keys = ON');

    for (const table of RESTORE_DELETE_ORDER) {
      if (!tableExists(db, table)) continue;
      db.prepare(`DELETE FROM ${table} WHERE household_id = ?`).run(householdId);
    }

    db.prepare(
      `UPDATE households
          SET name = ?,
              default_currency = ?,
              updated_at = datetime('now')
        WHERE id = ?`
    ).run(
      backup.household?.name || 'Orbit Household',
      backup.household?.default_currency || 'USD',
      householdId
    );

    const context = createRestoreContext();
    const inserted = {};
    for (const table of RESTORE_INSERT_ORDER) {
      inserted[table] = insertRestoreRows(db, table, backup.tables?.[table] || [], householdId, context);
    }
    applyDeferredTransactionPairs(db, householdId, context);

    const { restored: membershipsRestored, userIdMap } = restoreMemberships(
      db,
      backup,
      householdId,
      ownerUserId
    );
    inserted.user_preferences = restoreUserPreferences(db, backup, householdId, userIdMap);

    return { inserted, membershipsRestored };
  });

  return run();
}
