package app.overbay.orbit.ui.networth.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.NetWorthSnapshot
import app.overbay.orbit.theme.OrbitColorTokens

import app.overbay.orbit.ui.components.OrbitSectionCard

@Composable
fun AssetLiabilityBreakdownCard(
    snapshot: NetWorthSnapshot,
    isTwoPane: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isTwoPane) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
            modifier = modifier
        ) {
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
    BreakdownRow("Real Estate Equity", snapshot.realEstateEquityCents)
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

    BreakdownRow("Credit Card Debt", snapshot.creditDebtCents, isNegative = true)
    BreakdownRow("Loans & Mortgages", snapshot.loanDebtCents, isNegative = true)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = MoneyUtils.formatCents(amountCents),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isNegative && amountCents != 0L) OrbitColorTokens.Red else MaterialTheme.colorScheme.onSurface
        )
    }
}
