package app.overbay.orbit.ui.upcoming.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.overbay.orbit.domain.UpcomingCashFlowSummary
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.upcoming.UpcomingCashFlowPeriod
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun UpcomingCashFlowCard(
    summary: UpcomingCashFlowSummary,
    selectedPeriod: UpcomingCashFlowPeriod,
    onPeriodChanged: (UpcomingCashFlowPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val income = if (selectedPeriod == UpcomingCashFlowPeriod.REST_OF_MONTH) {
        summary.restOfMonthIncomeCents
    } else {
        summary.next30DaysIncomeCents
    }

    val expense = if (selectedPeriod == UpcomingCashFlowPeriod.REST_OF_MONTH) {
        summary.restOfMonthExpenseCents
    } else {
        summary.next30DaysExpenseCents
    }

    val net = if (selectedPeriod == UpcomingCashFlowPeriod.REST_OF_MONTH) {
        summary.restOfMonthNetCents
    } else {
        summary.next30DaysNetCents
    }

    val count = if (selectedPeriod == UpcomingCashFlowPeriod.REST_OF_MONTH) {
        summary.restOfMonthCount
    } else {
        summary.next30DaysCount
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Title + Period Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PROJECTED CASH FLOW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (selectedPeriod == UpcomingCashFlowPeriod.REST_OF_MONTH) "Rest of Month" else "Next 30 Days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Toggle Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isRom = selectedPeriod == UpcomingCashFlowPeriod.REST_OF_MONTH
                    Surface(
                        onClick = {
                            haptics.tick()
                            onPeriodChanged(UpcomingCashFlowPeriod.REST_OF_MONTH)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isRom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isRom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                            Text(
                                text = "Month",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isRom) FontWeight.Bold else FontWeight.Normal,
                                color = if (isRom) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isN30 = selectedPeriod == UpcomingCashFlowPeriod.NEXT_30_DAYS
                    Surface(
                        onClick = {
                            haptics.tick()
                            onPeriodChanged(UpcomingCashFlowPeriod.NEXT_30_DAYS)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isN30) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isN30) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                            Text(
                                text = "30 Days",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isN30) FontWeight.Bold else FontWeight.Normal,
                                color = if (isN30) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Stat: Projected Net Cash Flow
            AnimatedContent(
                targetState = net,
                transitionSpec = { fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault()) },
                label = "upcomingCashFlowNet"
            ) { targetNet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Net Cash Flow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val netColor = if (targetNet >= 0L) colors.income else colors.expense
                        Text(
                            text = MoneyUtils.formatCents(targetNet, showCents = false, signed = true),
                            style = TabularNumStyle.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = netColor
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "$count scheduled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bento Sub-row: Projected Income vs Projected Expenses
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Income Bento
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Projected Income",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+${MoneyUtils.formatCents(income, showCents = false)}",
                            style = TabularNumStyle.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.income
                            )
                        )
                    }
                }

                // Expenses Bento (Bills, Subscriptions, Giving)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Projected Outflow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "-${MoneyUtils.formatCents(expense, showCents = false)}",
                            style = TabularNumStyle.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.expense
                            )
                        )
                    }
                }
            }
        }
    }
}
