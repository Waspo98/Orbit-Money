package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.dashboard.AccountsOverviewState

/**
 * Editorial Accounts & Net Worth card featuring a live net worth ticker,
 * an Assets vs. Liabilities proportion bar, and divided ledger rows.
 */
@Composable
fun AccountsOverviewCard(
    accounts: AccountsOverviewState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors

    OrbitSectionCard(
        title = "Accounts",
        subtitle = "Across ${accounts.accountCount} ${if (accounts.accountCount == 1) "account" else "accounts"}",
        actionText = "See All",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
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
            val netWorthColor by animateColorAsState(
                targetValue = if (accounts.totalNetWorthCents >= 0L) colors.income else colors.danger,
                animationSpec = OrbitSprings.ColorEffectsFast,
                label = "accountsOverviewNetWorthColor"
            )
            RollingTickerCents(
                cents = accounts.totalNetWorthCents,
                style = TabularNumStyle.copy(
                    fontSize = 27.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = netWorthColor
            )

            if (accounts.totalAssetsCents > 0L || accounts.totalLiabilitiesCents > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                OrbitStackedSegmentBar(
                    segments = listOf(
                        OrbitBarSegment(
                            key = "assets",
                            fraction = accounts.totalAssetsCents.coerceAtLeast(0L).toFloat(),
                            color = colors.income
                        ),
                        OrbitBarSegment(
                            key = "liabilities",
                            fraction = accounts.totalLiabilitiesCents.coerceAtLeast(0L).toFloat(),
                            color = colors.expense.copy(alpha = 0.78f)
                        )
                    ),
                    height = 6.dp,
                    normalizeToFullWidth = true
                )
                if (accounts.totalLiabilitiesCents > 0L) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Assets ${MoneyUtils.formatCents(accounts.totalAssetsCents)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Debt ${MoneyUtils.formatCents(accounts.totalLiabilitiesCents)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.expense
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val rows = buildList {
                if (accounts.cashCents != 0L) {
                    add(Triple("Cash", MoneyUtils.formatCents(accounts.cashCents), MaterialTheme.colorScheme.onSurface))
                }
                if (accounts.investmentsCents != 0L) {
                    add(Triple("Investments", MoneyUtils.formatCents(accounts.investmentsCents), MaterialTheme.colorScheme.onSurface))
                }
                if (accounts.creditCardsOwedCents > 0L) {
                    add(Triple("Credit cards", MoneyUtils.formatCents(-accounts.creditCardsOwedCents, signed = true), colors.expense))
                }
                if (accounts.loansOwedCents > 0L) {
                    add(Triple("Loans", MoneyUtils.formatCents(-accounts.loansOwedCents, signed = true), colors.expense))
                }
                if (accounts.realEstateEquityCents != 0L) {
                    add(Triple("Real Estate", MoneyUtils.formatCents(accounts.realEstateEquityCents), MaterialTheme.colorScheme.onSurface))
                }
                if (accounts.otherCents != 0L) {
                    add(Triple("Other", MoneyUtils.formatCents(accounts.otherCents), MaterialTheme.colorScheme.onSurface))
                }
            }

            if (rows.isEmpty()) {
                Text(
                    text = "Add or sync an account to track balances here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
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
}
