package app.overbay.orbit.ui.networth.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.NetWorthEngine
import app.overbay.orbit.theme.OrbitColorTokens
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import kotlin.math.abs

@Composable
fun NetWorthSignalsCard(
    largestAsset: Account?,
    largestLiability: Account?,
    trackedAccountCount: Int,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "KEY SIGNALS",
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val assetLabel = largestAsset?.let {
                val inst = if (!it.institution.isNullOrBlank()) " (${it.institution})" else ""
                "${it.name}$inst"
            } ?: "None recorded"
            val assetVal = largestAsset?.let {
                val contribution = NetWorthEngine.accountContributionCents(it, it.currentBalanceCents)
                MoneyUtils.formatCents(contribution)
            } ?: "$0.00"

            OrbitMiniStatRow(
                label = "Largest Asset",
                detail = assetLabel,
                value = assetVal,
                valueColor = OrbitColorTokens.Green
            )

            Spacer(modifier = Modifier.height(4.dp))

            val liabLabel = largestLiability?.let {
                val inst = if (!it.institution.isNullOrBlank()) " (${it.institution})" else ""
                "${it.name}$inst"
            } ?: "None recorded"
            val liabVal = largestLiability?.let {
                MoneyUtils.formatCents(abs(it.currentBalanceCents))
            } ?: "$0.00"

            OrbitMiniStatRow(
                label = "Largest Debt",
                detail = liabLabel,
                value = liabVal,
                valueColor = if (largestLiability != null && abs(largestLiability.currentBalanceCents) > 0L) {
                    OrbitColorTokens.Red
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                showTopDivider = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            OrbitMiniStatRow(
                label = "Tracked Accounts",
                value = "$trackedAccountCount active",
                valueColor = MaterialTheme.colorScheme.primary,
                showTopDivider = true
            )
        }
    }
}
