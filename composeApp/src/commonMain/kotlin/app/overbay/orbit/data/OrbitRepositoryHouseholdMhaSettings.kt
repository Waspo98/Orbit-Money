package app.overbay.orbit.data

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BackupPreviewStats
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.HouseholdIncomeRecord
import app.overbay.orbit.data.model.HouseholdInfo
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.HouseholdSharingState
import app.overbay.orbit.data.model.ImportBatchRecord
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.NotificationPreferencesConfig
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.PushNotificationConfigInfo
import app.overbay.orbit.data.model.RetirementAccountMonthBalance
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.data.model.SimpleFinStatusInfo
import app.overbay.orbit.data.model.SimpleFinSyncLogEntry
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.network.AccountDto
import app.overbay.orbit.data.network.CategoryDto
import app.overbay.orbit.data.network.GoalDto
import app.overbay.orbit.data.network.RuleWireMapper
import app.overbay.orbit.data.network.UpcomingItemDto
import app.overbay.orbit.data.network.*
import app.overbay.orbit.domain.MoneyUtils
import kotlinx.datetime.Clock
import kotlinx.serialization.json.encodeToJsonElement

// =============================================================================
// Tail Methods Preserved from OrbitRepository
// =============================================================================

internal fun OrbitRepository.exportBackupJsonImpl(): String = json.encodeToString(OrbitWorkspaceSnapshot.serializer(), snapshot.value)

internal suspend fun OrbitRepository.restoreBackupJsonImpl(jsonString: String): Result<Unit> = runCatching {
    commitSnapshot(json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), jsonString), immediate = true)
}

internal suspend fun OrbitRepository.loadDemoDataImpl(snapshot: OrbitWorkspaceSnapshot) = commitSnapshot(snapshot)

internal suspend fun OrbitRepository.updateHouseholdInfoImpl(household: HouseholdInfo) = commitSnapshot(snapshot.value.copy(household = household))

internal suspend fun OrbitRepository.saveHouseholdShareImpl(share: HouseholdShare) = commitSnapshot(snapshot.value.copy(shares = snapshot.value.shares + share))

internal suspend fun OrbitRepository.deleteHouseholdShareImpl(id: Long) = commitSnapshot(snapshot.value.copy(shares = snapshot.value.shares.filterNot { it.id == id }))

// =============================================================================
// MHA Extension Methods
// =============================================================================

suspend fun OrbitRepository.setMhaEnabled(enabled: Boolean) {
    val updatedPref = preferences.value.copy(mhaTrackerEnabled = enabled)
    updatePreferences(updatedPref)
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().putMhaSettings(enabled)
    }
}

internal suspend fun OrbitRepository.updateMhaTaxProfileImpl(profile: MhaTaxProfile) {
    commitSnapshot(snapshot.value.copy(mhaTaxProfile = profile))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().putMhaTaxProfile(profile)
    }
}

suspend fun OrbitRepository.setMhaAccountDefault(accountId: Long, eligible: Boolean) {
    val current = accounts.value
    val target = current.find { it.id == accountId } ?: return
    val updated = target.copy(mhaDefaultEligible = eligible)
    commitSnapshot(snapshot.value.copy(accounts = current.map { if (it.id == accountId) updated else it }))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().putMhaAccountDefault(accountId, eligible)
    }
}

suspend fun OrbitRepository.setMhaCategoryDefault(categoryId: Long, eligible: Boolean) {
    val current = categories.value
    val target = current.find { it.id == categoryId } ?: return
    val updated = target.copy(mhaDefaultEligible = eligible)
    commitSnapshot(snapshot.value.copy(categories = current.map { if (it.id == categoryId) updated else it }))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().putMhaCategoryDefault(categoryId, eligible)
    }
}

suspend fun OrbitRepository.setMhaCategoryIgnoreDefault(categoryId: Long, ignored: Boolean) {
    val current = categories.value
    val target = current.find { it.id == categoryId } ?: return
    val updated = target.copy(mhaDefaultIgnored = ignored)
    commitSnapshot(snapshot.value.copy(categories = current.map { if (it.id == categoryId) updated else it }))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().putMhaCategoryIgnoreDefault(categoryId, ignored)
    }
}

// =============================================================================
// Household & Sharing Extension Methods
// =============================================================================

internal suspend fun OrbitRepository.saveHouseholdMemberImpl(member: HouseholdMember, effectiveDate: String? = null) {
    val currentMembers = snapshot.value.members
    val existing = currentMembers.find { it.id == member.id && member.id != 0L }
    val assignedId = if (existing != null) member.id
    else if (member.id == 0L) (currentMembers.maxOfOrNull { it.id } ?: 0L) + 1L
    else member.id
    val savedMember = member.copy(id = assignedId)
    val updatedMembers = if (existing != null) currentMembers.map { if (it.id == member.id) savedMember else it }
    else currentMembers + savedMember

    val otherLinks = snapshot.value.retirementAccountLinks.filterNot { it.memberId == assignedId }
    val updatedLinks = otherLinks + savedMember.retirementAccounts.map { it.copy(memberId = assignedId) }

    val compChanged = existing == null ||
        existing.grossIncomeAnnualCents != member.grossIncomeAnnualCents ||
        existing.netPayPerPeriodCents != member.netPayPerPeriodCents
    val updatedIncomeRecords = if (compChanged || effectiveDate != null) {
        val nextIncId = (snapshot.value.incomeRecords.maxOfOrNull { it.id } ?: 0L) + 1L
        val dateStr = effectiveDate ?: Clock.System.now().toString().take(10)
        val rec = HouseholdIncomeRecord(
            id = nextIncId,
            householdId = savedMember.householdId,
            memberId = assignedId,
            effectiveDate = dateStr,
            grossIncomeAnnualCents = savedMember.grossIncomeAnnualCents,
            netPayPerPeriodCents = savedMember.netPayPerPeriodCents,
            payFrequency = savedMember.payFrequency,
            payPeriodsPerYear = savedMember.payPeriodsPerYear,
            employeeContributionPercent = savedMember.employeeContributionPercent ?: 0.0,
            employeeContributionAnnualCents = savedMember.employeeContributionAnnualCents,
            employerMatchPercent = savedMember.employerMatchPercent,
            employerMatchLimitPercent = savedMember.employerMatchLimitPercent,
            employerMatchAnnualCapCents = savedMember.employerMatchAnnualCapCents,
            healthPremiumPerMonthCents = savedMember.healthPremiumPerMonthCents,
            hsaContributionAnnualCents = savedMember.hsaContributionAnnualCents,
            dependentCareFsaAnnualCents = savedMember.dependentCareFsaAnnualCents,
            otherBenefitsAnnualCents = savedMember.otherBenefitsAnnualCents
        )
        snapshot.value.incomeRecords + rec
    } else {
        snapshot.value.incomeRecords
    }

    commitSnapshot(snapshot.value.copy(
        members = updatedMembers,
        retirementAccountLinks = updatedLinks,
        incomeRecords = updatedIncomeRecords
    ))

    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().saveHouseholdMemberRemote(savedMember, effectiveDate)
    }
}

internal suspend fun OrbitRepository.deleteHouseholdMemberImpl(id: Long) {
    val updatedMembers = snapshot.value.members.filterNot { it.id == id }
    val updatedLinks = snapshot.value.retirementAccountLinks.filterNot { it.memberId == id }
    commitSnapshot(snapshot.value.copy(members = updatedMembers, retirementAccountLinks = updatedLinks))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().deleteHouseholdMemberRemote(id)
    }
}

suspend fun OrbitRepository.addHouseholdIncomeRecord(record: HouseholdIncomeRecord) {
    val current = snapshot.value.incomeRecords
    val assignedId = if (record.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else record.id
    val saved = record.copy(id = assignedId)
    commitSnapshot(snapshot.value.copy(incomeRecords = current + saved))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().addHouseholdIncomeRecordRemote(record.memberId, saved)
    }
}

suspend fun OrbitRepository.getRetirementHistory(months: Int = 1200): List<RetirementHistoryPoint> {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val remote = createApiClient().getRetirementHistoryRemote(months).getOrNull()
        if (remote != null) return remote
    }

    val linkedAccountIds = snapshot.value.retirementAccountLinks.map { it.accountId }.toSet()
        .ifEmpty { accounts.value.filter { it.type == AccountType.INVESTMENT }.map { it.id }.toSet() }
    val allBalRecords = snapshot.value.balanceRecords.filter { it.accountId in linkedAccountIds }
    val monthsSet = allBalRecords.map { it.recordDate.take(7) }.filter { it.length == 7 }.toSet()
        .ifEmpty { transactions.value.map { it.effectiveDate.take(7) }.filter { it.length == 7 }.toSet() }

    return monthsSet.sorted().takeLast(months).map { m ->
        val balances = linkedAccountIds.map { acctId ->
            val latestRec = allBalRecords.filter { it.accountId == acctId && it.recordDate.startsWith(m) }
                .maxByOrNull { it.recordDate }
            val bal = latestRec?.balanceCents ?: (accounts.value.find { it.id == acctId }?.currentBalanceCents ?: 0L)
            RetirementAccountMonthBalance(acctId, bal)
        }
        RetirementHistoryPoint(m, balances.sumOf { it.balanceCents }, balances)
    }
}

suspend fun OrbitRepository.fetchHouseholdSharing(): Result<HouseholdSharingState> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val res = createApiClient().getHouseholdSharingRemote().getOrThrow()
        commitSnapshot(snapshot.value.copy(shares = res.shares))
        res
    } else {
        HouseholdSharingState(
            householdId = snapshot.value.household.id,
            householdName = snapshot.value.household.name,
            shares = snapshot.value.shares
        )
    }
}

suspend fun OrbitRepository.inviteHouseholdPartner(email: String, role: String = "member", accessLevel: String = "write"): Result<HouseholdShare> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val created = createApiClient().createHouseholdShareRemote(email, role, accessLevel).getOrThrow()
        saveHouseholdShare(created)
        created
    } else {
        val nextId = (snapshot.value.shares.maxOfOrNull { it.id } ?: 0L) + 1L
        val share = HouseholdShare(id = nextId, invitedEmail = email, accessLevel = accessLevel)
        saveHouseholdShare(share)
        share
    }
}

suspend fun OrbitRepository.updatePartnerShareAccess(shareId: Long, accessLevel: String): Result<Boolean> = runCatching {
    val updated = snapshot.value.shares.map { if (it.id == shareId) it.copy(accessLevel = accessLevel) else it }
    commitSnapshot(snapshot.value.copy(shares = updated))
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().updateShareAccessRemote(shareId, accessLevel).getOrThrow()
    } else true
}

suspend fun OrbitRepository.revokePartnerShare(shareId: Long): Result<Boolean> = runCatching {
    deleteHouseholdShare(shareId)
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().revokeHouseholdShareRemote(shareId).getOrThrow()
    } else true
}

suspend fun OrbitRepository.updateHouseholdMemberUserAccess(userId: Long, accessLevel: String): Result<Boolean> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().updateHouseholdUserAccessRemote(userId, accessLevel).getOrThrow()
    } else true
}

suspend fun OrbitRepository.removeHouseholdMemberUser(userId: Long): Result<Boolean> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().removeHouseholdUserRemote(userId).getOrThrow()
    } else true
}

// =============================================================================
// SimpleFIN, Notifications, Import/Export & Backup Preview
// =============================================================================

suspend fun OrbitRepository.refreshSimpleFinStatus(): Result<SimpleFinStatusInfo> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val status = createApiClient().getSimpleFinStatus().getOrThrow()
        _simpleFinStatus.value = status
        status
    } else {
        val token = preferences.value.simpleFinToken
        val info = SimpleFinStatusInfo(
            configured = !token.isNullOrBlank(),
            connected = !token.isNullOrBlank(),
            cutoverDate = preferences.value.simpleFinCutoverDate
        )
        _simpleFinStatus.value = info
        info
    }
}

suspend fun OrbitRepository.setupSimpleFinBridge(setupToken: String, cutoverDate: String? = null): Result<SimpleFinStatusInfo> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val res = createApiClient().setupSimpleFin(setupToken, cutoverDate).getOrThrow()
        _simpleFinStatus.value = res
        res
    } else {
        val updatedPref = preferences.value.copy(simpleFinToken = setupToken, simpleFinCutoverDate = cutoverDate)
        updatePreferences(updatedPref)
        sync(force = true)
        val info = SimpleFinStatusInfo(configured = true, connected = true, cutoverDate = cutoverDate)
        _simpleFinStatus.value = info
        info
    }
}

suspend fun OrbitRepository.fetchSimpleFinSyncLogs(limit: Int = 20): Result<List<SimpleFinSyncLogEntry>> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().getSimpleFinSyncLogs(limit).getOrThrow()
    } else emptyList()
}

suspend fun OrbitRepository.muteSimpleFinAccountWarnings(accountLabels: List<String>) {
    val cleaned = accountLabels.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) return
    val existing = preferences.value.mutedSimpleFinAccounts
    val merged = (existing + cleaned).distinctBy { it.lowercase() }
    if (merged != existing) {
        updatePreferences(preferences.value.copy(mutedSimpleFinAccounts = merged))
    }
}

suspend fun OrbitRepository.unmuteSimpleFinAccountWarning(accountLabel: String) {
    val target = accountLabel.trim().lowercase()
    val existing = preferences.value.mutedSimpleFinAccounts
    val updated = existing.filterNot { it.trim().lowercase() == target }
    if (updated != existing) {
        updatePreferences(preferences.value.copy(mutedSimpleFinAccounts = updated))
    }
}

suspend fun OrbitRepository.disconnectSimpleFinBridge(): Result<Boolean> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().disconnectSimpleFin().getOrThrow()
    }
    updatePreferences(preferences.value.copy(simpleFinToken = null, simpleFinCutoverDate = null))
    _simpleFinStatus.value = SimpleFinStatusInfo()
    true
}

suspend fun OrbitRepository.fetchPushNotificationConfig(): Result<PushNotificationConfigInfo> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().getPushNotificationConfig().getOrThrow()
    } else PushNotificationConfigInfo(configured = false)
}

suspend fun OrbitRepository.subscribeWebPush(endpoint: String, p256dh: String, auth: String): Result<Boolean> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().registerPushSubscription(endpoint, p256dh, auth, null).getOrThrow()
    } else true
}

suspend fun OrbitRepository.sendTestNotification(): Result<Boolean> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        createApiClient().sendTestPushNotification().getOrThrow()
    } else true
}

suspend fun OrbitRepository.updateNotificationPreferences(config: NotificationPreferencesConfig) {
    val updated = preferences.value.copy(notificationPreferences = config)
    updatePreferences(updated)
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        runCatching {
            createApiClient().putRemotePreferenceKey("notificationPreferences", json.encodeToJsonElement(config))
        }
    }
}

fun OrbitRepository.previewBackupJson(jsonString: String): Result<BackupPreviewStats> = runCatching {
    val snap = json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), jsonString)
    BackupPreviewStats(
        schemaVersion = snap.schemaVersion,
        exportedAt = snap.exportedAt,
        accountCount = snap.accounts.size,
        transactionCount = snap.transactions.size,
        categoryCount = snap.categories.size,
        budgetCount = snap.budgets.size,
        goalCount = snap.goals.size,
        ruleCount = snap.rules.size,
        upcomingCount = snap.upcomingItems.size,
        memberCount = snap.members.size,
        balanceRecordCount = snap.balanceRecords.size
    )
}

suspend fun OrbitRepository.exportBudgetingCsv(): Result<String> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val remote = createApiClient().exportBudgetingDataRemote("csv").getOrNull()
        if (!remote.isNullOrBlank()) return@runCatching remote
    }
    val accountsMap = accounts.value.associateBy { it.id }
    val categoriesMap = categories.value.associateBy { it.id }
    val sb = StringBuilder("Date,Account,Merchant,Original Description,Category,Amount,Is Transfer,Is Ignored,Notes\n")
    for (tx in transactions.value.sortedByDescending { it.effectiveDate }) {
        val acctName = tx.accountName.ifBlank { accountsMap[tx.accountId]?.name ?: "" }
        val catName = tx.categoryName ?: tx.effectiveCategoryId?.let { categoriesMap[it]?.name } ?: ""
        val amt = MoneyUtils.centsToDollars(tx.effectiveAmountCents)
        val origDesc = (tx.originalDescription ?: "").replace("\"", "\"\"")
        val notes = (tx.notes ?: "").replace("\"", "\"\"")
        sb.append("${tx.effectiveDate},\"$acctName\",\"${tx.effectiveMerchant}\",\"$origDesc\",\"$catName\",$amt,${tx.effectiveIsTransfer},${tx.effectiveIsIgnored},\"$notes\"\n")
    }
    sb.toString()
}

suspend fun OrbitRepository.exportBudgetingJson(): Result<String> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val remote = createApiClient().exportBudgetingDataRemote("json").getOrNull()
        if (!remote.isNullOrBlank()) return@runCatching remote
    }
    exportBackupJson()
}

suspend fun OrbitRepository.importTransactionsWithBatch(
    fileName: String,
    rawCsvContent: String?,
    newTransactions: List<Transaction>,
    newAccounts: List<Account> = emptyList(),
    newCategories: List<Category> = emptyList(),
    newRules: List<TransactionRule> = emptyList()
): Result<ImportBatchRecord> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED && !rawCsvContent.isNullOrBlank()) {
        val remoteBatch = createApiClient().uploadRocketMoneyCsvRemote(rawCsvContent, fileName).getOrThrow()
        sync(force = true)
        return@runCatching remoteBatch
    }
    val curAccounts = accounts.value
    var nextAcctId = (curAccounts.maxOfOrNull { it.id } ?: 0L) + 1L
    val assignedAccounts = newAccounts.map { if (it.id <= 0L) it.copy(id = nextAcctId++) else it }

    val curCategories = categories.value
    var nextCatId = (curCategories.maxOfOrNull { it.id } ?: 0L) + 1L
    val assignedCategories = newCategories.map { if (it.id <= 0L) it.copy(id = nextCatId++) else it }

    val curRules = rules.value
    var nextRuleId = (curRules.maxOfOrNull { it.id } ?: 0L) + 1L
    val assignedRules = newRules.map { if (it.id <= 0L) it.copy(id = nextRuleId++) else it }

    val curTxns = transactions.value
    var nextTxId = (curTxns.maxOfOrNull { it.id } ?: 0L) + 1L
    val assignedTxns = newTransactions.map { if (it.id <= 0L || curTxns.any { c -> c.id == it.id }) it.copy(id = nextTxId++) else it }

    val nextBatchId = (snapshot.value.importBatches.maxOfOrNull { it.id } ?: 0L) + 1L
    val batch = ImportBatchRecord(
        id = nextBatchId,
        fileName = fileName,
        importedAt = Clock.System.now().toString(),
        totalRows = newTransactions.size,
        importedCount = assignedTxns.size,
        transactionIds = assignedTxns.map { it.id },
        createdAccountIds = assignedAccounts.map { it.id },
        createdCategoryIds = assignedCategories.map { it.id },
        createdRuleIds = assignedRules.map { it.id }
    )

    commitSnapshot(snapshot.value.copy(
        accounts = curAccounts + assignedAccounts,
        categories = curCategories + assignedCategories,
        rules = curRules + assignedRules,
        transactions = curTxns + assignedTxns,
        importBatches = snapshot.value.importBatches + batch
    ))
    batch
}

suspend fun OrbitRepository.undoImportBatch(batchId: Long): Result<Boolean> = runCatching {
    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val success = createApiClient().undoImportBatchRemote(batchId).getOrThrow()
        sync(force = true)
        return@runCatching success
    }
    val batch = snapshot.value.importBatches.find { it.id == batchId }
        ?: throw NoSuchElementException("Import batch $batchId not found")
    val txIdsToRemove = batch.transactionIds.toSet()
    val ruleIdsToRemove = batch.createdRuleIds.toSet()
    val acctIdsToRemove = batch.createdAccountIds.toSet()
    val catIdsToRemove = batch.createdCategoryIds.toSet()

    val updatedTxns = transactions.value.filterNot { it.id in txIdsToRemove }
    val remainingAcctIdsInUse = updatedTxns.map { it.accountId }.toSet()
    val updatedAccounts = accounts.value.filterNot { it.id in acctIdsToRemove && it.id !in remainingAcctIdsInUse }
    val updatedCategories = categories.value.filterNot { it.id in catIdsToRemove }
    val updatedRules = rules.value.filterNot { it.id in ruleIdsToRemove }
    val updatedBatches = snapshot.value.importBatches.filterNot { it.id == batchId }

    commitSnapshot(snapshot.value.copy(
        transactions = updatedTxns,
        accounts = updatedAccounts,
        categories = updatedCategories,
        rules = updatedRules,
        importBatches = updatedBatches
    ))
    true
}

suspend fun OrbitRepository.migrateLocalWorkspaceToSelfHosted(serverUrl: String): Result<String> = runCatching {
    val client = createApiClient(serverUrl)
    val snap = snapshot.value
    snap.categories.forEach { runCatching { client.createCategory(CategoryDto(id = 0L, name = it.name, color = it.color, icon = it.icon, isTransfer = it.isTransfer, isIncome = it.isIncome)) } }
    snap.accounts.forEach { runCatching { client.createAccount(AccountDto(id = 0L, name = it.name, type = it.type.name.lowercase(), institution = it.institution, currentBalance = MoneyUtils.centsToDollars(it.currentBalanceCents))) } }
    snap.rules.forEach { runCatching { client.createRule(RuleWireMapper.toDto(it.copy(id = 0L))) } }
    snap.goals.forEach { runCatching { client.createGoal(GoalDto(id = 0L, name = it.name, targetAmount = MoneyUtils.centsToDollars(it.targetAmountCents))) } }
    snap.upcomingItems.forEach { runCatching { client.createUpcoming(UpcomingItemDto(id = 0L, name = it.name, amount = MoneyUtils.centsToDollars(it.amountCents), direction = it.direction, frequencyType = it.frequencyType.name.lowercase(), nextDate = it.nextDate)) } }
    setStorageMode(StorageMode.SELF_HOSTED, serverUrl)
    "Migration completed successfully"
}
