package app.overbay.orbit.ui.goals.components

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.GoalAllocation
import app.overbay.orbit.data.model.GoalAllocationType
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import kotlinx.datetime.LocalDate

internal fun Goal.linkedAccountIds(): List<Long> {
    val ids = allocations.map { it.accountId }.distinct()
    return if (ids.isNotEmpty()) ids else listOfNotNull(linkedAccountId)
}

internal fun Goal.linkedAccountDisplayNames(accountMap: Map<Long, Account>): List<String> {
    val fromAllocs = allocations.mapNotNull { alloc ->
        accountMap[alloc.accountId]?.name ?: alloc.accountName.takeIf { it.isNotBlank() }
    }.distinct()
    if (fromAllocs.isNotEmpty()) return fromAllocs
    val legacy = linkedAccountId?.let { accountMap[it]?.name }
    return if (legacy != null) listOf(legacy) else emptyList()
}

internal fun Goal.sourceSummaryLabel(accountMap: Map<Long, Account>): String {
    val names = linkedAccountDisplayNames(accountMap)
    return when (names.size) {
        0 -> "No funding account linked"
        1 -> names.first()
        2 -> "${names[0]} · ${names[1]}"
        else -> "${names[0]} · ${names[1]} +${names.size - 2}"
    }
}

internal fun Goal.monthsUntilTargetDate(today: LocalDate = LocalDateUtils.todayLocalDate()): Int? =
    LocalDateUtils.calendarMonthsUntil(targetDate, today)

internal fun Goal.isProjectedPastTargetDate(today: LocalDate = LocalDateUtils.todayLocalDate()): Boolean {
    val projectedMonths = eta?.months ?: return false
    val targetMonths = monthsUntilTargetDate(today) ?: return false
    return projectedMonths > targetMonths
}

internal fun Goal.projectedLandingLabel(): String = when {
    isCompleted -> "Reached"
    !eta?.date.isNullOrBlank() -> eta!!.date!!
    else -> "Stalled"
}

internal fun formatMonthlyPace(paceCents: Cents): String = when {
    paceCents > 0L -> "+${MoneyUtils.formatCents(paceCents, showCents = false)}/mo"
    paceCents < 0L -> "-${MoneyUtils.formatCents(-paceCents, showCents = false)}/mo"
    else -> "$0/mo"
}

internal fun GoalAllocation.resolvedFundedCents(accountBalanceCents: Cents): Cents =
    if (allocationType == GoalAllocationType.PERCENT) {
        val allocatable = (accountBalanceCents - reserveAmountCents).coerceAtLeast(0L)
        MoneyUtils.multiplyPercent(allocatable, allocationPercent)
    } else {
        allocationAmountCents
    }

internal fun formatCurrencySmart(cents: Cents): String =
    MoneyUtils.formatCents(cents, showCents = cents % 100L != 0L)
