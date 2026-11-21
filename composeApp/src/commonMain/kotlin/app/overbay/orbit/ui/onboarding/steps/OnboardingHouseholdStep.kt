package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingErrorBanner
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun OnboardingHouseholdStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val draft = state.memberDraft
    val isAdult = draft.role == "adult"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OnboardingHeroHeader(
            title = "Who's in Your Household?",
            subtitle = "Leave any optional field blank—you can add or edit income, benefits, and details anytime on the Household screen.",
            icon = OrbitIcons.Household
        )

        if (state.householdMembers.isNotEmpty()) {
            OnboardingCardContainer(contentPaddingDp = 14) {
                Text(
                    text = "HOUSEHOLD MEMBERS (${state.householdMembers.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                state.householdMembers.forEach { member ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val details = buildList {
                                    add(if (member.role.equals("dependent", ignoreCase = true) || member.role.equals("child", ignoreCase = true)) "Dependent" else "Adult")
                                    member.birthDate?.take(4)?.let { add("Born $it") }
                                    if (member.grossIncomeAnnualCents > 0L) {
                                        add("${MoneyUtils.formatCents(member.grossIncomeAnnualCents, showCents = false)}/yr")
                                    }
                                    val pct = member.employeeContributionPercent ?: 0.0
                                    if (pct > 0.0) add("${pct}% retirement")
                                }.joinToString(" · ")
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    onIntent(OnboardingIntent.RemoveHouseholdMember(member.id))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${member.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        OnboardingCardContainer {
            Text(
                text = "Add a Person",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OrbitTextField(
                value = draft.name,
                onValueChange = { onIntent(OnboardingIntent.UpdateMemberName(it)) },
                label = { Text("First Name") },
                placeholder = { Text("e.g., Alex") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OrbitSegmentedTabRow(
                items = listOf("adult", "dependent"),
                selected = draft.role,
                onSelect = { onIntent(OnboardingIntent.UpdateMemberRole(it)) },
                label = { if (it == "adult") "Adult" else "Dependent / Child" }
            )

            OrbitAccordionVisibility(visible = isAdult) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "OPTIONAL — HELPS RETIREMENT & TAX ESTIMATES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OrbitTextField(
                            value = draft.birthYearText,
                            onValueChange = { onIntent(OnboardingIntent.UpdateMemberBirthYear(it)) },
                            label = { Text("Birth Year") },
                            placeholder = { Text("1990") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OrbitTextField(
                            value = draft.retirementContribPercentText,
                            onValueChange = { onIntent(OnboardingIntent.UpdateMemberRetirementPct(it)) },
                            label = { Text("401(k)/IRA %") },
                            placeholder = { Text("10") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    CurrencyTextField(
                        cents = draft.annualGrossCents,
                        onCentsChange = { onIntent(OnboardingIntent.UpdateMemberIncome(it)) },
                        label = "Annual Gross Income (Optional, $)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OnboardingErrorBanner(message = draft.error)

            FilledTonalButton(
                onClick = {
                    haptics.confirm()
                    onIntent(OnboardingIntent.SaveHouseholdMember)
                },
                enabled = draft.name.isNotBlank() && !draft.isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Person", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
