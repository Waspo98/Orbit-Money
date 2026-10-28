package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Editorial account list row featuring account badges, credit card limits,
 * bold balance, compact "See transactions" action pill, and circular 3-dots menu.
 * Strictly under the 400-line ceiling.
 */
@Composable
fun AccountRowCard(
    account: Account,
    onInspect: () -> Unit = {},
    onSeeTransactions: () -> Unit,
    onEdit: () -> Unit,
    onPerks: (() -> Unit)?,
    onHistory: () -> Unit,
    onMerge: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    isInspected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val isCredit = account.type == AccountType.CREDIT
    val isLiability = account.type.isLiability
    val isMortgage = account.type == AccountType.MORTGAGE

    val rowBgColor = if (isInspected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Surface(
        color = rowBgColor,
        modifier = modifier.fillMaxWidth()
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.tick()
                onInspect()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Column: Name, Institution, and Badges
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Type Badge
                AccountBadge(text = account.type.displayName)

                if (account.isManual) {
                    AccountBadge(text = "Manual", isAccent = true)
                }

                if (account.isArchived) {
                    AccountBadge(text = "Archived", isMuted = true)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                account.institution?.let { inst ->
                    Text(
                        text = inst,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                account.accountNumberLast4?.let { last4 ->
                    Text(
                        text = " •••• $last4",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Credit card glance chips
            if (isCredit && account.creditCardProfile?.creditLimitCents != null) {
                val limit = account.creditCardProfile.creditLimitCents
                val ratio = account.creditCardProfile.creditUtilizationRatio(account.currentBalanceCents)
                val pct = if (ratio != null) (ratio * 100.0).roundToInt() else null
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (pct != null) "$pct% of ${MoneyUtils.formatCents(limit)}" else "Limit ${MoneyUtils.formatCents(limit)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = if (pct != null && pct > 30) colors.danger else MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Column: Balance, See transactions pill, and circular 3-dots button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                val displayAmount = if (isMortgage && account.homeEquityCents != null) {
                    account.homeEquityCents!!
                } else {
                    account.currentBalanceCents
                }

                val balanceColor = when {
                    isCredit || isLiability -> {
                        if (account.currentBalanceCents != 0L) colors.expense else MaterialTheme.colorScheme.onSurface
                    }
                    displayAmount >= 0L -> colors.income
                    else -> colors.expense
                }

                Text(
                    text = MoneyUtils.formatCents(displayAmount),
                    style = TabularNumStyle.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = balanceColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Compact "See transactions" button
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        onSeeTransactions()
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = "See transactions",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 32dp circular bordered 3-dots menu button
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier
                        .size(28.dp)
                        .clickable {
                            haptics.tick()
                            menuExpanded = true
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = OrbitIcons.MoreHoriz,
                            contentDescription = "Account Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    if (onPerks != null) {
                        DropdownMenuItem(
                            text = { Text("Credit Card Perks") },
                            onClick = { menuExpanded = false; onPerks() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Balance History") },
                        onClick = { menuExpanded = false; onHistory() }
                    )
                    DropdownMenuItem(
                        text = { Text("Merge") },
                        onClick = { menuExpanded = false; onMerge() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (account.isArchived) "Unarchive" else "Archive") },
                        onClick = { menuExpanded = false; onToggleArchive() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.danger) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun AccountBadge(
    text: String,
    isAccent: Boolean = false,
    isMuted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = when {
            isAccent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isMuted -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                isAccent -> MaterialTheme.colorScheme.onPrimaryContainer
                isMuted -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
