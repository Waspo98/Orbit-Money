package app.overbay.orbit.ui.upcoming.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.upcoming.UpcomingFilter
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun UpcomingSummaryStatsGrid(
    incomeCents: Cents,
    givingCents: Cents,
    billsCents: Cents,
    subscriptionsCents: Cents,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UpcomingStatCard(
                label = "Upcoming Income",
                cents = incomeCents,
                color = colors.income,
                prefix = "+",
                modifier = Modifier.weight(1f)
            )
            UpcomingStatCard(
                label = "Upcoming Giving",
                cents = givingCents,
                color = MaterialTheme.colorScheme.tertiary,
                prefix = "-",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UpcomingStatCard(
                label = "Upcoming Bills",
                cents = billsCents,
                color = colors.expense,
                prefix = "-",
                modifier = Modifier.weight(1f)
            )
            UpcomingStatCard(
                label = "Upcoming Subs",
                cents = subscriptionsCents,
                color = MaterialTheme.colorScheme.secondary,
                prefix = "-",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UpcomingStatCard(
    label: String,
    cents: Cents,
    color: androidx.compose.ui.graphics.Color,
    prefix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            val display = if (cents > 0L) "$prefix${MoneyUtils.formatCents(cents, showCents = false)}" else "$0"
            Text(
                text = display,
                style = TabularNumStyle.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            )
        }
    }
}

@Composable
fun UpcomingFilterStrip(
    selectedFilter: UpcomingFilter,
    onFilterSelected: (UpcomingFilter) -> Unit,
    suggestionsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UpcomingFilter.entries.forEach { filter ->
            val isSelected = selectedFilter == filter
            Surface(
                onClick = {
                    haptics.tick()
                    onFilterSelected(filter)
                },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.height(34.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )

                    if (filter == UpcomingFilter.SUGGESTIONS && suggestionsCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)) {
                                Text(
                                    text = suggestionsCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
