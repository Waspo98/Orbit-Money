package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction

/**
 * Candidate transaction surfaced in the "Categorize Recent Transactions" swipe queue.
 * Mirrors the `/api/transactions/review-queue` and Fun Mode payload from `main`.
 */
data class ReviewQueueCandidate(
    val id: Long,
    val merchant: String,
    val originalMerchant: String,
    val originalDescription: String?,
    val amountCents: Cents,
    val isExpense: Boolean,
    val formattedAmount: String,
    val dateIso: String,
    val dateDisplay: String,
    val accountId: Long,
    val accountName: String,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColor: String?,
    val categoryIcon: String?,
    val suggestedCategoryId: Long?,
    val reviewReasons: List<String>,
    val merchantLogoUrl: String?
)

object ReviewQueueEngine {

    private val MERCHANT_NOISE_HINTS = listOf(
        "checkcard",
        "debit card",
        "online transfer",
        "pos debit",
        "purchase authorized",
        "recurring debit",
        "web authorized"
    )

    private val CREDIT_PAYMENT_HINTS = listOf(
        "autopay",
        "automatic payment",
        "cardmember serv",
        "chase credit crd",
        "citi card",
        "credit card payment",
        "discover e-payment",
        "online payment",
        "payment thank you",
        "pymt"
    )

    private val HASH_STAR_DIGITS_REGEX = Regex("[#*]\\d+")
    private val MULTI_DIGITS_REGEX = Regex("\\b\\d{2,}\\b")
    private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]+")
    private val NOISE_TOKENS_REGEX = Regex("\\b(llc|inc|co|corp|store|pos|debit|card|purchase)\\b")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
    private val THREE_PLUS_DIGITS_REGEX = Regex("\\d{3,}")
    private val HASH_STAR_THREE_DIGITS_REGEX = Regex("[#*]\\d{3,}")
    private val PAYMENT_TO_CARD_ENDING_REGEX = Regex("payment\\s+to\\s+.+\\bcard\\s+ending\\b")
    private val CARD_ENDING_REGEX = Regex("\\bcard\\s+ending\\s+(in\\s+)?[#*\\d]{2,}")

    fun buildReviewQueue(
        transactions: List<Transaction>,
        categories: List<Category>,
        accounts: List<Account>,
        excludedIds: Set<Long>,
        funMode: Boolean = false,
        limit: Int = 10
    ): List<ReviewQueueCandidate> {
        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }

        val sortedNonIgnored = transactions
            .filter { !it.effectiveIsIgnored }
            .sortedWith(compareByDescending<Transaction> { it.effectiveDate }.thenByDescending { it.id })

        if (funMode) {
            return sortedNonIgnored
                .take(100)
                .filter { it.id !in excludedIds }
                .take(limit)
                .map { row ->
                    toCandidate(
                        row = row,
                        categoriesById = categoriesById,
                        accountsById = accountsById,
                        reasons = listOf("fun_review"),
                        suggestedCategoryId = null
                    )
                }
        }

        val rows = sortedNonIgnored.take(1000)
        val patterns = buildMerchantPatterns(rows)
        val candidates = mutableListOf<ReviewQueueCandidate>()

        for (row in rows) {
            if (row.id in excludedIds) continue
            val catId = row.effectiveCategoryId
            val category = catId?.let { categoriesById[it] }
            val categoryName = category?.name ?: row.categoryName
            val reasons = mutableListOf<String>()
            val suggestedCategoryId = changedCategoryPattern(row, patterns)

            if (catId == null || categoryName.equals("uncategorized", ignoreCase = true)) {
                reasons.add("uncategorized")
            }
            if (looksNoisyMerchant(row)) {
                reasons.add("merchant_review")
            }
            if (suggestedCategoryId != null) {
                reasons.add("changed_pattern")
            }

            if (reasons.isEmpty()) continue

            candidates.add(
                toCandidate(
                    row = row,
                    categoriesById = categoriesById,
                    accountsById = accountsById,
                    reasons = reasons,
                    suggestedCategoryId = suggestedCategoryId
                )
            )
            if (candidates.size >= limit) break
        }

        return candidates
    }

    internal fun normalizeMerchant(value: String?): String =
        (value ?: "")
            .lowercase()
            .replace(HASH_STAR_DIGITS_REGEX, " ")
            .replace(MULTI_DIGITS_REGEX, " ")
            .replace(NON_ALPHANUM_REGEX, " ")
            .replace(NOISE_TOKENS_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    private fun textForReview(row: Transaction): String =
        "${row.effectiveMerchant} ${row.originalMerchant} ${row.originalDescription ?: ""}".lowercase()

    internal fun looksCreditCardPayment(row: Transaction): Boolean {
        val text = textForReview(row)
        if (CREDIT_PAYMENT_HINTS.any { text.contains(it) }) return true
        return PAYMENT_TO_CARD_ENDING_REGEX.containsMatchIn(text) ||
            CARD_ENDING_REGEX.containsMatchIn(text)
    }

    internal fun looksNoisyMerchant(row: Transaction): Boolean {
        val merchant = row.effectiveMerchant.trim()
        if (merchant.isEmpty()) return true
        if (looksCreditCardPayment(row)) return false
        val text = textForReview(row)
        if (MERCHANT_NOISE_HINTS.any { text.contains(it) }) return true
        if (merchant.length > 34 && THREE_PLUS_DIGITS_REGEX.containsMatchIn(merchant)) return true
        if (HASH_STAR_THREE_DIGITS_REGEX.containsMatchIn(merchant)) return true
        if (merchant.count { it.isDigit() } >= 6) return true
        return false
    }

    internal data class MerchantPattern(
        var total: Int = 0,
        val categories: MutableMap<Long, Int> = mutableMapOf(),
        var dominantCategoryId: Long? = null,
        var dominantCount: Int = 0
    )

    internal fun buildMerchantPatterns(rows: List<Transaction>): Map<String, MerchantPattern> {
        val patterns = mutableMapOf<String, MerchantPattern>()
        for (row in rows) {
            val catId = row.effectiveCategoryId ?: continue
            if (row.effectiveIsTransfer || row.effectiveIsIgnored) continue
            val key = normalizeMerchant(row.effectiveMerchant)
            if (key.length < 3) continue
            val pattern = patterns.getOrPut(key) { MerchantPattern() }
            pattern.total += 1
            pattern.categories[catId] = (pattern.categories[catId] ?: 0) + 1
        }
        for (pattern in patterns.values) {
            var dominantId: Long? = null
            var dominantCnt = 0
            for ((catId, count) in pattern.categories) {
                if (count > dominantCnt) {
                    dominantId = catId
                    dominantCnt = count
                }
            }
            pattern.dominantCategoryId = dominantId
            pattern.dominantCount = dominantCnt
        }
        return patterns
    }

    internal fun changedCategoryPattern(row: Transaction, patterns: Map<String, MerchantPattern>): Long? {
        val catId = row.effectiveCategoryId ?: return null
        val pattern = patterns[normalizeMerchant(row.effectiveMerchant)] ?: return null
        if (pattern.total < 3 || pattern.dominantCount < 2) return null
        if (pattern.dominantCategoryId == catId) return null
        val currentCount = pattern.categories[catId] ?: 0
        if (pattern.dominantCount - currentCount < 2) return null
        return pattern.dominantCategoryId
    }

    private fun toCandidate(
        row: Transaction,
        categoriesById: Map<Long, Category>,
        accountsById: Map<Long, Account>,
        reasons: List<String>,
        suggestedCategoryId: Long?
    ): ReviewQueueCandidate {
        val catId = row.effectiveCategoryId
        val cat = catId?.let { categoriesById[it] }
        val acct = accountsById[row.accountId]
        val dateDisplay = LocalDateUtils.parseIsoDate(row.effectiveDate)?.let {
            LocalDateUtils.formatShortDate(it)
        } ?: row.effectiveDate

        return ReviewQueueCandidate(
            id = row.id,
            merchant = row.effectiveMerchant.ifBlank { "Transaction" },
            originalMerchant = row.originalMerchant,
            originalDescription = row.originalDescription?.takeIf { it.isNotBlank() },
            amountCents = row.effectiveAmountCents,
            isExpense = row.isExpense,
            formattedAmount = MoneyUtils.formatCents(row.effectiveAmountCents, signed = true),
            dateIso = row.effectiveDate,
            dateDisplay = dateDisplay,
            accountId = row.accountId,
            accountName = acct?.name ?: row.accountName,
            categoryId = catId,
            categoryName = cat?.name ?: row.categoryName,
            categoryColor = cat?.color ?: row.categoryColor,
            categoryIcon = cat?.icon ?: row.categoryIcon,
            suggestedCategoryId = suggestedCategoryId,
            reviewReasons = reasons,
            merchantLogoUrl = row.merchantLogo?.url?.takeIf {
                it.isNotBlank() && row.merchantLogo.status != "hidden" && row.merchantLogo.status != "failed"
            }
        )
    }

    fun reasonLabel(reason: String): String = when (reason) {
        "uncategorized" -> "Uncategorized"
        "merchant_review" -> "Merchant Check"
        "changed_pattern" -> "Changed Pattern"
        "fun_review" -> "Bonus Round"
        else -> "Review"
    }
}
