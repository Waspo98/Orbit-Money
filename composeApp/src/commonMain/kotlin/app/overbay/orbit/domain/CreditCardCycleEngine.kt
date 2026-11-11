package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * Cycle and billing status for an active credit card account.
 *
 * All money fields are integer Cents. All dates are kotlinx.datetime.LocalDate.
 */
data class CardCycleStatus(
    val accountId: Long,
    val cardTitle: String,
    val holderLabel: String?,
    val lastCloseDate: LocalDate?,
    val nextCloseDate: LocalDate?,
    val dueDate: LocalDate?,
    val daysUntilDue: Int?,
    val estimatedStatementBalanceCents: Cents?,
    val owedNowCents: Cents,
    val creditLimitCents: Cents?,
    val utilizationBps: Int?,
    val promoEndDate: LocalDate?,
    val daysUntilPromoEnd: Int?,
    val nextAnnualFeeDate: LocalDate?,
    val daysUntilAnnualFee: Int?,
    val annualFeeCents: Cents,
    val authorizedUserNames: List<String> = emptyList()
)

/**
 * Aggregated credit portfolio summary across all active credit accounts in the household.
 */
data class HouseholdCreditSummary(
    val totalOwedCents: Cents,
    val totalLimitCents: Cents,
    val utilizationBps: Int?,
    val nextDue: CardCycleStatus?
)

/**
 * Pure domain calculation engine for credit card statement closing dates, payment due dates,
 * statement balance estimation, annual fee anniversary rolls, and portfolio credit utilization.
 *
 * SIGN CONVENTIONS:
 * - [owedNowCents]: absolute value of [Account.currentBalanceCents], matching liability conventions.
 * - [estimatedStatementBalanceCents]: computed when [Account.currentBalanceCents] <= 0 (the synced convention).
 *   Transactions in Orbit use negative cents for expenses/charges and positive cents for payments/refunds.
 *   `balanceAtClose = currentBalance - sum(effectiveAmountCents for txns with date > lastClose)`
 *   `estimate = max(0, -balanceAtClose)`
 *   If currentBalance > 0 (credit balance / overpayment) or lastClose is null, estimate is null.
 */
object CreditCardCycleEngine {

    /**
     * Clamps [day] to the valid number of days in [month] of [year].
     */
    fun closeDateIn(year: Int, month: Int, day: Int): LocalDate {
        val maxDays = LocalDateUtils.daysInMonth(year, month)
        val clampedDay = day.coerceIn(1, maxDays)
        return LocalDate(year, month, clampedDay)
    }

    /**
     * Determines the most recent statement closing date that occurred on or before [today].
     */
    fun lastClose(closingDay: Int, today: LocalDate): LocalDate {
        val thisMonthClose = closeDateIn(today.year, today.monthNumber, closingDay)
        return if (thisMonthClose <= today) {
            thisMonthClose
        } else {
            val (prevYear, prevMonth) = if (today.monthNumber == 1) {
                Pair(today.year - 1, 12)
            } else {
                Pair(today.year, today.monthNumber - 1)
            }
            closeDateIn(prevYear, prevMonth, closingDay)
        }
    }

    /**
     * Determines the next statement closing date occurring strictly after [lastClose].
     */
    fun nextClose(closingDay: Int, today: LocalDate): LocalDate {
        val thisMonthClose = closeDateIn(today.year, today.monthNumber, closingDay)
        return if (thisMonthClose <= today) {
            val (nextYear, nextMonth) = if (today.monthNumber == 12) {
                Pair(today.year + 1, 1)
            } else {
                Pair(today.year, today.monthNumber + 1)
            }
            closeDateIn(nextYear, nextMonth, closingDay)
        } else {
            thisMonthClose
        }
    }

    /**
     * Finds the first clamped [dueDay] strictly after [close].
     * If [dueDay] in close's month is after [close], uses that date; otherwise rolls to the subsequent month.
     */
    fun dueDateAfter(close: LocalDate, dueDay: Int): LocalDate {
        val sameMonthDue = closeDateIn(close.year, close.monthNumber, dueDay)
        return if (sameMonthDue > close) {
            sameMonthDue
        } else {
            val (nextYear, nextMonth) = if (close.monthNumber == 12) {
                Pair(close.year + 1, 1)
            } else {
                Pair(close.year, close.monthNumber + 1)
            }
            closeDateIn(nextYear, nextMonth, dueDay)
        }
    }

    /**
     * Rolls an annual fee post date forward by whole years until it is on or after [today].
     * Handles leap day (Feb 29) by clamping to Feb 28 on non-leap years.
     */
    fun nextAnnualFeeDate(postIso: String?, today: LocalDate): LocalDate? {
        val postDate = LocalDateUtils.parseIsoDate(postIso) ?: return null
        if (postDate >= today) return postDate

        var candidateYear = today.year
        var candidate = closeDateIn(candidateYear, postDate.monthNumber, postDate.dayOfMonth)
        if (candidate < today) {
            candidateYear += 1
            candidate = closeDateIn(candidateYear, postDate.monthNumber, postDate.dayOfMonth)
        }
        return candidate
    }

    /**
     * Computes the [CardCycleStatus] for an individual account.
     * Returns null if the account is not an active, non-archived credit account without a closed date.
     */
    fun status(
        account: Account,
        txns: List<Transaction>,
        today: LocalDate,
        householdMembers: List<HouseholdMember> = emptyList()
    ): CardCycleStatus? {
        if (account.type != AccountType.CREDIT || account.isArchived) return null
        val profile = account.creditCardProfile
        if (!profile?.strategy?.closedDate.isNullOrBlank()) return null

        val owedNowCents = abs(account.currentBalanceCents)
        val closingDay = profile?.statementClosingDay?.takeIf { it in 1..31 }
        val dueDay = profile?.paymentDueDay?.takeIf { it in 1..31 }

        val lastClose = closingDay?.let { lastClose(it, today) }
        val nextClose = closingDay?.let { nextClose(it, today) }

        val dueDate = when {
            lastClose != null && dueDay != null -> {
                val candidateDue = dueDateAfter(lastClose, dueDay)
                if (candidateDue >= today) candidateDue else nextClose?.let { dueDateAfter(it, dueDay) }
            }
            dueDay != null -> {
                val thisMonthDue = closeDateIn(today.year, today.monthNumber, dueDay)
                if (thisMonthDue >= today) {
                    thisMonthDue
                } else {
                    val (nextYear, nextMonth) = if (today.monthNumber == 12) Pair(today.year + 1, 1) else Pair(today.year, today.monthNumber + 1)
                    closeDateIn(nextYear, nextMonth, dueDay)
                }
            }
            else -> null
        }

        val daysUntilDue = dueDate?.let { LocalDateUtils.daysBetween(today, it) }

        val estimatedStatementBalanceCents = if (account.currentBalanceCents <= 0L && lastClose != null) {
            val lastCloseIso = LocalDateUtils.formatIsoDate(lastClose)
            val txnsSinceClose = txns.filter {
                it.accountId == account.id && !it.effectiveIsIgnored && it.effectiveDate > lastCloseIso
            }
            val netDeltaAfterClose = txnsSinceClose.sumOf { it.effectiveAmountCents }
            val balanceAtClose = account.currentBalanceCents - netDeltaAfterClose
            max(0L, -balanceAtClose)
        } else {
            null
        }

        val creditLimitCents = profile?.creditLimitCents?.takeIf { it > 0L }
        val utilizationBps = creditLimitCents?.let { limit ->
            ((owedNowCents * 10000L) / limit).toInt()
        }

        val promoEndDate = profile?.promoEndDate?.let { LocalDateUtils.parseIsoDate(it) }
        val daysUntilPromoEnd = promoEndDate?.let { LocalDateUtils.daysBetween(today, it) }

        val annualFeeCents = profile?.annualFeeCents ?: 0L
        val nextFeeDate = nextAnnualFeeDate(profile?.annualFeePostDate, today)
        val daysUntilAnnualFee = nextFeeDate?.let { LocalDateUtils.daysBetween(today, it) }

        val memberMap = householdMembers.associateBy { it.id }
        val cardTitle = profile?.cardName?.takeIf { it.isNotBlank() } ?: account.name
        val holderLabel = profile?.strategy?.let { CreditCardStrategyEngine.resolveCardholderName(it, memberMap) }
        val auNames = buildList {
            profile?.strategy?.authorizedUserMemberIds?.forEach { auId ->
                memberMap[auId]?.name?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            profile?.authorizedUsers?.forEach { raw ->
                val trimmed = raw.trim()
                if (trimmed.isNotEmpty() && none { it.equals(trimmed, ignoreCase = true) }) add(trimmed)
            }
        }

        return CardCycleStatus(
            accountId = account.id,
            cardTitle = cardTitle,
            holderLabel = holderLabel,
            lastCloseDate = lastClose,
            nextCloseDate = nextClose,
            dueDate = dueDate,
            daysUntilDue = daysUntilDue,
            estimatedStatementBalanceCents = estimatedStatementBalanceCents,
            owedNowCents = owedNowCents,
            creditLimitCents = creditLimitCents,
            utilizationBps = utilizationBps,
            promoEndDate = promoEndDate,
            daysUntilPromoEnd = daysUntilPromoEnd,
            nextAnnualFeeDate = nextFeeDate,
            daysUntilAnnualFee = daysUntilAnnualFee,
            annualFeeCents = annualFeeCents,
            authorizedUserNames = auNames
        )
    }

    /**
     * Aggregates all active household credit card accounts and computes portfolio totals.
     */
    fun summarize(
        accounts: List<Account>,
        txns: List<Transaction>,
        today: LocalDate,
        householdMembers: List<HouseholdMember> = emptyList()
    ): Pair<HouseholdCreditSummary, List<CardCycleStatus>> {
        val statuses = accounts.mapNotNull { status(it, txns, today, householdMembers) }
        val totalOwedCents = statuses.sumOf { it.owedNowCents }
        val totalLimitCents = statuses.mapNotNull { it.creditLimitCents }.sum()
        val utilizationBps = if (totalLimitCents > 0L) {
            ((totalOwedCents * 10000L) / totalLimitCents).toInt()
        } else {
            null
        }

        val nextDue = statuses
            .filter { it.daysUntilDue != null && it.daysUntilDue >= 0 }
            .minByOrNull { it.daysUntilDue!! }

        val summary = HouseholdCreditSummary(
            totalOwedCents = totalOwedCents,
            totalLimitCents = totalLimitCents,
            utilizationBps = utilizationBps,
            nextDue = nextDue
        )

        return Pair(summary, statuses)
    }
}
