package app.overbay.orbit.data.model

import app.overbay.orbit.domain.Cents
import kotlinx.serialization.Serializable

@Serializable
enum class BudgetPaceStatus {
    ON_TRACK,
    WATCH,
    OVER_BUDGET,
    NO_BUDGET
}

@Serializable
enum class CategorySortOption {
    OVER_BUDGET_FIRST,
    LEAST_ROOM_LEFT,
    MOST_SPENT,
    NAME_A_TO_Z
}

@Serializable
data class CategoryBudget(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val categoryId: Long,
    val categoryName: String = "",
    val categoryColor: String = "#888888",
    val categoryIcon: String? = null,
    val amountCents: Cents = 0L,
    val rollover: Boolean = false,
    val spentCents: Cents = 0L,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val remainingCents: Cents get() = amountCents - spentCents
    val utilizationRatio: Double
        get() = if (amountCents > 0L) spentCents.toDouble() / amountCents.toDouble() else 0.0

    val paceStatus: BudgetPaceStatus
        get() = when {
            amountCents <= 0L -> BudgetPaceStatus.NO_BUDGET
            spentCents > amountCents -> BudgetPaceStatus.OVER_BUDGET
            utilizationRatio >= 0.85 -> BudgetPaceStatus.WATCH
            else -> BudgetPaceStatus.ON_TRACK
        }
}

@Serializable
data class CategoryBudgetOverviewItem(
    val category: Category,
    val budgetId: Long? = null,
    val amountCents: Cents,
    val spentCents: Cents,
    val remainingCents: Cents,
    val transactionCount: Int,
    val utilizationRatio: Double,
    val paceStatus: BudgetPaceStatus,
    val rollover: Boolean = false
) {
    val budgetedCents: Cents get() = amountCents
}

@Serializable
data class DailySafeSpend(
    val dailyCents: Cents,
    val daysRemaining: Int,
    val detail: String,
    val tone: String // "good", "watch", "over", "neutral"
)

@Serializable
data class MonthlyBudgetOverview(
    val month: String, // YYYY-MM
    val totalBudgetedCents: Cents,
    val totalSpentInBudgetCents: Cents = 0L,
    val totalSpentUnbudgetedCents: Cents = 0L,
    val totalSpentCents: Cents,
    val totalIncomeCents: Cents = 0L,
    val totalExpensesCents: Cents = 0L,
    val netSavingsCents: Cents = 0L,
    val savingsRatePercent: Double = 0.0,
    val dailySafeSpend: DailySafeSpend = DailySafeSpend(0L, 0, "", "neutral"),
    val budgeted: List<CategoryBudgetOverviewItem> = emptyList(),
    val unbudgeted: List<CategoryBudgetOverviewItem> = emptyList(),
    val inactive: List<CategoryBudgetOverviewItem> = emptyList()
) {
    val totalRemainingCents: Cents get() = totalBudgetedCents - totalSpentCents
    val dailySafeSpendCents: Cents get() = dailySafeSpend.dailyCents
    val utilizationRatio: Double
        get() = if (totalBudgetedCents > 0L) totalSpentCents.toDouble() / totalBudgetedCents.toDouble() else 0.0
    val categoryRows: List<CategoryBudgetOverviewItem> get() = budgeted + unbudgeted
}

typealias MonthlyBudgetSummary = MonthlyBudgetOverview

@Serializable
enum class GoalAllocationType {
    PERCENT,
    FIXED
}

@Serializable
data class GoalAllocation(
    val id: Long = 0L,
    val goalId: Long,
    val accountId: Long,
    val accountName: String = "",
    val allocationType: GoalAllocationType = GoalAllocationType.FIXED,
    val allocationPercent: Double = 0.0,
    val allocationAmountCents: Cents = 0L,
    val reserveAmountCents: Cents = 0L,
    val currentAmountCents: Cents = 0L,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class Goal(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val name: String,
    val targetAmountCents: Cents,
    val currentAmountCents: Cents = 0L,
    val targetDate: String? = null, // YYYY-MM-DD
    val linkedAccountId: Long? = null,
    val kind: String = "custom",
    val icon: String? = "🎯",
    val sortOrder: Int = 0,
    val allocations: List<GoalAllocation> = emptyList(),
    val notes: String? = null,
    val monthlyPaceCents: Cents = 0L,
    val eta: GoalEtaInfo? = null,
    val history: List<GoalMonthlyPoint> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val progressRatio: Double
        get() = if (targetAmountCents > 0L) {
            (currentAmountCents.toDouble() / targetAmountCents.toDouble()).coerceIn(0.0, 1.0)
        } else 0.0

    val remainingAmountCents: Cents get() = (targetAmountCents - currentAmountCents).coerceAtLeast(0L)
    val isCompleted: Boolean get() = currentAmountCents >= targetAmountCents
}

data class GoalPreset(val kind: String, val label: String, val emoji: String)

val GOAL_PRESETS: List<GoalPreset> = listOf(
    GoalPreset(kind = "college", label = "Kids College", emoji = "🎓"),
    GoalPreset(kind = "car", label = "New Car", emoji = "🚗"),
    GoalPreset(kind = "home", label = "Home", emoji = "🏠"),
    GoalPreset(kind = "emergency", label = "Emergency Fund", emoji = "🛡️"),
    GoalPreset(kind = "travel", label = "Travel", emoji = "✈️"),
    GoalPreset(kind = "custom", label = "Something Else", emoji = "🎯")
)

val Goal.resolvedEmoji: String
    get() {
        val raw = icon?.trim().orEmpty()
        if (raw.isNotEmpty() && raw != "?" && raw != "??" && raw != "target") return raw
        return GOAL_PRESETS.firstOrNull { it.kind == kind }?.emoji ?: "🎯"
    }

val Goal.labelWithEmoji: String
    get() = "$resolvedEmoji $name"
