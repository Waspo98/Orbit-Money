package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.RetirementHistoryPoint
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun RetirementHistoryTrajectoryCard(
    historyPoints: List<RetirementHistoryPoint>,
    selectedHorizon: String,
    onSelectHorizon: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val horizons = listOf("YTD", "3Y", "5Y", "10Y", "All")
    val currentYear = LocalDateUtils.todayLocalDate().year.toString()

    val filteredPoints = when (selectedHorizon) {
        "YTD" -> historyPoints.filter { it.month.startsWith(currentYear) }.ifEmpty { historyPoints.takeLast(12) }
        "3Y" -> historyPoints.takeLast(36)
        "5Y" -> historyPoints.takeLast(60)
        "10Y" -> historyPoints.takeLast(120)
        else -> historyPoints
    }

    val latestBalance = filteredPoints.lastOrNull()?.balanceCents ?: 0L
    val startBalance = filteredPoints.firstOrNull()?.balanceCents ?: 0L
    val diffCents = latestBalance - startBalance
    val diffPercent = if (startBalance > 0L) (diffCents.toDouble() / startBalance.toDouble()) * 100.0 else 0.0

    OrbitSectionCard(
        title = "Retirement Portfolio History",
        subtitle = "Historical growth trajectory across connected accounts",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Horizon Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                horizons.forEach { horizon ->
                    FilterChip(
                        selected = selectedHorizon == horizon,
                        onClick = {
                            haptics.tick()
                            onSelectHorizon(horizon)
                        },
                        label = { Text(horizon, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (filteredPoints.isEmpty()) {
                EmptyStateCard(
                    title = "No Historical Snapshots",
                    description = "Historical investment balances will appear here as periodic snapshots accumulate."
                )
            } else {
                // Growth Summary Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Historical Balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = MoneyUtils.formatCents(latestBalance),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$selectedHorizon Change",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val isGain = diffCents >= 0L
                            Text(
                                text = "${if (isGain) "+" else ""}${MoneyUtils.formatCents(diffCents)} (${if (isGain) "+" else ""}${diffPercent.toInt()}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isGain) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Recent Monthly Snapshots
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Recent Monthly Milestones",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        filteredPoints.takeLast(5).reversed().forEach { point ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = point.month,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MoneyUtils.formatCents(point.balanceCents),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
