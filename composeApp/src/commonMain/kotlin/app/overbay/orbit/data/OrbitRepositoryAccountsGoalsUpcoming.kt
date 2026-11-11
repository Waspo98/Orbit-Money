package app.overbay.orbit.data

import app.overbay.orbit.data.model.*
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.data.network.*
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.RuleMatcherEngine

// =============================================================================
// Accounts Extension Methods
// =============================================================================

suspend fun OrbitRepository.setAccountArchived(accountId: Long, archived: Boolean) {
    val current = accounts.value
    val target = current.find { it.id == accountId } ?: return
    val updated = target.copy(isArchived = archived)
    commitSnapshot(snapshot.value.copy(accounts = current.map { if (it.id == accountId) updated else it }))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val client = createApiClient()
        if (archived) client.archiveAccount(accountId) else client.unarchiveAccount(accountId)
    }
}

suspend fun OrbitRepository.reorderAccounts(orderedIds: List<Long>) {
    val orderMap = orderedIds.mapIndexed { index, id -> id to index }.toMap()
    val updated = accounts.value.map { acct ->
        orderMap[acct.id]?.let { acct.copy(sortOrder = it) } ?: acct
    }.sortedBy { it.sortOrder }
    commitSnapshot(snapshot.value.copy(accounts = updated))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().reorderAccounts(orderedIds)
    }
}

suspend fun OrbitRepository.saveCreditCardProfile(
    accountId: Long,
    profile: CreditCardProfile,
    accountName: String? = null,
    accountInstitution: String? = null
): Result<CreditCardProfile> {
    val previousAccount = accounts.value.find { it.id == accountId }
    val previousProfile = snapshot.value.creditCardProfiles.find { it.accountId == accountId }
    applyLocalCardProfile(accountId, profile, accountName, accountInstitution)
    if (storageMode.value != StorageMode.SELF_HOSTED) return Result.success(profile)
    return createApiClient().saveCreditCardProfileRemote(accountId, accountName, accountInstitution, profile)
        .onSuccess { saved -> applyLocalCardProfile(accountId, saved, accountName, accountInstitution) }
        .onFailure { revertLocalCardProfile(accountId, previousAccount, previousProfile) }
}

suspend fun OrbitRepository.refreshCardCatalog() {
    if (storageMode.value != StorageMode.SELF_HOSTED) return
    createApiClient().fetchCardCatalogRemote().onSuccess { items ->
        app.overbay.orbit.domain.CreditCardTemplates.updateFromRemote(items)
    }
}

suspend fun OrbitRepository.setCardBenefitUsage(
    accountId: Long,
    benefitId: String,
    periodKey: String,
    amountCents: Cents?,
    usedOn: String? = null
): Result<CreditCardProfile> {
    val previousAccount = accounts.value.find { it.id == accountId }
    val rawPreviousProfile = snapshot.value.creditCardProfiles.find { it.accountId == accountId }
        ?: previousAccount?.creditCardProfile
        ?: return Result.failure(IllegalStateException("No credit card profile found for account $accountId"))
    val upgradedProfile = CreditCardBenefitEngine.upgradeProfile(rawPreviousProfile, LocalDateUtils.todayLocalDate())
    val updatedBenefits = upgradedProfile.benefits.map { b ->
        if (b.id == benefitId) CreditCardBenefitEngine.setUsage(b, periodKey, amountCents, usedOn) else b
    }
    val localProfile = upgradedProfile.copy(benefits = updatedBenefits)
    applyLocalCardProfile(accountId, localProfile, null, null)
    if (storageMode.value != StorageMode.SELF_HOSTED) return Result.success(localProfile)
    return createApiClient().saveBenefitUsageRemote(accountId, benefitId, periodKey, amountCents, usedOn)
        .onSuccess { saved -> applyLocalCardProfile(accountId, saved, null, null) }
        .onFailure { revertLocalCardProfile(accountId, previousAccount, rawPreviousProfile) }
}

private suspend fun OrbitRepository.applyLocalCardProfile(
    accountId: Long,
    profile: CreditCardProfile,
    accountName: String?,
    accountInstitution: String?
) {
    val currentAccounts = accounts.value
    val targetAccount = currentAccounts.find { it.id == accountId }
    val updatedAccount = targetAccount?.copy(
        creditCardProfile = profile,
        name = accountName ?: targetAccount.name,
        institution = accountInstitution ?: targetAccount.institution
    )
    val updatedAccounts = if (updatedAccount != null) currentAccounts.map { if (it.id == accountId) updatedAccount else it } else currentAccounts
    val currentProfiles = snapshot.value.creditCardProfiles
    val profileExists = currentProfiles.any { it.accountId == accountId || (it.id == profile.id && profile.id != 0L) }
    val updatedProfiles = if (profileExists) {
        currentProfiles.map { if (it.accountId == accountId || (it.id == profile.id && profile.id != 0L)) profile else it }
    } else currentProfiles + profile
    commitSnapshot(snapshot.value.copy(accounts = updatedAccounts, creditCardProfiles = updatedProfiles))
}

private suspend fun OrbitRepository.revertLocalCardProfile(
    accountId: Long,
    previousAccount: Account?,
    previousProfile: CreditCardProfile?
) {
    val currentAccounts = accounts.value
    val updatedAccounts = if (previousAccount != null) {
        currentAccounts.map { if (it.id == accountId) previousAccount else it }
    } else currentAccounts.map { if (it.id == accountId) it.copy(creditCardProfile = null) else it }
    val currentProfiles = snapshot.value.creditCardProfiles
    val updatedProfiles = if (previousProfile != null) {
        if (currentProfiles.any { it.accountId == accountId }) currentProfiles.map { if (it.accountId == accountId) previousProfile else it }
        else currentProfiles + previousProfile
    } else currentProfiles.filterNot { it.accountId == accountId }
    commitSnapshot(snapshot.value.copy(accounts = updatedAccounts, creditCardProfiles = updatedProfiles))
}

suspend fun OrbitRepository.addBalanceRecord(accountId: Long, date: String, balanceCents: Cents): Result<BalanceRecord> = runCatching {
    val currentAccount = accounts.value.find { it.id == accountId } ?: throw NoSuchElementException("Account $accountId not found")
    val previousBalanceCents = currentAccount.currentBalanceCents
    val existingRecords = snapshot.value.balanceRecords
    val nextId = (existingRecords.maxOfOrNull { it.id } ?: 0L) + 1L
    val newRecord = BalanceRecord(id = nextId, accountId = accountId, recordDate = date, balanceCents = balanceCents, source = BalanceRecordSource.MANUAL)
    val updatedRecords = existingRecords.filterNot { it.accountId == accountId && it.recordDate == date } + newRecord
    val latestDate = updatedRecords.filter { it.accountId == accountId }.maxOfOrNull { it.recordDate } ?: date
    val updatedAccount = if (date >= latestDate) currentAccount.copy(currentBalanceCents = balanceCents) else currentAccount
    val updatedAccounts = accounts.value.map { if (it.id == accountId) updatedAccount else it }
    commitSnapshot(snapshot.value.copy(balanceRecords = updatedRecords, accounts = updatedAccounts))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().addAccountBalanceRecord(accountId, date, balanceCents, previousBalanceCents).getOrThrow()
    } else newRecord
}

suspend fun OrbitRepository.unlinkSimpleFinAccount(accountId: Long) {
    val acct = accounts.value.find { it.id == accountId } ?: return
    saveAccount(acct.copy(simplefinAccountId = null, isManual = true))
}

fun OrbitRepository.accountTransactionCount(accountId: Long): Int {
    val acct = accounts.value.find { it.id == accountId }
    val serverCount = acct?.transactionCount ?: 0
    val localCount = transactions.value.count { it.accountId == accountId }
    return maxOf(serverCount, localCount)
}

// =============================================================================
// Budgets & Goals Extension Methods
// =============================================================================

suspend fun OrbitRepository.getActiveBudgetMonths(): List<String> {
    val localMonths = transactions.value.map { it.effectiveDate.take(7) }.filter { it.length == 7 }.toSet()
    val remoteMonths = if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().getActiveBudgetMonths().getOrNull()?.toSet() ?: emptySet()
    } else emptySet()
    return (localMonths + remoteMonths).sortedDescending()
}

private fun GoalAllocation.resolvedAmountCents(accountBalanceCents: Cents): Cents =
    if (allocationType == GoalAllocationType.PERCENT) {
        MoneyUtils.multiplyPercent((accountBalanceCents - reserveAmountCents).coerceAtLeast(0L), allocationPercent)
    } else allocationAmountCents

suspend fun OrbitRepository.saveGoalWithAllocations(
    goal: Goal,
    stealFromOthers: Boolean = false
): Result<Goal> = runCatching {
    val currentGoals = goals.value
    val accountsMap = accounts.value.associateBy { it.id }

    val computedCurrentAmountCents = if (goal.allocations.isNotEmpty()) {
        goal.allocations.sumOf { alloc -> alloc.resolvedAmountCents(accountsMap[alloc.accountId]?.currentBalanceCents ?: 0L) }
    } else goal.currentAmountCents

    val updatedGoal = goal.copy(currentAmountCents = computedCurrentAmountCents)

    val exists = currentGoals.any { it.id == goal.id && goal.id != 0L }
    val assignedId = if (exists) goal.id else if (goal.id == 0L) (currentGoals.maxOfOrNull { it.id } ?: 0L) + 1L else goal.id
    val savedGoal = updatedGoal.copy(id = assignedId)
    val baseList = if (exists) currentGoals.map { if (it.id == goal.id) savedGoal else it } else currentGoals + savedGoal

    val updatedGoalsList = if (stealFromOthers && savedGoal.allocations.isNotEmpty()) {
        var workingGoals = baseList
        for (newAlloc in savedGoal.allocations) {
            val acctId = newAlloc.accountId
            val acctBalance = (accountsMap[acctId]?.currentBalanceCents ?: 0L).coerceAtLeast(0L)
            val newAllocCents = newAlloc.resolvedAmountCents(acctBalance)
            val otherRows = workingGoals.filter { it.id != savedGoal.id }.mapNotNull { g ->
                val a = g.allocations.find { it.accountId == acctId } ?: return@mapNotNull null
                Triple(g.id, a, a.resolvedAmountCents(acctBalance))
            }
            val otherTotal = otherRows.sumOf { it.third }
            val overflow = (newAllocCents + otherTotal) - acctBalance
            if (overflow > 0L && otherTotal > 0L) {
                var remainingReduction = overflow
                val reductions = mutableMapOf<Long, Long>()
                otherRows.forEachIndexed { idx, (gId, _, amt) ->
                    val cut = if (idx == otherRows.lastIndex) remainingReduction.coerceAtMost(amt)
                    else ((overflow * amt) / otherTotal).coerceAtMost(amt)
                    reductions[gId] = cut
                    remainingReduction = (remainingReduction - cut).coerceAtLeast(0L)
                }
                workingGoals = workingGoals.map { g ->
                    val cut = reductions[g.id] ?: return@map g
                    val nextAllocs = g.allocations.mapNotNull { a ->
                        if (a.accountId != acctId) a
                        else {
                            val basis = (acctBalance - a.reserveAmountCents).coerceAtLeast(0L)
                            val curAmt = a.resolvedAmountCents(acctBalance)
                            val nextAmt = (curAmt - cut).coerceAtLeast(0L)
                            if (nextAmt <= 0L) null
                            else if (a.allocationType == GoalAllocationType.PERCENT && basis > 0L) {
                                a.copy(allocationPercent = MoneyUtils.percentOf(nextAmt, basis), allocationAmountCents = nextAmt)
                            } else a.copy(allocationAmountCents = nextAmt)
                        }
                    }
                    val nextCurrent = nextAllocs.sumOf { a -> a.resolvedAmountCents(accountsMap[a.accountId]?.currentBalanceCents ?: 0L) }
                    g.copy(allocations = nextAllocs, currentAmountCents = nextCurrent)
                }
            }
        }
        workingGoals
    } else baseList

    commitSnapshot(snapshot.value.copy(goals = updatedGoalsList))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().saveGoalWithAllocations(updatedGoal, stealFromOthers).getOrElse { updatedGoal }
    } else {
        updatedGoal
    }
}

suspend fun OrbitRepository.reorderGoals(orderedIds: List<Long>) {
    val orderMap = orderedIds.mapIndexed { index, id -> id to index }.toMap()
    val updated = goals.value.map { g ->
        orderMap[g.id]?.let { g.copy(sortOrder = it) } ?: g
    }.sortedBy { it.sortOrder }
    commitSnapshot(snapshot.value.copy(goals = updated))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().reorderGoals(orderedIds)
    }
}

// =============================================================================
// Upcoming Extension Methods
// =============================================================================

suspend fun OrbitRepository.createUpcomingFromTransaction(transactionId: Long): Result<UpcomingItem> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val created = createApiClient().createUpcomingFromTransaction(transactionId).getOrThrow()
        saveUpcoming(created)
        created
    } else {
        val tx = transactions.value.find { it.id == transactionId }
            ?: throw NoSuchElementException("Transaction $transactionId not found")
        val item = UpcomingItem(
            name = tx.effectiveMerchant.ifBlank { "Upcoming Item" },
            merchant = tx.effectiveMerchant,
            amountCents = tx.absoluteAmountCents,
            direction = if (tx.isIncome) UpcomingDirection.INCOME else UpcomingDirection.EXPENSE,
            frequency = RecurrenceFrequency.MONTHLY,
            nextDate = tx.effectiveDate,
            categoryId = tx.effectiveCategoryId,
            accountId = tx.accountId
        ).copy(
            source = UpcomingSource.TRANSACTION,
            sourceTransactionId = transactionId
        )
        saveUpcoming(item)
        item
    }
}

suspend fun OrbitRepository.dismissRecurringSuggestion(key: String) {
    val updatedDismissed = preferences.value.dismissedRecurringKeys + key
    val remaining = snapshot.value.upcomingSuggestions.filterNot { it.merchant == key }
    commitSnapshot(snapshot.value.copy(
        upcomingSuggestions = remaining,
        preferences = preferences.value.copy(dismissedRecurringKeys = updatedDismissed)
    ))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().dismissUpcomingSuggestion(key)
    }
}

suspend fun OrbitRepository.reconcileOccurrence(
    occurrenceId: Long,
    upcomingItemId: Long,
    status: OccurrenceStatus,
    matchedTransactionId: Long? = null,
    advanceNextDateTo: String? = null
) {
    val currentOccurrences = snapshot.value.upcomingOccurrences
    val updatedOccurrences = currentOccurrences.map { occ ->
        if (occ.id == occurrenceId) {
            occ.copy(
                status = status,
                matchedTransactionId = matchedTransactionId ?: occ.matchedTransactionId
            )
        } else occ
    }
    val updatedUpcoming = if (advanceNextDateTo != null) {
        upcomingItems.value.map { item ->
            if (item.id == upcomingItemId) item.copy(nextDate = advanceNextDateTo) else item
        }
    } else upcomingItems.value

    commitSnapshot(snapshot.value.copy(
        upcomingOccurrences = updatedOccurrences,
        upcomingItems = updatedUpcoming
    ))
}

// =============================================================================
// Transactions & Rules Extensions
// =============================================================================

suspend fun OrbitRepository.resetTransactionFields(id: Long, fields: List<String>) {
    val current = transactions.value
    val target = current.find { it.id == id } ?: return
    var cleared = target
    for (f in fields) {
        cleared = when (f.lowercase()) {
            "merchant" -> cleared.copy(editedMerchant = null, editedMerchantSource = null)
            "category_id", "category" -> cleared.copy(editedCategoryId = null, editedCategoryIdSource = null)
            "is_transfer", "transfer" -> cleared.copy(editedIsTransfer = null, editedIsTransferSource = null)
            "is_ignored", "ignored" -> cleared.copy(editedIsIgnored = null, editedIsIgnoredSource = null)
            "mha_eligible", "mha" -> cleared.copy(editedMhaEligible = null, editedMhaEligibleSource = null)
            "amount" -> cleared.copy(editedAmountCents = null)
            "date" -> cleared.copy(editedDate = null)
            else -> cleared
        }
    }
    val sources = listOfNotNull(
        cleared.editedMerchantSource, cleared.editedCategoryIdSource,
        cleared.editedIsTransferSource, cleared.editedIsIgnoredSource, cleared.editedMhaEligibleSource
    )
    val hasRemainingUserLock = sources.any { it == "user" } ||
        (target.editSource == EditSource.USER && (cleared.editedAmountCents != null || cleared.editedDate != null))

    // Temporarily pass EditSource.NONE so RuleMatcherEngine re-evaluates unlocked fields while respecting per-field "user" source locks
    val unlockedForRuleEval = cleared.copy(editSource = EditSource.NONE)
    val evaluated = RuleMatcherEngine.applyRules(listOf(unlockedForRuleEval), rules.value).firstOrNull() ?: unlockedForRuleEval
    val ruleEvaluated = if (hasRemainingUserLock) evaluated.copy(editSource = EditSource.USER) else evaluated
    commitSnapshot(snapshot.value.copy(transactions = current.map { if (it.id == id) ruleEvaluated else it }))
    activeEngine.resetTransaction(id, fields)
}

suspend fun OrbitRepository.setTransactionIgnored(id: Long, ignored: Boolean) {
    val current = transactions.value
    val target = current.find { it.id == id } ?: return
    val updated = target.copy(
        editedIsIgnored = ignored,
        editedIsIgnoredSource = "user",
        editSource = EditSource.USER
    )
    commitSnapshot(snapshot.value.copy(transactions = current.map { if (it.id == id) updated else it }))
    activeEngine.pushTransaction(updated)
}

suspend fun OrbitRepository.setRuleEnabled(id: Long, enabled: Boolean) {
    val currentRules = rules.value
    val updatedRules = currentRules.map { if (it.id == id) it.copy(enabled = enabled) else it }
    val updatedTxns = RuleMatcherEngine.applyRules(transactions.value, updatedRules)
    commitSnapshot(snapshot.value.copy(rules = updatedRules, transactions = updatedTxns))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().setRuleEnabled(id, enabled)
    }
}

suspend fun OrbitRepository.wipeAllRules(): Result<Int> = runCatching {
    val count = rules.value.size
    val clearedTxns = RuleMatcherEngine.applyRules(transactions.value, emptyList())
    commitSnapshot(snapshot.value.copy(rules = emptyList(), transactions = clearedTxns))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().deleteAllRules().getOrThrow()
    } else {
        count
    }
}

suspend fun OrbitRepository.previewRule(rule: TransactionRule): RulePreviewResult {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val remote = createApiClient().previewRuleRemote(rule).getOrNull()
        if (remote != null) return remote
    }
    return previewRuleLocal(rule)
}

fun OrbitRepository.previewRuleLocal(rule: TransactionRule): RulePreviewResult {
    val txns = transactions.value
    val existingRules = rules.value.filter { it.enabled && it.id != rule.id }
    val diffs = mutableListOf<RuleMutationDiff>()
    val conflicts = mutableListOf<RuleConflictEntry>()
    var matchedCount = 0

    for (tx in txns) {
        val wouldMatch = RuleMatcherEngine.matchesRule(tx, rule)
        if (wouldMatch) {
            matchedCount++
            val fieldDiffs = mutableListOf<RuleFieldDiff>()
            for (action in rule.actions) {
                when (action.type) {
                    RuleActionType.SET_CATEGORY, RuleActionType.CATEGORIZE -> {
                        val before = tx.effectiveCategoryId?.toString() ?: ""
                        val after = (action.targetCategoryId ?: action.value.toLongOrNull())?.toString() ?: ""
                        if (before != after) fieldDiffs.add(RuleFieldDiff("category_id", before, after))
                    }
                    RuleActionType.RENAME_MERCHANT, RuleActionType.RENAME -> {
                        val before = tx.effectiveMerchant
                        val after = action.textValue ?: action.value
                        if (before != after) fieldDiffs.add(RuleFieldDiff("merchant", before, after))
                    }
                    RuleActionType.MARK_TRANSFER -> {
                        val before = tx.effectiveIsTransfer.toString()
                        val after = (action.booleanValue ?: (action.value == "true")).toString()
                        if (before != after) fieldDiffs.add(RuleFieldDiff("is_transfer", before, after))
                    }
                    RuleActionType.MARK_IGNORED -> {
                        val before = tx.effectiveIsIgnored.toString()
                        val after = (action.booleanValue ?: (action.value == "true")).toString()
                        if (before != after) fieldDiffs.add(RuleFieldDiff("is_ignored", before, after))
                    }
                    RuleActionType.SET_MHA_ELIGIBLE -> {
                        val before = tx.effectiveMhaEligible?.toString() ?: "false"
                        val after = (action.booleanValue ?: (action.value == "true")).toString()
                        if (before != after) fieldDiffs.add(RuleFieldDiff("mha_eligible", before, after))
                    }
                }
            }
            if (fieldDiffs.isNotEmpty()) {
                diffs.add(RuleMutationDiff(tx.id, tx.effectiveDate, tx.effectiveMerchant, tx.effectiveAmountCents, fieldDiffs))
            }
            val ruleTargetFields = rule.actions.map { act ->
                when (act.type) {
                    RuleActionType.RENAME_MERCHANT, RuleActionType.RENAME -> "merchant"
                    RuleActionType.SET_CATEGORY, RuleActionType.CATEGORIZE -> "category_id"
                    RuleActionType.MARK_TRANSFER -> "is_transfer"
                    RuleActionType.MARK_IGNORED -> "is_ignored"
                    RuleActionType.SET_MHA_ELIGIBLE -> "mha_eligible"
                }
            }.toSet()
            for (existing in existingRules) {
                if (existing.priority >= rule.priority && RuleMatcherEngine.matchesRule(tx, existing)) {
                    val existingFields = existing.actions.map { act ->
                        when (act.type) {
                            RuleActionType.RENAME_MERCHANT, RuleActionType.RENAME -> "merchant"
                            RuleActionType.SET_CATEGORY, RuleActionType.CATEGORIZE -> "category_id"
                            RuleActionType.MARK_TRANSFER -> "is_transfer"
                            RuleActionType.MARK_IGNORED -> "is_ignored"
                            RuleActionType.SET_MHA_ELIGIBLE -> "mha_eligible"
                        }
                    }.toSet()
                    val overlapping = ruleTargetFields.intersect(existingFields)
                    for (field in overlapping) {
                        conflicts.add(
                            RuleConflictEntry(
                                transactionId = tx.id,
                                merchant = tx.effectiveMerchant,
                                field = field,
                                conflictingRuleId = existing.id,
                                conflictingRuleName = existing.name
                            )
                        )
                    }
                }
            }
        }
    }
    return RulePreviewResult(
        matchedCount = matchedCount,
        willChangeCount = diffs.size,
        conflictCount = conflicts.size,
        conflicts = conflicts,
        diffs = diffs
    )
}

// =============================================================================
// Merchant Logos Extension Methods
// =============================================================================

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
    useCategoryIcon: Boolean = false
): Result<MerchantLogo> = runCatching {
    val target = transactions.value.find { it.id == transactionId }
        ?: throw IllegalStateException("Transaction not found.")
    val cleanedName = cleanMerchantNameForLogo(target.effectiveMerchant.ifBlank { target.originalMerchant })
    val fallbackKey = target.resolvedMerchantKey
    if (fallbackKey.isBlank()) throw IllegalStateException("This merchant does not have a logo entry to update yet.")
    val updatedLogo = if (storageMode.value == StorageMode.SELF_HOSTED) {
        val client = createApiClient()
        val ensuredKey = target.merchantLogo?.merchantKey?.takeIf { it.isNotBlank() }
            ?: client.ensureMerchantLogo(transactionId).getOrNull()?.merchantKey
            ?: fallbackKey
        client.overrideMerchantLogo(ensuredKey, logoUrl, useCategoryIcon).getOrThrow().toDomain()
            ?: MerchantLogo(ensuredKey, cleanedName, url = if (useCategoryIcon) null else logoUrl, status = if (useCategoryIcon) "hidden" else "manual")
    } else {
        MerchantLogo(
            merchantKey = fallbackKey,
            merchantName = cleanedName,
            providerQuery = cleanedName,
            url = if (useCategoryIcon) null else logoUrl?.trim(),
            status = if (useCategoryIcon) "hidden" else "manual"
        )
    }
    val updatedList = transactions.value.map { txn ->
        if (txn.id == transactionId || txn.resolvedMerchantKey == updatedLogo.merchantKey || merchantLogoKey(txn.effectiveMerchant) == updatedLogo.merchantKey) {
            txn.copy(merchantLogo = updatedLogo)
        } else txn
    }
    commitSnapshot(snapshot.value.copy(transactions = updatedList))
    updatedLogo
}
