package app.overbay.orbit.domain

import app.overbay.orbit.data.model.AmountStrategy
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ProjectedAmountResult(
    val amountCents: Cents,
    val sampleCount: Int,
    val isProjectedAverage: Boolean
)

data class MatchedTransactionHistory(
    val transaction: Transaction,
    val matchScore: Int,
    val matchTier: String
)

data class UpcomingCashFlowSummary(
    val restOfMonthIncomeCents: Cents = 0L,
    val restOfMonthExpenseCents: Cents = 0L,
    val restOfMonthNetCents: Cents = 0L,
    val restOfMonthCount: Int = 0,
    val next30DaysIncomeCents: Cents = 0L,
    val next30DaysExpenseCents: Cents = 0L,
    val next30DaysNetCents: Cents = 0L,
    val next30DaysCount: Int = 0
)

object UpcomingScheduleHelper {

    val GIVING_HINTS = setOf(
        "church", "tithe", "charity", "donation", "ministries",
        "red cross", "united way", "foundation", "nonprofit"
    )

    val SUBSCRIPTION_HINTS = setOf(
        "netflix", "spotify", "hulu", "disney", "youtube", "apple.com/bill",
        "icloud", "chatgpt", "openai", "prime", "adobe", "github", "patreon",
        "audible", "xbox", "playstation", "paramount", "peacock", "max.com", "dropbox"
    )

    fun detectKind(merchant: String, direction: String = "expense"): UpcomingKind {
        if (direction.equals("income", ignoreCase = true)) {
            return UpcomingKind.INCOME
        }
        val lower = merchant.lowercase()
        if (GIVING_HINTS.any { lower.contains(it) }) {
            return UpcomingKind.GIVING
        }
        if (SUBSCRIPTION_HINTS.any { lower.contains(it) }) {
            return UpcomingKind.SUBSCRIPTION
        }
        return UpcomingKind.BILL
    }

    fun computeProjectedAmount(
        item: UpcomingItem,
        transactions: List<Transaction>,
        todayDate: String = LocalDateUtils.todayIsoDate()
    ): ProjectedAmountResult {
        if (item.amountStrategy == AmountStrategy.FIXED) {
            return ProjectedAmountResult(
                amountCents = item.amountCents,
                sampleCount = 0,
                isProjectedAverage = false
            )
        }

        val lookbackMonths = when (item.amountLookbackMonths) {
            6 -> 6
            12 -> 12
            else -> 3
        }

        val today = LocalDateUtils.parseIsoDate(todayDate) ?: LocalDateUtils.todayLocalDate()
        val totalMonths = (today.year * 12) + (today.monthNumber - 1) - lookbackMonths
        val cutoffYear = totalMonths / 12
        val cutoffMonth = (totalMonths % 12) + 1
        val maxDays = LocalDateUtils.daysInMonth(cutoffYear, cutoffMonth)
        val cutoffDay = min(today.dayOfMonth, maxDays)
        val cutoffIso = LocalDateUtils.formatIsoDate(LocalDate(cutoffYear, cutoffMonth, cutoffDay))

        val targetMerchant = item.merchant ?: item.name
        val matching = transactions.filter { txn ->
            !txn.effectiveIsIgnored && !txn.effectiveIsTransfer &&
                txn.effectiveDate in cutoffIso..todayDate &&
                UpcomingEngine.textMatchScore(targetMerchant, txn.effectiveMerchant) >= UpcomingEngine.MIN_MERCHANT_SCORE
        }

        if (matching.isEmpty()) {
            return ProjectedAmountResult(
                amountCents = item.amountCents,
                sampleCount = 0,
                isProjectedAverage = false
            )
        }

        val sum = matching.sumOf { abs(it.effectiveAmountCents) }
        val avg = sum / matching.size
        return ProjectedAmountResult(
            amountCents = avg,
            sampleCount = matching.size,
            isProjectedAverage = true
        )
    }

    fun advanceRecurrence(item: UpcomingItem, fromDate: String = item.nextDate): String {
        val rule = item.recurrenceRule
        if (rule != null) {
            val fromParsed = LocalDateUtils.parseIsoDate(fromDate) ?: return fromDate
            val horizon = LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(fromParsed, 75))
            val futureDates = UpcomingEngine.generateDates(item, fromDate, horizon)
            val next = futureDates.firstOrNull { it > fromDate }
            if (next != null) return next
        }

        if (item.frequencyType == RecurrenceFrequency.CUSTOM) {
            val d = LocalDateUtils.parseIsoDate(fromDate)
            val interval = max(1, item.frequencyInterval)
            if (d != null) {
                if (item.frequencyUnit.equals("days", ignoreCase = true)) {
                    return LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(d, interval))
                }
                if (item.frequencyUnit.equals("weeks", ignoreCase = true)) {
                    return LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(d, 7 * interval))
                }
            }
        }

        val anchorDay = when (val r = item.recurrenceRule) {
            is RecurrenceRule.MonthDays -> r.days.firstOrNull() ?: 0
            else -> LocalDateUtils.parseIsoDate(item.nextDate)?.dayOfMonth ?: 0
        }
        return UpcomingEngine.advanceInterval(
            date = fromDate,
            frequency = item.frequencyType,
            interval = item.frequencyInterval,
            anchorDay = anchorDay
        )
    }

    fun findMatchingTransactions(
        item: UpcomingItem,
        transactions: List<Transaction>
    ): List<MatchedTransactionHistory> {
        val targetMerchant = item.merchant ?: item.name
        val matches = mutableListOf<MatchedTransactionHistory>()

        for (txn in transactions) {
            if (txn.effectiveIsIgnored || txn.effectiveIsTransfer) continue
            val score = UpcomingEngine.scoreMatch(
                expectedMerchant = targetMerchant,
                expectedDate = txn.effectiveDate,
                expectedAmountCents = item.amountCents,
                expectedCategoryId = item.categoryId,
                expectedAccountId = item.accountId,
                txn = txn
            )
            if (score >= UpcomingEngine.MIN_MERCHANT_SCORE) {
                val tier = when {
                    score >= 70 -> "HIGH"
                    score >= 50 -> "MEDIUM"
                    else -> "LOW"
                }
                matches.add(MatchedTransactionHistory(txn, score, tier))
            }
        }

        return matches.sortedByDescending { it.transaction.effectiveDate }
    }

    fun calculateCashFlow(
        items: List<UpcomingItem>,
        transactions: List<Transaction>,
        todayDate: String = LocalDateUtils.todayIsoDate()
    ): UpcomingCashFlowSummary {
        val today = LocalDateUtils.parseIsoDate(todayDate) ?: return UpcomingCashFlowSummary()
        val daysInCurMonth = LocalDateUtils.daysInMonth(today.year, today.monthNumber)
        val endOfMonthIso = LocalDateUtils.formatIsoDate(LocalDate(today.year, today.monthNumber, daysInCurMonth))
        val next30DaysIso = LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(today, 30))

        var romIncome = 0L
        var romExpense = 0L
        var romCount = 0

        var n30Income = 0L
        var n30Expense = 0L
        var n30Count = 0

        val active = items.filter { it.status == UpcomingStatus.ACTIVE }

        for (item in active) {
            val amount = computeProjectedAmount(item, transactions, todayDate).amountCents
            val isIncome = item.direction.equals("income", ignoreCase = true)

            val monthDates = UpcomingEngine.generateDates(item, todayDate, endOfMonthIso)
            for (date in monthDates) {
                if (date >= todayDate && date <= endOfMonthIso) {
                    if (isIncome) romIncome += amount else romExpense += amount
                    romCount++
                }
            }

            val horizonDates = UpcomingEngine.generateDates(item, todayDate, next30DaysIso)
            for (date in horizonDates) {
                if (date >= todayDate && date <= next30DaysIso) {
                    if (isIncome) n30Income += amount else n30Expense += amount
                    n30Count++
                }
            }
        }

        return UpcomingCashFlowSummary(
            restOfMonthIncomeCents = romIncome,
            restOfMonthExpenseCents = romExpense,
            restOfMonthNetCents = romIncome - romExpense,
            restOfMonthCount = romCount,
            next30DaysIncomeCents = n30Income,
            next30DaysExpenseCents = n30Expense,
            next30DaysNetCents = n30Income - n30Expense,
            next30DaysCount = n30Count
        )
    }

    fun formatRecurrenceSchedule(item: UpcomingItem): String {
        val rule = item.recurrenceRule
        if (rule is RecurrenceRule.MonthDays) {
            val dayLabels = rule.days.map { if (it == -1) "Last Day" else it.toString() }
            return "Monthly on ${dayLabels.joinToString(", ")}"
        }
        if (rule is RecurrenceRule.MonthWeekdays) {
            val weekdayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val ordinalNames = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", -1 to "Last")
            val ordStr = rule.ordinals.mapNotNull { ordinalNames[it] }.joinToString("/")
            val wkStr = rule.weekdays.mapNotNull { weekdayNames.getOrNull(it) }.joinToString("/")
            return "Monthly on $ordStr $wkStr"
        }
        if (item.frequencyType == RecurrenceFrequency.CUSTOM) {
            val unit = item.frequencyUnit.ifBlank { "days" }
            return "Every ${item.frequencyInterval} $unit"
        }
        return item.frequencyType.name.lowercase().replaceFirstChar { it.uppercase() }
    }
}
