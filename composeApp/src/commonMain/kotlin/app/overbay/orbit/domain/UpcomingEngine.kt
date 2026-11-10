package app.overbay.orbit.domain

import app.overbay.orbit.data.model.AmountStrategy
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.OccurrenceStatus
import app.overbay.orbit.data.model.ProjectedOccurrence
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingItem
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class UpcomingReconciliationResult(
    val occurrences: List<ProjectedOccurrence>,
    val matchedCount: Int = 0
)

object UpcomingEngine {

    const val GRACE_DAYS = 4
    const val MIN_MATCH_SCORE = 70
    const val MIN_MERCHANT_SCORE = 30
    val GIVING_HINTS = UpcomingScheduleHelper.GIVING_HINTS

    /**
     * Generates all projected dates for an UpcomingItem between startDate and endDate.
     */
    fun generateDates(
        item: UpcomingItem,
        startDate: String,
        endDate: String
    ): List<String> {
        val rule = item.recurrenceRule
        if (rule != null) {
            return generateDatesForRule(rule, startDate, endDate)
        }

        val anchorIso = if (item.nextDate.isNotBlank()) item.nextDate else startDate
        val anchorDate = LocalDateUtils.parseIsoDate(anchorIso) ?: return emptyList()
        val sDate = LocalDateUtils.parseIsoDate(startDate) ?: return emptyList()
        val eDate = LocalDateUtils.parseIsoDate(endDate) ?: return emptyList()

        if (anchorDate > eDate) return emptyList()

        val dates = mutableListOf<String>()
        val interval = max(1, item.frequencyInterval)

        when (item.frequencyType) {
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.BIWEEKLY -> {
                val stepDays = when (item.frequencyType) {
                    RecurrenceFrequency.WEEKLY -> 7 * interval
                    RecurrenceFrequency.BIWEEKLY -> 14 * interval
                    else -> 7
                }
                var cur = anchorDate
                var guard = 0
                while (cur < sDate && guard < 1000) {
                    cur = LocalDateUtils.addDays(cur, stepDays)
                    guard++
                }
                guard = 0
                while (cur <= eDate && guard < 1000) {
                    if (cur >= sDate) {
                        dates.add(LocalDateUtils.formatIsoDate(cur))
                    }
                    cur = LocalDateUtils.addDays(cur, stepDays)
                    guard++
                }
            }

            RecurrenceFrequency.SEMIMONTHLY -> {
                val anchorDay = anchorDate.dayOfMonth
                val day1 = if (anchorDay <= 15) anchorDay else max(1, anchorDay - 14)
                val day2 = if (anchorDay <= 15) min(31, anchorDay + 14) else anchorDay

                var curYear = anchorDate.year
                var curMonth = anchorDate.monthNumber
                val endYear = eDate.year
                val endMonth = eDate.monthNumber

                var guard = 0
                while (guard < 1200) {
                    val maxDays = LocalDateUtils.daysInMonth(curYear, curMonth)
                    val d1 = min(day1, maxDays)
                    val d2 = min(day2, maxDays)

                    val date1 = LocalDate(curYear, curMonth, d1)
                    if (date1 in sDate..eDate && date1 >= anchorDate) {
                        dates.add(LocalDateUtils.formatIsoDate(date1))
                    }

                    if (d2 != d1) {
                        val date2 = LocalDate(curYear, curMonth, d2)
                        if (date2 in sDate..eDate && date2 >= anchorDate) {
                            dates.add(LocalDateUtils.formatIsoDate(date2))
                        }
                    }

                    if (curYear > endYear || (curYear == endYear && curMonth >= endMonth)) break
                    curMonth++
                    if (curMonth > 12) {
                        curMonth = 1
                        curYear++
                    }
                    guard++
                }
            }

            RecurrenceFrequency.CUSTOM -> {
                when (item.frequencyUnit.lowercase()) {
                    "days" -> {
                        val stepDays = interval
                        var cur = anchorDate
                        var guard = 0
                        while (cur < sDate && guard < 1000) {
                            cur = LocalDateUtils.addDays(cur, stepDays)
                            guard++
                        }
                        guard = 0
                        while (cur <= eDate && guard < 1000) {
                            if (cur >= sDate) {
                                dates.add(LocalDateUtils.formatIsoDate(cur))
                            }
                            cur = LocalDateUtils.addDays(cur, stepDays)
                            guard++
                        }
                    }
                    "weeks" -> {
                        val stepDays = interval * 7
                        var cur = anchorDate
                        var guard = 0
                        while (cur < sDate && guard < 1000) {
                            cur = LocalDateUtils.addDays(cur, stepDays)
                            guard++
                        }
                        guard = 0
                        while (cur <= eDate && guard < 1000) {
                            if (cur >= sDate) {
                                dates.add(LocalDateUtils.formatIsoDate(cur))
                            }
                            cur = LocalDateUtils.addDays(cur, stepDays)
                            guard++
                        }
                    }
                    else -> {
                        val stepMonths = interval
                        val anchorDay = anchorDate.dayOfMonth
                        var stepIndex = 0
                        var guard = 0
                        while (guard < 1200) {
                            val totalMonths = (anchorDate.year * 12) + (anchorDate.monthNumber - 1) + (stepIndex * stepMonths)
                            val nextYear = totalMonths / 12
                            val nextMonth = (totalMonths % 12) + 1
                            val maxDays = LocalDateUtils.daysInMonth(nextYear, nextMonth)
                            val nextDay = min(anchorDay, maxDays)
                            val cur = LocalDate(nextYear, nextMonth, nextDay)

                            if (cur > eDate) break
                            if (cur >= sDate) {
                                dates.add(LocalDateUtils.formatIsoDate(cur))
                            }
                            stepIndex++
                            guard++
                        }
                    }
                }
            }

            RecurrenceFrequency.MONTHLY,
            RecurrenceFrequency.BIMONTHLY,
            RecurrenceFrequency.QUARTERLY,
            RecurrenceFrequency.YEARLY -> {
                val stepMonths = when (item.frequencyType) {
                    RecurrenceFrequency.MONTHLY -> 1 * interval
                    RecurrenceFrequency.BIMONTHLY -> 2 * interval
                    RecurrenceFrequency.QUARTERLY -> 3 * interval
                    RecurrenceFrequency.YEARLY -> 12 * interval
                    else -> 1
                }
                val anchorDay = anchorDate.dayOfMonth
                var stepIndex = 0
                var guard = 0
                while (guard < 1200) {
                    val totalMonths = (anchorDate.year * 12) + (anchorDate.monthNumber - 1) + (stepIndex * stepMonths)
                    val nextYear = totalMonths / 12
                    val nextMonth = (totalMonths % 12) + 1
                    val maxDays = LocalDateUtils.daysInMonth(nextYear, nextMonth)
                    val nextDay = min(anchorDay, maxDays)
                    val cur = LocalDate(nextYear, nextMonth, nextDay)

                    if (cur > eDate) break
                    if (cur >= sDate) {
                        dates.add(LocalDateUtils.formatIsoDate(cur))
                    }
                    stepIndex++
                    guard++
                }
            }
        }

        return dates.distinct().sorted()
    }

    /**
     * Advances [date] by [interval] occurrences of [frequency].
     * For monthly/yearly frequencies, [anchorDay] preserves the original scheduled day of month
     * across variable month lengths (e.g. 31st advancing from Feb 28 back to Mar 31).
     * If [anchorDay] is -1, clamps to the last day of each resulting month.
     * If [anchorDay] is 0 (default), falls back to the day of month of [date].
     */
    fun advanceInterval(
        date: String,
        frequency: RecurrenceFrequency,
        interval: Int = 1,
        anchorDay: Int = 0
    ): String {
        val safeInterval = max(1, interval)
        val d = LocalDateUtils.parseIsoDate(date) ?: return date
        return when (frequency) {
            RecurrenceFrequency.WEEKLY -> LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(d, 7 * safeInterval))
            RecurrenceFrequency.BIWEEKLY -> LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(d, 14 * safeInterval))
            RecurrenceFrequency.SEMIMONTHLY -> {
                val curDay = d.dayOfMonth
                val maxDays = LocalDateUtils.daysInMonth(d.year, d.monthNumber)
                val effectiveAnchor = if (anchorDay in 1..31) anchorDay else curDay
                val d1 = if (effectiveAnchor <= 15) effectiveAnchor else max(1, effectiveAnchor - 14)
                val d2 = if (effectiveAnchor <= 15) min(effectiveAnchor + 14, maxDays) else min(effectiveAnchor, maxDays)
                if (curDay < d2) {
                    LocalDateUtils.formatIsoDate(LocalDate(d.year, d.monthNumber, d2))
                } else {
                    advanceMonths(d, 1, d1)
                }
            }
            RecurrenceFrequency.MONTHLY -> advanceMonths(d, 1 * safeInterval, anchorDay)
            RecurrenceFrequency.BIMONTHLY -> advanceMonths(d, 2 * safeInterval, anchorDay)
            RecurrenceFrequency.QUARTERLY -> advanceMonths(d, 3 * safeInterval, anchorDay)
            RecurrenceFrequency.YEARLY -> advanceMonths(d, 12 * safeInterval, anchorDay)
            RecurrenceFrequency.CUSTOM -> advanceMonths(d, safeInterval, anchorDay)
        }
    }

    /**
     * Overload that advances an [UpcomingItem]'s recurring date, automatically preserving
     * the anchor day extracted from its schedule or recurrence rule.
     */
    fun advanceInterval(item: UpcomingItem, date: String = item.nextDate): String {
        return UpcomingScheduleHelper.advanceRecurrence(item, date)
    }

    private fun advanceMonths(date: LocalDate, monthsToAdd: Int, anchorDay: Int): String {
        val totalMonths = (date.year * 12) + (date.monthNumber - 1) + monthsToAdd
        val nextYear = totalMonths / 12
        val nextMonth = (totalMonths % 12) + 1
        val maxDays = LocalDateUtils.daysInMonth(nextYear, nextMonth)
        val effectiveAnchor = when {
            anchorDay == -1 -> maxDays
            anchorDay in 1..31 -> anchorDay
            else -> date.dayOfMonth
        }
        val nextDay = min(effectiveAnchor, maxDays)
        return LocalDateUtils.formatIsoDate(LocalDate(nextYear, nextMonth, nextDay))
    }

    private fun generateDatesForRule(rule: RecurrenceRule, startDate: String, endDate: String): List<String> {
        val dates = mutableListOf<String>()
        val startParsed = LocalDateUtils.parseYearMonth(startDate) ?: return emptyList()
        val endParsed = LocalDateUtils.parseYearMonth(endDate) ?: return emptyList()
        var curYear = startParsed.first
        var curMonth = startParsed.second
        val endYear = endParsed.first
        val endMonth = endParsed.second

        var guard = 0
        while (guard < 120) {
            val monthDays = LocalDateUtils.daysInMonth(curYear, curMonth)

            when (rule) {
                is RecurrenceRule.MonthDays -> {
                    for (day in rule.days) {
                        val actualDay = if (day == -1) monthDays else min(day, monthDays)
                        if (actualDay >= 1) {
                            val iso = LocalDateUtils.formatIsoDate(LocalDate(curYear, curMonth, actualDay))
                            if (iso in startDate..endDate) dates.add(iso)
                        }
                    }
                }
                is RecurrenceRule.MonthWeekdays -> {
                    val firstDay = LocalDate(curYear, curMonth, 1)
                    val lastDay = LocalDate(curYear, curMonth, monthDays)
                    val firstDayOfWeek = (firstDay.dayOfWeek.ordinal + 1) % 7 // 0=Sun..6=Sat
                    val lastDayOfWeek = (lastDay.dayOfWeek.ordinal + 1) % 7
                    for (ordinal in rule.ordinals) {
                        for (weekday in rule.weekdays) {
                            val day = if (ordinal == -1) {
                                val offsetBack = (lastDayOfWeek - weekday + 7) % 7
                                monthDays - offsetBack
                            } else {
                                val offset = (weekday - firstDayOfWeek + 7) % 7
                                1 + offset + ((ordinal - 1) * 7)
                            }
                            if (day in 1..monthDays) {
                                val iso = LocalDateUtils.formatIsoDate(LocalDate(curYear, curMonth, day))
                                if (iso in startDate..endDate) dates.add(iso)
                            }
                        }
                    }
                }
            }

            if (curYear > endYear || (curYear == endYear && curMonth >= endMonth)) break
            curMonth++
            if (curMonth > 12) {
                curMonth = 1
                curYear++
            }
            guard++
        }
        return dates.distinct().sorted()
    }

    /**
     * Computes the effective amount in cents based on fixed vs history_average strategy.
     */
    fun computeProjectedAmount(
        item: UpcomingItem,
        postedTransactions: List<Transaction>
    ): Cents {
        return UpcomingScheduleHelper.computeProjectedAmount(item, postedTransactions).amountCents
    }

    /**
     * Monthly multiplier factor for bills (e.g. weekly bill = 52/12 = 4.33x per month).
     */
    fun monthlyFactor(frequency: RecurrenceFrequency, interval: Int = 1): Double {
        val safeInterval = max(1, interval).toDouble()
        return when (frequency) {
            RecurrenceFrequency.WEEKLY -> (52.0 / 12.0) / safeInterval
            RecurrenceFrequency.BIWEEKLY -> (26.0 / 12.0) / safeInterval
            RecurrenceFrequency.SEMIMONTHLY -> 2.0 / safeInterval
            RecurrenceFrequency.MONTHLY -> 1.0 / safeInterval
            RecurrenceFrequency.BIMONTHLY -> 0.5 / safeInterval
            RecurrenceFrequency.QUARTERLY -> (1.0 / 3.0) / safeInterval
            RecurrenceFrequency.YEARLY -> (1.0 / 12.0) / safeInterval
            RecurrenceFrequency.CUSTOM -> 1.0 / safeInterval
        }
    }

    /**
     * Reconciles upcoming items against posted transactions to match occurrences.
     */
    fun reconcileOccurrences(
        items: List<UpcomingItem>,
        transactions: List<Transaction>,
        todayDate: String
    ): UpcomingReconciliationResult {
        val occurrences = mutableListOf<ProjectedOccurrence>()
        val consumedTxnIds = mutableSetOf<Long>()
        var occId = 1L

        for (item in items) {
            val merchantName = item.merchant ?: item.name
            val expectedDate = item.nextDate
            val graceUntil = addDays(expectedDate, GRACE_DAYS)

            // Look for best matching transaction
            var bestTxn: Transaction? = null
            var bestScore = 0

            for (txn in transactions) {
                if (consumedTxnIds.contains(txn.id)) continue
                if (txn.effectiveIsIgnored || txn.effectiveIsTransfer) continue

                // Disqualify candidate transactions outside the ±GRACE_DAYS window
                val daysDiff = abs(daysBetween(expectedDate, txn.effectiveDate))
                if (daysDiff > GRACE_DAYS) continue

                val score = scoreMatch(
                    expectedMerchant = merchantName,
                    expectedDate = expectedDate,
                    expectedAmountCents = item.amountCents,
                    expectedCategoryId = item.categoryId,
                    expectedAccountId = item.accountId,
                    txn = txn
                )

                if (score >= MIN_MATCH_SCORE && score > bestScore) {
                    bestScore = score
                    bestTxn = txn
                }
            }

            if (bestTxn != null) {
                consumedTxnIds.add(bestTxn.id)
                occurrences.add(
                    ProjectedOccurrence(
                        id = occId++,
                        householdId = item.householdId,
                        upcomingItemId = item.id,
                        expectedDate = expectedDate,
                        graceUntil = graceUntil,
                        status = OccurrenceStatus.MATCHED,
                        matchedTransactionId = bestTxn.id,
                        matchedAt = bestTxn.effectiveDate
                    )
                )
            } else {
                val isMissed = todayDate.isNotBlank() && todayDate > graceUntil
                occurrences.add(
                    ProjectedOccurrence(
                        id = occId++,
                        householdId = item.householdId,
                        upcomingItemId = item.id,
                        expectedDate = expectedDate,
                        graceUntil = graceUntil,
                        status = if (isMissed) OccurrenceStatus.MISSED else OccurrenceStatus.PENDING,
                        missedAt = if (isMissed) graceUntil else null
                    )
                )
            }
        }

        val matchedCount = occurrences.count { it.status == OccurrenceStatus.MATCHED }
        return UpcomingReconciliationResult(occurrences, matchedCount)
    }

    /**
     * Scores how well a posted transaction matches a projected occurrence.
     */
    fun scoreMatch(
        expectedMerchant: String,
        expectedDate: String,
        expectedAmountCents: Cents,
        expectedCategoryId: Long?,
        expectedAccountId: Long?,
        txn: Transaction
    ): Int {
        val merchantScore = textMatchScore(expectedMerchant, txn.effectiveMerchant)
        if (merchantScore < MIN_MERCHANT_SCORE) return 0

        val daysDiff = abs(daysBetween(expectedDate, txn.effectiveDate))
        if (daysDiff > GRACE_DAYS) return 0

        val dateScore = when {
            daysDiff == 0 -> 15
            daysDiff <= 2 -> 10
            daysDiff <= GRACE_DAYS -> 5
            else -> -50
        }

        val categoryScore = if (expectedCategoryId != null && txn.effectiveCategoryId == expectedCategoryId) 15 else 0
        val accountScore = when {
            expectedAccountId == null -> 5
            txn.accountId == expectedAccountId -> 10
            else -> -15
        }

        val expAbs = abs(expectedAmountCents)
        val actAbs = abs(txn.effectiveAmountCents)
        val amtDiff = abs(expAbs - actAbs)
        val amountScore = when {
            expAbs <= 0L -> 0
            amtDiff == 0L -> 25
            amtDiff <= max(100L, (expAbs * 0.15).roundToLong()) -> 20
            amtDiff <= max(250L, (expAbs * 0.35).roundToLong()) -> 10
            else -> -20
        }

        return merchantScore + dateScore + categoryScore + accountScore + amountScore
    }

    fun textMatchScore(expected: String, actual: String): Int {
        val e = normalizeText(expected)
        val a = normalizeText(actual)
        if (e.isEmpty() || a.isEmpty()) return 0
        if (e == a) return 60
        if (e.length >= 4 && a.length >= 4 && (e.contains(a) || a.contains(e))) return 50

        val eTokens = e.split(" ").filter { it.length > 2 }
        val aTokens = a.split(" ").filter { it.length > 2 }.toSet()
        if (eTokens.isEmpty() || aTokens.isEmpty()) return 0

        val overlap = eTokens.count { aTokens.contains(it) }.toDouble() / eTokens.size.toDouble()
        val reverseOverlap = aTokens.count { eTokens.contains(it) }.toDouble() / aTokens.size.toDouble()
        val maxOverlap = max(overlap, reverseOverlap)
        return when {
            maxOverlap >= 0.75 -> 50
            maxOverlap >= 0.40 -> 40
            maxOverlap >= 0.25 -> 30
            else -> 0
        }
    }

    fun detectCandidates(transactions: List<Transaction>): List<RecurringCandidate> =
        detectRecurringCandidates(transactions, emptySet(), "")

    /**
     * Auto-detects recurring transaction candidates from past transaction history.
     */
    fun detectRecurringCandidates(
        transactions: List<Transaction>,
        existingMerchants: Set<String> = emptySet(),
        todayDate: String = "",
        categoryMap: Map<Long, Category> = emptyMap()
    ): List<RecurringCandidate> {
        val activeTxns = transactions.filter { !it.effectiveIsIgnored && !it.effectiveIsTransfer }
        val effectiveToday = if (todayDate.isNotBlank()) todayDate else (activeTxns.maxOfOrNull { it.effectiveDate } ?: LocalDateUtils.todayIsoDate())
        val byMerchant = activeTxns.groupBy { normalizeText(it.effectiveMerchant) }

        val candidates = mutableListOf<RecurringCandidate>()

        for ((normalizedMerchant, txns) in byMerchant) {
            if (txns.size < 2) continue
            if (existingMerchants.contains(normalizedMerchant)) continue

            val sorted = txns.sortedBy { it.effectiveDate }

            // Require transactions to span at least 2 distinct calendar months
            val months = sorted.mapNotNull { LocalDateUtils.parseYearMonth(it.effectiveDate) }.toSet()
            if (months.size < 2) continue

            val intervals = mutableListOf<Int>()
            for (i in 0 until (sorted.size - 1)) {
                intervals.add(daysBetween(sorted[i].effectiveDate, sorted[i + 1].effectiveDate))
            }
            val avgInterval = intervals.average()
            val amounts = sorted.map { abs(it.effectiveAmountCents) }
            val avgAmount = amounts.average().roundToLong()
            val latestDate = sorted.last().effectiveDate
            val lastTxn = sorted.last()

            val detectedFrequency = when {
                avgInterval in 6.0..8.0 -> RecurrenceFrequency.WEEKLY
                avgInterval in 13.0..16.0 -> RecurrenceFrequency.BIWEEKLY
                avgInterval in 27.0..34.0 -> RecurrenceFrequency.MONTHLY
                avgInterval in 355.0..375.0 -> RecurrenceFrequency.YEARLY
                else -> RecurrenceFrequency.CUSTOM
            }

            val category = lastTxn.effectiveCategoryId?.let { categoryMap[it] }
            val categoryName = category?.name ?: ""
            val isIncome = lastTxn.effectiveAmountCents > 0L
            val direction = if (isIncome) "income" else "expense"

            val confidence = UpcomingScheduleHelper.scoreUpcomingSuggestion(
                categoryName = categoryName,
                merchantName = lastTxn.effectiveMerchant,
                direction = direction,
                averageInterval = avgInterval,
                transactionCount = sorted.size,
                amounts = amounts,
                latestDate = latestDate,
                today = effectiveToday
            )

            if (confidence <= 0) continue

            val tier = if (confidence >= 70) "HIGH" else if (confidence >= 50) "MEDIUM" else "LOW"
            val nextDate = if (detectedFrequency == RecurrenceFrequency.CUSTOM) {
                addDays(latestDate, max(1, avgInterval.roundToLong().toInt()))
            } else {
                advanceInterval(latestDate, detectedFrequency)
            }

            candidates.add(
                RecurringCandidate(
                    merchant = lastTxn.effectiveMerchant, amountCents = avgAmount, frequency = detectedFrequency,
                    transactionCount = sorted.size, confidenceScore = confidence, confidenceTier = tier,
                    suggestedCategoryId = lastTxn.effectiveCategoryId, suggestedAccountId = lastTxn.accountId,
                    lastDate = latestDate, nextProjectedDate = nextDate
                )
            )
        }

        return candidates.sortedByDescending { it.confidenceScore }
    }

    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")

    private fun normalizeText(text: String): String =
        text.lowercase().replace(NON_ALPHANUMERIC_REGEX, " ").trim()

    private fun daysBetween(date1: String, date2: String): Int {
        val d1 = LocalDateUtils.parseIsoDate(date1) ?: return 999
        val d2 = LocalDateUtils.parseIsoDate(date2) ?: return 999
        return abs(LocalDateUtils.daysBetween(d1, d2))
    }

    private fun addDays(date: String, days: Int): String =
        LocalDateUtils.parseIsoDate(date)?.let { LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(it, days)) } ?: date
}
