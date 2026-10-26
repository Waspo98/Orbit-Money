package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.dashboard.AccountsOverviewState
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial Accounts & Net Worth card matching original Orbit Money design.
 * Features 32sp emerald net worth callout with full cents, account count caption,
 * and 1dp divided ledger rows for Cash, Investments, Credit Cards, Loans, and Real Estate.
 */
@Composable
fun AccountsOverviewCard(
    accounts: AccountsOverviewState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "ACCOUNTS",
        actionText = "See all",
        onActionClick = onClick,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable {
                haptics.tick()
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Net worth",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = MoneyUtils.formatCents(accounts.totalNetWorthCents, showCents = true),
                style = TabularNumStyle.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.income
            )
            Text(
                text = "Across ${accounts.accountCount} accounts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Divided ledger rows
            OrbitMiniStatRow(
                label = "Cash",
                value = MoneyUtils.formatCents(accounts.cashCents)
            )
            OrbitMiniStatRow(
                label = "Investments",
                value = MoneyUtils.formatCents(accounts.investmentsCents)
            )
            if (accounts.creditCardsOwedCents > 0L) {
                OrbitMiniStatRow(
                    label = "Credit cards",
                    value = "-${MoneyUtils.formatCents(accounts.creditCardsOwedCents)}",
                    valueColor = colors.expense
                )
            }
            if (accounts.loansOwedCents > 0L) {
                OrbitMiniStatRow(
                    label = "Loans",
                    value = "-${MoneyUtils.formatCents(accounts.loansOwedCents)}",
                    valueColor = colors.expense
                )
            }
            if (accounts.realEstateEquityCents != 0L) {
                OrbitMiniStatRow(
                    label = "Real Estate",
                    value = MoneyUtils.formatCents(accounts.realEstateEquityCents)
                )
            }
            if (accounts.otherCents != 0L) {
                OrbitMiniStatRow(
                    label = "Other",
                    value = MoneyUtils.formatCents(accounts.otherCents)
                )
            }
        }
    }
}
