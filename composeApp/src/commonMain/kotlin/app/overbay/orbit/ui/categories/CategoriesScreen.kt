package app.overbay.orbit.ui.categories

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import app.overbay.orbit.OrbitNavKey
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.ui.categories.components.CategoryDetailInspectorPane
import app.overbay.orbit.ui.categories.components.CategoryGroupCard
import app.overbay.orbit.ui.categories.dialogs.CategoryEditorDialog
import app.overbay.orbit.ui.components.CanopyMenuAction
import app.overbay.orbit.ui.components.CanopyMoreMenuButton
import app.overbay.orbit.ui.components.CanopySettingsButton
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitDragHandle
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitMorphingSplitLayout
import app.overbay.orbit.ui.components.dragReorderItem
import app.overbay.orbit.ui.components.rememberDragReorderState
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.transactions.components.CanopyMorphingSearchContent
import app.overbay.orbit.util.BackHandler
import app.overbay.orbit.util.DevicePosture
import app.overbay.orbit.util.LocalDevicePosture
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    posture: DevicePosture = LocalDevicePosture.current,
    onNavigate: (OrbitNavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val heroScrollState = rememberHeroScrollState()
    heroScrollState.scrollEnabled = !state.isSearchActive
    val listState = rememberLazyListState()
    val visibleCatCount = state.groups.sumOf { it.items.size }
    val catCount = if (state.searchQuery.isNotBlank()) {
        visibleCatCount
    } else if (state.totalCategoriesCount > 0) {
        state.totalCategoriesCount
    } else {
        state.categories.size
    }

    BackHandler(enabled = state.isSearchActive) {
        haptics.tick()
        viewModel.setSearchActive(false)
    }

    val searchActiveProgress by animateFloatAsState(
        targetValue = if (state.isSearchActive) 1f else 0f,
        animationSpec = OrbitSprings.FloatEffectsDefault,
        label = "categoriesSearchActiveProgress"
    )

    val contentAlpha = (1f - searchActiveProgress * 1.5f).coerceIn(0f, 1f)
    val activeHeightOverrideDp = if (searchActiveProgress > 0.001f) {
        val restingMorphHeightDp = with(density) {
            lerp(heroScrollState.maxHeightPx, heroScrollState.minHeightPx, heroScrollState.collapseFraction).toDp()
        }
        lerp(restingMorphHeightDp, 60.dp, searchActiveProgress)
    } else null

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
                        kicker = "CATEGORIES",
                        title = "Category Manager",
                        subtitle = "$catCount categories across ${state.groups.size} groups",
                        scrollState = heroScrollState,
                        contentAlpha = contentAlpha,
                        activeHeightOverrideDp = activeHeightOverrideDp,
                        morphingContent = { collapseFractionProvider, collapsedTitleRightDp, expandedTextBottomPxProvider ->
                            CanopyMorphingSearchContent(
                                collapseFractionProvider = collapseFractionProvider,
                                collapsedTitleRightDp = collapsedTitleRightDp,
                                expandedTextBottomPxProvider = expandedTextBottomPxProvider,
                                searchActiveProgress = searchActiveProgress,
                                isSearchActive = state.isSearchActive,
                                searchQuery = viewModel.searchQueryInput,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onSearchActiveChange = { viewModel.setSearchActive(it) },
                                expandedPlaceholder = "Search categories by name or emoji...",
                                trailingControls = { restingHeight ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CanopyMoreMenuButton(
                                            buttonSize = restingHeight,
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
                                            buttonSize = restingHeight,
                                            onClick = { onNavigate(OrbitNavKey.Settings) }
                                        )
                                    }
                                }
                            )
                        }
                    )

                    val startPad = if (isTwoPane) 20.dp else 16.dp
                    val endPad = if (isTwoPane) 0.dp else 16.dp

                    val masterList: @Composable () -> Unit = {
                        if (state.groups.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.searchQuery.isNotBlank()) {
                                    EmptyStateCard(
                                        title = "No Matching Categories",
                                        description = "No categories matched \"${state.searchQuery}\"."
                                    )
                                } else {
                                    EmptyStateCard(
                                        title = "No Categories Yet",
                                        description = "Add your first category to start organizing your transactions.",
                                        actionButtonText = "+ Add Category",
                                        onActionClick = { viewModel.openAddCategory() }
                                    )
                                }
                            }
                        } else {
                            val groupReorderState = rememberDragReorderState(
                                items = state.groups,
                                keySelector = { it.title },
                                onMove = { from, to -> viewModel.moveCategoryGroup(from, to) },
                                itemSpacingDp = 16.dp
                            )
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = startPad, end = endPad, top = 12.dp, bottom = 88.dp),
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
                                        showMhaBadge = state.mhaEnabled,
                                        modifier = Modifier
                                            .animateItem(
                                                fadeInSpec = OrbitSprings.FloatEffectsDefault,
                                                placementSpec = if (state.isReorderMode) null else OrbitSprings.IntOffsetSpatialDefault,
                                                fadeOutSpec = OrbitSprings.FloatEffectsFast
                                            )
                                            .dragReorderItem(
                                                state = if (state.isReorderMode) groupReorderState else null,
                                                key = group.title
                                            )
                                    )
                                }
                            }
                        }
                    }

                    if (isTwoPane) {
                        OrbitMorphingSplitLayout(
                            isDetailOpen = isDetailOpen,
                            onDismissDetail = { viewModel.dismissInspectorPane() },
                            masterWeightWhenOpen = 0.56f,
                            modifier = Modifier.fillMaxSize(),
                            masterContent = masterList,
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
                    } else {
                        masterList()
                    }
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
                onDismiss = { viewModel.closeEditorDialog() },
                showMhaOption = state.mhaEnabled
            )
        }

        val blockedSummary = state.ruleBlockedDeleteCategory
        if (blockedSummary != null) {
            OrbitConfirmationDialog(
                title = "Category in Use by Rules",
                message = "Cannot delete '${blockedSummary.category.name}' because ${blockedSummary.ruleCount} automation rule(s) use it. Update or delete those rules first.",
                confirmText = "View Rules",
                dismissText = "Dismiss",
                onConfirm = {
                    viewModel.dismissRuleBlockedDialog()
                    onNavigate(OrbitNavKey.Rules)
                },
                onDismiss = { viewModel.dismissRuleBlockedDialog() }
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
