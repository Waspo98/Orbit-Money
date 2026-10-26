package app.overbay.orbit.data.engine

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.HouseholdInfo
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.network.AccountDto
import app.overbay.orbit.data.network.CategoryDto
import app.overbay.orbit.data.network.GoalDto
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.RuleActionDto
import app.overbay.orbit.data.network.RuleConditionDto
import app.overbay.orbit.data.network.RuleDto
import app.overbay.orbit.data.network.TransactionDto
import app.overbay.orbit.data.network.UpcomingItemDto
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 * Storage engine communicating with the self-hosted Express API endpoints.
 * Caches the complete OrbitWorkspaceSnapshot locally via PlatformStorage so cold app starts
 * take 0ms and data remains readable offline.
 */
class SelfHostedApiEngine(
    private val apiClient: OrbitApiClient,
    private val storage: PlatformStorage
) : OrbitStorageEngine {

    override val mode: StorageMode = StorageMode.SELF_HOSTED

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun loadSnapshot(): OrbitWorkspaceSnapshot {
        // Attempt network fetch first; fall back to local offline cache
        return try {
            fetchRemoteSnapshot().also { freshSnapshot ->
                saveSnapshot(freshSnapshot)
            }
        } catch (_: Throwable) {
            loadCachedSnapshot() ?: OrbitWorkspaceSnapshot.empty()
        }
    }

    private suspend fun fetchRemoteSnapshot(): OrbitWorkspaceSnapshot = coroutineScope {
        val accountsDeferred = async { apiClient.getAccounts().getOrThrow() }
        val transactionsDeferred = async { apiClient.getTransactions(page = 1, limit = 200).getOrThrow() }
        val categoriesDeferred = async { apiClient.getCategories().getOrThrow() }
        val budgetsDeferred = async { apiClient.getBudgets("").getOrNull() }
        val goalsDeferred = async { apiClient.getGoals().getOrNull() ?: emptyList() }
        val rulesDeferred = async { apiClient.getRules().getOrNull() ?: emptyList() }
        val upcomingDeferred = async { apiClient.getUpcoming().getOrNull() ?: emptyList() }
        val authMeDeferred = async { apiClient.getMe().getOrNull() }

        val accountsDto = accountsDeferred.await()
        val txResponseDto = transactionsDeferred.await()
        val categoriesDto = categoriesDeferred.await()
        val budgetsDto = budgetsDeferred.await()
        val goalsDto = goalsDeferred.await()
        val rulesDto = rulesDeferred.await()
        val upcomingDto = upcomingDeferred.await()
        val authMe = authMeDeferred.await()

        OrbitWorkspaceSnapshot(
            schemaVersion = OrbitWorkspaceSnapshot.CURRENT_SCHEMA_VERSION,
            exportedAt = "",
            household = HouseholdInfo(
                id = authMe?.household?.id ?: 1L,
                name = authMe?.household?.name ?: "My Household"
            ),
            accounts = accountsDto.map { it.toDomain() },
            categories = categoriesDto.map { it.toDomain() },
            transactions = txResponseDto.items.map { it.toDomain() },
            budgets = budgetsDto?.budgets?.map { it.toDomain() } ?: emptyList(),
            goals = goalsDto.map { it.toDomain() },
            rules = rulesDto.map { it.toDomain() },
            upcomingItems = upcomingDto.map { it.toDomain() },
            preferences = OrbitPreferences(storageMode = StorageMode.SELF_HOSTED)
        )
    }

    private fun loadCachedSnapshot(): OrbitWorkspaceSnapshot? {
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
            val syncResult = apiClient.triggerSimpleFinSync().getOrThrow()
            val fresh = fetchRemoteSnapshot()
            saveSnapshot(fresh)
            SyncResult.Success("Remote sync succeeded: ${syncResult.transactionsInserted} new transactions inserted")
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
    override suspend fun resetTransaction(id: Long): Result<Transaction> = apiClient.resetTransaction(id).map { it.toDomain() }

    override suspend fun pushAccount(account: Account): Result<Account> {
        val dto = AccountDto(
            id = account.id,
            name = account.name,
            type = account.type.name.lowercase(),
            institution = account.institution,
            accountNumberLast4 = account.accountNumberLast4,
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
        val dto = GoalDto(
            id = goal.id,
            name = goal.name,
            targetAmount = MoneyUtils.centsToDollars(goal.targetAmountCents),
            currentAmount = MoneyUtils.centsToDollars(goal.currentAmountCents),
            targetDate = goal.targetDate,
            linkedAccountId = goal.linkedAccountId,
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
        val dto = RuleDto(
            id = rule.id,
            name = rule.name,
            conditions = rule.conditions.map { RuleConditionDto(it.field.name.lowercase(), it.operator.name.lowercase(), it.value) },
            actions = rule.actions.map { RuleActionDto(it.type.name.lowercase(), it.value) },
            priority = rule.priority,
            enabled = rule.enabled
        )
        return if (rule.id == 0L) {
            apiClient.createRule(dto).map { it.toDomain() }
        } else {
            apiClient.updateRule(rule.id, dto).map { it.toDomain() }
        }
    }

    override suspend fun deleteRule(id: Long): Result<Boolean> = apiClient.deleteRule(id)
    override suspend fun runRules(): Result<Int> = apiClient.runRules()

    override suspend fun pushUpcoming(item: UpcomingItem): Result<UpcomingItem> {
        val dto = UpcomingItemDto(
            id = item.id,
            name = item.name,
            amount = MoneyUtils.centsToDollars(item.amountCents),
            direction = item.direction,
            frequencyType = item.frequencyType.name.lowercase(),
            nextDate = item.nextDate,
            categoryId = item.categoryId,
            accountId = item.accountId,
            status = item.status.name.lowercase()
        )
        return if (item.id == 0L) {
            apiClient.createUpcoming(dto).map { it.toDomain() }
        } else {
            apiClient.updateUpcoming(item.id, dto).map { it.toDomain() }
        }
    }

    override suspend fun deleteUpcoming(id: Long): Result<Boolean> = apiClient.deleteUpcoming(id)
    override suspend fun pushPreferences(preferences: OrbitPreferences): Result<OrbitPreferences> = Result.success(preferences)

    companion object {
        const val CACHE_SNAPSHOT_KEY = "orbit_remote_cache_snapshot.json"
    }
}
