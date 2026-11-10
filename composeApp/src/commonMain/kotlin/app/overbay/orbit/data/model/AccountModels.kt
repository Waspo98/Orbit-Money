package app.overbay.orbit.data.model

import app.overbay.orbit.domain.Cents
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class AccountType(val storageKey: String, val displayName: String) {
    CHECKING("checking", "Checking"),
    SAVINGS("savings", "Savings"),
    CREDIT("credit", "Credit Card"),
    INVESTMENT("investment", "Investment"),
    LOAN("loan", "Loan"),
    MORTGAGE("mortgage", "Mortgage"),
    CASH("cash", "Cash"),
    OTHER("other", "Other Asset");

    val isAsset: Boolean
        get() = this in setOf(CHECKING, SAVINGS, INVESTMENT, CASH, OTHER)

    val isLiability: Boolean
        get() = this in setOf(CREDIT, LOAN, MORTGAGE)

    companion object {
        fun fromStorage(value: String?): AccountType {
            if (value.isNullOrBlank()) return OTHER
            return entries.firstOrNull { it.storageKey.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: OTHER
        }

        fun fromRaw(value: String?): AccountType = fromStorage(value)
    }
}

@Serializable
data class Account(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val name: String,
    val type: AccountType,
    val institution: String? = null,
    val accountNumberLast4: String? = null,
    val currentBalanceCents: Cents = 0L,
    val estimatedValueCents: Cents? = null,
    val isManual: Boolean = true,
    val simplefinAccountId: String? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val mhaDefaultEligible: Boolean = false,
    val creditCardProfile: CreditCardProfile? = null,
    val transactionCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    /**
     * Home equity calculated for mortgage accounts with an estimated property value.
     */
    val homeEquityCents: Cents?
        get() = if (type == AccountType.MORTGAGE && estimatedValueCents != null) {
            estimatedValueCents - abs(currentBalanceCents)
        } else null

    /**
     * Net worth contribution of this account.
     * Mortgages contribute (estimatedProperty - loanBalance).
     * Credit & loans contribute negative balance (liability).
     * Cash, savings, investments contribute positive balance (asset).
     */
    val netWorthContributionCents: Cents
        get() = when (type) {
            AccountType.MORTGAGE -> homeEquityCents ?: -abs(currentBalanceCents)
            AccountType.CREDIT, AccountType.LOAN -> -abs(currentBalanceCents)
            else -> currentBalanceCents
        }
}

@Serializable
enum class BenefitCadence(val storageKey: String, val displayName: String, val periodMonths: Int) {
    MONTHLY("monthly", "Monthly", 1),
    QUARTERLY("quarterly", "Quarterly", 3),
    SEMI_ANNUAL("semi_annual", "Semi-Annual", 6),
    ANNUAL("annual", "Annual", 12),
    EVERY_4_YEARS("every_4_years", "Every 4 Years", 48);

    @Deprecated("Use periodMonths for calculation", ReplaceWith("12 / periodMonths"))
    val periodsPerYear: Int get() = if (periodMonths >= 12) 1 else 12 / periodMonths

    companion object {
        fun fromStorage(raw: String?): BenefitCadence {
            if (raw.isNullOrBlank()) return ANNUAL
            return entries.firstOrNull { it.storageKey.equals(raw, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: ANNUAL
        }
    }
}

@Serializable
enum class BenefitAnchor(val storageKey: String) {
    CALENDAR("calendar"),
    CARDMEMBER_YEAR("cardmember_year");

    companion object {
        fun fromStorage(raw: String?): BenefitAnchor =
            entries.firstOrNull { it.storageKey.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: CALENDAR
    }
}

@Serializable
data class BenefitUsage(
    val periodKey: String,
    val amountCents: Cents,
    val usedOn: String? = null
)

@Serializable
data class RewardCategory(
    val category: String = "",
    val multiplier: Double = 1.0,
    val isRotating: Boolean = false,
    val capCents: Cents? = null,
    val description: String? = null,
    val label: String = "",
    val rateType: String = "multiplier",
    val notes: String? = null
)

@Serializable
data class CardBenefit(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val periodValueCents: Cents? = null,
    val cadence: BenefitCadence = BenefitCadence.ANNUAL,
    val anchor: BenefitAnchor = BenefitAnchor.CALENDAR,
    val usage: List<BenefitUsage> = emptyList(),
    val category: String? = null,
    val valueText: String? = null,
    // LEGACY: read-only, upgraded by CreditCardBenefitEngine.upgradeLegacy
    val annualCreditValueCents: Cents? = null,
    // LEGACY: read-only, upgraded by CreditCardBenefitEngine.upgradeLegacy
    val usedThisYear: Boolean = false,
    // LEGACY: read-only, upgraded by CreditCardBenefitEngine.upgradeLegacy
    val usedPeriods: List<String> = emptyList()
)

@Serializable
data class CreditCardStrategy(
    val cardholder: String? = null, // e.g. "Player 1", "Player 2"
    val openedDate: String? = null, // YYYY-MM-DD
    val closedDate: String? = null, // YYYY-MM-DD
    val isBusinessCard: Boolean = false,
    val countsToward524Override: Boolean? = null, // null = !isBusinessCard
    val productChangedFrom: String? = null,
    val subRewardText: String? = null, // e.g. "75,000 UR Points"
    val subEstimatedValueCents: Cents? = null,
    val subTargetSpendCents: Cents? = null,
    val subDeadlineDate: String? = null, // YYYY-MM-DD
    val subManualSpendOffsetCents: Cents = 0L,
    val subCompleted: Boolean = false,
    val subReceivedDate: String? = null, // YYYY-MM-DD
    val subCooldownMonths: Int? = null // 24, 48, 999 (Lifetime)
) {
    val countsToward524: Boolean
        get() = countsToward524Override ?: !isBusinessCard
}

@Serializable
data class CreditCardProfile(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val accountId: Long,
    val cardName: String? = null,
    val issuerName: String? = null,
    val network: String? = null,
    val imageUrl: String? = null,
    val annualFeeCents: Cents? = null,
    val annualFeePostDate: String? = null, // YYYY-MM-DD
    val creditLimitCents: Cents? = null,
    val statementClosingDay: Int? = null,
    val paymentDueDay: Int? = null,
    val authorizedUsers: List<String> = emptyList(),
    val rewardCategories: List<RewardCategory> = emptyList(),
    val benefits: List<CardBenefit> = emptyList(),
    val strategy: CreditCardStrategy = CreditCardStrategy(),
    val notes: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun creditUtilizationRatio(currentBalanceCents: Cents): Double? {
        val limit = creditLimitCents ?: return null
        if (limit <= 0L) return null
        return abs(currentBalanceCents).toDouble() / limit.toDouble()
    }
}

@Serializable
enum class BalanceRecordSource {
    MANUAL,
    SIMPLEFIN
}

@Serializable
data class BalanceRecord(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val accountId: Long,
    val recordDate: String, // YYYY-MM-DD
    val balanceCents: Cents,
    val source: BalanceRecordSource = BalanceRecordSource.MANUAL,
    val createdAt: String = "",
    val updatedAt: String = ""
)
