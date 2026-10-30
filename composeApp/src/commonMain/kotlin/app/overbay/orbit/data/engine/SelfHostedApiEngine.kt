package app.overbay.orbit.data.engine

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.HouseholdInfo
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.NotificationPreferencesConfig
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.network.AccountDto
import app.overbay.orbit.data.network.CategoryDto
import app.overbay.orbit.data.network.GoalDto
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.RuleWireMapper
import app.overbay.orbit.data.network.TransactionDto
import app.overbay.orbit.data.network.UpcomingItemDto
import app.overbay.orbit.data.network.*
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Storage engine communicating with the self-hosted Express API endpoints.
 * Caches the complete OrbitWorkspaceSnapshot locally via PlatformStorage so cold app starts
 * take 0ms and data remains readable offline.
 */
class SelfHostedApiEngine(
    val apiClient: OrbitApiClient,
    private val storage: PlatformStorage,
    private val preferencesProvider: () -> OrbitPreferences = { OrbitPreferences(storageMode = StorageMode.SELF_HOSTED) }
) : OrbitStorageEngine {

    override val mode: StorageMode = StorageMode.SELF_HOSTED

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private inline fun <reified T> JsonObject.prefOr(key: String, fallback: T): T {
        val elem = this[key] ?: return fallback
        return runCatching { json.decodeFromJsonElement<T>(elem) }.getOrDefault(fallback)
    }

    override suspend fun loadSnapshot(): OrbitWorkspaceSnapshot {
        // Attempt network fetch first; fall back to local offline cache preserving current preferences
        val currentPrefs = preferencesProvider().copy(storageMode = StorageMode.SELF_HOSTED)
        return try {
            fetchRemoteSnapshot(currentPrefs).also { freshSnapshot ->
                saveSnapshot(freshSnapshot)
            }
        } catch (_: Throwable) {
            loadCachedSnapshot()?.copy(preferences = currentPrefs)
                ?: OrbitWorkspaceSnapshot.empty().copy(preferences = currentPrefs)
        }
    }

    private suspend fun fetchRemoteSnapshot(currentPrefs: OrbitPreferences): OrbitWorkspaceSnapshot = coroutineScope {
        val accountsDeferred = async { apiClient.getAccounts().getOrThrow() }
        val transactionsDeferred = async { apiClient.getAllTransactions().getOrThrow() }
        val categoriesDeferred = async { apiClient.getCategories().getOrThrow() }
        val budgetsDeferred = async { apiClient.getBudgets("").getOrNull() }
        val goalsDeferred = async { apiClient.getGoals().getOrNull() ?: emptyList() }
        val rulesDeferred = async { apiClient.getRules().getOrNull() ?: emptyList() }
        val upcomingDeferred = async { apiClient.getUpcomingFull().getOrNull() }
        val householdDeferred = async { apiClient.getHousehold().getOrNull() }
        val mhaDeferred = async { apiClient.getMhaOverview().getOrNull() }
        val authMeDeferred = async { apiClient.getMe().getOrNull() }
        val sharingDeferred = async { apiClient.getHouseholdSharingRemote().getOrNull() }
        val remotePrefsDeferred = async { apiClient.getRemotePreferences().getOrNull() }

        val accountsDto = accountsDeferred.await()
        val txDtos = transactionsDeferred.await()
        val categoriesDto = categoriesDeferred.await()
        val budgetsDto = budgetsDeferred.await()
        val goalsDto = goalsDeferred.await()
        val rulesDto = rulesDeferred.await()
        val upcomingFull = upcomingDeferred.await()
        val householdDto = householdDeferred.await()
        val mhaDto = mhaDeferred.await()
        val authMe = authMeDeferred.await()
        val sharing = sharingDeferred.await()
        val remotePrefs = remotePrefsDeferred.await()

        val accounts = accountsDto.map { it.toDomain() }
        val categories = categoriesDto.map { it.toDomain() }
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }

        val transactions = txDtos.map { dto ->
            val tx = dto.toDomain()
            val cat = tx.effectiveCategoryId?.let { categoriesById[it] }
            tx.copy(
                accountName = tx.accountName.ifBlank { accountsById[tx.accountId]?.name ?: "" },
                categoryName = tx.categoryName ?: cat?.name,
                categoryColor = tx.categoryColor ?: cat?.color,
                categoryIcon = tx.categoryIcon ?: cat?.icon
            )
        }

        val members = householdDto?.members?.map { it.toDomain() } ?: emptyList()
        val memberRetirementLinks = members.flatMap { it.retirementAccounts }

        val upcomingItems = upcomingFull?.items?.map { it.toDomain() }
            ?: apiClient.getUpcoming().getOrNull()?.map { it.toDomain() }
            ?: emptyList()
        val upcomingOccurrences = upcomingFull?.occurrences?.map { it.toDomain() } ?: emptyList()
        val upcomingSuggestions = upcomingFull?.suggestions?.map { it.toDomain() } ?: emptyList()

        val creditCardProfiles = accounts.mapNotNull { it.creditCardProfile }
        val incomeRecords = runCatching { householdDto?.records?.map { it.toDomain() } }.getOrNull() ?: emptyList()
        val retirementAccountLinks = runCatching { householdDto?.retirementAccounts?.map { it.toDomain(it.memberId) } }
            .getOrNull()?.ifEmpty { null } ?: memberRetirementLinks

        val resolvedPrefs = currentPrefs.copy(
            mhaTrackerEnabled = mhaDto?.enabled ?: currentPrefs.mhaTrackerEnabled,
            amoledDarkMode = remotePrefs?.prefOr("amoledDarkMode", currentPrefs.amoledDarkMode) ?: currentPrefs.amoledDarkMode,
            hiddenRouteNames = remotePrefs?.prefOr("hiddenRouteNames", currentPrefs.hiddenRouteNames) ?: currentPrefs.hiddenRouteNames,
            moreRouteOrder = remotePrefs?.prefOr("moreRouteOrder", currentPrefs.moreRouteOrder) ?: currentPrefs.moreRouteOrder,
            budgetSortOption = remotePrefs?.prefOr<String?>("budgetSortOption", null)?.let { name ->
                CategorySortOption.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            } ?: currentPrefs.budgetSortOption,
            dashboardWidgets = remotePrefs?.prefOr("dashboardWidgets", currentPrefs.dashboardWidgets) ?: currentPrefs.dashboardWidgets,
            dashboardHiddenBiggestIds = remotePrefs?.prefOr("dashboardHiddenBiggestIds", currentPrefs.dashboardHiddenBiggestIds) ?: currentPrefs.dashboardHiddenBiggestIds,
            dashboardFocusedGoalId = remotePrefs?.prefOr("dashboardFocusedGoalId", currentPrefs.dashboardFocusedGoalId),
            dashboardRetirementAge = remotePrefs?.prefOr("dashboardRetirementAge", currentPrefs.dashboardRetirementAge) ?: currentPrefs.dashboardRetirementAge,
            dashboardRetirementPreset = remotePrefs?.prefOr("dashboardRetirementPreset", currentPrefs.dashboardRetirementPreset) ?: currentPrefs.dashboardRetirementPreset,
            accountGroupWideSpans = remotePrefs?.prefOr("accountGroupWideSpans", currentPrefs.accountGroupWideSpans) ?: currentPrefs.accountGroupWideSpans,
            dismissedRecurringKeys = remotePrefs?.prefOr("dismissedRecurringKeys", currentPrefs.dismissedRecurringKeys) ?: currentPrefs.dismissedRecurringKeys,
            mutedSimpleFinAccounts = remotePrefs?.prefOr("mutedSimpleFinAccounts", currentPrefs.mutedSimpleFinAccounts) ?: currentPrefs.mutedSimpleFinAccounts,
            includeHsaInRetirementRate = remotePrefs?.prefOr("includeHsaInRetirementRate", currentPrefs.includeHsaInRetirementRate) ?: currentPrefs.includeHsaInRetirementRate,
            notificationPreferences = remotePrefs?.prefOr("notificationPreferences", currentPrefs.notificationPreferences) ?: currentPrefs.notificationPreferences,
            isSharedNonOwnerSession = sharing != null && sharing.currentUserRole != "owner",
            isReadOnlySession = sharing != null && sharing.currentUserAccessLevel == "read",
            sharedSessionCachedAtEpochMs = Clock.System.now().toEpochMilliseconds()
        )

        OrbitWorkspaceSnapshot(
            schemaVersion = OrbitWorkspaceSnapshot.CURRENT_SCHEMA_VERSION,
            exportedAt = "",
            household = HouseholdInfo(
                id = authMe?.household?.id ?: 1L,
                name = authMe?.household?.name ?: "My Household"
            ),
            members = members,
            shares = sharing?.shares ?: emptyList(),
            accounts = accounts,
            creditCardProfiles = creditCardProfiles,
            balanceRecords = emptyList(),
            categories = categories,
            transactions = transactions,
            budgets = budgetsDto?.toDomainList() ?: emptyList(),
            goals = goalsDto.map { it.toDomain() },
            rules = rulesDto.map { it.toDomain() },
            upcomingItems = upcomingItems,
            upcomingOccurrences = upcomingOccurrences,
            upcomingSuggestions = upcomingSuggestions,
            incomeRecords = incomeRecords,
            retirementAccountLinks = retirementAccountLinks,
            importBatches = emptyList(),
            preferences = resolvedPrefs,
            mhaTaxProfile = mhaDto?.taxProfile?.toDomain() ?: MhaTaxProfile()
        )
    }

    internal fun loadCachedSnapshot(): OrbitWorkspaceSnapshot? {
        val raw = storage.loadString(CACHE_SNAPSHOT_KEY) ?: return null
        return runCatching {
            json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), raw)
        }.getOrNull()
    }

    override suspend fun saveSnapshot(snapshot: OrbitWorkspaceSnapshot) {
        // Saves locally for offline resilience and fast cold-start
        val encoded = json.encodeToString(OrbitWorkspaceSnapshot.serializer(), snapshot)
        storage.saveString(CACHE_SNAPSHOT_KEY, encoded)
    }

    override suspend fun sync(force: Boolean): SyncResult {
        return try {
            val simpleFinRes = apiClient.triggerSimpleFinSync().getOrNull()
            val fresh = fetchRemoteSnapshot(preferencesProvider().copy(storageMode = StorageMode.SELF_HOSTED))
            saveSnapshot(fresh)
            val suffix = if (simpleFinRes != null && simpleFinRes.transactionsInserted > 0) {
                " (${simpleFinRes.transactionsInserted} new SimpleFIN transactions)"
            } else {
                ""
            }
            SyncResult.Success("Synced ${fresh.accounts.size} accounts and ${fresh.transactions.size} transactions$suffix")
        } catch (e: Throwable) {
            SyncResult.Error("Remote sync failed: ${e.message}", e)
        }
    }

    override suspend fun clear() {
        storage.delete(CACHE_SNAPSHOT_KEY)
    }

    override suspend fun pushTransaction(transaction: Transaction): Result<Transaction> {
        val dto = TransactionDto(
            id = transaction.id,
            accountId = transaction.accountId,
            date = transaction.effectiveDate,
            amount = MoneyUtils.centsToDollars(transaction.effectiveAmountCents),
            merchant = transaction.effectiveMerchant,
            categoryId = transaction.effectiveCategoryId,
            isTransfer = transaction.effectiveIsTransfer,
            isIgnored = transaction.effectiveIsIgnored,
            originalMerchant = transaction.originalMerchant,
            originalDescription = transaction.originalDescription,
            originalCategoryId = transaction.originalCategoryId,
            editedMerchant = transaction.editedMerchant,
            editedCategoryId = transaction.editedCategoryId,
            notes = transaction.notes
        )
        return if (transaction.id == 0L) {
            apiClient.createTransaction(dto).map { it.toDomain() }
        } else {
            apiClient.updateTransaction(transaction.id, dto).map { it.toDomain() }
        }
    }

    override suspend fun deleteTransaction(id: Long): Result<Boolean> = apiClient.deleteTransaction(id)

    override suspend fun resetTransaction(
        id: Long,
        fields: List<String>
    ): Result<Transaction> = apiClient.resetTransaction(id, fields).map { it.toDomain() }

    override suspend fun pushAccount(account: Account): Result<Account> {
        val dto = AccountDto(
            id = account.id,
            name = account.name,
            type = account.type.name.lowercase(),
            institution = account.institution,
            accountNumberLast4 = account.accountNumberLast4,
            balance = MoneyUtils.centsToDollars(account.currentBalanceCents),
            currentBalance = MoneyUtils.centsToDollars(account.currentBalanceCents),
            estimatedValue = account.estimatedValueCents?.let { MoneyUtils.centsToDollars(it) },
            isManual = account.isManual,
            isArchived = account.isArchived,
            sortOrder = account.sortOrder,
            mhaDefaultEligible = account.mhaDefaultEligible,
            simplefinAccountId = account.simplefinAccountId
        )
        return if (account.id == 0L) {
            apiClient.createAccount(dto).map { it.toDomain() }
        } else {
            apiClient.updateAccount(account.id, dto).map { it.toDomain() }
        }
    }

    override suspend fun deleteAccount(id: Long): Result<Boolean> = apiClient.deleteAccount(id)
    override suspend fun mergeAccounts(sourceId: Long, targetId: Long): Result<Boolean> = apiClient.mergeAccounts(sourceId, targetId)

    override suspend fun pushCategory(category: Category): Result<Category> {
        val dto = CategoryDto(
            id = category.id,
            name = category.name,
            color = category.color,
            icon = category.icon,
            isTransfer = category.isTransfer,
            isIncome = category.isIncome,
            sortOrder = category.sortOrder,
            mhaDefaultEligible = category.mhaDefaultEligible,
            mhaDefaultIgnored = category.mhaDefaultIgnored
        )
        return if (category.id == 0L) {
            apiClient.createCategory(dto).map { it.toDomain() }
        } else {
            apiClient.updateCategory(category.id, dto).map { it.toDomain() }
        }
    }

    override suspend fun deleteCategory(id: Long): Result<Boolean> = apiClient.deleteCategory(id)

    override suspend fun pushBudget(budget: CategoryBudget): Result<CategoryBudget> {
        val amountDollars = MoneyUtils.centsToDollars(budget.amountCents)
        return apiClient.upsertBudget(budget.categoryId, amountDollars, budget.rollover).map { it.toDomain() }
    }

    override suspend fun deleteBudget(id: Long): Result<Boolean> = apiClient.deleteBudget(id)

    override suspend fun pushGoal(goal: Goal): Result<Goal> {
        if (goal.allocations.isNotEmpty()) {
            val allocatedResult = apiClient.saveGoalWithAllocations(goal)
            if (allocatedResult.isSuccess) return allocatedResult
        }
        val dto = GoalDto(
            id = goal.id,
            name = goal.name,
            targetAmount = MoneyUtils.centsToDollars(goal.targetAmountCents),
            currentAmount = MoneyUtils.centsToDollars(goal.currentAmountCents),
            targetDate = goal.targetDate,
            linkedAccountId = goal.linkedAccountId,
            kind = goal.kind,
            icon = goal.icon,
            sortOrder = goal.sortOrder
        )
        return if (goal.id == 0L) {
            apiClient.createGoal(dto).map { it.toDomain() }
        } else {
            apiClient.updateGoal(goal.id, dto).map { it.toDomain() }
        }
    }

    override suspend fun deleteGoal(id: Long): Result<Boolean> = apiClient.deleteGoal(id)

    override suspend fun pushRule(rule: TransactionRule): Result<TransactionRule> {
        val dto = RuleWireMapper.toDto(rule)
        return if (rule.id == 0L) {
            apiClient.createRule(dto).map { it.toDomain() }
        } else {
            apiClient.updateRule(rule.id, dto).map { it.toDomain() }
        }
    }

    override suspend fun deleteRule(id: Long): Result<Boolean> = apiClient.deleteRule(id)
    override suspend fun runRules(): Result<Int> = apiClient.runRules()

    override suspend fun pushUpcoming(item: UpcomingItem): Result<UpcomingItem> {
        val kindStr = when (item.kind) {
            UpcomingKind.BILL -> "bill"
            UpcomingKind.SUBSCRIPTION -> "subscription"
            UpcomingKind.INCOME -> "income"
            else -> "bill"
        }
        val dto = UpcomingItemDto(
            id = item.id,
            name = item.name,
            amount = MoneyUtils.centsToDollars(item.amountCents),
            direction = item.direction,
            frequencyType = item.frequencyType.name.lowercase(),
            nextDate = item.nextDate,
            categoryId = item.categoryId,
            accountId = item.accountId,
            status = item.status.name.lowercase(),
            merchant = item.merchant,
            kind = kindStr,
            frequencyInterval = item.frequencyInterval,
            frequencyUnit = item.frequencyUnit,
            amountStrategy = item.amountStrategy.name.lowercase(),
            amountLookbackMonths = item.amountLookbackMonths,
            source = item.source.name.lowercase(),
            sourceTransactionId = item.sourceTransactionId,
            notes = item.notes
        )
        return if (item.id == 0L) {
            apiClient.createUpcoming(dto).map { it.toDomain() }
        } else {
            apiClient.updateUpcoming(item.id, dto).map { it.toDomain() }
        }
    }

    override suspend fun deleteUpcoming(id: Long): Result<Boolean> = apiClient.deleteUpcoming(id)

    override suspend fun pushPreferences(preferences: OrbitPreferences): Result<OrbitPreferences> {
        runCatching {
            val map = mutableMapOf<String, JsonElement>()
            map["amoledDarkMode"] = JsonPrimitive(preferences.amoledDarkMode)
            map["budgetSortOption"] = JsonPrimitive(preferences.budgetSortOption.name)
            map["dashboardRetirementAge"] = JsonPrimitive(preferences.dashboardRetirementAge)
            map["dashboardRetirementPreset"] = JsonPrimitive(preferences.dashboardRetirementPreset)
            map["includeHsaInRetirementRate"] = JsonPrimitive(preferences.includeHsaInRetirementRate)
            preferences.dashboardFocusedGoalId?.let { map["dashboardFocusedGoalId"] = JsonPrimitive(it) }
            preferences.simpleFinCutoverDate?.let { map["simpleFinCutoverDate"] = JsonPrimitive(it) }
            map["hiddenRouteNames"] = json.encodeToJsonElement(preferences.hiddenRouteNames)
            map["moreRouteOrder"] = json.encodeToJsonElement(preferences.moreRouteOrder)
            map["dashboardHiddenBiggestIds"] = json.encodeToJsonElement(preferences.dashboardHiddenBiggestIds)
            map["dismissedRecurringKeys"] = json.encodeToJsonElement(preferences.dismissedRecurringKeys)
            map["mutedSimpleFinAccounts"] = json.encodeToJsonElement(preferences.mutedSimpleFinAccounts)
            map["dashboardWidgets"] = json.encodeToJsonElement(preferences.dashboardWidgets)
            map["notificationPreferences"] = json.encodeToJsonElement(preferences.notificationPreferences)
            map["accountGroupWideSpans"] = json.encodeToJsonElement(preferences.accountGroupWideSpans)
            apiClient.putRemotePreferences(map)
        }
        return Result.success(preferences)
    }

    companion object {
        const val CACHE_SNAPSHOT_KEY = "orbit_remote_cache_snapshot.json"
    }
}
