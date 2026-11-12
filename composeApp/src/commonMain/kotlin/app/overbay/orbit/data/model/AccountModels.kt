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
    val isProperty: Boolean = false,
    val propertyName: String? = null,
    val linkedPropertyAccountId: Long? = null,
    val simplefinLastSeenAt: String? = null,
    val simplefinMissingSince: String? = null,
    val closurePromptDismissedAt: String? = null,
    val isManual: Boolean = true,
    val simplefinAccountId: String? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val mhaDefaultEligible: Boolean = false,
    val creditCardProfile: CreditCardProfile? = null,
    val institutionLogo: MerchantLogo? = null,
    val transactionCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val resolvedInstitutionLogoKey: String
        get() = institutionLogo?.merchantKey?.takeIf { it.isNotBlank() }
            ?: institution?.let { merchantLogoKey(it) }?.takeIf { it.isNotBlank() }?.let { "inst:$it" }
            ?: "inst:acct-$id"

    val effectiveInstitutionLogoUrl: String?
        get() {
            val cached = institutionLogo
            if (cached != null) {
                if (cached.status == "hidden" || cached.status == "failed") return null
                return cached.url?.takeIf { it.isNotBlank() }
            }
            return InstitutionLogoDefaults.fallbackUrlFor(institution)
        }
    /**
     * Home equity calculated for real estate properties and mortgages with an estimated property value.
     */
    val homeEquityCents: Cents?
        get() = when {
            isProperty -> estimatedValueCents ?: currentBalanceCents.coerceAtLeast(0L)
            type == AccountType.MORTGAGE && estimatedValueCents != null -> estimatedValueCents - abs(currentBalanceCents)
            else -> null
        }

    /**
     * Net worth contribution of this account.
     * Standalone properties contribute their estimated property value.
     * Mortgages contribute (estimatedProperty - loanBalance).
     * Credit & loans contribute negative balance (liability).
     * Cash, savings, investments contribute positive balance (asset).
     */
    val netWorthContributionCents: Cents
        get() = when {
            isProperty -> estimatedValueCents ?: currentBalanceCents.coerceAtLeast(0L)
            type == AccountType.MORTGAGE -> homeEquityCents ?: -abs(currentBalanceCents)
            type == AccountType.CREDIT || type == AccountType.LOAN -> -abs(currentBalanceCents)
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
    val cardholderMemberId: Long? = null,
    val authorizedUserMemberIds: List<Long> = emptyList(),
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
    val artworkUrl: String? = null,
    val annualFeeCents: Cents? = null,
    val annualFeePostDate: String? = null, // YYYY-MM-DD
    val creditLimitCents: Cents? = null,
    val statementClosingDay: Int? = null,
    val paymentDueDay: Int? = null,
    val aprBps: Int? = null,
    val promoAprBps: Int? = null,
    val promoEndDate: String? = null, // YYYY-MM-DD
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

object InstitutionLogoDefaults {
    private val DOMAIN_BY_NAME: Map<String, String> = mapOf(
        "chase" to "chase.com",
        "chase bank" to "chase.com",
        "jpmorgan" to "chase.com",
        "jpmorgan chase" to "chase.com",
        "betterment" to "betterment.com",
        "fidelity" to "fidelity.com",
        "fidelity investments" to "fidelity.com",
        "vanguard" to "vanguard.com",
        "schwab" to "schwab.com",
        "charles schwab" to "schwab.com",
        "ally" to "ally.com",
        "ally bank" to "ally.com",
        "sofi" to "sofi.com",
        "capital one" to "capitalone.com",
        "american express" to "americanexpress.com",
        "amex" to "americanexpress.com",
        "bank of america" to "bankofamerica.com",
        "bofa" to "bankofamerica.com",
        "wells fargo" to "wellsfargo.com",
        "citi" to "citi.com",
        "citibank" to "citi.com",
        "discover" to "discover.com",
        "wealthfront" to "wealthfront.com",
        "marcus" to "marcus.com",
        "goldman sachs" to "marcus.com",
        "usaa" to "usaa.com",
        "navy federal" to "navyfederal.org",
        "navy federal credit union" to "navyfederal.org",
        "us bank" to "usbank.com",
        "u s bank" to "usbank.com",
        "pnc" to "pnc.com",
        "pnc bank" to "pnc.com",
        "td bank" to "td.com",
        "truist" to "truist.com",
        "fifth third" to "53.com",
        "huntington" to "huntington.com",
        "citizens" to "citizensbank.com",
        "bmo" to "bmo.com",
        "synchrony" to "synchrony.com",
        "barclays" to "barclaysus.com",
        "robinhood" to "robinhood.com",
        "coinbase" to "coinbase.com",
        "m1 finance" to "m1.com",
        "etrade" to "etrade.com",
        "e trade" to "etrade.com",
        "merrill" to "ml.com",
        "empower" to "empower.com",
        "personal capital" to "empower.com",
        "principal" to "principal.com",
        "tiaa" to "tiaa.org",
        "paypal" to "paypal.com",
        "venmo" to "venmo.com",
        "apple" to "apple.com",
        "rocket mortgage" to "rocketmortgage.com",
        "new american funding" to "newamericanfunding.com",
        "guild mortgage" to "guildmortgage.com",
        "loandepot" to "loandepot.com",
        "freedom mortgage" to "freedommortgage.com",
        "united wholesale mortgage" to "uwm.com",
        "uwm" to "uwm.com",
        "flagstar" to "flagstar.com",
        "flagstar bank" to "flagstar.com",
        "cenlar" to "cenlar.com",
        "shellpoint" to "shellpointmtg.com",
        "mr cooper" to "mrcooper.com",
        "pennymac" to "pennymac.com",
        "alliant" to "alliantcreditunion.org",
        "becu" to "becu.org",
        "penfed" to "penfed.org",
        "chime" to "chime.com"
    )

    fun resolveDomain(institution: String?): String? {
        val raw = institution?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val norm = raw.lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (norm.isBlank()) return null
        DOMAIN_BY_NAME[norm]?.let { return it }
        val stripped = norm.replace(Regex("\\s+(bank|financial|investments|savings|mortgage)$"), "").trim()
        return DOMAIN_BY_NAME[stripped]
    }

    fun fallbackUrlFor(institution: String?): String? {
        val domain = resolveDomain(institution) ?: return null
        return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
    }
}
