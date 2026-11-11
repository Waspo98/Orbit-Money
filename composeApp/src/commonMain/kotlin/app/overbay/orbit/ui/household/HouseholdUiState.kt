package app.overbay.orbit.ui.household

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.HouseholdIncomeRecord
import app.overbay.orbit.data.model.HouseholdInfo
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import kotlin.math.min

data class HouseholdUiState(
    val householdInfo: HouseholdInfo = HouseholdInfo(),
    val members: List<HouseholdMember> = emptyList(),
    val storageMode: StorageMode = StorageMode.LOCAL,
    val shares: List<HouseholdShare> = emptyList(),
    val incomeRecords: List<HouseholdIncomeRecord> = emptyList(),
    val availableAccounts: List<Account> = emptyList(),
    val includeHsaInRetirementRate: Boolean = true,
    val inspectingMemberId: Long? = null,
    val showInviteDialog: Boolean = false,
    val showMemberEditor: Boolean = false,
    val editingMember: HouseholdMember? = null,
    val memberToDelete: HouseholdMember? = null,
    val isLoading: Boolean = false,
    val totalHouseholdGrossIncomeCents: Cents = 0L
) {
    val inspectingMember: HouseholdMember?
        get() = members.find { it.id == inspectingMemberId }
}

object HouseholdCalculations {
    fun periodsPerYear(payFrequency: String?): Long = when (payFrequency?.lowercase()?.trim()) {
        "weekly" -> 52L
        "biweekly" -> 26L
        "semimonthly" -> 24L
        "monthly" -> 12L
        "annual" -> 1L
        else -> 0L
    }

    fun safePayPeriods(payFrequency: String?): Double {
        val periods = periodsPerYear(payFrequency).toDouble()
        return if (periods > 0.0) periods else 1.0
    }

    fun memberNetPayAnnual(m: HouseholdMember): Cents {
        val periods = periodsPerYear(m.payFrequency)
        return m.netPayPerPeriodCents * periods
    }

    fun memberEmployeeRetirementAnnual(m: HouseholdMember): Cents {
        if (m.employeeContributionAnnualCents > 0L) return m.employeeContributionAnnualCents
        val pct = m.employeeContributionPercent ?: 0.0
        val gross = m.grossIncomeAnnualCents
        return if (pct > 0.0 && gross > 0L) {
            MoneyUtils.multiplyPercent(gross, pct)
        } else 0L
    }

    fun memberEmployerMatchAnnual(m: HouseholdMember): Cents {
        val gross = m.grossIncomeAnnualCents
        val empPct = m.employeeContributionPercent ?: 0.0
        val matchPct = m.employerMatchPercent
        val matchLimitPct = if (m.employerMatchLimitPercent > 0.0) m.employerMatchLimitPercent else empPct
        val matchCap = m.employerMatchAnnualCapCents
        val eligiblePct = min(empPct, matchLimitPct)
        val uncapped = if (eligiblePct > 0.0 && matchPct > 0.0 && gross > 0L) {
            MoneyUtils.multiplyPercent(gross, (eligiblePct * matchPct) / 100.0)
        } else 0L
        return if (matchCap > 0L) min(matchCap, uncapped) else uncapped
    }

    fun memberBenefitsAnnual(m: HouseholdMember): Cents {
        val match = memberEmployerMatchAnnual(m)
        return match + m.hsaContributionAnnualCents + m.dependentCareFsaAnnualCents + m.otherBenefitsAnnualCents
    }

    fun memberHouseholdValueAnnual(m: HouseholdMember): Cents {
        return memberNetPayAnnual(m) + memberBenefitsAnnual(m)
    }

    fun memberRetirementRatePercent(m: HouseholdMember, includeHsa: Boolean): Double {
        val gross = m.grossIncomeAnnualCents
        if (gross <= 0L) return 0.0
        val empRet = memberEmployeeRetirementAnnual(m)
        val match = memberEmployerMatchAnnual(m)
        val hsa = if (includeHsa) m.hsaContributionAnnualCents else 0L
        return ((empRet + match + hsa).toDouble() / gross.toDouble()) * 100.0
    }
}
