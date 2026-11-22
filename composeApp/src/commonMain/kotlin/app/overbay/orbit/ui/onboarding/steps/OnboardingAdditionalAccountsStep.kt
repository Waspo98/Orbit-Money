package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingErrorBanner
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingAdditionalAccountsStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val draft = state.manualAccountDraft

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OnboardingHeroHeader(
            title = if (state.existingAccounts.isNotEmpty()) "Any Accounts Not Synced Yet?" else "Add Your Accounts",
            subtitle = "Great for a home value, mortgage, 401(k), HSA, or cash account—skip if you're all set.",
            icon = OrbitIcons.Accounts
        )

        if (state.existingAccounts.isNotEmpty()) {
            OnboardingCardContainer(contentPaddingDp = 14) {
                Text(
                    text = "ALREADY IN ORBIT (${state.existingAccounts.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    state.existingAccounts.forEach { acct ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
                        ) {
                            Text(
                                text = "${acct.name} · ${MoneyUtils.formatCents(acct.currentBalanceCents, showCents = false)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        OnboardingCardContainer {
            Text(
                text = "Quick Add Manual Account",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OrbitTextField(
                value = draft.name,
                onValueChange = { onIntent(OnboardingIntent.UpdateManualAccountName(it)) },
                label = { Text("Account Name") },
                placeholder = { Text("e.g., Vanguard 401(k), Home Mortgage, Cash") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Account Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccountType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = {
                                haptics.tick()
                                onIntent(OnboardingIntent.UpdateManualAccountType(type))
                            },
                            label = { Text(type.displayName) }
                        )
                    }
                }
            }

            CurrencyTextField(
                cents = draft.balanceCents,
                onCentsChange = { onIntent(OnboardingIntent.UpdateManualAccountBalance(it)) },
                label = if (draft.type.isLiability) {
                    "Balance Owed ($)"
                } else {
                    "Current Balance ($)"
                },
                modifier = Modifier.fillMaxWidth()
            )

            OrbitAccordionVisibility(visible = draft.type == AccountType.MORTGAGE) {
                CurrencyTextField(
                    cents = draft.estimatedValueCents,
                    onCentsChange = { onIntent(OnboardingIntent.UpdateManualAccountEstValue(it)) },
                    label = "Estimated Home Value (Optional, $)",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OnboardingErrorBanner(message = draft.error)

            FilledTonalButton(
                onClick = {
                    haptics.confirm()
                    onIntent(OnboardingIntent.SaveManualAccount)
                },
                enabled = draft.name.isNotBlank() && !draft.isSubmitting,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Account", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
