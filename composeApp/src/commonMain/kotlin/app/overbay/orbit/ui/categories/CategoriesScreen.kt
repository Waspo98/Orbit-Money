package app.overbay.orbit.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.categories.components.CategoryGroupCard
import app.overbay.orbit.ui.categories.dialogs.CategoryEditorDialog
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val haptics = LocalOrbitHaptics.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.tick()
                    viewModel.openAddCategory()
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Category")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Canopy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.totalCategoriesCount} total categories across ${state.groups.size} groups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.groups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        title = "No Categories Yet",
                        description = "Add your first category to start organizing your transactions.",
                        actionButtonText = "Add Category",
                        onActionClick = { viewModel.openAddCategory() }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = state.groups,
                        key = { it.title }
                    ) { group ->
                        CategoryGroupCard(
                            group = group,
                            onEditCategory = { cat -> viewModel.openEditCategory(cat) },
                            onDeleteCategory = { id -> viewModel.requestDeleteCategory(id) }
                        )
                    }
                }
            }
        }
    }

    if (state.showEditorDialog) {
        CategoryEditorDialog(
            category = state.editingCategory,
            onSave = { id, name, color, icon, isTrans, isInc, mha ->
                viewModel.saveCategory(id, name, color, icon, isTrans, isInc, mha)
            },
            onDelete = { id -> viewModel.requestDeleteCategory(id) },
            onDismiss = { viewModel.closeEditorDialog() }
        )
    }

    if (state.deleteConfirmCategoryId != null) {
        val catId = state.deleteConfirmCategoryId!!
        OrbitConfirmationDialog(
            title = "Delete Category?",
            message = "Are you sure you want to delete this category? Existing transactions with this category will become uncategorized.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { viewModel.deleteCategory(catId) },
            onDismiss = { viewModel.dismissDeleteCategory() }
        )
    }
}
