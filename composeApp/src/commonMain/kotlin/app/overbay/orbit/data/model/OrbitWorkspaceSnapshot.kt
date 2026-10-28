package app.overbay.orbit.data.model

import kotlinx.serialization.Serializable

/**
 * Root serializable container representing the complete user workspace.
 * Used for:
 * 1. Single-User Local Mode disk persistence via Okio (Android) / IndexedDB (Wasm).
 * 2. 0ms instantaneous cold-start cache for Self-Hosted Remote Mode.
 * 3. 1-tap Google Drive appDataFolder backup/restore (.orbit.json).
 */
@Serializable
data class OrbitWorkspaceSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: String = "", // ISO UTC timestamp
    val household: HouseholdInfo = HouseholdInfo(),
    val members: List<HouseholdMember> = emptyList(),
    val shares: List<HouseholdShare> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val creditCardProfiles: List<CreditCardProfile> = emptyList(),
    val balanceRecords: List<BalanceRecord> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<CategoryBudget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val rules: List<TransactionRule> = emptyList(),
    val upcomingItems: List<UpcomingItem> = emptyList(),
    val upcomingOccurrences: List<ProjectedOccurrence> = emptyList(),
    val preferences: OrbitPreferences = OrbitPreferences(),
    val mhaTaxProfile: MhaTaxProfile = MhaTaxProfile()
) {
    val householdMembers: List<HouseholdMember> get() = members

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1

        fun empty(exportedAt: String = ""): OrbitWorkspaceSnapshot =
            OrbitWorkspaceSnapshot(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                exportedAt = exportedAt,
                household = HouseholdInfo(id = 1L, name = "My Household"),
                categories = emptyList(),
                accounts = emptyList(),
                transactions = emptyList()
            )
    }
}
