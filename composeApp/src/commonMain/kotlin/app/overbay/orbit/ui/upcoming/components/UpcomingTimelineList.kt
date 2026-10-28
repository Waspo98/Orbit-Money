package app.overbay.orbit.ui.upcoming.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.upcoming.UpcomingFilterOption
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@Composable
fun UpcomingTimelineList(
    items: List<UpcomingItem>,
    categoryMap: Map<Long, Category>,
    accountMap: Map<Long, Account>,
    filterOption: UpcomingFilterOption,
    onFilterChanged: (UpcomingFilterOption) -> Unit,
    onItemClick: (UpcomingItem) -> Unit,
    onMarkPaid: (UpcomingItem) -> Unit,
    onAddClick: () -> Unit,
    inspectingItemId: Long? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            EmptyStateCard(
                title = "No Upcoming Items",
                description = "Track recurring subscriptions, utility bills, and paychecks to forecast cashflow.",
                actionButtonText = "+ Add Recurring Item",
                onActionClick = onAddClick
            )
        } else {
            items.forEach { item ->
                UpcomingItemRow(
                    item = item,
                    category = item.categoryId?.let { categoryMap[it] },
                    accountName = item.accountId?.let { accountMap[it]?.name },
                    isInspected = item.id == inspectingItemId,
                    onClick = { onItemClick(item) },
                    onMarkPaid = {
                        haptics.confirm()
                        onMarkPaid(item)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun UpcomingItemRow(
    item: UpcomingItem,
    category: Category?,
    accountName: String?,
    onClick: () -> Unit,
    onMarkPaid: () -> Unit,
    isInspected: Boolean = false
) {
    val isExpense = item.direction.lowercase() != "income"
    val amountColor = if (isExpense) OrbitTheme.colors.expense else OrbitTheme.colors.income

    val catColor = try {
        val hex = category?.color?.trim()?.removePrefix("#") ?: "9E9E9E"
        Color(hex.toLong(16) or 0xFF000000)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val rowBgColor = if (isInspected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val rowBorderColor = if (isInspected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = rowBgColor,
        border = BorderStroke(if (isInspected) 1.5.dp else 1.dp, rowBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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

                Spacer(modifier = Modifier.width(12.dp))

                val iconPrefix = category?.displayEmoji?.let { "$it " } ?: ""
                Column {
                    Text(
                        text = "$iconPrefix${item.name.ifBlank { item.merchant ?: "Recurring Item" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val catSuffix = category?.labelWithEmoji?.let { " • $it" } ?: ""
                    Text(
                        text = "Due ${item.nextDate} • ${item.frequencyType.name.lowercase().replaceFirstChar { it.uppercase() }}$catSuffix${if (accountName != null) " • $accountName" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                val prefix = if (isExpense) "-" else "+"
                Text(
                    text = "$prefix${MoneyUtils.formatCents(abs(item.amountCents))}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = amountColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onMarkPaid,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Mark Paid",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isExpense) "Mark Paid" else "Mark Received",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
