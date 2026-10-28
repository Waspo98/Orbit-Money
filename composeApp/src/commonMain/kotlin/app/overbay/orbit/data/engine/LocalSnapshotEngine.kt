package app.overbay.orbit.data.engine

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.network.SimpleFinDirectClient
import app.overbay.orbit.util.PlatformStorage
import kotlinx.serialization.json.Json

/**
 * Local-First single-user storage engine.
 * Persists the complete OrbitWorkspaceSnapshot atomically to disk via PlatformStorage
 * (Okio on Android, localStorage on WasmJs).
 */
class LocalSnapshotEngine(
    private val storage: PlatformStorage,
    private val simpleFinClient: SimpleFinDirectClient? = null
) : OrbitStorageEngine {

    override val mode: StorageMode = StorageMode.LOCAL

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override suspend fun loadSnapshot(): OrbitWorkspaceSnapshot {
        val raw = storage.loadString(SNAPSHOT_KEY) ?: return OrbitWorkspaceSnapshot.empty()
        return try {
            json.decodeFromString(OrbitWorkspaceSnapshot.serializer(), raw)
        } catch (e: Throwable) {
            // Backup corrupt file and return empty fallback
            storage.saveString("${SNAPSHOT_KEY}.corrupt_backup", raw)
            OrbitWorkspaceSnapshot.empty()
        }
    }

    override suspend fun saveSnapshot(snapshot: OrbitWorkspaceSnapshot) {
        val serialized = json.encodeToString(OrbitWorkspaceSnapshot.serializer(), snapshot)
        storage.saveString(SNAPSHOT_KEY, serialized)
    }

    override suspend fun sync(force: Boolean): SyncResult {
        // Direct device-to-SimpleFIN sync if configured in preferences
        val snapshot = loadSnapshot()
        val token = snapshot.preferences.simpleFinToken
        if (token.isNullOrBlank() || simpleFinClient == null) {
            return SyncResult.NoOp
        }

        return try {
            val syncResult = simpleFinClient.syncWithSnapshot(token, snapshot)
            saveSnapshot(syncResult.updatedSnapshot)
            SyncResult.Success("Synced ${syncResult.newTransactionCount} transactions directly from SimpleFIN")
        } catch (e: Throwable) {
            SyncResult.Error("Direct SimpleFIN sync failed: ${e.message}", e)
        }
    }

    override suspend fun clear() {
        storage.delete(SNAPSHOT_KEY)
    }

    // In Local Mode, state mutations are committed through OrbitRepository saving the snapshot.
    override suspend fun pushTransaction(transaction: Transaction): Result<Transaction> = Result.success(transaction)
    override suspend fun deleteTransaction(id: Long): Result<Boolean> = Result.success(true)
    override suspend fun resetTransaction(id: Long): Result<Transaction> {
        val found = loadSnapshot().transactions.find { it.id == id }
        return if (found != null) Result.success(found) else Result.failure(NoSuchElementException("Transaction $id not found"))
    }
    override suspend fun pushAccount(account: Account): Result<Account> = Result.success(account)
    override suspend fun deleteAccount(id: Long): Result<Boolean> = Result.success(true)
    override suspend fun pushCategory(category: Category): Result<Category> = Result.success(category)
    override suspend fun deleteCategory(id: Long): Result<Boolean> = Result.success(true)
    override suspend fun pushBudget(budget: CategoryBudget): Result<CategoryBudget> = Result.success(budget)
    override suspend fun deleteBudget(id: Long): Result<Boolean> = Result.success(true)
    override suspend fun pushGoal(goal: Goal): Result<Goal> = Result.success(goal)
    override suspend fun deleteGoal(id: Long): Result<Boolean> = Result.success(true)
    override suspend fun pushRule(rule: TransactionRule): Result<TransactionRule> = Result.success(rule)
    override suspend fun deleteRule(id: Long): Result<Boolean> = Result.success(true)
    override suspend fun runRules(): Result<Int> = Result.success(0)
    override suspend fun pushUpcoming(item: UpcomingItem): Result<UpcomingItem> = Result.success(item)
    override suspend fun deleteUpcoming(id: Long): Result<Boolean> = Result.success(true)
    override suspend fun pushPreferences(preferences: OrbitPreferences): Result<OrbitPreferences> = Result.success(preferences)

    companion object {
        const val SNAPSHOT_KEY = "orbit_workspace_snapshot.json"
    }
}
