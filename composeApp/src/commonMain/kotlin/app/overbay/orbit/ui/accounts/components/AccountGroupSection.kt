package app.overbay.orbit.ui.accounts.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.accounts.AccountGroupDisplay
import app.overbay.orbit.ui.accounts.AccountTypeCategory
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial accordion group section card.
 * Collapsed by default with 16dp corners, 1dp cardBorder, tracked uppercase header,
 * overlapping mini-stack visual preview, bold colored balance, circular 30dp chevron,
 * left-side drag handle, and support for 1-column vs 2-column wideSpan layout.
 */
@Composable
fun AccountGroupSection(
    group: AccountGroupDisplay,
    selectedAccountId: Long?,
    cardCycles: Map<Long, CardCycleStatus> = emptyMap(),
    closureCandidateIds: Set<Long> = emptySet(),
    onInspectAccount: (Account) -> Unit = {},
    onReviewClosure: (Account) -> Unit = {},
    onSeeTransactions: (Account) -> Unit,
    onEditAccount: (Account) -> Unit,
    onPerksClick: (Account) -> Unit,
    onFindLogo: (Account) -> Unit = {},
    onReportLogoStatus: (String, String) -> Unit = { _, _ -> },
    onHistoryClick: (Account) -> Unit,
    onMergeClick: (Account) -> Unit,
    onToggleArchive: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    isReorderMode: Boolean = false,
    wideSpan: Int = 1,
    onToggleWideSpan: (() -> Unit)? = null,
    groupDragHandle: (@Composable () -> Unit)? = null,
    onMoveAccount: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
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

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = OrbitSprings.FloatSpatialDefault
    )

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
                if (groupDragHandle != null) {
                    groupDragHandle()
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Left Column: Tracked uppercase title + account count & collapsed mini-stack preview
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
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val countLabel = if (group.category == AccountTypeCategory.REAL_ESTATE && group.propertyUnits.isNotEmpty()) {
                            val n = group.propertyUnits.size
                            "$n ${if (n == 1) "property" else "properties"}"
                        } else {
                            "${group.accounts.size} ${if (group.accounts.size == 1) "account" else "accounts"}"
                        }
                        Text(
                            text = countLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        AnimatedVisibility(
                            visible = !expanded,
                            enter = fadeIn(OrbitSprings.effectsDefault()) + expandHorizontally(OrbitSprings.spatialDefault()),
                            exit = fadeOut(OrbitSprings.effectsFast()) + shrinkHorizontally(OrbitSprings.spatialDefault())
                        ) {
                            AccountGroupPreviewStack(
                                category = group.category,
                                accounts = group.accounts,
                                onReportLogoStatus = onReportLogoStatus,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Right Column: WideSpan toggle, Bold balance, Chevron
                val supportsTwoPane = LocalDevicePosture.current.supportsTwoPane
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (supportsTwoPane && onToggleWideSpan != null && group.accounts.size > 1) {
                        Surface(
                            shape = CircleShape,
                            color = if (wideSpan == 2) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    haptics.tick()
                                    onToggleWideSpan()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (wideSpan == 2) "2C" else "1C",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (wideSpan == 2) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = MoneyUtils.formatCents(group.totalCents),
                        style = TabularNumStyle.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = totalColor
                    )

                    Spacer(modifier = Modifier.width(10.dp))

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

            // Expanded Account Rows inside the same card
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(OrbitSprings.spatialDefault()) + fadeIn(OrbitSprings.effectsDefault()),
                exit = shrinkVertically(OrbitSprings.spatialDefault()) + fadeOut(OrbitSprings.effectsFast())
            ) {
                val accountReorderState = rememberDragReorderState(
                    items = group.accounts,
                    keySelector = { it.id },
                    onMove = onMoveAccount,
                    itemSpacingDp = 1.dp
                )

                if (group.category == AccountTypeCategory.REAL_ESTATE && group.propertyUnits.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                        group.propertyUnits.forEach { unit ->
                            key(unit.anchorAccount.id) {
                                RealEstatePropertyCard(
                                    unit = unit,
                                    selectedAccountId = selectedAccountId,
                                    closureCandidateIds = closureCandidateIds,
                                    onInspectAccount = onInspectAccount,
                                    onEditAccount = onEditAccount,
                                    onReviewClosure = onReviewClosure,
                                    onSeeTransactions = onSeeTransactions,
                                    onFindLogo = onFindLogo,
                                    onReportLogoStatus = onReportLogoStatus,
                                    onHistoryClick = onHistoryClick,
                                    onMergeClick = onMergeClick,
                                    onToggleArchive = onToggleArchive,
                                    onDeleteAccount = onDeleteAccount
                                )
                            }
                        }
                    }
                } else if (wideSpan == 2 && group.accounts.size >= 2 && !isReorderMode) {
                    val pairs = group.accounts.chunked(2)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        pairs.forEach { pairAccounts ->
                            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                pairAccounts.forEach { account ->
                                    AccountRowCard(
                                        account = account,
                                        groupCategory = group.category,
                                        cycleStatus = cardCycles[account.id],
                                        isInspected = selectedAccountId == account.id,
                                        hasClosureAlert = account.id in closureCandidateIds,
                                        onReviewClosure = { onReviewClosure(account) },
                                        onInspect = { onInspectAccount(account) },
                                        onSeeTransactions = { onSeeTransactions(account) },
                                        onEdit = { onEditAccount(account) },
                                        onPerks = if (account.type == AccountType.CREDIT) {
                                            { onPerksClick(account) }
                                        } else null,
                                        onFindLogo = if (account.type != AccountType.CREDIT) {
                                            { onFindLogo(account) }
                                        } else null,
                                        onReportLogoStatus = onReportLogoStatus,
                                        onHistory = { onHistoryClick(account) },
                                        onMerge = { onMergeClick(account) },
                                        onToggleArchive = { onToggleArchive(account) },
                                        onDelete = { onDeleteAccount(account) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pairAccounts.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        group.accounts.forEach { account ->
                            key(account.id) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = OrbitTheme.colors.cardBorder
                                )
                                AccountRowCard(
                                    account = account,
                                    groupCategory = group.category,
                                    cycleStatus = cardCycles[account.id],
                                    isInspected = selectedAccountId == account.id,
                                    hasClosureAlert = account.id in closureCandidateIds,
                                    onReviewClosure = { onReviewClosure(account) },
                                    onInspect = { onInspectAccount(account) },
                                    onSeeTransactions = { onSeeTransactions(account) },
                                    onEdit = { onEditAccount(account) },
                                    onPerks = if (account.type == AccountType.CREDIT) {
                                        { onPerksClick(account) }
                                    } else null,
                                    onFindLogo = if (account.type != AccountType.CREDIT) {
                                        { onFindLogo(account) }
                                    } else null,
                                    onReportLogoStatus = onReportLogoStatus,
                                    onHistory = { onHistoryClick(account) },
                                    onMerge = { onMergeClick(account) },
                                    onToggleArchive = { onToggleArchive(account) },
                                    onDelete = { onDeleteAccount(account) },
                                    dragHandle = if (isReorderMode) {
                                        { OrbitDragHandle(state = accountReorderState, key = account.id) }
                                    } else null,
                                    modifier = Modifier.dragReorderItem(
                                        state = if (isReorderMode) accountReorderState else null,
                                        key = account.id
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
