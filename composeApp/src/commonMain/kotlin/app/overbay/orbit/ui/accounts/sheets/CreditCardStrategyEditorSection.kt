package app.overbay.orbit.ui.accounts.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.util.LocalOrbitHaptics

enum class SubWizardMode(val label: String) {
    CHASING_NOW("Working on Bonus"),
    ALREADY_EARNED("Already Earned"),
    NO_BONUS("No Bonus")
}

private val COOLDOWN_OPTIONS = listOf(
    null to "None",
    24 to "24 Mo (Chase/Citi)",
    48 to "48 Mo (Sapphire)",
    999 to "Lifetime (Amex)"
)

@Composable
fun WizardStep3WelcomeBonus(
    strategy: CreditCardStrategy,
    onStrategyChange: (CreditCardStrategy) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WizardStepHeader(
            title = "3. Welcome Bonus (SUB)",
            subtitle = "Are you currently working toward a sign-up bonus on this card?"
        )
        ProgressiveSubAndCooldownFields(
            strategy = strategy,
            onStrategyChange = onStrategyChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressiveSubAndCooldownFields(
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

    var subMode by remember {
        val initial = when {
            strategy.subCompleted || !strategy.subReceivedDate.isNullOrBlank() -> SubWizardMode.ALREADY_EARNED
            (strategy.subTargetSpendCents ?: 0L) > 0L -> SubWizardMode.CHASING_NOW
            else -> SubWizardMode.NO_BONUS
        }
        mutableStateOf(initial)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WizardSectionContainer(title = "Bonus Status") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubWizardMode.entries.forEach { mode ->
                    val isSelected = subMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.tick()
                            subMode = mode
                            when (mode) {
                                SubWizardMode.CHASING_NOW -> {
                                    val defaultDeadline = strategy.subDeadlineDate
                                        ?: LocalDateUtils.formatIsoDate(LocalDateUtils.addMonthsClamped(openedLocal, 3))
                                    onStrategyChange(strategy.copy(subCompleted = false, subDeadlineDate = defaultDeadline, subReceivedDate = null))
                                }
                                SubWizardMode.ALREADY_EARNED -> {
                                    val defaultEarned = strategy.subReceivedDate
                                        ?: strategy.openedDate?.let {
                                            LocalDateUtils.formatIsoDate(LocalDateUtils.addMonthsClamped(openedLocal, 3))
                                        }
                                    onStrategyChange(strategy.copy(subCompleted = true, subReceivedDate = defaultEarned))
                                }
                                SubWizardMode.NO_BONUS -> {
                                    onStrategyChange(
                                        strategy.copy(
                                            subTargetSpendCents = null,
                                            subDeadlineDate = null,
                                            subCompleted = false,
                                            subReceivedDate = null
                                        )
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }
        }

        if (subMode == SubWizardMode.CHASING_NOW) {
            WizardSectionContainer(
                title = "Live Minimum Spend Tracker",
                subtitle = "Orbit automatically sums qualifying purchases from your card's transactions."
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrbitTextField(
                        value = strategy.subRewardText ?: "",
                        onValueChange = { onStrategyChange(strategy.copy(subRewardText = it.ifBlank { null })) },
                        label = { Text("Bonus (e.g. 75,000 pts)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    Box(modifier = Modifier.weight(0.8f)) {
                        CurrencyTextField(
                            cents = strategy.subTargetSpendCents ?: 0L,
                            onCentsChange = { onStrategyChange(strategy.copy(subTargetSpendCents = it.takeIf { c -> c > 0L })) },
                            label = "Min Spend ($)"
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DateFieldButton(
                            date = deadlineLocal,
                            onDateSelected = { onStrategyChange(strategy.copy(subDeadlineDate = LocalDateUtils.formatIsoDate(it))) },
                            label = "Spend Deadline"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CurrencyTextField(
                            cents = strategy.subManualSpendOffsetCents,
                            onCentsChange = { onStrategyChange(strategy.copy(subManualSpendOffsetCents = it)) },
                            label = "Prior Spend ($)"
                        )
                    }
                }
            }
        } else if (subMode == SubWizardMode.ALREADY_EARNED) {
            WizardSectionContainer(
                title = "When Did You Earn the Bonus?",
                subtitle = "Used to calculate when you'll be eligible for another bonus on this card family."
            ) {
                DateFieldButton(
                    date = subReceivedLocal,
                    onDateSelected = {
                        onStrategyChange(strategy.copy(subReceivedDate = LocalDateUtils.formatIsoDate(it), subCompleted = true))
                    },
                    label = if (strategy.subReceivedDate != null) "Bonus Earned (${strategy.subReceivedDate})" else "Select Bonus Earned Date"
                )
            }
        }

        WizardSectionContainer(
            title = "Issuer Cooldown Rule",
            subtitle = "How long until you can earn this bonus again?"
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                COOLDOWN_OPTIONS.forEach { (months, label) ->
                    val isSelected = strategy.subCooldownMonths == months
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.tick()
                            onStrategyChange(strategy.copy(subCooldownMonths = months))
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
fun WizardStep4PerksAndMultipliers(
    rewards: List<RewardCategory>,
    onRewardsChange: (List<RewardCategory>) -> Unit,
    benefits: List<CardBenefit>,
    onBenefitsChange: (List<CardBenefit>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WizardStepHeader(
            title = "4. Perks & Multipliers",
            subtitle = "Review your card's statement credits and reward categories, then tap Save Card."
        )

        WizardSectionContainer(title = "Statement Credits & Perks") {
            CardBenefitsEditorSection(
                benefits = benefits,
                onBenefitsChange = onBenefitsChange
            )
        }

        WizardSectionContainer(title = "Reward Multipliers") {
            RewardCategoriesEditorSection(
                rewards = rewards,
                onRewardsChange = onRewardsChange
            )
        }
    }
}
