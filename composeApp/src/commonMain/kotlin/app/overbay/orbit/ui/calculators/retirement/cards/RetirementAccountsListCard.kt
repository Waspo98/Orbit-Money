package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard
import kotlin.math.max
import kotlin.math.roundToLong

val RETIREMENT_ACCOUNT_MIX_COLORS = listOf(
    Color(0xFF10B981), // Emerald
    Color(0xFF38BDF8), // Sky Blue
    Color(0xFFA78BFA), // Lavender / Purple
    Color(0xFFF59E0B), // Amber
    Color(0xFFF472B6), // Pink
    Color(0xFF22D3EE)  // Cyan
)

@Composable
fun RetirementAccountsListCard(
    uiState: RetirementUiState,
    onCurrentBalanceChange: (Cents) -> Unit,
    onResetCurrentBalance: () -> Unit,
    onMonthlySavingsChange: (Cents) -> Unit,
    onResetMonthlySavings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scenario = uiState.scenario
    val linkedAccounts = uiState.linkedAccounts
    val totalLinkedBalance = linkedAccounts.sumOf { it.balanceCents }

    OrbitSectionCard(
        title = "Portfolio Balance & Savings",
        subtitle = "Current retirement assets, monthly contributions, and required pace",
        modifier = modifier
    ) {
        // 1. Current Portfolio Balance with Reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Current Portfolio Balance",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (uiState.currentBalanceOverridden) {
                TextButton(
                    onClick = onResetCurrentBalance,
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Reset to Linked (${MoneyUtils.formatCompactCents(uiState.connectedBalanceCents)})", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        CurrencyTextField(
            cents = uiState.currentBalanceCents,
            onCentsChange = onCurrentBalanceChange,
            label = "Current Balance",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Monthly Savings with CurrencyTextField + Slider + Reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Monthly Savings",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (uiState.monthlySavingsOverridden) {
                TextButton(
                    onClick = onResetMonthlySavings,
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Household Pace (${MoneyUtils.formatCompactCents(uiState.plannedMonthlySavingsCents)}/mo)", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        CurrencyTextField(
            cents = uiState.monthlySavingsCents,
            onCentsChange = onMonthlySavingsChange,
            label = "Monthly Savings Amount",
            modifier = Modifier.fillMaxWidth()
        )

        // Monthly Savings Slider ($0 to $10,000/mo, step $50)
        Spacer(modifier = Modifier.height(4.dp))
        val maxSavingsFloat = 10_000_00f
        val currentSavingsFloat = uiState.monthlySavingsCents.toFloat().coerceIn(0f, maxSavingsFloat)
        Slider(
            value = currentSavingsFloat,
            onValueChange = { raw ->
                // Step $50 = 50_00 cents
                val step = 50_00L
                val stepped = ((raw.roundToLong() + step / 2) / step) * step
                onMonthlySavingsChange(stepped)
            },
            valueRange = 0f..maxSavingsFloat,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$0/mo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(
                text = "${MoneyUtils.formatCents(uiState.monthlySavingsCents, showCents = false)}/mo",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("$10,000/mo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Required Monthly Savings Callout Card
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
                        text = "${MoneyUtils.formatCents(scenario.requiredMonthlyContributionCents, showCents = false)} / mo",
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
                            text = "-${MoneyUtils.formatCents(scenario.monthlySavingsGapCents, showCents = false)}",
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

        // 4. Linked Accounts Breakdown with Proportional Color Mix Bar
        if (linkedAccounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Retirement Accounts (${linkedAccounts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = MoneyUtils.formatCents(totalLinkedBalance, showCents = false),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Proportional horizontal account-mix stacked color bar
            if (totalLinkedBalance > 0L) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    linkedAccounts.forEachIndexed { index, account ->
                        val weight = max(0.02f, account.balanceCents.toFloat() / totalLinkedBalance.toFloat())
                        val color = RETIREMENT_ACCOUNT_MIX_COLORS[index % RETIREMENT_ACCOUNT_MIX_COLORS.size]
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .height(10.dp)
                                .background(color)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Per-Account List Rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                linkedAccounts.forEachIndexed { index, account ->
                    val color = RETIREMENT_ACCOUNT_MIX_COLORS[index % RETIREMENT_ACCOUNT_MIX_COLORS.size]
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .background(color, shape = RoundedCornerShape(2.dp))
                                        .padding(horizontal = 2.dp)
                                )
                                Column {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val subtitleParts = listOfNotNull(account.ownerName, account.institution).filter { it.isNotBlank() }
                                    if (subtitleParts.isNotEmpty()) {
                                        Text(
                                            text = subtitleParts.joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OrbitBadge(
                                    text = account.planKind,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = MoneyUtils.formatCents(account.balanceCents, showCents = false),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
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
