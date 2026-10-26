package app.overbay.orbit.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.ui.categories.components.CategoryGroupCard
import app.overbay.orbit.ui.categories.dialogs.CategoryEditorDialog
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.rememberHeroScrollState

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()
    val catCount = if (state.totalCategoriesCount > 0) state.totalCategoriesCount else state.categories.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (!state.isLoading) {
                OrbitFloatingActionButton(
                    visible = heroScrollState.isFabVisible,
                    onClick = { viewModel.openAddCategory() },
                    contentDescription = "New Category"
                )
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(heroScrollState.nestedScrollConnection)
            ) {
                MorphingPageHero(
                    kicker = "SPENDING CLUSTERS",
                    title = "Category Manager",
                    subtitle = "$catCount categories across ${state.groups.size} groups",
                    canNavigateBack = true,
                    onBack = { onNavigate(OrbitNavKey.Dashboard) },
                    scrollState = heroScrollState
                )

                // Scrollable Content
                if (state.groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateCard(
                            title = "No Categories Yet",
                            description = "Add your first category to start organizing your transactions.",
                            actionButtonText = "+ Add Category",
                            onActionClick = { viewModel.openAddCategory() }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
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
