package app.overbay.orbit.ui.upcoming.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RecurringCandidate
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.ProjectedAmountResult
import app.overbay.orbit.domain.UpcomingScheduleHelper
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.LocalOrbitHaptics

data class LaneConfig(
    val title: String,
    val kind: UpcomingKind,
    val items: List<UpcomingItem>
)

fun isGivingItem(item: UpcomingItem, categoryMap: Map<Long, Category>): Boolean {
    if (item.kind == UpcomingKind.GIVING) return true
    if (item.direction.lowercase() == "income" || item.kind == UpcomingKind.INCOME) return false
    val cat = categoryMap[item.categoryId]
    val text = "${item.name} ${item.merchant ?: ""} ${item.notes ?: ""} ${cat?.name ?: ""}".lowercase()
    return UpcomingScheduleHelper.GIVING_HINTS.any { text.contains(it) }
}

@Composable
fun UpcomingLaneCard(
    title: String,
    laneKind: UpcomingKind,
    items: List<UpcomingItem>,
    categoryMap: Map<Long, Category>,
    accountMap: Map<Long, Account>,
    projectedAmounts: Map<Long, ProjectedAmountResult>,
    inspectingItemId: Long?,
    onItemClick: (UpcomingItem) -> Unit,
    onMarkPaid: (UpcomingItem) -> Unit,
    onAddClick: (UpcomingKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val totalCents = items.sumOf { projectedAmounts[it.id]?.amountCents ?: it.amountCents }
    val isIncome = laneKind == UpcomingKind.INCOME
    val laneColor = if (isIncome) colors.income else when (laneKind) {
        UpcomingKind.GIVING -> MaterialTheme.colorScheme.tertiary
        UpcomingKind.SUBSCRIPTION -> MaterialTheme.colorScheme.secondary
        else -> colors.expense
    }

    OrbitSectionCard(
        title = title,
        action = {
            Text(
                text = "${if (isIncome) "+" else "-"}${MoneyUtils.formatCents(totalCents, showCents = false)}",
                style = TabularNumStyle.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                color = laneColor
            )
        },
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        if (items.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "No ${title.lowercase()} saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { onAddClick(laneKind) },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Add", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    UpcomingItemRow(
                        item = item,
                        category = item.categoryId?.let { categoryMap[it] },
                        accountName = item.accountId?.let { accountMap[it]?.name },
                        projected = projectedAmounts[item.id],
                        isInspected = item.id == inspectingItemId,
                        onClick = { onItemClick(item) },
                        onMarkPaid = { onMarkPaid(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun UpcomingCandidateRow(
    candidate: RecurringCandidate,
    category: Category?,
    accountName: String?,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val relDue = LocalDateUtils.formatRelativeDueLabel(candidate.nextProjectedDate)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val catSuffix = category?.labelWithEmoji?.let { " • $it" } ?: ""
                    Text(
                        text = "${candidate.frequency.name.lowercase().replaceFirstChar { it.uppercase() }}$catSuffix • Due $relDue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = MoneyUtils.formatCents(candidate.amountCents),
                    style = TabularNumStyle.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrbitBadge(
                        text = "${candidate.confidenceScore}% Match",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    OrbitBadge(
                        text = "${candidate.transactionCount} hits",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { haptics.tick(); onDismiss() },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { haptics.confirm(); onReview() },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Review", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
