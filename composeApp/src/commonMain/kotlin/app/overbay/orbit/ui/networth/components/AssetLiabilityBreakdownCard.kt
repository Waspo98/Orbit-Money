package app.overbay.orbit.ui.networth.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.NetWorthSnapshot
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import kotlin.math.abs

@Composable
fun AssetLiabilityBreakdownCard(
    snapshot: NetWorthSnapshot,
    isTwoPane: Boolean = false,
    onAccountsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isTwoPane) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OrbitSectionCard(
                title = "Balance Allocation",
                actionText = "Accounts",
                onActionClick = onAccountsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                AllocationBar(snapshot = snapshot)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                OrbitSectionCard(
                    title = "Assets Breakdown",
                    modifier = Modifier.weight(1f)
                ) {
                    AssetsSectionContent(snapshot = snapshot)
                }

                OrbitSectionCard(
                    title = "Liabilities Breakdown",
                    modifier = Modifier.weight(1f)
                ) {
                    LiabilitiesSectionContent(snapshot = snapshot)
                }
            }

            NetWorthSummaryBanner(snapshot = snapshot)
        }
    } else {
        OrbitSectionCard(
            title = "Asset & Liability Breakdown",
            actionText = "Accounts",
            onActionClick = onAccountsClick,
            modifier = modifier
        ) {
            AllocationBar(snapshot = snapshot)

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            AssetsSectionContent(snapshot = snapshot)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            LiabilitiesSectionContent(snapshot = snapshot)

            Spacer(modifier = Modifier.height(14.dp))

            NetWorthSummaryBanner(snapshot = snapshot)
        }
    }
}

@Composable
fun AllocationBar(
    snapshot: NetWorthSnapshot,
    modifier: Modifier = Modifier
) {
    val cash = maxOf(0L, snapshot.cashCents)
    val investments = maxOf(0L, snapshot.investmentsCents)
    val realEstate = maxOf(0L, snapshot.realEstateEquityCents)
    val credit = maxOf(0L, abs(snapshot.creditDebtCents))
    val loans = maxOf(0L, abs(snapshot.loanDebtCents))
    val other = maxOf(0L, snapshot.otherCents)

    val total = cash + investments + realEstate + credit + loans + other
    if (total <= 0L) return

    val cashFraction = cash.toFloat() / total.toFloat()
    val invFraction = investments.toFloat() / total.toFloat()
    val reFraction = realEstate.toFloat() / total.toFloat()
    val creditFraction = credit.toFloat() / total.toFloat()
    val loansFraction = loans.toFloat() / total.toFloat()
    val otherFraction = other.toFloat() / total.toFloat()

    val cashColor = MaterialTheme.colorScheme.primary
    val invColor = MaterialTheme.colorScheme.secondary
    val reColor = MaterialTheme.colorScheme.tertiary
    val creditColor = OrbitColorTokens.Amber
    val loanColor = OrbitColorTokens.Red
    val otherColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Portfolio Allocation",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Segmented Bar (6 Buckets)
        val barSegments = listOf(
            OrbitBarSegment("cash", cashFraction, cashColor),
            OrbitBarSegment("inv", invFraction, invColor),
            OrbitBarSegment("re", reFraction, reColor),
            OrbitBarSegment("credit", creditFraction, creditColor),
            OrbitBarSegment("loans", loansFraction, loanColor),
            OrbitBarSegment("other", otherFraction, otherColor)
        )
        OrbitStackedSegmentBar(
            segments = barSegments,
            height = 10.dp,
            cornerRadius = 5.dp,
            normalizeToFullWidth = true,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Compact Legend (6 Buckets)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (cash > 0L) {
                AllocationLegendItem(label = "Cash (${(cashFraction * 100).toInt()}%)", color = cashColor)
            }
            if (investments > 0L) {
                AllocationLegendItem(label = "Investments (${(invFraction * 100).toInt()}%)", color = invColor)
            }
            if (realEstate > 0L) {
                AllocationLegendItem(label = "Real Estate (${(reFraction * 100).toInt()}%)", color = reColor)
            }
            if (credit > 0L) {
                AllocationLegendItem(label = "Credit Cards (${(creditFraction * 100).toInt()}%)", color = creditColor)
            }
            if (loans > 0L) {
                AllocationLegendItem(label = "Loans (${(loansFraction * 100).toInt()}%)", color = loanColor)
            }
            if (other > 0L) {
                AllocationLegendItem(label = "Other (${(otherFraction * 100).toInt()}%)", color = otherColor)
            }
        }
    }
}

@Composable
private fun AllocationLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AssetsSectionContent(snapshot: NetWorthSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Total Assets",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = OrbitColorTokens.Green
        )
        Text(
            text = MoneyUtils.formatCents(snapshot.totalAssetsCents),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = OrbitColorTokens.Green
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    BreakdownRow("Cash & Checking", snapshot.cashCents)
    BreakdownRow("Investments & Retirement", snapshot.investmentsCents)
    BreakdownRow("Real Estate", snapshot.realEstateEquityCents)
    if (snapshot.otherCents != 0L) {
        BreakdownRow("Other Assets", snapshot.otherCents)
    }
}

@Composable
private fun LiabilitiesSectionContent(snapshot: NetWorthSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Total Liabilities",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = OrbitColorTokens.Red
        )
        Text(
            text = MoneyUtils.formatCents(snapshot.totalLiabilitiesCents),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = OrbitColorTokens.Red
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    BreakdownRow("Credit Cards", snapshot.creditDebtCents, isNegative = true)
    BreakdownRow("Loans", snapshot.loanDebtCents, isNegative = true)
}

@Composable
private fun NetWorthSummaryBanner(snapshot: NetWorthSnapshot) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Net Worth",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val netColor = if (snapshot.netWorthCents >= 0L) OrbitColorTokens.Green else OrbitColorTokens.Red
            Text(
                text = MoneyUtils.formatCents(snapshot.netWorthCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = netColor
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amountCents: Long,
    isNegative: Boolean = false
) {
    OrbitMiniStatRow(
        label = label,
        value = MoneyUtils.formatCents(amountCents),
        valueColor = if (isNegative && amountCents != 0L) OrbitColorTokens.Red else MaterialTheme.colorScheme.onSurface
    )
}
