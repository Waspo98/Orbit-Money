package app.overbay.orbit.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
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
                        },
                        persistentBottomBar = {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Search categories by name or emoji...") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = if (state.searchQuery.isNotBlank()) {
                                        {
                                            IconButton(
                                                onClick = { viewModel.setSearchQuery("") },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    } else null,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
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

        val blockedSummary = state.ruleBlockedDeleteCategory
        if (blockedSummary != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissRuleBlockedDialog() },
                title = { Text("Category in Use by Rules", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Cannot delete '${blockedSummary.category.name}' because ${blockedSummary.ruleCount} automation rule(s) use it. Update or delete those rules first."
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.dismissRuleBlockedDialog()
                        onNavigate(OrbitNavKey.Rules)
                    }) {
                        Text("View Rules")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissRuleBlockedDialog() }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        if (state.deleteConfirmCategoryId != null) {
            val catId = state.deleteConfirmCategoryId!!
            val targetCat = state.categories.find { it.id == catId }
            val summary = state.groups.asSequence().flatMap { it.items.asSequence() }.find { it.category.id == catId }
            val txCount = summary?.transactionCount ?: targetCat?.transactionCount ?: 0
            val budgetCount = summary?.budgetCount ?: targetCat?.budgetCount ?: 0
            val catName = targetCat?.name ?: "this category"
            OrbitConfirmationDialog(
                title = "Delete Category?",
                message = "Deleting '$catName' will leave $txCount transaction(s) uncategorized and remove $budgetCount budget(s).",
                confirmText = "Delete",
                isDestructive = true,
                onConfirm = { viewModel.deleteCategory(catId) },
                onDismiss = { viewModel.dismissDeleteCategory() }
            )
        }
    }
}
