package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.labelWithEmoji
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun TransactionBatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCategorize: () -> Unit,
    onMarkTransfer: () -> Unit,
    onToggleMha: () -> Unit,
    onDelete: () -> Unit,
    categories: List<Category> = emptyList(),
    showCategoryPicker: Boolean = false,
    onDismissCategoryPicker: () -> Unit = {},
    onSelectCategory: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Top header: count, select all, close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            haptics.tick()
                            onClearSelection()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$selectedCount Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (selectedCount < totalCount) {
                        TextButton(onClick = {
                            haptics.tick()
                            onSelectAll()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Select All",
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("Select All")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action buttons row (Title Case)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilledTonalButton(
                            onClick = {
                                haptics.tick()
                                onCategorize()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Categorize",
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text("Categorize")
                        }

                        DropdownMenu(
                            expanded = showCategoryPicker,
                            onDismissRequest = onDismissCategoryPicker
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.labelWithEmoji) },
                                    onClick = {
                                        haptics.tick()
                                        onSelectCategory(cat.id)
                                    }
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            haptics.tick()
                            onMarkTransfer()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Transfer",
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("Transfer")
                    }

                    FilledTonalButton(
                        onClick = {
                            haptics.tick()
                            onToggleMha()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "MHA",
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("MHA")
                    }

                    FilledTonalButton(
                        onClick = {
                            haptics.reject()
                            onDelete()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("Delete")
                    }
                }
            }
        }
    }
}
