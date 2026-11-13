package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.LocalOrbitSheetDismiss
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitModalFooter
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.SelectableListRow
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Searchable bottom-sheet category picker opened with one tap on a transaction's Category chip.
 */
@Composable
fun CategoryPickerSheet(
    transaction: Transaction,
    categories: List<Category>,
    onSaveCategory: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember(transaction.id, transaction.effectiveCategoryId) {
        mutableStateOf(transaction.effectiveCategoryId)
    }

    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredCategories = remember(categories, normalizedQuery) {
        val sorted = categories.sortedBy { it.name.lowercase() }
        if (normalizedQuery.isBlank()) sorted
        else sorted.filter { it.name.lowercase().contains(normalizedQuery) }
    }
    val showUncategorized = normalizedQuery.isBlank() || "uncategorized".contains(normalizedQuery)

    OrbitModalSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        footer = {
            val sheetDismiss = LocalOrbitSheetDismiss.current
            OrbitModalFooter(
                confirmText = "Save",
                onDismiss = onDismiss,
                onConfirm = {
                    val chosen = selectedCategoryId
                    sheetDismiss?.invoke { onSaveCategory(chosen) } ?: onSaveCategory(chosen)
                }
            )
        }
    ) {
        OrbitSheetHeader(
            title = "Select Category",
            subtitle = transaction.effectiveMerchant,
            onDismiss = onDismiss,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            OrbitTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search categories…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (searchQuery.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                searchQuery = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showUncategorized) {
                    item(key = "uncategorized") {
                        val isCurrentOrig = transaction.effectiveCategoryId == null
                        SelectableListRow(
                            title = "Uncategorized",
                            isSelected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            modifier = Modifier.animateItem(
                                fadeInSpec = OrbitSprings.FloatEffectsDefault,
                                placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                                fadeOutSpec = OrbitSprings.FloatEffectsFast
                            ),
                            leadingContent = {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text("❓", style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            },
                            trailingContent = if (isCurrentOrig) {
                                {
                                    OrbitBadge(
                                        text = "Current",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }

                items(filteredCategories, key = { it.id }) { category ->
                    val isCurrent = category.id == transaction.effectiveCategoryId
                    val catColor = category.color.toComposeColor(MaterialTheme.colorScheme.primary)
                    SelectableListRow(
                        title = category.name,
                        isSelected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        modifier = Modifier.animateItem(
                            fadeInSpec = OrbitSprings.FloatEffectsDefault,
                            placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                            fadeOutSpec = OrbitSprings.FloatEffectsFast
                        ),
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = catColor.copy(alpha = 0.18f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = CategoryEmojiMapper.resolveEmoji(category.icon) ?: "$",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = catColor
                                    )
                                }
                            }
                        },
                        trailingContent = if (isCurrent) {
                            {
                                OrbitBadge(
                                    text = "Current",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null
                    )
                }

                if (!showUncategorized && filteredCategories.isEmpty()) {
                    item(key = "empty_category_search") {
                        Text(
                            text = "No categories match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .animateItem(
                                    fadeInSpec = OrbitSprings.FloatEffectsDefault,
                                    placementSpec = OrbitSprings.IntOffsetSpatialDefault,
                                    fadeOutSpec = OrbitSprings.FloatEffectsFast
                                )
                        )
                    }
                }
            }
        }
    }
}
