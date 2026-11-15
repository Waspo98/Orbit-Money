package app.overbay.orbit.ui.calculators.retirement

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.ProjectionPoint
import app.overbay.orbit.domain.RetirementCalculatorEngine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

internal object RetirementAccountResolver {

    private data class MemberContributionBreakdown(
        val employeeSavingsCents: Cents,
        val employerMatchCents: Cents,
        val matchCapReached: Boolean,
        val hsaCents: Cents,
        val totalAnnualCents: Cents
    )

    private fun computeMemberBreakdown(member: HouseholdMember): MemberContributionBreakdown {
        val gross = member.grossIncomeAnnualCents
        val empPct = member.employeeContributionPercent ?: 0.0
        val empContribCents = if (member.employeeContributionAnnualCents > 0L) {
            member.employeeContributionAnnualCents
        } else if (empPct > 0.0 && gross > 0L) {
            ((gross.toDouble() * empPct) / 100.0).roundToLong()
        } else 0L

        val matchPct = member.employerMatchPercent
        val matchLimitPct = member.employerMatchLimitPercent
        val matchCap = member.employerMatchAnnualCapCents
        val eligiblePct = if (matchLimitPct > 0.0) min(empPct, matchLimitPct) else empPct
        val matchRate = if (matchPct > 0.0) matchPct / 100.0 else 0.0
        val uncappedMatchCents = if (eligiblePct > 0.0 && gross > 0L) {
            ((gross.toDouble() * eligiblePct / 100.0) * matchRate).roundToLong()
        } else 0L
        val matchCapReached = matchCap > 0L && uncappedMatchCents > matchCap
        val matchCents = if (matchCap > 0L) min(matchCap, uncappedMatchCents) else uncappedMatchCents
        val hsaCents = member.hsaContributionAnnualCents
        return MemberContributionBreakdown(
            employeeSavingsCents = empContribCents,
            employerMatchCents = matchCents,
            matchCapReached = matchCapReached,
            hsaCents = hsaCents,
            totalAnnualCents = empContribCents + matchCents + hsaCents
        )
    }

    fun calculateMemberRetirementAnnual(member: HouseholdMember): Cents =
        computeMemberBreakdown(member).totalAnnualCents

    fun classifyPlanKind(nameOrKind: String): String =
        RetirementCalculatorEngine.formatPlanKindLabel(nameOrKind)

    fun buildMemberSummaries(
        members: List<HouseholdMember>,
        accountMap: Map<Long, Account>
    ): List<MemberRetirementSummary> {
        return members.map { member ->
            val breakdown = computeMemberBreakdown(member)
            val payPeriods = if (member.payPeriodsPerYear > 0.0) member.payPeriodsPerYear else 26.0
            val perPaycheck = (breakdown.totalAnnualCents.toDouble() / payPeriods).roundToLong()
            val chips = member.retirementAccounts.map { link ->
                val acct = accountMap[link.accountId]
                val labelSource = link.accountKind.ifBlank { acct?.name ?: "" }
                LinkedAccountChip(
                    id = link.accountId,
                    displayKind = classifyPlanKind(labelSource),
                    name = acct?.name ?: "Account #${link.accountId}"
                )
            }
            MemberRetirementSummary(
                memberId = member.id,
                name = member.name,
                employer = member.employer,
                payFrequencyLabel = member.payFrequency.replaceFirstChar { it.uppercase() },
                perPaycheckCents = perPaycheck,
                annualEmployeeSavingsCents = breakdown.employeeSavingsCents,
                annualEmployerMatchCents = breakdown.employerMatchCents,
                matchCapReached = breakdown.matchCapReached,
                annualHsaCents = breakdown.hsaCents,
                annualTotalCents = breakdown.totalAnnualCents,
                linkedAccounts = chips
            )
        }
    }

    fun resolveDefaultIncludedAccountIds(
        members: List<HouseholdMember>,
        investments: List<Account>,
        goals: List<Goal>
    ): Set<Long> {
        val memberIds = members.flatMap { m -> m.retirementAccounts.map { it.accountId } }.toSet()
        if (memberIds.isNotEmpty()) return memberIds

        if (investments.isNotEmpty()) return investments.map { it.id }.toSet()

        val retirementGoal = goals.find { it.name.trim().equals("retirement", ignoreCase = true) }
        if (retirementGoal != null) {
            if (retirementGoal.allocations.isNotEmpty()) {
                return retirementGoal.allocations.mapNotNull { it.accountId }.toSet()
            }
            retirementGoal.linkedAccountId?.let { return setOf(it) }
        }
        return emptySet()
    }

    fun buildLinkedAccountDetails(
        members: List<HouseholdMember>,
        accounts: List<Account>,
        accountMap: Map<Long, Account>,
        investments: List<Account>,
        goals: List<Goal>,
        customIncludedIds: Set<Long>?
    ): List<LinkedRetirementAccountDetail> {
        val list = mutableListOf<LinkedRetirementAccountDetail>()
        var colorIndex = 0

        if (customIncludedIds != null) {
            val memberLinkByAccount = mutableMapOf<Long, Pair<String, String>>()
            members.forEach { member ->
                member.retirementAccounts.forEach { ra ->
                    val acct = accountMap[ra.accountId]
                    val planKind = classifyPlanKind(ra.accountKind.ifBlank { acct?.name ?: "" })
                    memberLinkByAccount.put(ra.accountId, member.name to planKind)
                }
            }
            accounts.asSequence()
                .filter { !it.isArchived && it.id in customIncludedIds }
                .forEach { acct ->
                    val linkedMeta = memberLinkByAccount[acct.id]
                    val planKind = linkedMeta?.second ?: classifyPlanKind(acct.name).let { label ->
                        if (label == "Other" && acct.type != AccountType.OTHER) acct.type.displayName else label
                    }
                    list.add(
                        LinkedRetirementAccountDetail(
                            id = acct.id,
                            accountId = acct.id,
                            name = acct.name,
                            ownerName = linkedMeta?.first,
                            planKind = planKind,
                            institution = acct.institution,
                            balanceCents = max(0L, acct.currentBalanceCents),
                            colorIndex = colorIndex++
                        )
                    )
                }
            return list
        }

        // 1. Check household member retirement accounts
        members.forEach { member ->
            member.retirementAccounts.forEach { ra ->
                val acct = accountMap[ra.accountId]
                val planKind = classifyPlanKind(ra.accountKind.ifBlank { acct?.name ?: "" })
                val balance = acct?.currentBalanceCents ?: 0L
                list.add(
                    LinkedRetirementAccountDetail(
                        id = ra.accountId,
                        accountId = ra.accountId,
                        name = acct?.name ?: "Retirement Account #${ra.accountId}",
                        ownerName = member.name,
                        planKind = planKind,
                        institution = acct?.institution,
                        balanceCents = max(0L, balance),
                        colorIndex = colorIndex++
                    )
                )
            }
        }
        if (list.isNotEmpty()) return list

        // 2. Check general active investment accounts
        if (investments.isNotEmpty()) {
            investments.forEach { acct ->
                list.add(
                    LinkedRetirementAccountDetail(
                        id = acct.id,
                        accountId = acct.id,
                        name = acct.name,
                        ownerName = null,
                        planKind = classifyPlanKind(acct.name),
                        institution = acct.institution,
                        balanceCents = max(0L, acct.currentBalanceCents),
                        colorIndex = colorIndex++
                    )
                )
            }
            return list
        }

        // 3. Fallback to goal named "Retirement"
        val retirementGoal = goals.find { it.name.trim().equals("retirement", ignoreCase = true) }
        if (retirementGoal != null) {
            if (retirementGoal.allocations.isNotEmpty()) {
                retirementGoal.allocations.forEach { alloc ->
                    val acct = accountMap[alloc.accountId]
                    list.add(
                        LinkedRetirementAccountDetail(
                            id = alloc.id,
                            accountId = alloc.accountId,
                            name = alloc.accountName.ifBlank { acct?.name ?: "Retirement Allocation" },
                            ownerName = null,
                            planKind = classifyPlanKind(alloc.accountName),
                            institution = acct?.institution,
                            balanceCents = max(0L, alloc.currentAmountCents),
                            colorIndex = colorIndex++
                        )
                    )
                }
            } else if (retirementGoal.currentAmountCents > 0L) {
                list.add(
                    LinkedRetirementAccountDetail(
                        id = retirementGoal.id,
                        accountId = retirementGoal.linkedAccountId,
                        name = retirementGoal.name,
                        ownerName = null,
                        planKind = "Retirement Goal",
                        institution = null,
                        balanceCents = retirementGoal.currentAmountCents,
                        colorIndex = colorIndex
                    )
                )
            }
        }
        return list
    }

    fun filterHistoryPoints(
        history: List<RetirementHistoryPoint>,
        horizon: String,
        currentYear: String,
        customIncludedIds: Set<Long>? = null
    ): List<RetirementHistoryPoint> {
        val scoped = filterHistoryByAccounts(history, customIncludedIds)
        return when (horizon) {
            "YTD" -> scoped.filter { it.month.startsWith(currentYear) }.ifEmpty { scoped.takeLast(12) }
            "3Y" -> scoped.takeLast(36)
            "5Y" -> scoped.takeLast(60)
            "10Y" -> scoped.takeLast(120)
            else -> scoped
        }
    }

    fun filterHistoryByAccounts(
        history: List<RetirementHistoryPoint>,
        customIncludedIds: Set<Long>?
    ): List<RetirementHistoryPoint> {
        if (customIncludedIds == null) return history
        if (customIncludedIds.isEmpty()) return emptyList()
        return history.mapNotNull { pt ->
            if (pt.accounts.isEmpty()) return@mapNotNull null
            val matching = pt.accounts.filter { it.accountId in customIncludedIds }
            if (matching.isEmpty()) null else pt.copy(
                balanceCents = matching.sumOf { it.balanceCents },
                accounts = matching
            )
        }
    }

    fun buildActualHistoryProjectionPoints(
        history: List<RetirementHistoryPoint>,
        currentAge: Double,
        currentBalanceCents: Cents
    ): List<ProjectionPoint> {
        val today = LocalDateUtils.todayLocalDate()
        val result = mutableListOf<ProjectionPoint>()

        history.forEach { pt ->
            val year = pt.month.take(4).toIntOrNull()
            val month = pt.month.drop(5).take(2).toIntOrNull()
            if (year != null && month != null) {
                val monthDiff = (today.year - year) * 12 + (today.monthNumber - month)
                val age = currentAge - (monthDiff.toDouble() / 12.0)
                if (age <= currentAge + 0.05) {
                    result.add(ProjectionPoint(age, pt.balanceCents))
                }
            }
        }

        result.sortBy { it.age }
        if (result.none { abs(it.age - currentAge) < 0.08 }) {
            result.add(ProjectionPoint(currentAge, currentBalanceCents))
            result.sortBy { it.age }
        }
        return result
    }
}
