package app.overbay.orbit.ui.cardperks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountsUiState
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.accounts.cards.CardStatusPill
import app.overbay.orbit.ui.accounts.cards.CardStatusTone
import app.overbay.orbit.ui.components.OrbitActionChip
import app.overbay.orbit.ui.components.OrbitPillProgressBar
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@Composable
internal fun PortfolioRoiSummaryStrip(
    uiState: AccountsUiState,
    modifier: Modifier = Modifier
) {
    val summary = uiState.cardPortfolioSummary
    val scopedCredit = if (uiState.playerScopedHub.walletAccountIds.isNotEmpty() || uiState.playerScopedHub.cardCycles.isNotEmpty()) {
        uiState.playerScopedHub.householdCredit
    } else {
        uiState.householdCredit
    }
    val totalOwed = scopedCredit?.totalOwedCents ?: 0L
    val totalLimit = scopedCredit?.totalLimitCents ?: 0L
    val utilBps = scopedCredit?.utilizationBps
    val showFeeRoi = uiState.rewardsToolsEnabled || summary.totalAnnualFeesCents > 0L || summary.totalAnnualCreditsPotentialCents > 0L

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWide = maxWidth >= 560.dp
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BalanceUtilizationTile(
                    totalOwed = totalOwed,
                    totalLimit = totalLimit,
                    utilBps = utilBps,
                    modifier = Modifier.weight(1f)
                )
                if (showFeeRoi) {
                    AnnualCreditsOffsetTile(
                        feesCents = summary.totalAnnualFeesCents,
                        capturedCents = summary.capturedCreditsCardYearCents,
                        potentialCents = summary.totalAnnualCreditsPotentialCents,
                        modifier = Modifier.weight(1f)
                    )
                    NetFeeRoiTile(
                        feesCents = summary.totalAnnualFeesCents,
                        netFeeCents = summary.feeAfterCreditsCents,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BalanceUtilizationTile(
                    totalOwed = totalOwed,
                    totalLimit = totalLimit,
                    utilBps = utilBps,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showFeeRoi) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnnualCreditsOffsetTile(
                            feesCents = summary.totalAnnualFeesCents,
                            capturedCents = summary.capturedCreditsCardYearCents,
                            potentialCents = summary.totalAnnualCreditsPotentialCents,
                            modifier = Modifier.weight(1f)
                        )
                        NetFeeRoiTile(
                            feesCents = summary.totalAnnualFeesCents,
                            netFeeCents = summary.feeAfterCreditsCents,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceUtilizationTile(
    totalOwed: Long,
    totalLimit: Long,
    utilBps: Int?,
    modifier: Modifier = Modifier
) {
    val utilTone = when {
        utilBps == null -> CardStatusTone.NEUTRAL
        utilBps >= 7500 -> CardStatusTone.CRITICAL
        utilBps >= 3000 -> CardStatusTone.WARNING
        utilBps < 1000 -> CardStatusTone.POSITIVE
        else -> CardStatusTone.NEUTRAL
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (utilBps != null) {
                    CardStatusPill(text = "${CardFormat.formatBps(utilBps)} util", tone = utilTone)
                }
            }
            RollingTickerCents(
                cents = totalOwed,
                style = TabularNumStyle.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                showCents = totalOwed % 100L != 0L
            )
            if (totalLimit > 0L) {
                Text(
                    text = "of ${CardFormat.formatAdaptiveCents(totalLimit)} combined limit",
                    style = TabularNumStyle.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun AnnualCreditsOffsetTile(
    feesCents: Long,
    capturedCents: Long,
    potentialCents: Long,
    modifier: Modifier = Modifier
) {
    val denominator = if (feesCents > 0L) feesCents else potentialCents.coerceAtLeast(capturedCents)
    val offsetRatio = if (denominator > 0L) (capturedCents.toFloat() / denominator.toFloat()).coerceIn(0f, 1f) else 0f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "Credits Captured",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CardFormat.formatAdaptiveCents(capturedCents),
                style = TabularNumStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = if (capturedCents > 0L) OrbitTheme.colors.income else MaterialTheme.colorScheme.onSurface
            )
            if (denominator > 0L) {
                OrbitPillProgressBar(
                    percent = offsetRatio,
                    height = 5.dp,
                    customFillColor = OrbitTheme.colors.income
                )
            }
            Text(
                text = if (feesCents > 0L) {
                    "against ${CardFormat.formatAdaptiveCents(feesCents)} fees"
                } else {
                    "of ${CardFormat.formatAdaptiveCents(potentialCents)} potential"
                },
                style = TabularNumStyle.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun NetFeeRoiTile(
    feesCents: Long,
    netFeeCents: Long,
    modifier: Modifier = Modifier
) {
    val isNetPositiveValue = netFeeCents <= 0L
    val displayText = when {
        netFeeCents < 0L -> "+${CardFormat.formatAdaptiveCents(abs(netFeeCents))}"
        else -> CardFormat.formatAdaptiveCents(netFeeCents)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isNetPositiveValue) "Net Card Value" else "Net Annual Fee",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayText,
                style = TabularNumStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = if (isNetPositiveValue) OrbitTheme.colors.income else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = when {
                    feesCents == 0L -> "No annual fees"
                    isNetPositiveValue -> "Fees offset by perks"
                    else -> "Fee minus captured credits"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CardholderFilterStrip(
    availablePlayers: List<Pair<String, String>>,
    selectedPlayer: String,
    allPlayersKey: String,
    onSelectPlayer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val options = remember(availablePlayers, allPlayersKey) {
        listOf(allPlayersKey to "All Household") + availablePlayers
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = options, key = { it.first }) { (key, label) ->
            val isSelected = selectedPlayer == key
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else OrbitTheme.colors.cardBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        haptics.tick()
                        onSelectPlayer(key)
                    }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RewardsToolsDiscoveryBanner(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitSectionCard(
        title = "Unlock Strategy & Multiplier Tools",
        subtitle = "Track Chase 5/24 slots, active welcome bonuses, and best card per checkout category.",
        modifier = modifier
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OrbitActionChip(label = "Enable Strategy Tools", onClick = onEnable)
            OrbitActionChip(label = "Dismiss", onClick = onDismiss)
        }
    }
}

@Composable
internal fun CustomizeRewardsToolsToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "Rewards & 5/24 Strategy Tools",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Show Which Card Where, Welcome Bonuses, and Chase 5/24 tracker",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
