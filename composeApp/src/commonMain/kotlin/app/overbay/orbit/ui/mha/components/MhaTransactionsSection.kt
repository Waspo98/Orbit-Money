package app.overbay.orbit.ui.mha.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitCompactListRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.RollingTickerCents
import app.overbay.orbit.ui.mha.MhaCategoryTotal
import app.overbay.orbit.ui.mha.MhaMonthGroup
import app.overbay.orbit.ui.mha.MhaTransactionItem
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlin.math.abs

@Composable
fun MhaTransactionsHeaderAndFilters(
    visibleCount: Int,
    visibleTotalCents: Cents,
    actualEligibleTotalCents: Cents,
    categoryBreakdown: List<MhaCategoryTotal>,
    selectedCategoryId: Long?,
    showExcludedInList: Boolean,
    searchQuery: String,
    onSelectCategory: (Long?) -> Unit,
    onToggleShowExcluded: (Boolean) -> Unit,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (showExcludedInList) "Housing & Outflows" else "Counted Expenses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OrbitBadge(
                    text = "$visibleCount",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            RollingTickerCents(
                cents = visibleTotalCents,
                style = TabularNumStyle.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        OrbitTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(if (showExcludedInList) "Search all year outflows" else "Search counted expenses")
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Search")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            item(key = "cat_all") {
                val selected = selectedCategoryId == null
                FilterChip(
                    selected = selected,
                    onClick = {
                        haptics.tick()
                        onSelectCategory(null)
                    },
                    label = {
                        Text(
                            text = "All · ${MoneyUtils.formatCompactCents(actualEligibleTotalCents)}",
                            style = TabularNumStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        )
                    },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            items(items = categoryBreakdown, key = { "cat_${it.categoryId}" }) { cat ->
                val selected = selectedCategoryId == cat.categoryId
                FilterChip(
                    selected = selected,
                    onClick = {
                        haptics.tick()
                        onSelectCategory(if (selected) null else cat.categoryId)
                    },
                    label = {
                        Text(
                            text = "${cat.emoji} ${cat.name} · ${MoneyUtils.formatCompactCents(cat.totalCents)}",
                            style = TabularNumStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                )
            }

            item(key = "toggle_excluded") {
                FilterChip(
                    selected = showExcludedInList,
                    onClick = {
                        haptics.tick()
                        onToggleShowExcluded(!showExcludedInList)
                    },
                    label = {
                        Text(
                            text = if (showExcludedInList) "Showing Excluded" else "Include Excluded",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = if (showExcludedInList) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

fun LazyListScope.mhaCountedTransactionsItems(
    groupedMonths: List<MhaMonthGroup>,
    selectedYear: Int,
    showExcludedInList: Boolean,
    searchQuery: String,
    selectedCategoryId: Long?,
    onInspectItem: (MhaTransactionItem) -> Unit,
    onShowExcluded: () -> Unit,
    onClearFilters: () -> Unit
) {
    if (groupedMonths.isEmpty()) {
        item(key = "mha_txns_empty", contentType = "empty") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmptyStateCard(
                    title = if (searchQuery.isNotBlank() || selectedCategoryId != null) {
                        "No Matching Expenses"
                    } else {
                        "No Counted Housing Expenses"
                    },
                    description = when {
                        searchQuery.isNotBlank() && !showExcludedInList ->
                            "No counted $selectedYear expenses match \"$searchQuery\". Try including excluded outflows to check non-MHA items."
                        searchQuery.isNotBlank() ->
                            "No $selectedYear expenses match \"$searchQuery\"."
                        else ->
                            "Eligible transactions from your MHA accounts and categories in $selectedYear will appear here."
                    }
                )

                if (!showExcludedInList && searchQuery.isNotBlank()) {
                    OutlinedButton(
                        onClick = onShowExcluded,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Search Excluded Outflows")
                    }
                } else if (selectedCategoryId != null || searchQuery.isNotBlank()) {
                    OutlinedButton(
                        onClick = onClearFilters,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Clear Filters")
                    }
                }
            }
        }
        return
    }

    groupedMonths.forEach { group ->
        item(key = "mha_month_hdr_${group.yearMonth}", contentType = "month_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.monthLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val noun = if (group.countedCount == 1) "item" else "items"
                Text(
                    text = "${MoneyUtils.formatCents(group.countedTotalCents)} · ${group.countedCount} $noun",
                    style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(
            items = group.items,
            key = { item -> "mha_txn_${item.id}" },
            contentType = { "mha_txn_row" }
        ) { item ->
            MhaCountedTransactionRow(
                item = item,
                onClick = { onInspectItem(item) },
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
private fun MhaCountedTransactionRow(
    item: MhaTransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val catColor = item.category?.color.toComposeColor(MaterialTheme.colorScheme.primary)
    val emoji = item.category?.displayEmoji ?: "❓"
    val parsedDate = LocalDateUtils.parseIsoDate(item.effectiveDate)
    val dateLabel = parsedDate?.let { LocalDateUtils.formatShortDate(it) } ?: item.effectiveDate
    val catName = item.category?.name ?: "Uncategorized"
    val subtitle = "$dateLabel · $catName"

    val trailingBadge: (@Composable () -> Unit)? = when {
        !item.isEligible -> {
            {
                OrbitBadge(
                    text = "Excluded",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item.isExplicitOverride -> {
            {
                OrbitBadge(
                    text = "Manual",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        else -> null
    }

    OrbitCompactListRow(
        title = item.merchant,
        subtitle = subtitle,
        amount = MoneyUtils.formatCents(abs(item.amountCents)),
        amountDetail = item.account?.name?.takeIf { it.isNotBlank() },
        amountColor = if (item.isEligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        containerColor = if (item.isEligible) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = colors.cardBorder,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (item.isEligible) catColor.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 15.sp)
            }
        },
        trailingContent = trailingBadge,
        onClick = onClick,
        modifier = modifier
    )
}
