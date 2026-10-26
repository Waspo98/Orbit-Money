package app.overbay.orbit.data

import app.overbay.orbit.data.engine.LocalSnapshotEngine
import app.overbay.orbit.data.engine.OrbitStorageEngine
import app.overbay.orbit.data.engine.SelfHostedApiEngine
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.SimpleFinDirectClient
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.RuleMatcherEngine
import app.overbay.orbit.domain.TransferMatcherEngine
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Single source of truth for the Orbit Money application.
 * Manages reactive in-memory state and delegates persistence to the active OrbitStorageEngine.
 */
class OrbitRepository(
    private val storage: PlatformStorage,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val customApiClient: OrbitApiClient? = null,
    private val customSimpleFinClient: SimpleFinDirectClient? = null
) {
    private val simpleFinClient = customSimpleFinClient ?: SimpleFinDirectClient()
    private val localEngine = LocalSnapshotEngine(storage, simpleFinClient)

    private var activeEngine: OrbitStorageEngine = localEngine

    private val _snapshot = MutableStateFlow(OrbitWorkspaceSnapshot.empty())
    val snapshot: StateFlow<OrbitWorkspaceSnapshot> = _snapshot.asStateFlow()

    private val _storageMode = MutableStateFlow(StorageMode.LOCAL)
    val storageMode: StateFlow<StorageMode> = _storageMode.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _budgets = MutableStateFlow<List<CategoryBudget>>(emptyList())
    val budgets: StateFlow<List<CategoryBudget>> = _budgets.asStateFlow()

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _rules = MutableStateFlow<List<TransactionRule>>(emptyList())
    val rules: StateFlow<List<TransactionRule>> = _rules.asStateFlow()

    private val _upcomingItems = MutableStateFlow<List<UpcomingItem>>(emptyList())
    val upcomingItems: StateFlow<List<UpcomingItem>> = _upcomingItems.asStateFlow()

    private val _preferences = MutableStateFlow(OrbitPreferences())
    val preferences: StateFlow<OrbitPreferences> = _preferences.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncResult>(SyncResult.NoOp)
    val syncStatus: StateFlow<SyncResult> = _syncStatus.asStateFlow()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun initialize() {
        // Load initial snapshot from local storage to inspect storageMode preference
        val localSnap = localEngine.loadSnapshot()
        val pref = localSnap.preferences
        _preferences.value = pref
        _storageMode.value = pref.storageMode

        activeEngine = if (pref.storageMode == StorageMode.SELF_HOSTED) {
            val client = customApiClient ?: OrbitApiClient({ pref.selfHostedServerUrl }, storage)
            SelfHostedApiEngine(client, storage)
        } else {
            localEngine
        }

        val loaded = activeEngine.loadSnapshot()
        updateMemoryState(loaded)
    }

    private fun updateMemoryState(newSnapshot: OrbitWorkspaceSnapshot) {
        _snapshot.value = newSnapshot
        _accounts.value = newSnapshot.accounts
        _transactions.value = newSnapshot.transactions
        _categories.value = newSnapshot.categories
        _budgets.value = newSnapshot.budgets
        _goals.value = newSnapshot.goals
        _rules.value = newSnapshot.rules
        _upcomingItems.value = newSnapshot.upcomingItems
        _preferences.value = newSnapshot.preferences
        _storageMode.value = newSnapshot.preferences.storageMode
    }

    private suspend fun commitSnapshot(updated: OrbitWorkspaceSnapshot) {
        updateMemoryState(updated)
        activeEngine.saveSnapshot(updated)
    }

    // -------------------------------------------------------------------------
    // Storage Mode Switch
    // -------------------------------------------------------------------------
    suspend fun setStorageMode(mode: StorageMode, serverUrl: String? = null) {
        val updatedPref = _preferences.value.copy(
            storageMode = mode,
            selfHostedServerUrl = serverUrl ?: _preferences.value.selfHostedServerUrl
        )
        _storageMode.value = mode
        _preferences.value = updatedPref

        activeEngine = if (mode == StorageMode.SELF_HOSTED) {
            val client = customApiClient ?: OrbitApiClient({ updatedPref.selfHostedServerUrl }, storage)
            SelfHostedApiEngine(client, storage)
        } else {
            localEngine
        }

        val snapshotToCommit = _snapshot.value.copy(preferences = updatedPref)
        commitSnapshot(snapshotToCommit)

        val fresh = activeEngine.loadSnapshot()
        updateMemoryState(fresh)
    }

    // -------------------------------------------------------------------------
    // Transactions (with Strict Provenance Preservation)
    // -------------------------------------------------------------------------
    suspend fun saveTransaction(transaction: Transaction) {
        val current = _transactions.value
        val exists = current.any { it.id == transaction.id && transaction.id != 0L }
        val updatedList = if (exists) {
            current.map { if (it.id == transaction.id) transaction else it }
        } else {
            val newId = if (transaction.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else transaction.id
            current + transaction.copy(id = newId)
        }

        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        activeEngine.pushTransaction(transaction)
    }

    /**
     * User edit: Strictly preserves original_* fields and writes to edited_* fields with editSource = USER.
     */
    suspend fun editTransaction(
        id: Long,
        merchant: String? = null,
        amountCents: Cents? = null,
        date: String? = null,
        categoryId: Long? = null,
        notes: String? = null
    ) {
        val current = _transactions.value
        val target = current.find { it.id == id } ?: return

        val updated = target.copy(
            editedMerchant = merchant ?: target.editedMerchant,
            editedMerchantSource = if (merchant != null) "user" else target.editedMerchantSource,
            editedAmountCents = amountCents ?: target.editedAmountCents,
            editedDate = date ?: target.editedDate,
            editedCategoryId = categoryId ?: target.editedCategoryId,
            editedCategoryIdSource = if (categoryId != null) "user" else target.editedCategoryIdSource,
            notes = notes ?: target.notes,
            editSource = EditSource.USER
        )

        val updatedList = current.map { if (it.id == id) updated else it }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        activeEngine.pushTransaction(updated)
    }

    /**
     * Resets user edits: Clears all edited_* fields and resets editSource to NONE.
     */
    suspend fun resetTransaction(id: Long) {
        val current = _transactions.value
        val target = current.find { it.id == id } ?: return

        val reset = target.copy(
            editedMerchant = null,
            editedMerchantSource = null,
            editedAmountCents = null,
            editedDate = null,
            editedCategoryId = null,
            editedCategoryIdSource = null,
            editedIsTransfer = null,
            editedIsTransferSource = null,
            editedIsIgnored = null,
            editedIsIgnoredSource = null,
            editedMhaEligible = null,
            editedMhaEligibleSource = null,
            editSource = EditSource.NONE
        )

        val updatedList = current.map { if (it.id == id) reset else it }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        activeEngine.resetTransaction(id)
    }

    suspend fun deleteTransaction(id: Long) {
        val updatedList = _transactions.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        activeEngine.deleteTransaction(id)
    }

    suspend fun batchEditCategory(ids: Set<Long>, categoryId: Long) {
        val current = _transactions.value
        val updatedList = current.map { txn ->
            if (txn.id in ids) {
                txn.copy(
                    editedCategoryId = categoryId,
                    editedCategoryIdSource = "user",
                    editSource = EditSource.USER
                )
            } else txn
        }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
    }

    suspend fun batchMarkTransfer(ids: Set<Long>, isTransfer: Boolean) {
        val current = _transactions.value
        val updatedList = current.map { txn ->
            if (txn.id in ids) {
                txn.copy(
                    editedIsTransfer = isTransfer,
                    editedIsTransferSource = "user",
                    editSource = EditSource.USER
                )
            } else txn
        }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
    }

    suspend fun batchSetMhaEligible(ids: Set<Long>, eligible: Boolean) {
        val current = _transactions.value
        val updatedList = current.map { txn ->
            if (txn.id in ids) {
                txn.copy(
                    editedMhaEligible = eligible,
                    editedMhaEligibleSource = "user",
                    editSource = EditSource.USER
                )
            } else txn
        }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
    }

    suspend fun batchSetIgnored(ids: Set<Long>, ignored: Boolean) {
        val current = _transactions.value
        val updatedList = current.map { txn ->
            if (txn.id in ids) {
                txn.copy(
                    editedIsIgnored = ignored,
                    editedIsIgnoredSource = "user",
                    editSource = EditSource.USER
                )
            } else txn
        }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
    }

    suspend fun batchDeleteTransactions(ids: Set<Long>) {
        val updatedList = _transactions.value.filterNot { it.id in ids }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
    }

    suspend fun importTransactions(
        newTransactions: List<Transaction>,
        newRules: List<TransactionRule> = emptyList()
    ) {
        val currentTxns = _transactions.value
        val currentMaxId = currentTxns.maxOfOrNull { it.id } ?: 0L
        var nextId = currentMaxId + 1L
        val assignedTxns = newTransactions.map { txn ->
            if (txn.id <= 0L || currentTxns.any { it.id == txn.id }) {
                txn.copy(id = nextId++)
            } else {
                if (txn.id >= nextId) nextId = txn.id + 1L
                txn
            }
        }
        val combinedTxns = currentTxns + assignedTxns

        val currentRules = _rules.value
        var nextRuleId = (currentRules.maxOfOrNull { it.id } ?: 0L) + 1L
        val assignedRules = newRules.map { rule ->
            if (rule.id <= 0L || currentRules.any { it.id == rule.id }) {
                rule.copy(id = nextRuleId++)
            } else rule
        }
        val combinedRules = currentRules + assignedRules

        commitSnapshot(_snapshot.value.copy(
            transactions = combinedTxns,
            rules = combinedRules
        ))
    }

    suspend fun splitTransaction(
        parentTransactionId: Long,
        splits: List<Pair<Cents, Long?>>
    ) {
        val current = _transactions.value
        val parent = current.find { it.id == parentTransactionId } ?: return
        if (splits.isEmpty()) return

        var nextId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
        val newTransactions = splits.mapIndexed { index, (amount, categoryId) ->
            if (index == 0) {
                parent.copy(
                    editedAmountCents = amount,
                    editedCategoryId = categoryId,
                    editedCategoryIdSource = "user",
                    editSource = EditSource.USER
                )
            } else {
                Transaction(
                    id = nextId++,
                    householdId = parent.householdId,
                    accountId = parent.accountId,
                    accountName = parent.accountName,
                    originalDate = parent.effectiveDate,
                    originalMerchant = parent.effectiveMerchant,
                    originalAmountCents = amount,
                    originalCategoryId = categoryId,
                    originalDescription = "Split from #${parent.id}: ${parent.notes ?: ""}".trim(),
                    externalId = "${parent.externalId ?: parent.id}_split_$index",
                    editSource = EditSource.USER,
                    source = "split"
                )
            }
        }

        val updatedList = current.map { if (it.id == parentTransactionId) newTransactions[0] else it } + newTransactions.drop(1)
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
    }


    // -------------------------------------------------------------------------
    // Accounts
    // -------------------------------------------------------------------------
    suspend fun saveAccount(account: Account) {
        val current = _accounts.value
        val exists = current.any { it.id == account.id && account.id != 0L }
        val updatedList = if (exists) {
            current.map { if (it.id == account.id) account else it }
        } else {
            val newId = if (account.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else account.id
            current + account.copy(id = newId)
        }
        commitSnapshot(_snapshot.value.copy(accounts = updatedList))
        activeEngine.pushAccount(account)
    }

    suspend fun deleteAccount(id: Long) {
        val updatedList = _accounts.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(accounts = updatedList))
        activeEngine.deleteAccount(id)
    }

    suspend fun mergeAccounts(sourceAccountId: Long, targetAccountId: Long, combineBalances: Boolean = false) {
        val currentAccounts = _accounts.value
        val source = currentAccounts.find { it.id == sourceAccountId } ?: return
        val target = currentAccounts.find { it.id == targetAccountId } ?: return

        val updatedTxns = _transactions.value.map { txn ->
            if (txn.accountId == sourceAccountId) {
                txn.copy(
                    accountId = targetAccountId,
                    accountName = target.name
                )
            } else txn
        }

        val updatedTarget = if (combineBalances) {
            target.copy(currentBalanceCents = target.currentBalanceCents + source.currentBalanceCents)
        } else target

        val remainingAccounts = currentAccounts
            .filterNot { it.id == sourceAccountId }
            .map { if (it.id == targetAccountId) updatedTarget else it }

        commitSnapshot(_snapshot.value.copy(
            accounts = remainingAccounts,
            transactions = updatedTxns
        ))
        activeEngine.deleteAccount(sourceAccountId)
        activeEngine.pushAccount(updatedTarget)
    }

    // -------------------------------------------------------------------------
    // Categories & Budgets
    // -------------------------------------------------------------------------
    suspend fun saveCategory(category: Category) {
        val current = _categories.value
        val exists = current.any { it.id == category.id && category.id != 0L }
        val updatedList = if (exists) {
            current.map { if (it.id == category.id) category else it }
        } else {
            val newId = if (category.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else category.id
            current + category.copy(id = newId)
        }
        commitSnapshot(_snapshot.value.copy(categories = updatedList))
        activeEngine.pushCategory(category)
    }

    suspend fun deleteCategory(id: Long) {
        val updatedList = _categories.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(categories = updatedList))
        activeEngine.deleteCategory(id)
    }

    suspend fun saveBudget(categoryId: Long, amountCents: Cents, rollover: Boolean = false) {
        val current = _budgets.value
        val existing = current.find { it.categoryId == categoryId }
        val updated = existing?.copy(amountCents = amountCents, rollover = rollover)
            ?: CategoryBudget(
                id = (current.maxOfOrNull { it.id } ?: 0L) + 1L,
                categoryId = categoryId,
                amountCents = amountCents,
                rollover = rollover
            )
        val updatedList = if (existing != null) {
            current.map { if (it.categoryId == categoryId) updated else it }
        } else {
            current + updated
        }
        commitSnapshot(_snapshot.value.copy(budgets = updatedList))
        activeEngine.pushBudget(updated)
    }

    suspend fun deleteBudget(id: Long) {
        val updatedList = _budgets.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(budgets = updatedList))
        activeEngine.deleteBudget(id)
    }

    // -------------------------------------------------------------------------
    // Goals & Rules
    // -------------------------------------------------------------------------
    suspend fun saveGoal(goal: Goal) {
        val current = _goals.value
        val exists = current.any { it.id == goal.id && goal.id != 0L }
        val updatedList = if (exists) {
            current.map { if (it.id == goal.id) goal else it }
        } else {
            val newId = if (goal.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else goal.id
            current + goal.copy(id = newId)
        }
        commitSnapshot(_snapshot.value.copy(goals = updatedList))
        activeEngine.pushGoal(goal)
    }

    suspend fun deleteGoal(id: Long) {
        val updatedList = _goals.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(goals = updatedList))
        activeEngine.deleteGoal(id)
    }

    suspend fun saveRule(rule: TransactionRule) {
        val current = _rules.value
        val exists = current.any { it.id == rule.id && rule.id != 0L }
        val updatedList = if (exists) {
            current.map { if (it.id == rule.id) rule else it }
        } else {
            val newId = if (rule.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else rule.id
            current + rule.copy(id = newId)
        }
        commitSnapshot(_snapshot.value.copy(rules = updatedList))
        activeEngine.pushRule(rule)
    }

    suspend fun deleteRule(id: Long) {
        val updatedList = _rules.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(rules = updatedList))
        activeEngine.deleteRule(id)
    }

    suspend fun runRulesOnAllTransactions() {
        val evaluated = RuleMatcherEngine.evaluateAll(_rules.value, _transactions.value)
        commitSnapshot(_snapshot.value.copy(transactions = evaluated))
        activeEngine.runRules()
    }

    suspend fun pairTransfers() {
        val paired = TransferMatcherEngine.matchTransfers(_transactions.value)
        commitSnapshot(_snapshot.value.copy(transactions = paired))
    }

    // -------------------------------------------------------------------------
    // Upcoming Items
    // -------------------------------------------------------------------------
    suspend fun saveUpcoming(item: UpcomingItem) {
        val current = _upcomingItems.value
        val exists = current.any { it.id == item.id && item.id != 0L }
        val updatedList = if (exists) {
            current.map { if (it.id == item.id) item else it }
        } else {
            val newId = if (item.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else item.id
            current + item.copy(id = newId)
        }
        commitSnapshot(_snapshot.value.copy(upcomingItems = updatedList))
        activeEngine.pushUpcoming(item)
    }

    suspend fun deleteUpcoming(id: Long) {
        val updatedList = _upcomingItems.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(upcomingItems = updatedList))
        activeEngine.deleteUpcoming(id)
    }

    // -------------------------------------------------------------------------
    // Preferences & Sync
    // -------------------------------------------------------------------------
    suspend fun updatePreferences(preferences: OrbitPreferences) {
        _preferences.value = preferences
        commitSnapshot(_snapshot.value.copy(preferences = preferences))
        activeEngine.pushPreferences(preferences)
    }

    suspend fun updateMhaTaxProfile(profile: MhaTaxProfile) {
        commitSnapshot(_snapshot.value.copy(mhaTaxProfile = profile))
    }

    suspend fun sync(force: Boolean = false): SyncResult {
        val result = activeEngine.sync(force)
        _syncStatus.value = result
        if (result is SyncResult.Success) {
            val fresh = activeEngine.loadSnapshot()
            updateMemoryState(fresh)
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Backup & Restore
    // -------------------------------------------------------------------------
    fun exportBackupJson(): String = json.encodeToString(OrbitWorkspaceSnapshot.serializer(), _snapshot.value)

    suspend fun restoreBackupJson(jsonString: String): Result<Unit> = runCatching {
        val restored = json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), jsonString)
        commitSnapshot(restored)
    }

    suspend fun loadDemoData(snapshot: OrbitWorkspaceSnapshot) {
        commitSnapshot(snapshot)
    }
}
