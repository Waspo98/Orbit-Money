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
import app.overbay.orbit.data.model.HouseholdInfo
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.SimpleFinStatusInfo
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.network.MerchantLogoSearchResponseDto
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.ServerUrlNormalizer
import app.overbay.orbit.data.network.SimpleFinDirectClient
import app.overbay.orbit.data.sync.CloudSyncManager
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.RuleMatcherEngine
import app.overbay.orbit.domain.TransferMatcherEngine
import app.overbay.orbit.util.OidcCallbackBridge
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Single source of truth for the Orbit Money application.
 * Manages reactive in-memory state and delegates persistence to the active OrbitStorageEngine.
 */
class OrbitRepository(
    internal val storage: PlatformStorage,
    internal val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val customApiClient: OrbitApiClient? = null,
    private val customSimpleFinClient: SimpleFinDirectClient? = null,
    val cloudSyncManager: CloudSyncManager = CloudSyncManager()
) {
    internal val simpleFinClient = customSimpleFinClient ?: SimpleFinDirectClient()
    internal val localEngine = LocalSnapshotEngine(storage, simpleFinClient)
    internal var activeEngine: OrbitStorageEngine = localEngine
    internal val flushCoordinator = SnapshotFlushCoordinator(scope, localEngine, cloudSyncManager)

    internal val _snapshot = MutableStateFlow(OrbitWorkspaceSnapshot.empty())
    val snapshot: StateFlow<OrbitWorkspaceSnapshot> = _snapshot.asStateFlow()
    internal val _storageMode = MutableStateFlow(StorageMode.LOCAL)
    val storageMode: StateFlow<StorageMode> = _storageMode.asStateFlow()
    internal val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    internal val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()
    internal val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    internal val _budgets = MutableStateFlow<List<CategoryBudget>>(emptyList())
    val budgets: StateFlow<List<CategoryBudget>> = _budgets.asStateFlow()
    internal val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()
    internal val _rules = MutableStateFlow<List<TransactionRule>>(emptyList())
    val rules: StateFlow<List<TransactionRule>> = _rules.asStateFlow()
    internal val _upcomingItems = MutableStateFlow<List<UpcomingItem>>(emptyList())
    val upcomingItems: StateFlow<List<UpcomingItem>> = _upcomingItems.asStateFlow()
    internal val _preferences = MutableStateFlow(OrbitPreferences())
    val preferences: StateFlow<OrbitPreferences> = _preferences.asStateFlow()
    internal val _syncStatus = MutableStateFlow<SyncResult>(SyncResult.NoOp)
    val syncStatus: StateFlow<SyncResult> = _syncStatus.asStateFlow()
    internal val _isAuthenticated = MutableStateFlow(true)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()
    internal val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    internal val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()
    internal val _simpleFinStatus = MutableStateFlow(SimpleFinStatusInfo())
    val simpleFinStatus: StateFlow<SimpleFinStatusInfo> = _simpleFinStatus.asStateFlow()
    internal val _pendingRuleIdToEdit = MutableStateFlow<Long?>(null)
    val pendingRuleIdToEdit: StateFlow<Long?> = _pendingRuleIdToEdit.asStateFlow()
    fun requestOpenRuleEditor(ruleId: Long?) { _pendingRuleIdToEdit.value = ruleId }
    internal val _pendingBudgetMonth = MutableStateFlow<String?>(null)
    val pendingBudgetMonth: StateFlow<String?> = _pendingBudgetMonth.asStateFlow()
    fun requestOpenBudgetMonth(month: String?) { _pendingBudgetMonth.value = month }
    internal val _pendingSettingsSubpage = MutableStateFlow<String?>(null)
    val pendingSettingsSubpage: StateFlow<String?> = _pendingSettingsSubpage.asStateFlow()
    fun requestOpenSettingsSubpage(subpage: String?) { _pendingSettingsSubpage.value = subpage }

    internal val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }
    internal val snapshotMutex = Mutex()
    internal var defaultApiClient: OrbitApiClient? = null
    internal var defaultClientUrl: String? = null

    fun createApiClient(serverUrl: String = _preferences.value.selfHostedServerUrl): OrbitApiClient {
        customApiClient?.let { return it }
        val resolvedUrl = serverUrl.ifBlank { _preferences.value.selfHostedServerUrl }
        val existing = defaultApiClient
        if (existing != null && defaultClientUrl == resolvedUrl) return existing
        existing?.close()
        return OrbitApiClient({ resolvedUrl.ifBlank { _preferences.value.selfHostedServerUrl } }, storage).also {
            defaultApiClient = it
            defaultClientUrl = resolvedUrl
        }
    }

    suspend fun initialize() {
        val pendingOidcUrl = storage.loadString(OidcCallbackBridge.PENDING_SERVER_URL_KEY)
        val startupPrefs = localEngine.loadStartupPreferences()
        val isFastSelfHostedStartup = startupPrefs != null &&
            (startupPrefs.storageMode == StorageMode.SELF_HOSTED || !pendingOidcUrl.isNullOrBlank())

        var localSnap: OrbitWorkspaceSnapshot? = null
        val basePref = if (isFastSelfHostedStartup) {
            startupPrefs
        } else {
            val loadedLocal = localEngine.loadSnapshot()
            localSnap = loadedLocal
            if (startupPrefs == null) {
                localEngine.saveStartupPreferences(loadedLocal.preferences)
            }
            loadedLocal.preferences
        }

        val pref = if (!pendingOidcUrl.isNullOrBlank()) {
            basePref.copy(storageMode = StorageMode.SELF_HOSTED, selfHostedServerUrl = if (pendingOidcUrl == "/") "" else pendingOidcUrl, onboardingCompleted = true)
        } else basePref
        _preferences.value = pref
        _storageMode.value = pref.storageMode
        if (pref.storageMode == StorageMode.SELF_HOSTED) {
            val client = createApiClient(pref.selfHostedServerUrl)
            val remoteEngine = SelfHostedApiEngine(client, storage) { _preferences.value }
            activeEngine = remoteEngine
            val cachedSnap = remoteEngine.loadCachedSnapshot()?.copy(preferences = pref) ?: (localSnap ?: localEngine.loadSnapshot()).copy(preferences = pref)
            val hasSession = !client.sessionCookie.isNullOrBlank() || cachedSnap.accounts.isNotEmpty() || cachedSnap.transactions.isNotEmpty()
            _isAuthenticated.value = hasSession
            updateMemoryState(cachedSnap)
            if (customApiClient == null) scope.launch { refreshSelfHostedSession(client, remoteEngine, pref, pendingOidcUrl, hasSession) }
            else refreshSelfHostedSession(client, remoteEngine, pref, pendingOidcUrl, hasSession)
        } else {
            _isAuthenticated.value = true
            activeEngine = localEngine
            updateMemoryState((localSnap ?: localEngine.loadSnapshot()).copy(preferences = pref))
        }
    }

    private suspend fun refreshSelfHostedSession(
        client: OrbitApiClient,
        remoteEngine: SelfHostedApiEngine,
        pref: OrbitPreferences,
        pendingOidcUrl: String?,
        hadCachedSession: Boolean
    ) {
        val meResult = client.getMe()
        val me = meResult.getOrNull()
        if (me != null && me.authenticated) {
            _isOffline.value = false
            storage.delete(OidcCallbackBridge.PENDING_SERVER_URL_KEY)
            _isAuthenticated.value = true
            _currentUser.value = me.displayName ?: me.username ?: me.email
            val loaded = remoteEngine.loadSnapshot(preFetchedMe = me).copy(preferences = pref)
            val existing = _snapshot.value
            val sameData = loaded.copy(preferences = loaded.preferences.copy(sharedSessionCachedAtEpochMs = existing.preferences.sharedSessionCachedAtEpochMs)) == existing
            if (!sameData) updateMemoryState(loaded) else _preferences.value = loaded.preferences
            localEngine.saveStartupPreferences(loaded.preferences)
        } else {
            val errMsg = meResult.exceptionOrNull()?.message.orEmpty()
            val is401 = (me != null && !me.authenticated) || errMsg.contains("401") || !hadCachedSession
            if (is401) {
                _isOffline.value = false
                _isAuthenticated.value = false
                _currentUser.value = null
            } else if (hadCachedSession) {
                _isOffline.value = true
                val cachedEpoch = pref.sharedSessionCachedAtEpochMs
                if (pref.isSharedNonOwnerSession && cachedEpoch != null) {
                    val ageMs = Clock.System.now().toEpochMilliseconds() - cachedEpoch
                    if (ageMs > 7L * 24 * 3600 * 1000L) {
                        _isAuthenticated.value = false
                        _currentUser.value = null
                        activeEngine.clear()
                    }
                }
            }
        }
    }

    internal fun updateMemoryState(newSnapshot: OrbitWorkspaceSnapshot) {
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

    internal suspend fun commitSnapshot(updated: OrbitWorkspaceSnapshot, immediate: Boolean = false) = snapshotMutex.withLock {
        updateMemoryState(updated)
        if (immediate) flushCoordinator.flushImmediate(updated, activeEngine)
        else flushCoordinator.scheduleFlush({ _snapshot.value }, { activeEngine })
    }

    suspend fun setStorageMode(mode: StorageMode, serverUrl: String? = null) {
        val normalizedUrl = serverUrl?.let { ServerUrlNormalizer.normalize(it) }
            ?: ServerUrlNormalizer.normalize(_preferences.value.selfHostedServerUrl)
        val updatedPref = _preferences.value.copy(storageMode = mode, selfHostedServerUrl = normalizedUrl)
        _storageMode.value = mode
        _preferences.value = updatedPref
        if (mode == StorageMode.LOCAL) _isAuthenticated.value = true
        activeEngine = if (mode == StorageMode.SELF_HOSTED) {
            SelfHostedApiEngine(createApiClient(normalizedUrl), storage) { _preferences.value }
        } else localEngine
        val loaded = activeEngine.loadSnapshot()
        val latestPref = _preferences.value.copy(
            storageMode = mode,
            selfHostedServerUrl = if (serverUrl != null) normalizedUrl else _preferences.value.selfHostedServerUrl
        )
        commitSnapshot(loaded.copy(preferences = latestPref), immediate = true)
    }

    suspend fun prepareForOidcLogin(serverUrl: String) {
        val normalizedUrl = ServerUrlNormalizer.normalize(serverUrl)
        storage.saveString(OidcCallbackBridge.PENDING_SERVER_URL_KEY, normalizedUrl.ifEmpty { "/" })
        val updatedPref = _preferences.value.copy(storageMode = StorageMode.SELF_HOSTED, selfHostedServerUrl = normalizedUrl)
        _preferences.value = updatedPref
        _storageMode.value = StorageMode.SELF_HOSTED
        _isAuthenticated.value = false
        localEngine.saveStartupPreferences(updatedPref)
    }

    suspend fun markAuthenticated(serverUrl: String, userLabel: String) {
        val normalizedUrl = ServerUrlNormalizer.normalize(serverUrl)
        val updatedPref = _preferences.value.copy(storageMode = StorageMode.SELF_HOSTED, selfHostedServerUrl = normalizedUrl, onboardingCompleted = true)
        _preferences.value = updatedPref
        _storageMode.value = StorageMode.SELF_HOSTED
        _isAuthenticated.value = true
        _currentUser.value = userLabel
        localEngine.saveStartupPreferences(updatedPref)
        activeEngine = SelfHostedApiEngine(createApiClient(normalizedUrl), storage) { _preferences.value }
        val fresh = activeEngine.loadSnapshot().copy(preferences = updatedPref)
        commitSnapshot(fresh, immediate = true)
        _syncStatus.value = SyncResult.Success("Signed in as $userLabel (${fresh.transactions.size} transactions)")
    }

    suspend fun logout() {
        _isAuthenticated.value = false
        _currentUser.value = null
        _syncStatus.value = SyncResult.NoOp
        runCatching { createApiClient().logout() }
        activeEngine.clear()
    }

    suspend fun completeOidcNativeExchange(code: String): Result<String> = runCatching {
        val pendingUrl = storage.loadString(OidcCallbackBridge.PENDING_SERVER_URL_KEY)
        storage.delete(OidcCallbackBridge.PENDING_SERVER_URL_KEY)
        val rawUrl = if (!pendingUrl.isNullOrBlank() && pendingUrl != "/") pendingUrl else _preferences.value.selfHostedServerUrl
        val serverUrl = ServerUrlNormalizer.normalize(rawUrl)
        val client = createApiClient(serverUrl)
        val authMe = client.exchangeOidcCode(code).getOrThrow()
        val userLabel = authMe.displayName ?: authMe.username ?: authMe.email ?: "SSO User"
        markAuthenticated(serverUrl, userLabel)
        userLabel
    }

    // Transactions (with Strict Provenance Preservation)
    suspend fun saveTransaction(transaction: Transaction) {
        val current = _transactions.value
        val exists = current.any { it.id == transaction.id && transaction.id != 0L }
        val localId = if (exists) transaction.id else if (transaction.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else transaction.id
        val updatedList = if (exists) current.map { if (it.id == transaction.id) transaction else it } else current + transaction.copy(id = localId)
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        val pushed = activeEngine.pushTransaction(if (exists) transaction else transaction.copy(id = 0L)).getOrNull()
        if (pushed != null && (pushed.id != localId || pushed.merchantLogo != transaction.merchantLogo)) {
            val syncedId = if (pushed.id > 0L) pushed.id else localId
            commitSnapshot(_snapshot.value.copy(transactions = _transactions.value.map { if (it.id == localId) it.copy(id = syncedId, merchantLogo = pushed.merchantLogo ?: it.merchantLogo) else it }))
        }
    }

    /** User edit: Strictly preserves original_* fields and writes to edited_* fields with editSource = USER. */
    suspend fun editTransaction(
        id: Long, merchant: String? = null, amountCents: Cents? = null,
        date: String? = null, categoryId: Long? = null, notes: String? = null
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
        commitSnapshot(_snapshot.value.copy(transactions = current.map { if (it.id == id) updated else it }))
        activeEngine.pushTransaction(updated)
    }

    /** Resets user edits: Clears all edited_* fields and resets editSource to NONE. */
    suspend fun resetTransaction(id: Long) {
        val current = _transactions.value
        val target = current.find { it.id == id } ?: return
        val reset = target.copy(
            editedMerchant = null, editedMerchantSource = null, editedAmountCents = null, editedDate = null,
            editedCategoryId = null, editedCategoryIdSource = null, editedIsTransfer = null, editedIsTransferSource = null,
            editedIsIgnored = null, editedIsIgnoredSource = null, editedMhaEligible = null, editedMhaEligibleSource = null,
            editSource = EditSource.NONE
        )
        commitSnapshot(_snapshot.value.copy(transactions = current.map { if (it.id == id) reset else it }))
        val pushed = activeEngine.resetTransaction(id).getOrNull()
        if (pushed != null && pushed.merchantLogo != reset.merchantLogo) {
            commitSnapshot(_snapshot.value.copy(transactions = _transactions.value.map { if (it.id == id) it.copy(merchantLogo = pushed.merchantLogo) else it }))
        }
    }

    suspend fun deleteTransaction(id: Long) {
        val updatedList = _transactions.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        activeEngine.deleteTransaction(id)
    }

    suspend fun batchEditCategory(ids: Set<Long>, categoryId: Long) {
        val updatedList = _transactions.value.map { if (it.id in ids) it.copy(editedCategoryId = categoryId, editedCategoryIdSource = "user", editSource = EditSource.USER) else it }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        updatedList.filter { it.id in ids }.forEach { activeEngine.pushTransaction(it) }
    }

    suspend fun batchMarkTransfer(ids: Set<Long>, isTransfer: Boolean) {
        val updatedList = _transactions.value.map { if (it.id in ids) it.copy(editedIsTransfer = isTransfer, editedIsTransferSource = "user", editSource = EditSource.USER) else it }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        updatedList.filter { it.id in ids }.forEach { activeEngine.pushTransaction(it) }
    }

    suspend fun batchSetMhaEligible(ids: Set<Long>, eligible: Boolean) {
        val updatedList = _transactions.value.map { if (it.id in ids) it.copy(editedMhaEligible = eligible, editedMhaEligibleSource = "user", editSource = EditSource.USER) else it }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        updatedList.filter { it.id in ids }.forEach { activeEngine.pushTransaction(it) }
    }

    suspend fun batchSetIgnored(ids: Set<Long>, ignored: Boolean) {
        val updatedList = _transactions.value.map { if (it.id in ids) it.copy(editedIsIgnored = ignored, editedIsIgnoredSource = "user", editSource = EditSource.USER) else it }
        commitSnapshot(_snapshot.value.copy(transactions = updatedList))
        updatedList.filter { it.id in ids }.forEach { activeEngine.pushTransaction(it) }
    }

    suspend fun batchDeleteTransactions(ids: Set<Long>) {
        commitSnapshot(_snapshot.value.copy(transactions = _transactions.value.filterNot { it.id in ids }))
        ids.forEach { activeEngine.deleteTransaction(it) }
    }


    suspend fun importTransactions(
        newTransactions: List<Transaction>,
        newRules: List<TransactionRule> = emptyList()
    ) {
        val currentTxns = _transactions.value
        var nextId = (currentTxns.maxOfOrNull { it.id } ?: 0L) + 1L
        val assignedTxns = newTransactions.map { txn ->
            if (txn.id <= 0L || currentTxns.any { it.id == txn.id }) txn.copy(id = nextId++)
            else {
                if (txn.id >= nextId) nextId = txn.id + 1L
                txn
            }
        }
        val currentRules = _rules.value
        var nextRuleId = (currentRules.maxOfOrNull { it.id } ?: 0L) + 1L
        val assignedRules = newRules.map { rule ->
            if (rule.id <= 0L || currentRules.any { it.id == rule.id }) rule.copy(id = nextRuleId++) else rule
        }
        commitSnapshot(_snapshot.value.copy(transactions = currentTxns + assignedTxns, rules = currentRules + assignedRules))
    }

    suspend fun splitTransaction(parentTransactionId: Long, splits: List<Pair<Cents, Long?>>) {
        val current = _transactions.value
        val parent = current.find { it.id == parentTransactionId } ?: return
        if (splits.isEmpty()) return
        var nextId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
        val newTransactions = splits.mapIndexed { index, (amount, categoryId) ->
            if (index == 0) {
                parent.copy(editedAmountCents = amount, editedCategoryId = categoryId, editedCategoryIdSource = "user", editSource = EditSource.USER)
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
        activeEngine.pushTransaction(newTransactions[0])
        newTransactions.drop(1).forEach { activeEngine.pushTransaction(it.copy(id = 0L)) }
    }

    // Accounts
    suspend fun saveAccount(account: Account) {
        val current = _accounts.value
        val exists = current.any { it.id == account.id && account.id != 0L }
        val localId = if (exists) account.id else if (account.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else account.id
        val updatedList = if (exists) current.map { if (it.id == account.id) account else it } else current + account.copy(id = localId)
        commitSnapshot(_snapshot.value.copy(accounts = updatedList))
        val pushed = activeEngine.pushAccount(if (exists) account else account.copy(id = 0L)).getOrNull()
        if (pushed != null && pushed.id > 0L && pushed.id != localId) {
            commitSnapshot(_snapshot.value.copy(accounts = _accounts.value.map { if (it.id == localId) it.copy(id = pushed.id) else it }))
        }
    }

    suspend fun deleteAccount(id: Long) {
        commitSnapshot(_snapshot.value.copy(accounts = _accounts.value.filterNot { it.id == id }))
        activeEngine.deleteAccount(id)
    }

    suspend fun mergeAccounts(sourceAccountId: Long, targetAccountId: Long, combineBalances: Boolean = false) {
        val currentAccounts = _accounts.value
        val source = currentAccounts.find { it.id == sourceAccountId } ?: return
        val target = currentAccounts.find { it.id == targetAccountId } ?: return
        val updatedTxns = _transactions.value.map { txn ->
            if (txn.accountId == sourceAccountId) txn.copy(accountId = targetAccountId, accountName = target.name) else txn
        }
        val updatedTarget = if (combineBalances) target.copy(currentBalanceCents = target.currentBalanceCents + source.currentBalanceCents) else target
        val remainingAccounts = currentAccounts.filterNot { it.id == sourceAccountId }.map { if (it.id == targetAccountId) updatedTarget else it }
        commitSnapshot(_snapshot.value.copy(accounts = remainingAccounts, transactions = updatedTxns))
        activeEngine.mergeAccounts(sourceAccountId, targetAccountId)
        activeEngine.pushAccount(updatedTarget)
    }

    // Categories & Budgets
    suspend fun saveCategory(category: Category) {
        val current = _categories.value
        val exists = current.any { it.id == category.id && category.id != 0L }
        val localId = if (exists) category.id else if (category.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else category.id
        val updatedList = if (exists) current.map { if (it.id == category.id) category else it } else current + category.copy(id = localId)
        commitSnapshot(_snapshot.value.copy(categories = updatedList))
        val pushed = activeEngine.pushCategory(if (exists) category else category.copy(id = 0L)).getOrNull()
        if (pushed != null && pushed.id > 0L && pushed.id != localId) {
            commitSnapshot(_snapshot.value.copy(categories = _categories.value.map { if (it.id == localId) it.copy(id = pushed.id) else it }))
        }
    }

    suspend fun deleteCategory(id: Long) {
        commitSnapshot(_snapshot.value.copy(categories = _categories.value.filterNot { it.id == id }))
        activeEngine.deleteCategory(id)
    }

    suspend fun saveBudget(categoryId: Long, amountCents: Cents, rollover: Boolean = false) {
        val current = _budgets.value
        val existing = current.find { it.categoryId == categoryId }
        val updated = existing?.copy(amountCents = amountCents, rollover = rollover)
            ?: CategoryBudget(id = (current.maxOfOrNull { it.id } ?: 0L) + 1L, categoryId = categoryId, amountCents = amountCents, rollover = rollover)
        val updatedList = if (existing != null) current.map { if (it.categoryId == categoryId) updated else it } else current + updated
        commitSnapshot(_snapshot.value.copy(budgets = updatedList))
        activeEngine.pushBudget(updated)
    }

    suspend fun deleteBudget(id: Long) {
        commitSnapshot(_snapshot.value.copy(budgets = _budgets.value.filterNot { it.id == id }))
        activeEngine.deleteBudget(id)
    }

    // Goals & Rules
    suspend fun saveGoal(goal: Goal) {
        val current = _goals.value
        val exists = current.any { it.id == goal.id && goal.id != 0L }
        val localId = if (exists) goal.id else if (goal.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else goal.id
        val updatedList = if (exists) current.map { if (it.id == goal.id) goal else it } else current + goal.copy(id = localId)
        commitSnapshot(_snapshot.value.copy(goals = updatedList))
        val pushed = activeEngine.pushGoal(if (exists) goal else goal.copy(id = 0L)).getOrNull()
        if (pushed != null && pushed.id > 0L && pushed.id != localId) {
            commitSnapshot(_snapshot.value.copy(goals = _goals.value.map { if (it.id == localId) it.copy(id = pushed.id) else it }))
        }
    }

    suspend fun deleteGoal(id: Long) {
        commitSnapshot(_snapshot.value.copy(goals = _goals.value.filterNot { it.id == id }))
        activeEngine.deleteGoal(id)
    }

    suspend fun saveRule(rule: TransactionRule) {
        val current = _rules.value
        val exists = current.any { it.id == rule.id && rule.id != 0L }
        val localId = if (exists) rule.id else if (rule.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else rule.id
        val saved = if (exists) rule else rule.copy(id = localId)
        val updatedRules = if (exists) current.map { if (it.id == rule.id) saved else it } else current + saved
        val updatedTxns = if (!exists && saved.enabled) RuleMatcherEngine.applyNewRuleIncrementally(_transactions.value, updatedRules, saved)
        else RuleMatcherEngine.applyRules(_transactions.value, updatedRules)
        commitSnapshot(_snapshot.value.copy(rules = updatedRules, transactions = updatedTxns))
        val pushed = activeEngine.pushRule(if (exists) rule else rule.copy(id = 0L)).getOrNull()
        if (pushed != null && pushed.id > 0L && pushed.id != localId) {
            commitSnapshot(_snapshot.value.copy(
                rules = _rules.value.map { if (it.id == localId) it.copy(id = pushed.id) else it },
                transactions = RuleMatcherEngine.remapRuleSourceTag(_transactions.value, localId, pushed.id)
            ))
        }
    }

    suspend fun deleteRule(id: Long) {
        val remaining = _rules.value.filterNot { it.id == id }
        commitSnapshot(_snapshot.value.copy(rules = remaining, transactions = RuleMatcherEngine.applyRules(_transactions.value, remaining)))
        activeEngine.deleteRule(id)
    }

    suspend fun runRulesOnAllTransactions() {
        val evaluated = RuleMatcherEngine.evaluateAll(_rules.value, _transactions.value)
        commitSnapshot(_snapshot.value.copy(transactions = evaluated))
        activeEngine.runRules()
    }

    suspend fun pairTransfers() = commitSnapshot(_snapshot.value.copy(transactions = TransferMatcherEngine.matchTransfers(_transactions.value)))

    // Upcoming Items
    suspend fun saveUpcoming(item: UpcomingItem) {
        val current = _upcomingItems.value
        val exists = current.any { it.id == item.id && item.id != 0L }
        val localId = if (exists) item.id else if (item.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else item.id
        val updatedList = if (exists) current.map { if (it.id == item.id) item else it } else current + item.copy(id = localId)
        commitSnapshot(_snapshot.value.copy(upcomingItems = updatedList))
        val pushed = activeEngine.pushUpcoming(if (exists) item else item.copy(id = 0L)).getOrNull()
        if (pushed != null && pushed.id > 0L && pushed.id != localId) {
            commitSnapshot(_snapshot.value.copy(upcomingItems = _upcomingItems.value.map { if (it.id == localId) it.copy(id = pushed.id) else it }))
        }
    }

    suspend fun deleteUpcoming(id: Long) {
        commitSnapshot(_snapshot.value.copy(upcomingItems = _upcomingItems.value.filterNot { it.id == id }))
        activeEngine.deleteUpcoming(id)
    }

    // Preferences & Sync
    suspend fun updatePreferences(preferences: OrbitPreferences) {
        _preferences.value = preferences
        _storageMode.value = preferences.storageMode
        commitSnapshot(_snapshot.value.copy(preferences = preferences), immediate = true)
        activeEngine.pushPreferences(preferences)
    }

    suspend fun sync(force: Boolean = false): SyncResult {
        val result = activeEngine.sync(force)
        _syncStatus.value = result
        if (result is SyncResult.Success) {
            val fresh = (activeEngine as? SelfHostedApiEngine)?.consumeLastSyncedSnapshot() ?: activeEngine.loadSnapshot()
            commitSnapshot(fresh, immediate = true)
        }
        return result
    }

    suspend fun reportMerchantLogoStatus(merchantKey: String, status: String) = reportMerchantLogoStatusImpl(merchantKey, status)
    suspend fun searchMerchantLogos(transactionId: Long, query: String): Result<MerchantLogoSearchResponseDto> = searchMerchantLogosImpl(transactionId, query)
    suspend fun overrideMerchantLogo(transactionId: Long, logoUrl: String? = null, useCategoryIcon: Boolean = false): Result<MerchantLogo> = overrideMerchantLogoImpl(transactionId, logoUrl, useCategoryIcon)
    fun exportBackupJson(): String = exportBackupJsonImpl()
    suspend fun restoreBackupJson(jsonString: String): Result<Unit> = restoreBackupJsonImpl(jsonString)
    suspend fun loadDemoData(snapshot: OrbitWorkspaceSnapshot) = loadDemoDataImpl(snapshot)
    suspend fun updateMhaTaxProfile(profile: MhaTaxProfile) = updateMhaTaxProfileImpl(profile)
    suspend fun updateHouseholdInfo(household: HouseholdInfo) = updateHouseholdInfoImpl(household)
    suspend fun saveHouseholdMember(member: HouseholdMember, effectiveDate: String? = null) = saveHouseholdMemberImpl(member, effectiveDate)
    suspend fun deleteHouseholdMember(id: Long) = deleteHouseholdMemberImpl(id)
    suspend fun saveHouseholdShare(share: HouseholdShare) = saveHouseholdShareImpl(share)
    suspend fun deleteHouseholdShare(id: Long) = deleteHouseholdShareImpl(id)
}
