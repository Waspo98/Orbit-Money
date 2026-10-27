package app.overbay.orbit.data.model

import app.overbay.orbit.domain.Cents
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class EditSource {
    NONE,
    USER,
    RULE,
    SYSTEM
}

@Serializable
enum class TransactionSource {
    CSV_IMPORT,
    SIMPLEFIN,
    MANUAL,
    SEED
}

@Serializable
data class MerchantLogo(
    val merchantKey: String,
    val merchantName: String = "",
    val provider: String = "logo_dev",
    val providerQuery: String = "",
    val url: String? = null,
    val status: String = "candidate"
)

private val PAYMENT_PREFIX_REGEX = Regex("^(sq|tst|sp|paypal|pp|stripe|sumup|toast|clover)[\\s*.-]+", RegexOption.IGNORE_CASE)
private val TRAILING_STORE_NUM_REGEX = Regex("\\s+#?\\d{3,}\\b.*$", RegexOption.IGNORE_CASE)
private val LOCATION_WORD_REGEX = Regex("\\b(store|location|terminal|auth|pending)\\s*#?\\d*\\b", RegexOption.IGNORE_CASE)
private val DATE_TOKEN_REGEX = Regex("\\b\\d{2}/\\d{2}(/\\d{2,4})?\\b")
private val LONG_DIGITS_REGEX = Regex("\\b\\d{4,}\\b")
private val CORP_SUFFIX_REGEX = Regex("\\s+(inc|llc|ltd|co|corp|corporation)\\.?$", RegexOption.IGNORE_CASE)
private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]+")
private val MULTI_SPACE_REGEX = Regex("\\s+")

fun cleanMerchantNameForLogo(value: String?): String =
    (value ?: "")
        .trim()
        .replace(MULTI_SPACE_REGEX, " ")
        .replace(PAYMENT_PREFIX_REGEX, "")
        .replace(TRAILING_STORE_NUM_REGEX, "")
        .replace(LOCATION_WORD_REGEX, " ")
        .replace(DATE_TOKEN_REGEX, " ")
        .replace(LONG_DIGITS_REGEX, " ")
        .replace(CORP_SUFFIX_REGEX, "")
        .replace(MULTI_SPACE_REGEX, " ")
        .trim()

fun merchantLogoKey(value: String?): String =
    cleanMerchantNameForLogo(value)
        .lowercase()
        .replace("&", " and ")
        .replace(NON_ALPHANUM_REGEX, " ")
        .trim()
        .replace(MULTI_SPACE_REGEX, "-")

@Serializable
data class Transaction(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val accountId: Long,
    val accountName: String = "",
    // Immutable imported original fields
    val originalDate: String,          // YYYY-MM-DD
    val originalMerchant: String,
    val originalDescription: String? = null,
    val originalAmountCents: Cents,    // Negative = outflow/expense, Positive = inflow/income
    val originalCategoryId: Long? = null,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val categoryIcon: String? = null,
    val isTransfer: Boolean = false,
    val transferPairId: Long? = null,
    val isIgnored: Boolean = false,
    val source: String = "manual",
    val externalId: String? = null,
    val isPending: Boolean = false,
    val notes: String? = null,

    // Mutable overlay / Provenance fields
    val editedDate: String? = null,
    val editedMerchant: String? = null,
    val editedMerchantSource: String? = null,
    val editedAmountCents: Cents? = null,
    val editedCategoryId: Long? = null,
    val editedCategoryIdSource: String? = null,
    val editedIsTransfer: Boolean? = null,
    val editedIsTransferSource: String? = null,
    val editedIsIgnored: Boolean? = null,
    val editedIsIgnoredSource: String? = null,
    val editedMhaEligible: Boolean? = null,
    val editedMhaEligibleSource: String? = null,
    val editSource: EditSource = EditSource.NONE,
    val merchantLogo: MerchantLogo? = null,

    val importedAt: String = "",
    val updatedAt: String = ""
) {
    val resolvedMerchantKey: String
        get() = merchantLogo?.merchantKey?.takeIf { it.isNotBlank() } ?: merchantLogoKey(effectiveMerchant)
    // Derived effective accessors
    val effectiveDate: String get() = editedDate ?: originalDate
    val effectiveMerchant: String get() = editedMerchant ?: originalMerchant
    val effectiveAmountCents: Cents get() = editedAmountCents ?: originalAmountCents
    val effectiveCategoryId: Long?
        get() = if (editedCategoryIdSource != null) editedCategoryId else originalCategoryId
    val effectiveIsTransfer: Boolean get() = editedIsTransfer ?: isTransfer
    val effectiveIsIgnored: Boolean get() = editedIsIgnored ?: isIgnored
    val effectiveMhaEligible: Boolean? get() = editedMhaEligible

    val isExpense: Boolean get() = effectiveAmountCents < 0
    val isIncome: Boolean get() = effectiveAmountCents > 0
    val absoluteAmountCents: Cents get() = abs(effectiveAmountCents)

    // Aliases for engine parity
    val originalIsTransfer: Boolean get() = isTransfer
    val originalIsIgnored: Boolean get() = isIgnored
    val mhaEligibleOverride: Boolean? get() = editedMhaEligible
    val mhaEligibleOverrideSource: String? get() = editedMhaEligibleSource
}

@Serializable
data class TransactionFilter(
    val query: String = "",
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val startDate: String? = null,
    val endDate: String? = null,
    val minAmountCents: Cents? = null,
    val maxAmountCents: Cents? = null,
    val pendingOnly: Boolean = false,
    val transfersOnly: Boolean = false,
    val uncategorizedOnly: Boolean = false,
    val mhaOnly: Boolean = false,
    val ignoredOnly: Boolean = false
) {
    val isActive: Boolean
        get() = query.isNotBlank() ||
                accountIds.isNotEmpty() ||
                categoryIds.isNotEmpty() ||
                startDate != null ||
                endDate != null ||
                minAmountCents != null ||
                maxAmountCents != null ||
                pendingOnly ||
                transfersOnly ||
                uncategorizedOnly ||
                mhaOnly ||
                ignoredOnly
}

@Serializable
enum class UpcomingKind {
    BILL,
    SUBSCRIPTION,
    INCOME
}

@Serializable
enum class UpcomingDirection(val rawValue: String) {
    EXPENSE("expense"),
    INCOME("income")
}

@Serializable
enum class UpcomingSource {
    MANUAL,
    TRANSACTION,
    SUGGESTION
}

@Serializable
enum class UpcomingStatus {
    ACTIVE,
    PAUSED
}

@Serializable
enum class RecurrenceFrequency {
    WEEKLY,
    BIWEEKLY,
    SEMIMONTHLY,
    MONTHLY,
    BIMONTHLY,
    QUARTERLY,
    YEARLY,
    CUSTOM
}

@Serializable
enum class AmountStrategy {
    FIXED,
    HISTORY_AVERAGE
}

@Serializable
sealed interface RecurrenceRule {
    @Serializable
    data class MonthDays(val days: List<Int>) : RecurrenceRule

    @Serializable
    data class MonthWeekdays(val ordinals: List<Int>, val weekdays: List<Int>) : RecurrenceRule
}

@Serializable
data class UpcomingItem(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val name: String,
    val kind: UpcomingKind = UpcomingKind.BILL,
    val source: UpcomingSource = UpcomingSource.MANUAL,
    val merchant: String? = null,
    val amountCents: Cents = 0L,
    val direction: String = "expense", // expense | income
    val frequencyType: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val frequencyInterval: Int = 1,
    val frequencyUnit: String = "months", // days | weeks | months
    val nextDate: String, // YYYY-MM-DD
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val sourceTransactionId: Long? = null,
    val status: UpcomingStatus = UpcomingStatus.ACTIVE,
    val notes: String? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val amountStrategy: AmountStrategy = AmountStrategy.FIXED,
    val amountLookbackMonths: Int = 6,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val frequency: RecurrenceFrequency get() = frequencyType

    constructor(
        id: Long = 0L,
        householdId: Long = 1L,
        name: String,
        merchant: String? = null,
        amountCents: Cents = 0L,
        direction: UpcomingDirection,
        kind: UpcomingKind = UpcomingKind.BILL,
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        frequencyInterval: Int = 1,
        nextDate: String,
        categoryId: Long? = null,
        accountId: Long? = null
    ) : this(
        id = id,
        householdId = householdId,
        name = name,
        kind = kind,
        merchant = merchant,
        amountCents = amountCents,
        direction = direction.rawValue,
        frequencyType = frequency,
        frequencyInterval = frequencyInterval,
        nextDate = nextDate,
        categoryId = categoryId,
        accountId = accountId
    )
}

@Serializable
enum class OccurrenceStatus {
    PENDING,
    MATCHED,
    MISSED
}

@Serializable
data class ProjectedOccurrence(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val upcomingItemId: Long,
    val expectedDate: String, // YYYY-MM-DD
    val graceUntil: String,   // YYYY-MM-DD
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val matchedTransactionId: Long? = null,
    val matchedAt: String? = null,
    val missedAt: String? = null
)

@Serializable
enum class CandidateConfidence {
    HIGH,
    MEDIUM,
    LOW
}

@Serializable
data class RecurringCandidate(
    val merchant: String,
    val amountCents: Cents,
    val frequency: RecurrenceFrequency,
    val transactionCount: Int = 0,
    val confidenceScore: Int = 0, // 0..100
    val confidenceTier: String = "MEDIUM",
    val suggestedCategoryId: Long? = null,
    val suggestedAccountId: Long? = null,
    val lastDate: String = "",
    val nextProjectedDate: String = "",
    val categoryName: String? = null,
    val intervalDays: Int = 0
) {
    val confidenceLevel: CandidateConfidence
        get() = when {
            confidenceScore >= 70 -> CandidateConfidence.HIGH
            confidenceScore >= 50 -> CandidateConfidence.MEDIUM
            else -> CandidateConfidence.LOW
        }

    val sampleCount: Int get() = transactionCount
}
