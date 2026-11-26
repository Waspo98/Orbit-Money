package app.overbay.orbit.data

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.ImportBatchRecord
import app.overbay.orbit.data.model.MerchantLogoResolver
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.network.NormalizedCsvAccountDto
import app.overbay.orbit.data.network.NormalizedCsvImportRequestDto
import app.overbay.orbit.data.network.NormalizedCsvRuleDto
import app.overbay.orbit.data.network.NormalizedCsvTransactionDto
import app.overbay.orbit.data.network.importNormalizedCsvRemote
import app.overbay.orbit.domain.CsvImportPreview
import kotlinx.datetime.Clock

/**
 * Commits a normalized multi-app [CsvImportPreview] (Rocket Money, Mint, Monarch, YNAB, or Generic CSV)
 * in both Local and Self-Hosted modes, auto-creating missing accounts, preserving deduplication,
 * and recording an undoable [ImportBatchRecord].
 */
suspend fun OrbitRepository.importCsvPreview(
    fileName: String,
    preview: CsvImportPreview,
    fallbackAccountId: Long? = null,
    includeRules: Boolean = true
): Result<ImportBatchRecord> = runCatching {
    require(preview.transactions.isNotEmpty()) { "No new transactions found to import." }

    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val request = NormalizedCsvImportRequestDto(
            fileName = fileName,
            source = preview.detectedFormat.id,
            fallbackAccountId = fallbackAccountId,
            accounts = preview.accounts.map { acct ->
                NormalizedCsvAccountDto(
                    key = acct.key,
                    name = acct.name,
                    type = acct.type.name.lowercase(),
                    institution = acct.institution,
                    last4 = acct.last4
                )
            },
            transactions = preview.transactions.map { txn ->
                NormalizedCsvTransactionDto(
                    accountKey = preview.transactionAccountKeys[txn.id].orEmpty(),
                    date = txn.originalDate,
                    amountCents = txn.originalAmountCents,
                    merchant = txn.originalMerchant,
                    description = txn.originalDescription.orEmpty(),
                    categoryId = txn.originalCategoryId,
                    notes = txn.notes,
                    isIgnored = txn.isIgnored,
                    externalId = txn.externalId
                )
            },
            rules = if (includeRules) {
                preview.rules.map { rule ->
                    NormalizedCsvRuleDto(
                        originalName = rule.originalName,
                        customName = rule.customName,
                        friendlyRuleName = rule.friendlyRuleName
                    )
                }
            } else emptyList()
        )
        val remoteBatch = createApiClient().importNormalizedCsvRemote(request).getOrThrow()
        sync(force = true)
        return@runCatching remoteBatch
    }

    val curAccounts = accounts.value
    var nextAcctId = (curAccounts.maxOfOrNull { it.id } ?: 0L) + 1L
    var nextSortOrder = (curAccounts.maxOfOrNull { it.sortOrder } ?: 0) + 10
    val createdAccounts = mutableListOf<Account>()
    val accountIdByKey = mutableMapOf<String, Long>()
    val accountNameById = curAccounts.associate { it.id to it.name }.toMutableMap()

    for (candidate in preview.accounts) {
        val existing = curAccounts.firstOrNull { acct ->
            acct.name.equals(candidate.name, ignoreCase = true) &&
                (candidate.institution.isNullOrBlank() || acct.institution.equals(candidate.institution, ignoreCase = true))
        }
        if (existing != null) {
            accountIdByKey[candidate.key] = existing.id
        } else {
            val newAcct = Account(
                id = nextAcctId++,
                name = candidate.name,
                type = candidate.type,
                institution = candidate.institution,
                accountNumberLast4 = candidate.last4,
                isManual = true,
                currentBalanceCents = 0L,
                sortOrder = nextSortOrder
            )
            nextSortOrder += 10
            createdAccounts.add(newAcct)
            accountIdByKey[candidate.key] = newAcct.id
            accountNameById[newAcct.id] = newAcct.name
        }
    }

    var resolvedFallbackId = fallbackAccountId?.takeIf { id -> curAccounts.any { it.id == id } }
        ?: curAccounts.firstOrNull()?.id
        ?: createdAccounts.firstOrNull()?.id

    fun ensureFallbackAccount(): Long {
        resolvedFallbackId?.let { return it }
        val defaultAcct = Account(
            id = nextAcctId++,
            name = "Imported Checking",
            type = AccountType.CHECKING,
            isManual = true,
            currentBalanceCents = 0L,
            sortOrder = nextSortOrder
        )
        nextSortOrder += 10
        createdAccounts.add(defaultAcct)
        accountNameById[defaultAcct.id] = defaultAcct.name
        resolvedFallbackId = defaultAcct.id
        return defaultAcct.id
    }

    val curRules = rules.value
    var nextRuleId = (curRules.maxOfOrNull { it.id } ?: 0L) + 1L
    val existingRuleNames = curRules.map { it.name.lowercase() }.toSet()
    val createdRules = if (includeRules) {
        preview.rules.filter { it.friendlyRuleName.lowercase() !in existingRuleNames }.map { candidate ->
            TransactionRule(
                id = nextRuleId++,
                name = candidate.friendlyRuleName,
                conditions = listOf(
                    RuleCondition(
                        field = RuleConditionField.MERCHANT,
                        operator = RuleOperator.CONTAINS,
                        value = candidate.originalName
                    )
                ),
                actions = listOf(
                    RuleAction(
                        type = RuleActionType.RENAME,
                        value = candidate.customName,
                        textValue = candidate.customName
                    )
                ),
                enabled = true
            )
        }
    } else emptyList()

    val curTxns = transactions.value
    val existingExtIds = curTxns.mapNotNull { it.externalId }.toHashSet()
    val dedupedPreviewTxns = preview.transactions.filter { txn ->
        val extId = txn.externalId
        extId == null || existingExtIds.add(extId)
    }
    require(dedupedPreviewTxns.isNotEmpty()) { "All transactions in this import were already imported." }

    var nextTxId = (curTxns.maxOfOrNull { it.id } ?: 0L) + 1L
    val assignedTxns = dedupedPreviewTxns.map { txn ->
        val key = preview.transactionAccountKeys[txn.id]
        val acctId = (key?.let { accountIdByKey[it] }) ?: ensureFallbackAccount()
        txn.copy(
            id = nextTxId++,
            accountId = acctId,
            accountName = accountNameById[acctId] ?: txn.accountName
        )
    }

    val nextBatchId = (snapshot.value.importBatches.maxOfOrNull { it.id } ?: 0L) + 1L
    val batch = ImportBatchRecord(
        id = nextBatchId,
        source = preview.detectedFormat.id,
        fileName = fileName,
        importedAt = Clock.System.now().toString(),
        totalRows = preview.totalRowCount,
        importedCount = assignedTxns.size,
        duplicateCount = preview.duplicateRowCount + (preview.transactions.size - dedupedPreviewTxns.size),
        transactionIds = assignedTxns.map { it.id },
        createdAccountIds = createdAccounts.map { it.id },
        createdRuleIds = createdRules.map { it.id }
    )

    val updatedAccounts = curAccounts + createdAccounts
    val updatedTransactions = MerchantLogoResolver.reconcileLocalTransactions(
        curTxns + assignedTxns,
        updatedAccounts,
        preferences.value.merchantLogoOverrides
    )
    commitSnapshot(
        snapshot.value.copy(
            accounts = updatedAccounts,
            rules = curRules + createdRules,
            transactions = updatedTransactions,
            importBatches = snapshot.value.importBatches + batch
        ),
        immediate = true
    )
    batch
}
