package app.overbay.orbit.ui.accounts.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountTypeCategory
import app.overbay.orbit.ui.accounts.cards.CardFormat
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.roundToInt

/**
 * Editorial account list row featuring leading credit card art or institution brandmark,
 * smart-decluttered account badges, credit card due/utilization glance text,
 * bold tabular balance, and circular 3-dots action menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountRowCard(
    account: Account,
    groupCategory: AccountTypeCategory? = null,
    cycleStatus: CardCycleStatus? = null,
    onInspect: () -> Unit = {},
    onSeeTransactions: () -> Unit,
    onEdit: () -> Unit,
    onPerks: (() -> Unit)?,
    onFindLogo: (() -> Unit)? = null,
    onReportLogoStatus: ((merchantKey: String, status: String) -> Unit)? = null,
    onHistory: () -> Unit,
    onMerge: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    isInspected: Boolean = false,
    hasClosureAlert: Boolean = false,
    onReviewClosure: (() -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val isCredit = account.type == AccountType.CREDIT
    val isLiability = account.type.isLiability
    val isMortgage = account.type == AccountType.MORTGAGE
    val showSubtypeBadge = groupCategory == null ||
        groupCategory == AccountTypeCategory.CASH_AND_CHECKING ||
        groupCategory == AccountTypeCategory.LOANS

    val rowBgColor = if (isInspected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        Color.Transparent
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dragHandle != null) {
                dragHandle()
                Spacer(modifier = Modifier.width(8.dp))
            }

            AccountLeadingVisual(
                account = account,
                onReportLogoStatus = onReportLogoStatus
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Center Column: Name, Institution, Smart Badges, and Credit Glance
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val institutionMeta = buildString {
                    account.institution?.takeIf { it.isNotBlank() }?.let { append(it) }
                    account.accountNumberLast4?.takeIf { it.isNotBlank() }?.let { last4 ->
                        if (isNotEmpty()) append(" ")
                        append("•••• $last4")
                    }
                }
                val resolvedHolder = if (isCredit && account.creditCardProfile != null) {
                    cycleStatus?.holderLabel
                        ?: account.creditCardProfile.strategy.cardholder?.takeIf { it.isNotBlank() }
                } else null

                if (institutionMeta.isNotEmpty() || showSubtypeBadge || resolvedHolder != null || account.isArchived || hasClosureAlert) {
                    Spacer(modifier = Modifier.height(3.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (institutionMeta.isNotEmpty()) {
                            Text(
                                text = institutionMeta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }

                        if (showSubtypeBadge) {
                            AccountBadge(
                                text = if (account.isProperty) "Property" else account.type.displayName,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }

                        if (resolvedHolder != null) {
                            AccountBadge(
                                text = resolvedHolder,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }

                        if (hasClosureAlert && onReviewClosure != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .clickable { haptics.tick(); onReviewClosure() }
                            ) {
                                Text(
                                    text = "Closed / Paid Off?",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (account.isArchived) {
                            AccountBadge(
                                text = "Archived",
                                isMuted = true,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                }

                // Credit card glance status
                if (isCredit) {
                    val dueDays = cycleStatus?.daysUntilDue
                    val dueDate = cycleStatus?.dueDate
                    val dueText = when {
                        dueDays != null -> "Due ${CardFormat.formatDaysUntil(dueDays)}"
                        dueDate != null -> "Due ${CardFormat.formatCardDate(dueDate.toString())}"
                        else -> null
                    }
                    if (dueText != null) {
                        val dueColor = when {
                            dueDays != null && dueDays <= 3 -> colors.danger
                            dueDays != null && dueDays in 4..7 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = dueText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = if (dueDays != null && dueDays <= 3) FontWeight.SemiBold else FontWeight.Normal,
                            color = dueColor
                        )
                    } else if (account.creditCardProfile?.creditLimitCents != null) {
                        val limit = account.creditCardProfile.creditLimitCents
                        val ratio = account.creditCardProfile.creditUtilizationRatio(account.currentBalanceCents)
                        val pct = if (ratio != null) (ratio * 100.0).roundToInt() else null
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (pct != null) "$pct% of ${MoneyUtils.formatCents(limit)}" else "Limit ${MoneyUtils.formatCents(limit)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = if (pct != null && pct > 30) colors.danger else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Column: Bold Balance + Circular 3-dots button
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isEquityDisplay = (isMortgage || account.isProperty) && account.homeEquityCents != null
                val displayAmount = if (isEquityDisplay) {
                    account.homeEquityCents!!
                } else {
                    account.currentBalanceCents
                }

                val balanceColor = when {
                    isEquityDisplay -> if (displayAmount >= 0L) colors.income else colors.expense
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

                Spacer(modifier = Modifier.width(8.dp))

                // 32dp circular bordered 3-dots menu button
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(32.dp)
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
                            text = { Text("See Transactions") },
                            onClick = { haptics.tick(); menuExpanded = false; onSeeTransactions() }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Account") },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        if (onPerks != null) {
                            DropdownMenuItem(
                                text = { Text("Credit Card Tracker") },
                                onClick = { menuExpanded = false; onPerks() }
                            )
                        }
                        if (!isCredit && onFindLogo != null) {
                            DropdownMenuItem(
                                text = { Text("Find Logo") },
                                onClick = { haptics.tick(); menuExpanded = false; onFindLogo() }
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
internal fun AccountBadge(
    text: String,
    isMuted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (isMuted) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            color = if (isMuted) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
