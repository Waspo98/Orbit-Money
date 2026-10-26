package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.MhaSummaryState
import app.overbay.orbit.ui.dashboard.MortgageSnapshotState
import app.overbay.orbit.ui.dashboard.RetirementSnapshotState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Retirement snapshot card showing current nest egg, monthly contributions,
 * projected retirement at target age, and age/preset interactive controls.
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
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "RETIREMENT SNAPSHOT",
        actionText = "Retirement Calc",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitMiniStatRow(
                label = "Current Retirement Balance",
                value = MoneyUtils.formatCents(state.currentBalanceCents),
                valueColor = colors.income
            )
            OrbitMiniStatRow(
                label = "Estimated Monthly Addition",
                value = MoneyUtils.formatCents(state.monthlyContributionCents)
            )
            OrbitMiniStatRow(
                label = "Projected at Age ${state.targetAge}",
                value = MoneyUtils.formatCents(state.projectedAtRetirementCents),
                valueColor = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Age & Preset controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            onChangeAge(state.targetAge - 1)
                        },
                        shape = CircleShape,
                        modifier = Modifier.height(30.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("-1 Age", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            onChangeAge(state.targetAge + 1)
                        },
                        shape = CircleShape,
                        modifier = Modifier.height(30.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+1 Age", fontSize = 11.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("conservative", "balanced", "aggressive").forEach { key ->
                        val isSelected = state.presetKey.equals(key, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptics.tick()
                                onChangePreset(key)
                            },
                            label = {
                                Text(
                                    text = key.replaceFirstChar { it.uppercase() },
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.height(28.dp),
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * MHA Tracker Summary card showing YTD eligible military housing allowance spending.
 */
@Composable
fun MhaSummaryCard(
    state: MhaSummaryState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "MHA TRACKER SUMMARY",
        actionText = "MHA Tracker",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitMiniStatRow(
                label = "Eligible spending YTD",
                value = MoneyUtils.formatCents(state.eligibleSpendingCents),
                valueColor = colors.income
            )
            OrbitMiniStatRow(
                label = "Estimated tax savings",
                value = MoneyUtils.formatCents(state.estimatedTaxSavingsCents),
                valueColor = MaterialTheme.colorScheme.primary
            )
            OrbitMiniStatRow(
                label = "Transactions YTD",
                value = "${state.ytdTransactionCount} items"
            )
        }
    }
}

/**
 * Mortgage Snapshot card showing home value, remaining mortgage balance, and estimated home equity.
 */
@Composable
fun MortgageSnapshotCard(
    state: MortgageSnapshotState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "MORTGAGE SNAPSHOT",
        actionText = "Accounts",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitMiniStatRow(
                label = "Estimated home value",
                value = MoneyUtils.formatCents(state.estimatedHomeValueCents)
            )
            OrbitMiniStatRow(
                label = "Mortgage balance",
                value = "-${MoneyUtils.formatCents(state.mortgageBalanceCents)}",
                valueColor = colors.expense
            )
            OrbitMiniStatRow(
                label = "Estimated equity",
                detail = "${state.equityPercent.toInt()}% equity",
                value = MoneyUtils.formatCents(state.estimatedEquityCents),
                valueColor = colors.income
            )
        }
    }
}
