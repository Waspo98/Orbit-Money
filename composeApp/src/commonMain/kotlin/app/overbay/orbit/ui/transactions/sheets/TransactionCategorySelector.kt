package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.theme.toComposeColor
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitFieldLabel
import app.overbay.orbit.ui.components.OrbitFormDefaults
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.SelectableListRow
import app.overbay.orbit.ui.components.orbitTactilePress
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Compact full-width category field combining category color + emoji into a single 30dp tinted mark
 * (eliminating the oversized 48dp leading colored-dot slot and text truncation).
 */
@Composable
fun TransactionCategoryField(
    selectedCategory: Category?,
    isTransfer: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Category"
) {
    val haptics = LocalOrbitHaptics.current
    val catColor = selectedCategory?.color.toComposeColor(MaterialTheme.colorScheme.primary)
    val badgeBg = if (selectedCategory != null) {
        catColor.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val emoji = when {
        selectedCategory != null -> CategoryEmojiMapper.resolveEmoji(selectedCategory.icon) ?: "$"
        isTransfer -> "🔄"
        else -> "❓"
    }
    val displayTitle = when {
        selectedCategory != null -> selectedCategory.name
        isTransfer -> "Transfer (Uncategorized)"
        else -> "Uncategorized"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OrbitFieldLabel(text = label)
        Surface(
            shape = OrbitFormDefaults.FieldShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f),
            border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .orbitTactilePress()
                .clip(OrbitFormDefaults.FieldShape)
                .clickable {
                    haptics.tick()
                    onClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = badgeBg,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCategory != null) catColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (selectedCategory != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Category",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Inline searchable category picker stage rendered inside [TransactionEditorContent].
 * One tap selects a category and immediately transitions back to the transaction form.
 */
@Composable
fun InlineCategoryPickerStage(
    categories: List<Category>,
    selectedCategoryId: Long?,
    currentSavedCategoryId: Long?,
    onSelectCategory: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    var searchQuery by remember { mutableStateOf("") }

    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredCategories = remember(categories, normalizedQuery) {
        val sorted = categories.sortedBy { it.name.lowercase() }
        if (normalizedQuery.isBlank()) sorted
        else sorted.filter { it.name.lowercase().contains(normalizedQuery) }
    }
    val showUncategorized = normalizedQuery.isBlank() || "uncategorized".contains(normalizedQuery)

    Column(modifier = modifier.fillMaxWidth()) {
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

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showUncategorized) {
                SelectableListRow(
                    title = "Uncategorized",
                    isSelected = selectedCategoryId == null,
                    onClick = { onSelectCategory(null) },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("❓", style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    },
                    trailingContent = if (currentSavedCategoryId == null) {
                        {
                            OrbitBadge(
                                text = "Saved",
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }

            filteredCategories.forEach { category ->
                val catColor = category.color.toComposeColor(MaterialTheme.colorScheme.primary)
                SelectableListRow(
                    title = category.name,
                    isSelected = selectedCategoryId == category.id,
                    onClick = { onSelectCategory(category.id) },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = catColor.copy(alpha = 0.18f),
                            modifier = Modifier.size(34.dp)
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
                    trailingContent = if (category.id == currentSavedCategoryId) {
                        {
                            OrbitBadge(
                                text = "Saved",
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }

            if (!showUncategorized && filteredCategories.isEmpty()) {
                Text(
                    text = "No categories match \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}
