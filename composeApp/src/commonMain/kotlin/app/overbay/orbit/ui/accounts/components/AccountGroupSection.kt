package app.overbay.orbit.ui.accounts.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountGroupDisplay
import app.overbay.orbit.ui.accounts.AccountTypeCategory
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial white accordion group section card matching Screenshot 3.
 * Collapsed by default with 20dp corners, 1dp cardBorder, tracked uppercase header,
 * bold colored balance, and circular 30dp bordered chevron button.
 * Strictly under the 400-line ceiling.
 */
@Composable
fun AccountGroupSection(
    group: AccountGroupDisplay,
    selectedAccountId: Long?,
    onInspectAccount: (Account) -> Unit = {},
    onSeeTransactions: (Account) -> Unit,
    onEditAccount: (Account) -> Unit,
    onPerksClick: (Account) -> Unit,
    onHistoryClick: (Account) -> Unit,
    onMergeClick: (Account) -> Unit,
    onToggleArchive: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    isReorderMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    var expanded by rememberSaveable(group.category.name) { mutableStateOf(false) }

    LaunchedEffect(selectedAccountId) {
        if (selectedAccountId != null && group.accounts.any { it.id == selectedAccountId }) {
            expanded = true
        }
    }

    val isLiability = group.category in setOf(AccountTypeCategory.CREDIT_CARDS, AccountTypeCategory.LOANS)
    val totalColor = if (isLiability) {
        if (group.totalCents != 0L) colors.expense else MaterialTheme.colorScheme.onSurface
    } else {
        if (group.totalCents >= 0L) colors.income else colors.expense
    }

    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Group Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.tick()
                        expanded = !expanded
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Column: Tracked uppercase title + account count
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.78.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${group.accounts.size} ${if (group.accounts.size == 1) "account" else "accounts"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Right Column: Bold colored balance + 30dp circular bordered chevron
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = MoneyUtils.formatCents(group.totalCents),
                        style = TabularNumStyle.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = totalColor
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    if (isReorderMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { haptics.tick(); onMoveUp() },
                                enabled = canMoveUp,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move Group Up",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { haptics.tick(); onMoveDown() },
                                enabled = canMoveDown,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move Group Down",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(chevronRotation)
                                )
                            }
                        }
                    }
                }
            }

            // Expanded Account Rows inside the same card
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    group.accounts.forEach { account ->
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = OrbitTheme.colors.cardBorder
                        )
                        AccountRowCard(
                            account = account,
                            isInspected = selectedAccountId == account.id,
                            onInspect = { onInspectAccount(account) },
                            onSeeTransactions = { onSeeTransactions(account) },
                            onEdit = { onEditAccount(account) },
                            onPerks = if (account.type == AccountType.CREDIT) {
                                { onPerksClick(account) }
                            } else null,
                            onHistory = { onHistoryClick(account) },
                            onMerge = { onMergeClick(account) },
                            onToggleArchive = { onToggleArchive(account) },
                            onDelete = { onDeleteAccount(account) }
                        )
                    }
                }
            }
        }
    }
}
