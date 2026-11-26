package app.overbay.orbit.data

import app.overbay.orbit.data.model.LogoOverrideScope
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.MerchantLogoResolver
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.data.network.overrideMerchantLogo
import app.overbay.orbit.data.network.reportMerchantLogoStatus
import app.overbay.orbit.data.network.searchMerchantLogos

internal suspend fun OrbitRepository.reportMerchantLogoStatusImpl(merchantKey: String, status: String) {
    if (merchantKey.isBlank()) return
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        runCatching { createApiClient().reportMerchantLogoStatus(merchantKey, status) }
    }
    if (status == "loaded") {
        // "candidate" and "loaded" render identically in UI; avoid cloning all transactions
        // and triggering full workspace snapshot emissions/disk flushes as each logo finishes loading.
        return
    }
    val updatedList = transactions.value.map { txn ->
        val currentLogo = txn.merchantLogo
        if (currentLogo?.merchantKey == merchantKey && currentLogo.status == "candidate") {
            txn.copy(merchantLogo = currentLogo.copy(status = status, url = if (status == "failed") null else currentLogo.url))
        } else txn
    }
    if (updatedList != transactions.value) commitSnapshot(snapshot.value.copy(transactions = updatedList))
}

internal suspend fun OrbitRepository.searchMerchantLogosImpl(transactionId: Long, query: String): Result<MerchantLogoSearchResponseDto> = runCatching {
    if (storageMode.value != StorageMode.SELF_HOSTED) {
        MerchantLogoSearchResponseDto(configured = false, candidates = emptyList())
    } else {
        createApiClient().searchMerchantLogos(transactionId, query).getOrThrow()
    }
}

internal suspend fun OrbitRepository.overrideMerchantLogoImpl(
    transactionId: Long,
    logoUrl: String? = null,
    useCategoryIcon: Boolean = false,
    scope: LogoOverrideScope? = null
): Result<MerchantLogo> = runCatching {
    val currentTxns = transactions.value
    val target = currentTxns.find { it.id == transactionId }
        ?: throw IllegalStateException("Transaction not found.")
    val accountsById = accounts.value.associateBy { it.id }
    val txnsById = currentTxns.associateBy { it.id }
    val keys = MerchantLogoResolver.resolveScopeKeys(target, accountsById, txnsById)
    val effectiveScope = scope ?: MerchantLogoResolver.defaultScopeForTransaction(target)

    val resolvedTargetKey = when (effectiveScope) {
        LogoOverrideScope.TRANSACTION -> keys.txnKey
        LogoOverrideScope.INSTITUTION -> keys.scopedKey ?: keys.txnKey
        LogoOverrideScope.GLOBAL -> keys.globalTargetKey
    }?.takeIf { it.isNotBlank() }
        ?: target.resolvedMerchantKey.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("This merchant does not have a logo entry to update yet.")

    val cleanedName = keys.merchantName.ifBlank { target.effectiveMerchant.ifBlank { target.originalMerchant } }
    val updatedLogo = if (storageMode.value == StorageMode.SELF_HOSTED) {
        val client = createApiClient()
        client.overrideMerchantLogo(
            merchantKey = resolvedTargetKey,
            logoUrl = logoUrl,
            useCategoryIcon = useCategoryIcon,
            transactionId = transactionId,
            scope = effectiveScope.wireValue,
            merchantName = cleanedName.ifBlank { null }
        ).getOrThrow().toDomain()?.let { domainLogo ->
            if (domainLogo.scope.isNullOrBlank()) domainLogo.copy(scope = effectiveScope.wireValue) else domainLogo
        } ?: MerchantLogo(
            merchantKey = resolvedTargetKey,
            merchantName = cleanedName,
            url = if (useCategoryIcon) null else logoUrl?.trim(),
            status = if (useCategoryIcon) "hidden" else "manual",
            scope = effectiveScope.wireValue
        )
    } else {
        MerchantLogo(
            merchantKey = resolvedTargetKey,
            merchantName = cleanedName,
            providerQuery = cleanedName,
            url = if (useCategoryIcon) null else logoUrl?.trim(),
            status = if (useCategoryIcon) "hidden" else "manual",
            scope = effectiveScope.wireValue
        )
    }

    var nextPreferences = preferences.value
    if (storageMode.value != StorageMode.SELF_HOSTED) {
        val overrides = nextPreferences.merchantLogoOverrides.toMutableMap()
        if (effectiveScope == LogoOverrideScope.INSTITUTION) {
            keys.txnKey?.takeIf { it != updatedLogo.merchantKey }?.let { overrides.remove(it) }
        } else if (effectiveScope == LogoOverrideScope.GLOBAL) {
            keys.txnKey?.takeIf { it != updatedLogo.merchantKey }?.let { overrides.remove(it) }
            keys.scopedKey?.takeIf { it != updatedLogo.merchantKey }?.let { overrides.remove(it) }
        }
        overrides[updatedLogo.merchantKey] = updatedLogo
        nextPreferences = nextPreferences.copy(merchantLogoOverrides = overrides)
    }

    val updatedList = currentTxns.map { txn ->
        if (txn.id == transactionId) {
            txn.copy(merchantLogo = updatedLogo)
        } else {
            val existingScope = txn.merchantLogo?.scope
            val existingKey = txn.merchantLogo?.merchantKey.orEmpty()
            val isTxnPinned = (txn.merchantLogo?.status == "manual" || txn.merchantLogo?.status == "hidden") &&
                (existingScope == LogoOverrideScope.TRANSACTION.wireValue || existingKey.startsWith("txn:"))
            val isInstPinned = (txn.merchantLogo?.status == "manual" || txn.merchantLogo?.status == "hidden") &&
                (existingScope == LogoOverrideScope.INSTITUTION.wireValue || "@inst:" in existingKey)

            val shouldUpdate = when (effectiveScope) {
                LogoOverrideScope.TRANSACTION -> false
                LogoOverrideScope.INSTITUTION -> {
                    !isTxnPinned &&
                        MerchantLogoResolver.resolveScopeKeys(txn, accountsById, txnsById).scopedKey == updatedLogo.merchantKey
                }
                LogoOverrideScope.GLOBAL -> {
                    val isDifferentInstPin = isInstPinned && existingKey != keys.scopedKey
                    !isTxnPinned && !isDifferentInstPin &&
                        MerchantLogoResolver.resolveScopeKeys(txn, accountsById, txnsById).globalTargetKey == updatedLogo.merchantKey
                }
            }
            if (shouldUpdate) txn.copy(merchantLogo = updatedLogo) else txn
        }
    }

    commitSnapshot(
        snapshot.value.copy(
            transactions = updatedList,
            preferences = nextPreferences
        )
    )
    updatedLogo
}

internal fun OrbitRepository.resolveLocalTransactionLogoIfNeeded(
    transaction: Transaction,
    currentTransactions: List<Transaction> = transactions.value
): Transaction {
    if (storageMode.value == StorageMode.SELF_HOSTED) return transaction
    val resolvedLogo = MerchantLogoResolver.resolveLocalLogo(
        transaction = transaction,
        accountsById = accounts.value.associateBy { it.id },
        transactionsById = currentTransactions.associateBy { it.id },
        overrides = preferences.value.merchantLogoOverrides
    )
    return if (resolvedLogo != transaction.merchantLogo) transaction.copy(merchantLogo = resolvedLogo) else transaction
}

internal fun OrbitRepository.reconcileLocalTransactionsIfNeeded(
    txns: List<Transaction>
): List<Transaction> =
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        txns
    } else {
        MerchantLogoResolver.reconcileLocalTransactions(txns, accounts.value, preferences.value.merchantLogoOverrides)
    }

internal fun reconcileSnapshotLogos(snapshot: OrbitWorkspaceSnapshot): OrbitWorkspaceSnapshot {
    val reconciled = MerchantLogoResolver.reconcileLocalTransactions(
        snapshot.transactions,
        snapshot.accounts,
        snapshot.preferences.merchantLogoOverrides
    )
    return if (reconciled !== snapshot.transactions) snapshot.copy(transactions = reconciled) else snapshot
}

