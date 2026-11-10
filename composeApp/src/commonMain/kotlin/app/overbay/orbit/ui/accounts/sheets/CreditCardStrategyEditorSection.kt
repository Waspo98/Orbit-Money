package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.domain.CreditCardTemplate
import app.overbay.orbit.domain.CreditCardTemplates
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.datetime.LocalDate

private val QUICK_PLAYERS = listOf("Player 1", "Player 2", "Shared")
private val COOLDOWN_PRESETS = listOf(
    null to "None",
    24 to "24 Mo (Chase/Citi)",
    48 to "48 Mo (Sapphire)",
    999 to "Lifetime (Amex)"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardTemplateQuickPickerSection(
    selectedCardName: String,
    onApplyTemplate: (CreditCardTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "1-Tap Popular Card Templates",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Auto-fills annual fee, reward multipliers, statement credit cadences, and SUB cooldown rules.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CreditCardTemplates.ALL.forEach { tpl ->
                    val isSelected = selectedCardName.equals(tpl.cardName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.confirm()
                            onApplyTemplate(tpl)
                        },
                        label = {
                            Text(
                                text = tpl.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreditCardStrategyEditorSection(
    strategy: CreditCardStrategy,
    onStrategyChange: (CreditCardStrategy) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val today = LocalDateUtils.todayLocalDate()
    val openedLocal = LocalDateUtils.parseIsoDate(strategy.openedDate) ?: today
    val deadlineLocal = LocalDateUtils.parseIsoDate(strategy.subDeadlineDate)
        ?: LocalDateUtils.addMonthsClamped(openedLocal, 3)
    val subReceivedLocal = LocalDateUtils.parseIsoDate(strategy.subReceivedDate) ?: today

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Lifecycle, Player & Chase 5/24 Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Card Lifecycle & Chase 5/24 Tracking",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Cardholder (Player 1 / Player 2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrbitTextField(
                        value = strategy.cardholder ?: "",
                        onValueChange = { onStrategyChange(strategy.copy(cardholder = it.ifBlank { null })) },
                        label = { Text("Cardholder (e.g. Player 1)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK_PLAYERS.forEach { p ->
                        val selected = strategy.cardholder.equals(p, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.tick()
                                onStrategyChange(strategy.copy(cardholder = if (selected) null else p))
                            },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) }
                        )
                    }
                }

                // Opened Date & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DateFieldButton(
                            date = openedLocal,
                            onDateSelected = { selected ->
                                val iso = LocalDateUtils.formatIsoDate(selected)
                                val nextDeadline = if (strategy.subDeadlineDate == null && (strategy.subTargetSpendCents ?: 0L) > 0L) {
                                    LocalDateUtils.formatIsoDate(LocalDateUtils.addMonthsClamped(selected, 3))
                                } else strategy.subDeadlineDate
                                onStrategyChange(strategy.copy(openedDate = iso, subDeadlineDate = nextDeadline))
                            },
                            label = if (strategy.openedDate != null) "Opened Date (${strategy.openedDate})" else "Set Sign-Up / Opened Date"
                        )
                    }
                    if (strategy.openedDate != null) {
                        TextButton(onClick = {
                            haptics.tick()
                            onStrategyChange(strategy.copy(openedDate = null))
                        }) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Business Card Toggle (Exempt from Chase 5/24)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Business Credit Card (5/24 Exempt)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Most business cards do not report to personal bureaus or count toward Chase 5/24.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = strategy.isBusinessCard,
                        onCheckedChange = {
                            haptics.tick()
                            onStrategyChange(strategy.copy(isBusinessCard = it))
                        }
                    )
                }
            }
        }

        // 2. Sign-Up Bonus (SUB) & Eligibility Cooldown Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Sign-Up Bonus (SUB) Tracker & Eligibility",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitTextField(
                        value = strategy.subRewardText ?: "",
                        onValueChange = { onStrategyChange(strategy.copy(subRewardText = it.ifBlank { null })) },
                        label = { Text("Welcome Bonus (e.g. 75,000 pts)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    Box(modifier = Modifier.weight(0.8f)) {
                        CurrencyTextField(
                            cents = strategy.subTargetSpendCents ?: 0L,
                            onCentsChange = { cents ->
                                val target = cents.takeIf { it > 0L }
                                val autoDeadline = if (target != null && strategy.subDeadlineDate == null && strategy.openedDate != null) {
                                    LocalDateUtils.formatIsoDate(LocalDateUtils.addMonthsClamped(openedLocal, 3))
                                } else strategy.subDeadlineDate
                                onStrategyChange(strategy.copy(subTargetSpendCents = target, subDeadlineDate = autoDeadline))
                            },
                            label = "Min Spend ($)"
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DateFieldButton(
                            date = deadlineLocal,
                            onDateSelected = {
                                onStrategyChange(strategy.copy(subDeadlineDate = LocalDateUtils.formatIsoDate(it)))
                            },
                            label = if (strategy.subDeadlineDate != null) "SUB Deadline (${strategy.subDeadlineDate})" else "Set SUB Deadline"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CurrencyTextField(
                            cents = strategy.subManualSpendOffsetCents,
                            onCentsChange = { onStrategyChange(strategy.copy(subManualSpendOffsetCents = it)) },
                            label = "Prior Spend Offset ($)"
                        )
                    }
                }

                // SUB Completed & SUB Received Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = strategy.subCompleted,
                        onClick = {
                            haptics.tick()
                            val nextCompleted = !strategy.subCompleted
                            val nextReceived = if (nextCompleted && strategy.subReceivedDate == null) {
                                LocalDateUtils.formatIsoDate(today)
                            } else strategy.subReceivedDate
                            onStrategyChange(strategy.copy(subCompleted = nextCompleted, subReceivedDate = nextReceived))
                        },
                        label = { Text("SUB Earned / Completed", style = MaterialTheme.typography.labelSmall) }
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        DateFieldButton(
                            date = subReceivedLocal,
                            onDateSelected = {
                                onStrategyChange(
                                    strategy.copy(
                                        subReceivedDate = LocalDateUtils.formatIsoDate(it),
                                        subCompleted = true
                                    )
                                )
                            },
                            label = if (strategy.subReceivedDate != null) "SUB Earned (${strategy.subReceivedDate})" else "SUB Earned Date"
                        )
                    }
                }

                // Cooldown Rule Chips
                Text(
                    text = "Bonus Re-Eligibility Cooldown Rule:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COOLDOWN_PRESETS.forEach { (months, label) ->
                        val isSelected = strategy.subCooldownMonths == months
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptics.tick()
                                onStrategyChange(strategy.copy(subCooldownMonths = months))
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }
    }
}
