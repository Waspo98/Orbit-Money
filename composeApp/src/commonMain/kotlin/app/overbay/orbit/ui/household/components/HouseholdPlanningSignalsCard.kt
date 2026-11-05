package app.overbay.orbit.ui.household.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSwitchRow
import kotlin.math.min
import kotlin.math.roundToLong

@Composable
fun HouseholdPlanningSignalsCard(
    members: List<HouseholdMember>,
    includeHsaInSavingsRate: Boolean,
    onToggleIncludeHsa: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalGrossCents = members.sumOf { it.grossIncomeAnnualCents }

    // 1. Annual Take-Home Pay
    val annualTakeHomeCents = members.sumOf { m ->
        val periods = if (m.payPeriodsPerYear > 0.0) m.payPeriodsPerYear else 26.0
        ((m.netPayPerPeriodCents.toDouble() * periods).roundToLong())
    }

    // 2. Total Retirement Savings + Match
    var totalEmployeeSavingsCents = 0L
    var totalEmployerMatchCents = 0L
    var totalHsaCents = 0L
    var totalBenefitsValueCents = 0L

    for (m in members) {
        val gross = m.grossIncomeAnnualCents
        val empPct = m.employeeContributionPercent ?: 0.0
        val empSavings = if (m.employeeContributionAnnualCents > 0L) {
            m.employeeContributionAnnualCents
        } else if (empPct > 0.0 && gross > 0L) {
            ((gross.toDouble() * empPct) / 100.0).roundToLong()
        } else 0L

        val matchPct = m.employerMatchPercent
        val matchLimitPct = m.employerMatchLimitPercent
        val matchCap = m.employerMatchAnnualCapCents
        val eligiblePct = if (matchLimitPct > 0.0) min(empPct, matchLimitPct) else empPct
        val matchRate = if (matchPct > 0.0) matchPct / 100.0 else 1.0
        val uncappedMatch = if (eligiblePct > 0.0 && gross > 0L) {
            ((gross.toDouble() * eligiblePct / 100.0) * matchRate).roundToLong()
        } else 0L
        val match = if (matchCap > 0L) min(matchCap, uncappedMatch) else uncappedMatch

        val hsa = m.hsaContributionAnnualCents
        val benefits = (m.healthPremiumPerMonthCents * 12) + hsa + m.dependentCareFsaAnnualCents + m.otherBenefitsAnnualCents

        totalEmployeeSavingsCents += empSavings
        totalEmployerMatchCents += match
        totalHsaCents += hsa
        totalBenefitsValueCents += benefits
    }

    val totalRetirementAndMatchCents = totalEmployeeSavingsCents + totalEmployerMatchCents

    // 3. Largest Contributor & Effective Savings Rate
    val largestContributor = members.maxByOrNull { it.grossIncomeAnnualCents }
    val qualifiedSavingsForRate = totalEmployeeSavingsCents + totalEmployerMatchCents + (if (includeHsaInSavingsRate) totalHsaCents else 0L)
    val effectiveSavingsRatePercent = if (totalGrossCents > 0L) {
        (qualifiedSavingsForRate.toDouble() / totalGrossCents.toDouble()) * 100.0
    } else 0.0

    OrbitSectionCard(
        title = "Household Financial Signals",
        subtitle = "Consolidated income, benefits, and retirement accumulation",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 2x2 KPI Tiles
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalTile(
                    title = "Annual Take-Home",
                    value = MoneyUtils.formatCents(annualTakeHomeCents, showCents = false),
                    subtitle = "Net household paycheck cash flow",
                    modifier = Modifier.weight(1f)
                )
                SignalTile(
                    title = "Retirement + Match",
                    value = MoneyUtils.formatCents(totalRetirementAndMatchCents, showCents = false),
                    subtitle = "Annual employee & employer savings",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalTile(
                    title = "Pre-Tax Benefits Value",
                    value = MoneyUtils.formatCents(totalBenefitsValueCents, showCents = false),
                    subtitle = "Health, HSA, FSA & employer plans",
                    modifier = Modifier.weight(1f)
                )
                SignalTile(
                    title = "Effective Savings Rate",
                    value = "${effectiveSavingsRatePercent.toInt()}%",
                    subtitle = "Top Earner: ${largestContributor?.name?.split(" ")?.firstOrNull() ?: "None"}",
                    isEmphasized = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // HSA Rate Toggle
            OrbitSwitchRow(
                title = "Include HSA in Retirement Savings Rate",
                subtitle = if (includeHsaInSavingsRate) "Counting annual HSA contributions toward household wealth accumulation" else "Excluding HSA contributions from retirement rate",
                checked = includeHsaInSavingsRate,
                onCheckedChange = onToggleIncludeHsa
            )
        }
    }
}

@Composable
private fun SignalTile(
    title: String,
    value: String,
    subtitle: String,
    isEmphasized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isEmphasized) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isEmphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
