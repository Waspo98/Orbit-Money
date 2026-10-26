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

/**
 * Result of a background or user-initiated synchronization event.
 */
sealed interface SyncResult {
    data class Success(val message: String = "Sync completed successfully") : SyncResult
    data object NoOp : SyncResult
    data class Error(val message: String, val throwable: Throwable? = null) : SyncResult
}

/**
 * Common storage engine interface abstracting single-user local disk snapshot persistence
 * from multi-user self-hosted Express API communication.
 */
interface OrbitStorageEngine {
    val mode: StorageMode

    suspend fun loadSnapshot(): OrbitWorkspaceSnapshot
    suspend fun saveSnapshot(snapshot: OrbitWorkspaceSnapshot)
    suspend fun sync(force: Boolean = false): SyncResult
    suspend fun clear()

    // Granular mutation push hooks (for SelfHostedApiEngine remote sync; LocalSnapshotEngine persists via snapshot)
    suspend fun pushTransaction(transaction: Transaction): Result<Transaction>
    suspend fun deleteTransaction(id: Long): Result<Boolean>
    suspend fun resetTransaction(id: Long): Result<Transaction>

    suspend fun pushAccount(account: Account): Result<Account>
    suspend fun deleteAccount(id: Long): Result<Boolean>

    suspend fun pushCategory(category: Category): Result<Category>
    suspend fun deleteCategory(id: Long): Result<Boolean>

    suspend fun pushBudget(budget: CategoryBudget): Result<CategoryBudget>
    suspend fun deleteBudget(id: Long): Result<Boolean>

    suspend fun pushGoal(goal: Goal): Result<Goal>
    suspend fun deleteGoal(id: Long): Result<Boolean>

    suspend fun pushRule(rule: TransactionRule): Result<TransactionRule>
    suspend fun deleteRule(id: Long): Result<Boolean>
    suspend fun runRules(): Result<Int>

    suspend fun pushUpcoming(item: UpcomingItem): Result<UpcomingItem>
    suspend fun deleteUpcoming(id: Long): Result<Boolean>

    suspend fun pushPreferences(preferences: OrbitPreferences): Result<OrbitPreferences>
}
