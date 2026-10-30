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
    val title: String = "",
    val description: String? = null,
    val annualCreditValueCents: Cents? = null,
    val usedThisYear: Boolean = false,
    val category: String? = null,
    val valueText: String? = null
)

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
