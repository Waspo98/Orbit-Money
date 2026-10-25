package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.data.model.CategorySortOption
import app.overbay.orbit.domain.MoneyUtils

@Composable
fun BudgetCategoryGroupList(
    budgeted: List<CategoryBudgetOverviewItem>,
    unbudgeted: List<CategoryBudgetOverviewItem>,
    inactive: List<CategoryBudgetOverviewItem>,
    activeSort: CategorySortOption,
    onSortSelected: (CategorySortOption) -> Unit,
    onItemClick: (CategoryBudgetOverviewItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Sort Option Filter Chips Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortFilterChip(
                label = "Over Budget First",
                selected = activeSort == CategorySortOption.OVER_BUDGET_FIRST,
                onClick = { onSortSelected(CategorySortOption.OVER_BUDGET_FIRST) }
            )

            SortFilterChip(
                label = "Least Room Left",
                selected = activeSort == CategorySortOption.LEAST_ROOM_LEFT,
                onClick = { onSortSelected(CategorySortOption.LEAST_ROOM_LEFT) }
            )

            SortFilterChip(
                label = "Most Spent",
                selected = activeSort == CategorySortOption.MOST_SPENT,
                onClick = { onSortSelected(CategorySortOption.MOST_SPENT) }
            )

            SortFilterChip(
                label = "Name A-Z",
                selected = activeSort == CategorySortOption.NAME_A_TO_Z,
                onClick = { onSortSelected(CategorySortOption.NAME_A_TO_Z) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Budgeted Section
        if (budgeted.isNotEmpty()) {
            val totalBudgeted = budgeted.sumOf { it.amountCents }
            SectionHeader(
                title = "Budgeted Categories",
                subtitle = "${budgeted.size} categories • ${MoneyUtils.formatCents(totalBudgeted, showCents = false)}"
            )

            Spacer(modifier = Modifier.height(8.dp))

            budgeted.forEach { item ->
                BudgetCategoryRow(
                    item = item,
                    onClick = { onItemClick(item) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Unbudgeted Spending Section
        if (unbudgeted.isNotEmpty()) {
            val totalUnbudgeted = unbudgeted.sumOf { it.spentCents }
            SectionHeader(
                title = "Unbudgeted Spending",
                subtitle = "${unbudgeted.size} categories • ${MoneyUtils.formatCents(totalUnbudgeted, showCents = false)}"
            )

            Spacer(modifier = Modifier.height(8.dp))

            unbudgeted.forEach { item ->
                BudgetCategoryRow(
                    item = item,
                    onClick = { onItemClick(item) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Inactive Categories Section
        if (inactive.isNotEmpty()) {
            SectionHeader(
                title = "Other Categories",
                subtitle = "${inactive.size} categories with no spending this month"
            )

            Spacer(modifier = Modifier.height(8.dp))

            inactive.forEach { item ->
                BudgetCategoryRow(
                    item = item,
                    onClick = { onItemClick(item) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SortFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
    )
}
