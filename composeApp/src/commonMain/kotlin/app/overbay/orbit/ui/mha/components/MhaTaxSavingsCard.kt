package app.overbay.orbit.ui.mha.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.MhaYearTaxRecord
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MhaTaxCalculationResult
import app.overbay.orbit.domain.MhaTaxEngine
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.ui.components.RollingTickerCents
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MhaTaxSavingsCard(
    taxResult: MhaTaxCalculationResult?,
    eligibleExpensesCents: Cents,
    eligibleTransactionCount: Int,
    excludableAllowanceCents: Cents,
    designatedCapCents: Cents?,
    fairMarketRentalValueCents: Cents?,
    selectedYear: Int,
    currentYear: Int,
    taxProfile: MhaTaxProfile,
    activeYearTaxRecord: MhaYearTaxRecord?,
    taxRateSourceLabel: String,
    isUsingStoredYearRate: Boolean,
    isHistoricalYearWithoutSavedRate: Boolean,
    onEditSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val isHousehold = taxProfile.mode.equals("household", ignoreCase = true)
    val totalSavings = taxResult?.totalSavingsCents ?: 0L
    val fedSavings = taxResult?.federalSavingsCents ?: 0L
    val stateSavings = taxResult?.stateSavingsCents ?: 0L

    val effectiveRatePercent = taxResult?.let {
        it.effectiveSavingsRate * 100.0
    } ?: (taxProfile.simpleFederalRatePercent + taxProfile.simpleStateRatePercent)

    val fedRatePercent = taxResult?.let {
        it.federalMarginalRate * 100.0
    } ?: taxProfile.simpleFederalRatePercent

    val stateRatePercent = taxResult?.let {
        it.stateRate * 100.0
    } ?: taxProfile.simpleStateRatePercent

    val taxableIncomeBeforeMha = taxResult?.taxableIncomeBeforeMhaCents ?: 0L
    val grossIncome = taxResult?.grossIncomeCents ?: 0L
    val reduction = taxResult?.reductionCents ?: 0L

    val showGraduatedWarning = !isUsingStoredYearRate &&
        isHousehold &&
        taxProfile.stateRateOverridePercent == null &&
        MhaTaxEngine.isGraduatedState(taxProfile.state)

    val activeCap: Pair<String, Cents>? = when {
        designatedCapCents != null && fairMarketRentalValueCents != null ->
            if (designatedCapCents <= fairMarketRentalValueCents) {
                "Designation Cap" to designatedCapCents
            } else {
                "Fair Rental Value" to fairMarketRentalValueCents
            }
        designatedCapCents != null -> "Designation Cap" to designatedCapCents
        fairMarketRentalValueCents != null -> "Fair Rental Value" to fairMarketRentalValueCents
        else -> null
    }

    val daysRemaining = remember(selectedYear, currentYear) {
        if (selectedYear == currentYear) {
            val today = LocalDateUtils.todayLocalDate()
            LocalDateUtils.daysBetween(today, LocalDate(currentYear, 12, 31)).coerceAtLeast(0)
        } else null
    }

    val badgeContainerColor = when {
        isUsingStoredYearRate -> MaterialTheme.colorScheme.secondaryContainer
        isHistoricalYearWithoutSavedRate -> colors.warningContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeContentColor = when {
        isUsingStoredYearRate -> MaterialTheme.colorScheme.onSecondaryContainer
        isHistoricalYearWithoutSavedRate -> colors.onWarningContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    OrbitSectionCard(
        title = "$selectedYear TAX SAVINGS",
        headerBadge = if (taxRateSourceLabel.isNotBlank()) {
            {
                OrbitBadge(
                    text = taxRateSourceLabel,
                    containerColor = badgeContainerColor,
                    contentColor = badgeContentColor
                )
            }
        } else null,
        actionText = "Tax Rules",
        onActionClick = onEditSettings,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Editorial Hero: Primary Tax Savings + Eligible Housing Driver
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    RollingTickerCents(
                        cents = totalSavings,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 34.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatRatePercent(effectiveRatePercent)}% effective rate",
                        style = TabularNumStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    RollingTickerCents(
                        cents = eligibleExpensesCents,
                        style = TabularNumStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val isCapped = excludableAllowanceCents < eligibleExpensesCents
                    val noun = if (eligibleTransactionCount == 1) "expense" else "expenses"
                    Text(
                        text = if (isCapped) {
                            "${MoneyUtils.formatCents(excludableAllowanceCents, showCents = false)} excludable"
                        } else {
                            "$eligibleTransactionCount $noun"
                        },
                        style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = if (isCapped) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Section 107 Exclusion Bar OR Federal/State Split Bar
            if (activeCap != null) {
                val (capLabel, capCents) = activeCap
                val isCapped = eligibleExpensesCents > capCents
                val progress = if (capCents > 0L) {
                    (eligibleExpensesCents.toFloat() / capCents.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }

                OrbitPillProgressBar(
                    percent = progress,
                    overBudget = isCapped,
                    customFillColor = if (isCapped) colors.warning else MaterialTheme.colorScheme.primary,
                    height = 10.dp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val capStatus = if (isCapped) {
                    val excess = eligibleExpensesCents - capCents
                    "Capped by $capLabel (${MoneyUtils.formatCents(capCents, showCents = false)}) · ${MoneyUtils.formatCents(excess)} excess taxable"
                } else {
                    val remaining = capCents - eligibleExpensesCents
                    val daysSuffix = if (daysRemaining != null) " · $daysRemaining days left" else ""
                    "${MoneyUtils.formatCents(remaining, showCents = false)} room under ${MoneyUtils.formatCents(capCents, showCents = false)} $capLabel$daysSuffix"
                }
                Text(
                    text = capStatus,
                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = if (isCapped) colors.warning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (totalSavings > 0L && (fedSavings > 0L || stateSavings > 0L)) {
                val fedColor = MaterialTheme.colorScheme.primary
                val stateColor = MaterialTheme.colorScheme.tertiary
                OrbitStackedSegmentBar(
                    segments = listOf(
                        OrbitBarSegment("fed", fedSavings.coerceAtLeast(0L).toFloat(), fedColor),
                        OrbitBarSegment("state", stateSavings.coerceAtLeast(0L).toFloat(), stateColor)
                    ),
                    height = 10.dp,
                    normalizeToFullWidth = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SavingsLegendDot(fedColor, "Federal ${MoneyUtils.formatCents(fedSavings, showCents = false)}")
                    if (stateSavings > 0L) {
                        SavingsLegendDot(stateColor, "State ${MoneyUtils.formatCents(stateSavings, showCents = false)}")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OrbitActionChip(
                    label = "Set Section 107 Caps",
                    onClick = onEditSettings
                )
            } else {
                OrbitActionChip(
                    label = "Set Section 107 Caps",
                    onClick = onEditSettings
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
            Spacer(modifier = Modifier.height(6.dp))

            // Clean Full-Width Breakdown Rows (Zero Nested Boxes / Zero Clipping)
            val hasStoredSingleRateOnly = isUsingStoredYearRate &&
                activeYearTaxRecord?.federalRatePercent == null &&
                activeYearTaxRecord?.stateRatePercent == null

            if (hasStoredSingleRateOnly) {
                OrbitMiniStatRow(
                    label = "Saved $selectedYear effective rate",
                    detail = "Applied to ${MoneyUtils.formatCents(excludableAllowanceCents)} excludable housing",
                    value = "${formatRatePercent(effectiveRatePercent)}%",
                    valueColor = MaterialTheme.colorScheme.primary
                )
            } else {
                OrbitMiniStatRow(
                    label = "Federal tax savings",
                    detail = "${formatRatePercent(fedRatePercent)}% marginal bracket",
                    value = MoneyUtils.formatCents(fedSavings)
                )
                OrbitMiniStatRow(
                    label = "State tax savings",
                    detail = "${formatRatePercent(stateRatePercent)}% ${taxProfile.state} rate",
                    value = MoneyUtils.formatCents(stateSavings),
                    showTopDivider = true
                )
            }

            if (isHousehold && !isUsingStoredYearRate) {
                OrbitMiniStatRow(
                    label = "Taxable income (before MHA)",
                    detail = "Gross ${MoneyUtils.formatCents(grossIncome, showCents = false)} − ${MoneyUtils.formatCents(reduction, showCents = false)} deductions",
                    value = MoneyUtils.formatCents(taxableIncomeBeforeMha),
                    showTopDivider = true
                )
            }

            if (isHistoricalYearWithoutSavedRate) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEditSettings() }
                ) {
                    Text(
                        text = "No $selectedYear rate saved yet. Using current profile — tap Tax Rules to store $selectedYear's effective rate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            if (showGraduatedWarning) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "State savings are not auto-estimated for graduated-rate states yet. Enter a State Rate Override in Tax Rules if applicable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsLegendDot(
    color: Color,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatRatePercent(percent: Double): String {
    val hundredths = (percent * 100.0).roundToLong()
    val whole = hundredths / 100
    val frac = abs(hundredths % 100)
    return when {
        frac == 0L -> "$whole"
        frac % 10L == 0L -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
}
