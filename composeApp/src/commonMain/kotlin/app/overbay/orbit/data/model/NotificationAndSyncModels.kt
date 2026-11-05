package app.overbay.orbit.data.model

import app.overbay.orbit.domain.Cents
import kotlinx.serialization.Serializable

@Serializable
data class NotificationTypeSchedule(
    val enabled: Boolean = false,
    val time: String? = null,
    val dayOfWeek: Int? = null,
    val minAmountCents: Cents? = null,
    val accountIds: List<Long> = emptyList()
)

@Serializable
data class NotificationPreferencesConfig(
    val enabled: Boolean = false,
    val showAmounts: Boolean = true,
    val defaultTime: String = "09:00",
    val weeklySnapshot: NotificationTypeSchedule = NotificationTypeSchedule(enabled = true, dayOfWeek = 1, time = "09:00"),
    val income: NotificationTypeSchedule = NotificationTypeSchedule(enabled = true, minAmountCents = 10_000L),
    val syncIssues: NotificationTypeSchedule = NotificationTypeSchedule(enabled = true),
    val accountSnapshots: NotificationTypeSchedule = NotificationTypeSchedule(enabled = false, dayOfWeek = 1, time = "09:00")
)

@Serializable
data class PushNotificationConfigInfo(
    val configured: Boolean = false,
    val vapidPublicKey: String? = null,
    val activeSubscriptionCount: Int = 0
)

@Serializable
data class SimpleFinStatusInfo(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val syncEnabled: Boolean = false,
    val cutoverDate: String? = null,
    val lastSyncAt: String? = null,
    val lastSyncStatus: String? = null,
    val lastSyncError: String? = null,
    val bridgeUrl: String = "https://beta-bridge.simplefin.org"
)

@Serializable
data class SimpleFinSyncLogEntry(
    val id: Long = 0L,
    val startedAt: String = "",
    val finishedAt: String? = null,
    val status: String = "",
    val accountsSynced: Int = 0,
    val transactionsInserted: Int = 0,
    val transactionsUpdated: Int = 0,
    val errorMessage: String? = null
)

@Serializable
data class ImportBatchRecord(
    val id: Long = 0L,
    val source: String = "rocket_money",
    val fileName: String = "",
    val importedAt: String = "",
    val totalRows: Int = 0,
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val transactionIds: List<Long> = emptyList(),
    val createdAccountIds: List<Long> = emptyList(),
    val createdCategoryIds: List<Long> = emptyList(),
    val createdRuleIds: List<Long> = emptyList()
)

@Serializable
data class CsvImportPreviewInfo(
    val previewId: Long? = null,
    val fileName: String = "",
    val totalRows: Int = 0,
    val newTransactionCount: Int = 0,
    val duplicateCount: Int = 0,
    val newAccountNames: List<String> = emptyList(),
    val newCategoryNames: List<String> = emptyList(),
    val dateRangeStart: String? = null,
    val dateRangeEnd: String? = null
)

@Serializable
data class BackupPreviewStats(
    val schemaVersion: Int = 1,
    val exportedAt: String = "",
    val accountCount: Int = 0,
    val transactionCount: Int = 0,
    val categoryCount: Int = 0,
    val budgetCount: Int = 0,
    val goalCount: Int = 0,
    val ruleCount: Int = 0,
    val upcomingCount: Int = 0,
    val memberCount: Int = 0,
    val balanceRecordCount: Int = 0
)

@Serializable
data class HouseholdUserAccess(
    val id: Long = 0L,
    val email: String? = null,
    val username: String? = null,
    val displayName: String = "",
    val role: String = "member",
    val accessLevel: String = "write"
)

@Serializable
data class HouseholdSharingState(
    val householdId: Long = 1L,
    val householdName: String = "My Household",
    val currentUserId: Long = 0L,
    val currentUserRole: String = "owner",
    val currentUserAccessLevel: String = "write",
    val canManageSharing: Boolean = true,
    val canRemoveUsers: Boolean = true,
    val users: List<HouseholdUserAccess> = emptyList(),
    val shares: List<HouseholdShare> = emptyList()
)

@Serializable
data class RetirementAccountMonthBalance(
    val accountId: Long,
    val balanceCents: Cents
)

@Serializable
data class RetirementHistoryPoint(
    val month: String,
    val balanceCents: Cents,
    val accounts: List<RetirementAccountMonthBalance> = emptyList()
)

@Serializable
data class GoalMonthlyPoint(
    val month: String,
    val amountCents: Cents
)

@Serializable
data class GoalEtaInfo(
    val date: String? = null,
    val months: Int? = null,
    val status: String = "projected"
)

@Serializable
data class AccountAllocationSummary(
    val accountId: Long,
    val accountName: String,
    val accountType: AccountType,
    val currentBalanceCents: Cents,
    val reserveAmountCents: Cents = 0L,
    val allocatableAmountCents: Cents = 0L,
    val allocatedAmountCents: Cents = 0L,
    val remainingAmountCents: Cents = 0L,
    val allocatedPercent: Double = 0.0
)

@Serializable
data class MhaFilingStatusRef(
    val value: String,
    val label: String,
    val standardDeductionCents: Cents
)

@Serializable
data class MhaStateTaxRef(
    val code: String,
    val name: String,
    val ratePercent: Double,
    val hasIncomeTax: Boolean = true
)

@Serializable
data class RuleConflictEntry(
    val transactionId: Long,
    val merchant: String = "",
    val field: String = "",
    val conflictingRuleId: Long,
    val conflictingRuleName: String = "",
    val currentValue: String = "",
    val proposedValue: String = ""
)

@Serializable
data class RuleFieldDiff(
    val field: String,
    val beforeValue: String,
    val afterValue: String
)

@Serializable
data class RuleMutationDiff(
    val transactionId: Long,
    val date: String = "",
    val merchant: String = "",
    val amountCents: Cents = 0L,
    val changes: List<RuleFieldDiff> = emptyList()
)

@Serializable
data class RulePreviewResult(
    val matchedCount: Int = 0,
    val willChangeCount: Int = 0,
    val conflictCount: Int = 0,
    val conflicts: List<RuleConflictEntry> = emptyList(),
    val diffs: List<RuleMutationDiff> = emptyList()
)
