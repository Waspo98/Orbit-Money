package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.ui.components.OrbitStatTile
import app.overbay.orbit.util.currentTaxTrackingLabels
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.dashboard.MhaSummaryState
import app.overbay.orbit.ui.dashboard.MortgageSnapshotState
import app.overbay.orbit.ui.dashboard.RetirementSnapshotState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Retirement snapshot card with a projected nest egg hero ticker, supporting stat tiles,
 * compact age stepper pill, and an M3E segmented preset switcher.
 */
@Composable
fun RetirementSnapshotCard(
    state: RetirementSnapshotState,
    onChangeAge: (Int) -> Unit,
    onChangePreset: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val presets = listOf("conservative", "balanced", "aggressive")
    val selectedPreset = presets.firstOrNull { it.equals(state.presetKey, ignoreCase = true) } ?: "balanced"

    OrbitSectionCard(
        title = "Retirement Overview",
        subtitle = "Assumes age 35 · est. \$500/mo contribution",
        actionText = "Calculator",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Projected at Age ${state.targetAge}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    RollingTickerCents(
                        cents = state.projectedAtRetirementCents,
                        style = TabularNumStyle.copy(
                            fontSize = 25.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Compact Age Stepper Pill
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                onChangeAge(state.targetAge - 1)
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Decrease target age",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Age ${state.targetAge}",
                            style = TabularNumStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = {
                                haptics.tick()
                                onChangeAge(state.targetAge + 1)
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Increase target age",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrbitStatTile(
                    label = "Current Balance",
                    value = MoneyUtils.formatCents(state.currentBalanceCents),
                    modifier = Modifier.weight(1f)
                )
                OrbitStatTile(
                    label = "Est. Monthly",
                    value = "${MoneyUtils.formatCents(state.monthlyContributionCents)}/mo",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OrbitSegmentedTabRow(
                items = presets,
                selected = selectedPreset,
                onSelect = onChangePreset,
                label = { it.replaceFirstChar { c -> c.uppercase() } }
            )
        }
    }
}

/**
 * Tax Deductions / MHA Summary card showing YTD eligible spending
 * and estimated tax savings in side-by-side stat tiles.
 */
@Composable
fun MhaSummaryCard(
    state: MhaSummaryState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val taxLabels = currentTaxTrackingLabels()
    val noun = if (state.ytdTransactionCount == 1) "transaction" else "transactions"

    OrbitSectionCard(
        title = taxLabels.widgetTitle,
        subtitle = "${state.ytdTransactionCount} eligible $noun YTD",
        actionText = taxLabels.navTitle,
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrbitStatTile(
                label = "Eligible Spending YTD",
                value = MoneyUtils.formatCents(state.eligibleSpendingCents),
                valueColor = colors.income,
                modifier = Modifier.weight(1f)
            )
            OrbitStatTile(
                label = "Est. Tax Savings",
                value = MoneyUtils.formatCents(state.estimatedTaxSavingsCents),
                valueColor = MaterialTheme.colorScheme.primary,
                subText = "Est. 27% combined",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Mortgage Snapshot card showing home value, remaining mortgage balance,
 * visual equity-vs-loan bar, and estimated home equity.
 */
@Composable
fun MortgageSnapshotCard(
    state: MortgageSnapshotState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "Mortgage Overview",
        subtitle = if (state.hasMortgage) "${state.equityPercent.toInt()}% home equity" else null,
        actionText = "Accounts",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        if (!state.hasMortgage) {
            Text(
                text = "Link a mortgage or property account in Accounts to track home equity.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Estimated equity",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                RollingTickerCents(
                    cents = state.estimatedEquityCents,
                    style = TabularNumStyle.copy(
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.income
                )

                Spacer(modifier = Modifier.height(10.dp))

                OrbitStackedSegmentBar(
                    segments = listOf(
                        OrbitBarSegment(
                            key = "equity",
                            fraction = state.estimatedEquityCents.coerceAtLeast(0L).toFloat(),
                            color = colors.income
                        ),
                        OrbitBarSegment(
                            key = "loan",
                            fraction = state.mortgageBalanceCents.coerceAtLeast(0L).toFloat(),
                            color = colors.expense.copy(alpha = 0.72f)
                        )
                    ),
                    height = 7.dp,
                    normalizeToFullWidth = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OrbitMiniStatRow(
                    label = "Estimated home value",
                    value = MoneyUtils.formatCents(state.estimatedHomeValueCents)
                )
                OrbitMiniStatRow(
                    label = "Mortgage balance",
                    value = MoneyUtils.formatCents(-state.mortgageBalanceCents, signed = true),
                    valueColor = colors.expense,
                    showTopDivider = true
                )
            }
        }
    }
}
