package app.overbay.orbit.ui.calculators.retirement.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.calculators.retirement.RetirementUiState
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitBarSegment
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitStackedSegmentBar
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.max

@Composable
fun retirementAccountPalette(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    OrbitTheme.colors.transfer,
    MaterialTheme.colorScheme.tertiary,
    OrbitTheme.colors.warning,
    MaterialTheme.colorScheme.secondary,
    OrbitTheme.colors.income
)

fun retirementAccountColorMap(accountIds: List<Long?>, palette: List<Color>): Map<Long, Color> =
    accountIds.filterNotNull().distinct().mapIndexed { idx, id -> id to palette[idx % palette.size] }.toMap()

@Composable
fun RetirementAccountsListCard(
    uiState: RetirementUiState,
    onEditStartingBalance: () -> Unit,
    onResetCurrentBalance: () -> Unit,
    onChooseIncludedAccounts: () -> Unit,
    onResetToAutoAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val linkedAccounts = uiState.linkedAccounts
    val totalLinkedBalance = remember(linkedAccounts) { linkedAccounts.sumOf { it.balanceCents } }
    val palette = retirementAccountPalette()
    val accountColorMap = remember(linkedAccounts, palette) {
        retirementAccountColorMap(linkedAccounts.map { it.accountId }, palette)
    }

    OrbitSectionCard(
        title = "Connected Portfolio Sources",
        subtitle = "Accounts included in projections and starting balance override",
        actionText = "Choose",
        onActionClick = onChooseIncludedAccounts,
        modifier = modifier
    ) {
        // Starting Portfolio Summary + Override Action
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = if (uiState.currentBalanceOverridden) {
                            "Starting Balance (Override)"
                        } else if (uiState.hasCustomAccountSelection) {
                            "Custom Selection Total (${linkedAccounts.size})"
                        } else {
                            "Connected Portfolio Total (${linkedAccounts.size})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = MoneyUtils.formatCents(uiState.currentBalanceCents, showCents = false),
                        style = TabularNumStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.hasCustomAccountSelection && !uiState.currentBalanceOverridden) {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                onResetToAutoAccounts()
                            },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Auto", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (uiState.currentBalanceOverridden) {
                        TextButton(
                            onClick = {
                                haptics.tick()
                                onResetCurrentBalance()
                            },
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.tick()
                            onEditStartingBalance()
                        },
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Override", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (linkedAccounts.isEmpty()) {
            EmptyStateCard(
                title = if (uiState.hasCustomAccountSelection) "No Accounts Included" else "No Linked Retirement Accounts",
                description = if (uiState.hasCustomAccountSelection) {
                    "No accounts are currently selected for retirement projections."
                } else {
                    "Choose specific accounts to include, or link investment accounts to a household member."
                },
                actionButtonText = "Choose Accounts",
                onActionClick = onChooseIncludedAccounts
            )
        } else {
            if (totalLinkedBalance > 0L) {
                OrbitStackedSegmentBar(
                    segments = linkedAccounts.mapIndexed { index, account ->
                        val color = account.accountId?.let { accountColorMap[it] } ?: palette[index % palette.size]
                        OrbitBarSegment(
                            key = account.id,
                            fraction = max(0.02f, account.balanceCents.toFloat() / totalLinkedBalance.toFloat()),
                            color = color
                        )
                    },
                    height = 8.dp,
                    cornerRadius = 999.dp,
                    normalizeToFullWidth = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                linkedAccounts.forEachIndexed { index, account ->
                    val color = account.accountId?.let { accountColorMap[it] } ?: palette[index % palette.size]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
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
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(32.dp)
                                        .background(color, shape = RoundedCornerShape(999.dp))
                                )
                                Column {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitleParts = listOfNotNull(account.ownerName, account.institution).filter { it.isNotBlank() }
                                    if (subtitleParts.isNotEmpty()) {
                                        Text(
                                            text = subtitleParts.joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                    style = TabularNumStyle.copy(fontWeight = FontWeight.Bold),
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
