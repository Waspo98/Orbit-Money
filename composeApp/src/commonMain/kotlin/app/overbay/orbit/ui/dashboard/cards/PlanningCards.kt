package app.overbay.orbit.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitAnimatedEntryColumn
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.rememberAnimatedExternalEntries
import app.overbay.orbit.ui.dashboard.SubscriptionsState
import app.overbay.orbit.ui.dashboard.UpcomingBillItem
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Subscriptions / Recurring items card showing estimated monthly recurring total
 * and up to 5 upcoming active recurring expense items.
 */
@Composable
fun SubscriptionsCard(
    state: SubscriptionsState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val subtitleText = if (state.totalActiveCount > 0) {
        val noun = if (state.totalActiveCount == 1) "item" else "items"
        "~${MoneyUtils.formatCents(state.estimatedMonthlyTotalCents)}/mo · ${state.totalActiveCount} active $noun"
    } else null

    OrbitSectionCard(
        title = "Subscriptions & Recurring",
        subtitle = subtitleText,
        actionText = "Review",
        onActionClick = onClick,
        onCardClick = onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.items.isEmpty()) {
                Text(
                    text = "No active recurring subscriptions found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.items.forEachIndexed { index, item ->
                    val catPrefix = CategoryEmojiMapper.resolveEmoji(item.categoryIcon)?.let { "$it " } ?: ""
                    val catLabel = item.categoryName?.let { "$catPrefix$it" } ?: "Subscription"
                    OrbitCompactListRow(
                        title = item.merchant ?: item.name,
                        subtitle = catLabel,
                        amount = MoneyUtils.formatCents(item.amountCents),
                        amountDetail = if (item.nextDateDisplay.isNotBlank()) "Next ${item.nextDateDisplay}" else null,
                        amountColor = if (item.isExpense) colors.expense else colors.income,
                        showTopDivider = index > 0,
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = item.frequencyLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Upcoming bills and income card showing next 3 occurrences with urgency badges
 * and context-aware Pay / Receive quick actions.
 */
@Composable
fun UpcomingBillsCard(
    bills: List<UpcomingBillItem>,
    onReconcile: (Long, String) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors

    val subtitleText = if (bills.isNotEmpty()) {
        val totalDue = bills.filter { it.isExpense }.sumOf { it.amountCents }
        if (totalDue > 0L) {
            "${MoneyUtils.formatCents(totalDue)} due in next 14 days"
        } else {
            "${bills.size} scheduled in next 14 days"
        }
    } else null

    OrbitSectionCard(
        title = "Upcoming Bills & Income",
        subtitle = subtitleText,
        actionText = "Upcoming",
        onActionClick = onViewAll,
        onCardClick = onViewAll,
        modifier = modifier
    ) {
        val (billEntries, onBillRemoveFinished) = rememberAnimatedExternalEntries(
            items = bills.take(3),
            itemKey = { "${it.upcomingItemId}_${it.dueDateIso}" }
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            OrbitAccordionVisibility(visible = bills.isEmpty()) {
                Text(
                    text = "No bills or income scheduled for the next 14 days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OrbitAnimatedEntryColumn(
                entries = billEntries,
                onRemoveFinished = onBillRemoveFinished,
                minActiveCount = 0,
                bringIntoViewOnAdd = false
            ) { entry, displayIndex, _ ->
                val bill = entry.value
                val amtColor = if (bill.isExpense) colors.expense else colors.income
                val formattedAmt = if (bill.isExpense) {
                    "-${MoneyUtils.formatCents(bill.amountCents)}"
                } else {
                    "+${MoneyUtils.formatCents(bill.amountCents)}"
                }

                val catPrefix = CategoryEmojiMapper.resolveEmoji(bill.categoryIcon)?.let { "$it " } ?: ""
                val catLabel = bill.categoryName?.let { "$catPrefix$it" } ?: "General"
                val hasUrgency = bill.isOverdue || bill.isDueSoon
                val urgencyColor = if (bill.isOverdue) colors.danger else colors.warning

                OrbitCompactListRow(
                    title = bill.merchant ?: bill.name,
                    subtitle = if (hasUrgency) catLabel else "$catLabel · ${bill.dueLabel}",
                    amount = formattedAmt,
                    amountColor = amtColor,
                    showTopDivider = displayIndex > 0,
                    trailingAction = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (hasUrgency) {
                                OrbitBadge(
                                    text = bill.dueLabel,
                                    containerColor = urgencyColor.copy(alpha = 0.14f),
                                    contentColor = urgencyColor
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    haptics.confirm()
                                    onReconcile(bill.upcomingItemId, bill.dueDateIso)
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (bill.isExpense) "Pay" else "Receive",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
