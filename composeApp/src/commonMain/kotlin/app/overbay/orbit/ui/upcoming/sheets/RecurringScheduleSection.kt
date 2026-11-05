package app.overbay.orbit.ui.upcoming.sheets

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.AmountStrategy
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitDropdownField
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringKindSelector(
    selectedKind: UpcomingKind,
    onKindSelected: (UpcomingKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val kinds = listOf(
        UpcomingKind.BILL to "Bill",
        UpcomingKind.SUBSCRIPTION to "Subscription",
        UpcomingKind.GIVING to "Giving",
        UpcomingKind.INCOME to "Income"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "KIND",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            kinds.forEach { (kind, label) ->
                val isSelected = selectedKind == kind
                Surface(
                    onClick = {
                        haptics.tick()
                        onKindSelected(kind)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecurringAmountStrategySection(
    amountStrategy: AmountStrategy,
    lookbackMonths: Int,
    onStrategyChanged: (AmountStrategy) -> Unit,
    onLookbackChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "AMOUNT STRATEGY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isFixed = amountStrategy == AmountStrategy.FIXED
            Surface(
                onClick = {
                    haptics.tick()
                    onStrategyChanged(AmountStrategy.FIXED)
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isFixed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, if (isFixed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Fixed Amount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isFixed) FontWeight.Bold else FontWeight.Medium,
                        color = if (isFixed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val isAvg = amountStrategy == AmountStrategy.HISTORY_AVERAGE
            Surface(
                onClick = {
                    haptics.tick()
                    onStrategyChanged(AmountStrategy.HISTORY_AVERAGE)
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isAvg) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, if (isAvg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f).height(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "History Average",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isAvg) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAvg) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (amountStrategy == AmountStrategy.HISTORY_AVERAGE) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lookback Window:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 6, 12).forEach { months ->
                        val isSel = lookbackMonths == months
                        Surface(
                            onClick = {
                                haptics.tick()
                                onLookbackChanged(months)
                            },
                            shape = CircleShape,
                            color = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "$months Months",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecurringCustomScheduleFields(
    frequency: RecurrenceFrequency,
    customInterval: Int,
    customUnit: String,
    onIntervalChanged: (Int) -> Unit,
    onUnitChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (frequency != RecurrenceFrequency.CUSTOM) return

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitTextField(
                value = if (customInterval > 0) customInterval.toString() else "",
                onValueChange = { str ->
                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 1
                    onIntervalChanged(num)
                },
                label = { Text("Every (N)") },
                singleLine = true,
                modifier = Modifier.weight(0.45f)
            )

            OrbitDropdownField(
                label = "Unit",
                selectedText = customUnit.replaceFirstChar { it.uppercase() },
                options = listOf("days", "weeks", "months"),
                optionLabel = { it.replaceFirstChar { c -> c.uppercase() } },
                onOptionSelected = onUnitChanged,
                modifier = Modifier.weight(0.55f)
            )
        }
    }
}

@Composable
fun RecurringStatusAndNotesSection(
    status: UpcomingStatus,
    notes: String,
    onStatusChanged: (UpcomingStatus) -> Unit,
    onNotesChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STATUS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isActive = status == UpcomingStatus.ACTIVE
                Surface(
                    onClick = {
                        haptics.tick()
                        onStatusChanged(UpcomingStatus.ACTIVE)
                    },
                    shape = CircleShape,
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                val isPaused = status == UpcomingStatus.PAUSED
                Surface(
                    onClick = {
                        haptics.tick()
                        onStatusChanged(UpcomingStatus.PAUSED)
                    },
                    shape = CircleShape,
                    color = if (isPaused) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "Paused",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isPaused) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPaused) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OrbitTextField(
            value = notes,
            onValueChange = onNotesChanged,
            label = { Text("Notes (Optional)") },
            placeholder = { Text("e.g. Account number, renewal terms") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
