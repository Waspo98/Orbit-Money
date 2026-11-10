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
import app.overbay.orbit.ui.components.OrbitStatTile
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.ui.household.HouseholdCalculations

@Composable
fun HouseholdPlanningSignalsCard(
    members: List<HouseholdMember>,
    includeHsaInSavingsRate: Boolean,
    onToggleIncludeHsa: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalGrossCents = members.sumOf { it.grossIncomeAnnualCents }

    // 1. Annual Take-Home Pay (strictly netPayPerPeriod * periodsPerYear)
    val annualTakeHomeCents = members.sumOf { m ->
        HouseholdCalculations.memberNetPayAnnual(m)
    }

    // 2. Total Retirement Savings + Match & Benefits
    var totalEmployeeSavingsCents = 0L
    var totalEmployerMatchCents = 0L
    var totalHsaCents = 0L
    var totalBenefitsValueCents = 0L

    for (m in members) {
        val empSavings = HouseholdCalculations.memberEmployeeRetirementAnnual(m)
        val match = HouseholdCalculations.memberEmployerMatchAnnual(m)
        val hsa = m.hsaContributionAnnualCents
        val benefits = HouseholdCalculations.memberBenefitsAnnual(m)

        totalEmployeeSavingsCents += empSavings
        totalEmployerMatchCents += match
        totalHsaCents += hsa
        totalBenefitsValueCents += benefits
    }

    val totalRetirementAndMatchCents = totalEmployeeSavingsCents + totalEmployerMatchCents

    // 3. Top Contributor (ranked by annualNet + memberBenefits) & Effective Savings Rate
    val largestContributor = members.maxByOrNull { HouseholdCalculations.memberHouseholdValueAnnual(it) }
    val qualifiedSavingsForRate = totalEmployeeSavingsCents + totalEmployerMatchCents + (if (includeHsaInSavingsRate) totalHsaCents else 0L)
    val effectiveSavingsRatePercent = if (totalGrossCents > 0L) {
        (qualifiedSavingsForRate.toDouble() / totalGrossCents.toDouble()) * 100.0
    } else 0.0

    OrbitSectionCard(
        title = "Household Summary",
        subtitle = "Consolidated income, benefits, and retirement accumulation",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 2x2 KPI Tiles
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrbitStatTile(
                    label = "Annual Take-Home",
                    value = MoneyUtils.formatCents(annualTakeHomeCents, showCents = false),
                    subtitle = "Net household paycheck cash flow",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                )
                OrbitStatTile(
                    label = "Retirement + Match",
                    value = MoneyUtils.formatCents(totalRetirementAndMatchCents, showCents = false),
                    subtitle = "Annual employee & employer savings",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrbitStatTile(
                    label = "Pre-Tax Benefits Value",
                    value = MoneyUtils.formatCents(totalBenefitsValueCents, showCents = false),
                    subtitle = "Health, HSA, FSA & employer plans",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                )
                OrbitStatTile(
                    label = "Effective Savings Rate",
                    value = "${effectiveSavingsRatePercent.toInt()}%",
                    subtitle = "Top Contributor: ${largestContributor?.name?.split(" ")?.firstOrNull() ?: "None"}",
                    valueColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
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
