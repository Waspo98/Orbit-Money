package app.overbay.orbit.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import app.overbay.orbit.ui.categories.components.CategoryDetailInspectorPane
import app.overbay.orbit.ui.categories.components.CategoryGroupCard
import app.overbay.orbit.ui.categories.dialogs.CategoryEditorDialog
import app.overbay.orbit.ui.components.CanopyMenuAction
import app.overbay.orbit.ui.components.CanopyMoreMenuButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.CanopySurfacePill
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    onBack: () -> Unit = { onNavigate(OrbitNavKey.Dashboard) },
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val heroScrollState = rememberHeroScrollState()
    val catCount = if (state.totalCategoriesCount > 0) state.totalCategoriesCount else state.categories.size

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTwoPane = maxWidth >= 600.dp || posture.supportsTwoPane
        val isDetailOpen = isTwoPane && (state.inspectingSummary != null || state.showEditorDialog)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                        onBack = onBack,
                        scrollState = heroScrollState,
                        canopyBar = { _, isCompact, barHeight ->
                            CanopySurfacePill(
                                expandedText = "$catCount Categories · ${state.groups.size} Groups",
                                compactText = "$catCount Categories",
                                isCompact = isCompact,
                                leadingIcon = OrbitIcons.Categories,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            CanopyMoreMenuButton(
                                buttonSize = barHeight,
                                contentDescription = "Category Options",
                                actions = listOf(
                                    CanopyMenuAction(
                                        label = "Reorder Categories",
                                        isChecked = state.isReorderMode,
                                        onClick = { viewModel.toggleReorderMode() }
                                    ),
                                    CanopyMenuAction(
                                        label = "Add Category",
                                        onClick = { viewModel.openAddCategory() }
                                    )
                                )
                            )

                            CanopySettingsButton(
                                buttonSize = barHeight,
                                onClick = { onNavigate(OrbitNavKey.Settings) }
                            )
                        }
                    )

                    OrbitMorphingSplitLayout(
                        isDetailOpen = isDetailOpen,
                        onDismissDetail = { viewModel.dismissInspectorPane() },
                        masterWeightWhenOpen = 0.56f,
                        modifier = Modifier.fillMaxSize(),
                        masterContent = {
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
                                val groupReorderState = rememberDragReorderState(
                                    items = state.groups,
                                    keySelector = { it.title },
                                    onMove = { from, to -> viewModel.moveCategoryGroup(from, to) },
                                    itemSpacingDp = 16.dp
                                )
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
                                            inspectingCategoryId = if (isTwoPane) state.inspectingCategoryId else null,
                                            onInspectCategory = if (isTwoPane) {
                                                { cat -> viewModel.selectCategoryForInspect(cat.id) }
                                            } else {
                                                null
                                            },
                                            onEditCategory = { cat -> viewModel.openEditCategory(cat) },
                                            onDeleteCategory = { id -> viewModel.requestDeleteCategory(id) },
                                            isReorderMode = state.isReorderMode,
                                            groupDragHandle = if (state.isReorderMode) {
                                                { OrbitDragHandle(state = groupReorderState, key = group.title) }
                                            } else null,
                                            onMoveCategory = { from, to -> viewModel.moveCategoryInGroup(group.title, from, to) },
                                            modifier = Modifier.dragReorderItem(
                                                state = if (state.isReorderMode) groupReorderState else null,
                                                key = group.title
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        detailContent = {
                            CategoryDetailInspectorPane(
                                state = state,
                                onOpenEdit = { cat -> viewModel.openEditCategory(cat) },
                                onSaveCategory = { id, name, color, icon, isTrans, isInc, mha ->
                                    viewModel.saveCategory(id, name, color, icon, isTrans, isInc, mha)
                                },
                                onCloseEdit = { viewModel.closeEditorDialog() },
                                onDeleteCategory = { id -> viewModel.requestDeleteCategory(id) },
                                onViewTransactions = { onNavigate(OrbitNavKey.Transactions(categoryId = state.inspectingCategoryId)) },
                                onDismissPane = { viewModel.dismissInspectorPane() }
                            )
                        }
                    )
                }
            }
        }

        if (!isTwoPane && state.showEditorDialog) {
            CategoryEditorDialog(
                category = state.editingCategory,
                onSave = { id, name, color, icon, isTrans, isInc, mha ->
                    viewModel.saveCategory(id, name, color, icon, isTrans, isInc, mha)
                },
                onDelete = { id -> viewModel.requestDeleteCategory(id) },
                onViewTransactions = { id ->
                    viewModel.closeEditorDialog()
                    onNavigate(OrbitNavKey.Transactions(categoryId = id))
                },
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
}
