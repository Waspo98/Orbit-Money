package app.overbay.orbit.ui.budgets.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.CategoryBudgetOverviewItem
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Shared interactive Donut + tappable pill legend + expandable detail strip
 * supporting both Categories and Merchants modes in [SpendingMixPanel].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpendingMixCategoryTab(
    slices: List<SpendingMixSlice>,
    totalSpent: Cents,
    entitySummary: String,
    activeSlice: SpendingMixSlice?,
    onToggleSlice: (String) -> Unit,
    onClearSelection: () -> Unit,
    onInspectCategory: (Long) -> Unit,
    onEditBudget: (CategoryBudgetOverviewItem) -> Unit,
    onViewTransactions: (Long) -> Unit
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpendingMixDonut(
            slices = slices,
            totalSpent = totalSpent,
            entitySummary = entitySummary,
            activeSlice = activeSlice,
            onToggleSlice = onToggleSlice,
            onClearSelection = onClearSelection
        )

        Spacer(modifier = Modifier.height(14.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.forEach { slice ->
                val isSelected = activeSlice?.key == slice.key
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) {
                        slice.color.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) slice.color else OrbitTheme.colors.cardBorder
                    ),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            haptics.tick()
                            onToggleSlice(slice.key)
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(slice.color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${slice.percentInt}%",
                            style = TabularNumStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        OrbitAccordionVisibility(visible = activeSlice != null) {
            if (activeSlice != null) {
                SpendingMixSelectionStrip(
                    slice = activeSlice,
                    totalSpent = totalSpent,
                    onInspectCategory = onInspectCategory,
                    onEditBudget = onEditBudget,
                    onViewTransactions = onViewTransactions,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
