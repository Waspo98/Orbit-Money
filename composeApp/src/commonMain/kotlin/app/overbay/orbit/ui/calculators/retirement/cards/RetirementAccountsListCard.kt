package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun RetirementAccountsListCard(
    uiState: RetirementUiState,
    onCurrentBalanceChange: (Cents) -> Unit,
    onMonthlySavingsChange: (Cents) -> Unit,
    onToggleUseConnectedAccounts: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scenario = uiState.scenario

    OrbitSectionCard(
        title = "Portfolio Balance & Savings",
        subtitle = "Current retirement assets, monthly contributions, and required pace",
        modifier = modifier
    ) {
        // Toggle Linked Accounts vs Manual
        if (uiState.investmentAccounts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.useConnectedAccounts,
                    onClick = { onToggleUseConnectedAccounts(true) },
                    label = { Text("Linked Accounts (${uiState.investmentAccounts.size})") }
                )
                FilterChip(
                    selected = !uiState.useConnectedAccounts,
                    onClick = { onToggleUseConnectedAccounts(false) },
                    label = { Text("Manual Balance") }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Current Balance & Monthly Savings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CurrencyTextField(
                cents = uiState.currentBalanceCents,
                onCentsChange = onCurrentBalanceChange,
                label = "Current Portfolio Balance",
                modifier = Modifier.weight(1f)
            )
            CurrencyTextField(
                cents = uiState.monthlySavingsCents,
                onCentsChange = onMonthlySavingsChange,
                label = "Monthly Savings",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Required Savings Callout
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Required Monthly Savings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = MoneyUtils.formatCents(scenario.requiredMonthlyContributionCents) + " / mo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (scenario.monthlySavingsGapCents > 0L) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Monthly Gap",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "-${MoneyUtils.formatCents(scenario.monthlySavingsGapCents)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        text = "Pace on Track! 🎯",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Linked Accounts Detail (if any)
        if (uiState.useConnectedAccounts && uiState.investmentAccounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Included Investment Accounts",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            uiState.investmentAccounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (account.institution != null) {
                            Text(
                                text = account.institution,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = MoneyUtils.formatCents(account.currentBalanceCents),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }
        }
    }
}
