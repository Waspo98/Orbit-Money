package app.overbay.orbit.ui.upcoming.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.ProjectedAmountResult
import app.overbay.orbit.domain.UpcomingScheduleHelper
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.upcoming.UpcomingFilter
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

fun LazyListScope.upcomingTimelineItems(
    items: List<UpcomingItem>,
    candidates: List<RecurringCandidate>,
    categoryMap: Map<Long, Category>,
    accountMap: Map<Long, Account>,
    selectedFilter: UpcomingFilter,
    selectedDateFilter: String?,
    onItemClick: (UpcomingItem) -> Unit,
    onMarkPaid: (UpcomingItem) -> Unit,
    onAddClick: (UpcomingKind) -> Unit,
    onReviewCandidate: (RecurringCandidate) -> Unit,
    onDismissCandidate: (String) -> Unit,
    projectedAmounts: Map<Long, ProjectedAmountResult> = emptyMap(),
    inspectingItemId: Long? = null
) {
    if (selectedFilter == UpcomingFilter.SUGGESTIONS) {
        if (candidates.isEmpty()) {
            item(key = "upcoming_suggestions_empty", contentType = "empty_state") {
                EmptyStateCard(
                    title = "No Suggestions Right Now",
                    description = "We automatically detect recurring subscriptions, utility bills, and paychecks from past transactions.",
                    actionButtonText = "+ Add Recurring Item",
                    onActionClick = { onAddClick(UpcomingKind.BILL) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = OrbitSprings.FloatEffectsDefault,
                        placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                        fadeOutSpec = OrbitSprings.FloatEffectsFast
                    )
                )
            }
        } else {
            items(
                items = candidates,
                key = { "candidate_${it.merchant}" },
                contentType = { "upcoming_candidate_row" }
            ) { candidate ->
                UpcomingCandidateRow(
                    candidate = candidate,
                    category = candidate.suggestedCategoryId?.let { categoryMap[it] },
                    accountName = candidate.suggestedAccountId?.let { accountMap[it]?.name },
                    onReview = { onReviewCandidate(candidate) },
                    onDismiss = { onDismissCandidate(candidate.merchant) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = OrbitSprings.FloatEffectsDefault,
                        placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                        fadeOutSpec = OrbitSprings.FloatEffectsFast
                    )
                )
            }
        }
        return
    }

    if (selectedFilter == UpcomingFilter.ALL && selectedDateFilter == null) {
        val incomeItems = items.filter { it.direction.lowercase() == "income" || it.kind == UpcomingKind.INCOME }
        val givingItems = items.filter { !it.direction.lowercase().equals("income") && isGivingItem(it, categoryMap) }
        val subItems = items.filter { !it.direction.lowercase().equals("income") && !isGivingItem(it, categoryMap) && it.kind == UpcomingKind.SUBSCRIPTION }
        val billItems = items.filter { !it.direction.lowercase().equals("income") && !isGivingItem(it, categoryMap) && it.kind == UpcomingKind.BILL }

        val lanes = listOf(
            LaneConfig("Income", UpcomingKind.INCOME, incomeItems),
            LaneConfig("Giving", UpcomingKind.GIVING, givingItems),
            LaneConfig("Subscriptions", UpcomingKind.SUBSCRIPTION, subItems),
            LaneConfig("Bills", UpcomingKind.BILL, billItems)
        )

        lanes.forEach { lane ->
            item(key = "upcoming_lane_${lane.kind.name}", contentType = "bento_lane") {
                UpcomingLaneCard(
                    title = lane.title,
                    laneKind = lane.kind,
                    items = lane.items,
                    categoryMap = categoryMap,
                    accountMap = accountMap,
                    projectedAmounts = projectedAmounts,
                    inspectingItemId = inspectingItemId,
                    onItemClick = onItemClick,
                    onMarkPaid = onMarkPaid,
                    onAddClick = onAddClick,
                    modifier = Modifier.animateItem(
                        fadeInSpec = OrbitSprings.FloatEffectsDefault,
                        placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                        fadeOutSpec = OrbitSprings.FloatEffectsFast
                    )
                )
            }
        }
        return
    }

    if (items.isEmpty()) {
        item(key = "upcoming_empty_state", contentType = "empty_state") {
            EmptyStateCard(
                title = "No Upcoming Items",
                description = "Track recurring subscriptions, utility bills, and paychecks to forecast cashflow.",
                actionButtonText = "+ Add Recurring Item",
                onActionClick = { onAddClick(UpcomingKind.BILL) },
                modifier = Modifier.animateItem(
                    fadeInSpec = OrbitSprings.FloatEffectsDefault,
                    placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                    fadeOutSpec = OrbitSprings.FloatEffectsFast
                )
            )
        }
    } else {
        items(
            items = items,
            key = { "upcoming_${it.id}" },
            contentType = { "upcoming_item_row" }
        ) { item ->
            UpcomingItemRow(
                item = item,
                category = item.categoryId?.let { categoryMap[it] },
                accountName = item.accountId?.let { accountMap[it]?.name },
                projected = projectedAmounts[item.id],
                isInspected = item.id == inspectingItemId,
                onClick = { onItemClick(item) },
                onMarkPaid = { onMarkPaid(item) },
                modifier = Modifier.animateItem(
                    fadeInSpec = OrbitSprings.FloatEffectsDefault,
                    placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                    fadeOutSpec = OrbitSprings.FloatEffectsFast
                )
            )
        }
    }
}

@Composable
fun UpcomingItemRow(
    item: UpcomingItem,
    category: Category?,
    accountName: String?,
    projected: ProjectedAmountResult?,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit,
    isInspected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val isExpense = item.direction.lowercase() != "income"
    val amountColor = if (isExpense) OrbitTheme.colors.expense else OrbitTheme.colors.income
    val effectiveAmountCents = projected?.amountCents ?: item.amountCents
    val catColor = category?.color.toComposeColor(MaterialTheme.colorScheme.primary)
    val relDue = LocalDateUtils.formatRelativeDueLabel(item.nextDate)

    val rowBgColor by animateColorAsState(
        targetValue = if (isInspected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "upcomingRowBgColor"
    )
    val rowBorderColor by animateColorAsState(
        targetValue = if (isInspected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        animationSpec = OrbitSprings.ColorEffectsDefault,
        label = "upcomingRowBorderColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = rowBgColor,
        border = BorderStroke(if (isInspected) 1.5.dp else 1.dp, rowBorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(catColor)
                )

                Spacer(modifier = Modifier.width(10.dp))

                val iconPrefix = category?.displayEmoji?.let { "$it " } ?: ""
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$iconPrefix${item.name.ifBlank { item.merchant ?: "Recurring Item" }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (item.kind == UpcomingKind.GIVING || item.kind == UpcomingKind.SUBSCRIPTION) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val kindLabel = if (item.kind == UpcomingKind.GIVING) "Giving" else "Sub"
                            OrbitBadge(
                                text = kindLabel,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val scheduleText = UpcomingScheduleHelper.formatRecurrenceSchedule(item)
                    val catSuffix = category?.labelWithEmoji?.let { " • $it" } ?: ""
                    Text(
                        text = "$relDue (${item.nextDate}) • $scheduleText$catSuffix${if (accountName != null) " • $accountName" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (projected?.isProjectedAverage == true) {
                        Spacer(modifier = Modifier.height(3.dp))
                        OrbitBadge(
                            text = "Projected from ${projected.sampleCount} transactions",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                val prefix = if (isExpense) "-" else "+"
                Text(
                    text = "$prefix${MoneyUtils.formatCents(abs(effectiveAmountCents))}",
                    style = TabularNumStyle.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = amountColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        haptics.confirm()
                        onMarkPaid()
                    },
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Mark Paid",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isExpense) "Mark Paid" else "Mark Received",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                    )
                }
            }
        }
    }
}
