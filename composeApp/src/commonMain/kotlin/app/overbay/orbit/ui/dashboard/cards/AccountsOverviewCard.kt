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
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = MoneyUtils.formatCents(accounts.totalNetWorthCents, showCents = true),
                style = TabularNumStyle.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (accounts.totalNetWorthCents >= 0L) colors.income else colors.expense
            )
            Text(
                text = "Across ${accounts.accountCount} ${if (accounts.accountCount == 1) "account" else "accounts"}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            val rows = buildList {
                if (accounts.cashCents != 0L) {
                    add(Triple("Cash", MoneyUtils.formatCents(accounts.cashCents), MaterialTheme.colorScheme.onSurface))
                }
                if (accounts.investmentsCents != 0L) {
                    add(Triple("Investments", MoneyUtils.formatCents(accounts.investmentsCents), MaterialTheme.colorScheme.onSurface))
                }
                if (accounts.creditCardsOwedCents > 0L) {
                    add(Triple("Credit cards", "-${MoneyUtils.formatCents(accounts.creditCardsOwedCents)}", colors.expense))
                }
                if (accounts.loansOwedCents > 0L) {
                    add(Triple("Loans", "-${MoneyUtils.formatCents(accounts.loansOwedCents)}", colors.expense))
                }
                if (accounts.realEstateEquityCents != 0L) {
                    add(Triple("Real Estate", MoneyUtils.formatCents(accounts.realEstateEquityCents), MaterialTheme.colorScheme.onSurface))
                }
                if (accounts.otherCents != 0L) {
                    add(Triple("Other", MoneyUtils.formatCents(accounts.otherCents), MaterialTheme.colorScheme.onSurface))
                }
            }

            rows.forEachIndexed { index, (label, value, valueColor) ->
                OrbitMiniStatRow(
                    label = label,
                    value = value,
                    valueColor = valueColor,
                    showTopDivider = index > 0
                )
            }
        }
    }
}
