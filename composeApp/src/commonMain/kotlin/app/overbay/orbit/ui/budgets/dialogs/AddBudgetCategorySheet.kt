package app.overbay.orbit.ui.budgets.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.TabularNumStyle
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.CurrencyTextField
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSwitchRow
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial modal sheet allowing users to add or configure a budget for any unbudgeted category,
 * with quick 3-month average spend chips and rollover toggle.
 * Strictly <= 390 lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetCategorySheet(
    availableCategories: List<Category>,
    initialCategoryId: Long? = null,
    threeMonthAvgProvider: (Long) -> Cents,
    onSave: (categoryId: Long, amountCents: Cents, rollover: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        val haptics = LocalOrbitHaptics.current
        var selectedCategory by remember(initialCategoryId, availableCategories) {
            mutableStateOf(availableCategories.find { it.id == initialCategoryId } ?: availableCategories.firstOrNull())
        }
        var amountCents by remember { mutableStateOf(10000L) }
        var rollover by remember { mutableStateOf(false) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        val threeMonthAvg = selectedCategory?.let { threeMonthAvgProvider(it.id) } ?: 0L

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Add Category Budget",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { haptics.tick(); onDismiss() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Picker
            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (availableCategories.isEmpty()) {
                Text(
                    text = "All expense categories currently have budgets set.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.let { "${it.icon ?: "📁"} ${it.name}" } ?: "Select Category",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        availableCategories.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        val color = category.color.toComposeColor(MaterialTheme.colorScheme.primary)
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                        ) {
                                            Surface(color = color.copy(alpha = 0.2f), modifier = Modifier.fillMaxWidth().height(24.dp)) {}
                                            Text(
                                                text = category.icon ?: "📁",
                                                fontSize = 12.sp,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }
                                        Text(text = category.name, fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    haptics.tick()
                                    selectedCategory = category
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Amount Input
            CurrencyTextField(
                cents = amountCents,
                onCentsChange = { amountCents = it },
                label = "Monthly Budget Amount"
            )

            // Quick 3-Month Average Chip
            if (threeMonthAvg > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "3-Mo Average:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        onClick = {
                            haptics.tick()
                            amountCents = threeMonthAvg
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "Use ${MoneyUtils.formatCents(threeMonthAvg, showCents = false)}",
                            style = TabularNumStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Rollover Switch
            OrbitSwitchRow(
                title = "Monthly Rollover",
                subtitle = "Roll unspent balance forward into next month",
                checked = rollover,
                onCheckedChange = { rollover = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { haptics.tick(); onDismiss() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val cat = selectedCategory
                        if (cat != null && amountCents > 0L) {
                            haptics.confirm()
                            onSave(cat.id, amountCents, rollover)
                        }
                    },
                    enabled = selectedCategory != null && amountCents > 0L,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Save Budget")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
