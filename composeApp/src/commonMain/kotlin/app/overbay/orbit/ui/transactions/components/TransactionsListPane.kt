package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.EmptyStateCard
import app.overbay.orbit.ui.components.MorphingPageHero
import app.overbay.orbit.ui.components.OrbitFloatingActionButton
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.rememberHeroScrollState
import app.overbay.orbit.ui.transactions.TransactionsUiState
import app.overbay.orbit.ui.transactions.TransactionsViewModel
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Editorial Transactions list pane featuring flat MorphingPageHero with a physical-glide
 * search bar that shrinks beside the title on collapse and expands to full-width on tap,
 * a persistent Filter/Sort/View + active-filter chip strip, pull-to-refresh SimpleFIN sync,
 * and scroll-reactive '+' FAB.
 * Strictly under the 400-line ceiling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsListPane(
    state: TransactionsUiState,
    viewModel: TransactionsViewModel,
    onOpenDrawer: () -> Unit,
    onOpenCsv: () -> Unit,
    onSelectForInspect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val heroScrollState = rememberHeroScrollState()
    heroScrollState.scrollEnabled = !state.isSearchActive

    val searchActiveProgress by animateFloatAsState(
        targetValue = if (state.isSearchActive) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "searchActiveProgress"
    )

    val contentAlpha = (1f - searchActiveProgress * 1.5f).coerceIn(0f, 1f)
    val restingMorphHeightDp = with(density) {
        lerp(heroScrollState.maxHeightPx, heroScrollState.minHeightPx, heroScrollState.collapseFraction).toDp()
    }
    val activeHeightOverrideDp = if (searchActiveProgress > 0.001f) {
        lerp(restingMorphHeightDp, 60.dp, searchActiveProgress)
    } else null

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MorphingPageHero(
                kicker = "MONEY MOVEMENT",
                title = "Transactions",
                subtitle = "${state.monthlyTransactionCount} transactions this month",
                scrollState = heroScrollState,
                contentAlpha = contentAlpha,
                activeHeightOverrideDp = activeHeightOverrideDp,
                morphingContent = { collapseFraction, collapsedTitleRightDp, expandedTextBottomDp ->
                    val collapsedSearchLeft = collapsedTitleRightDp + 12.dp
                    val collapsedSearchWidth = (maxWidth - 16.dp - collapsedSearchLeft).coerceAtLeast(96.dp)

                    val restingLeft = lerp(20.dp, collapsedSearchLeft, collapseFraction)
                    val restingTop = lerp(expandedTextBottomDp, 9.dp, collapseFraction)
                    val restingWidth = lerp((maxWidth - 40.dp).coerceAtLeast(100.dp), collapsedSearchWidth, collapseFraction)
                    val restingHeight = lerp(46.dp, 38.dp, collapseFraction)

                    val currentLeft = lerp(restingLeft, 12.dp, searchActiveProgress)
                    val currentTop = lerp(restingTop, 6.dp, searchActiveProgress)
                    val currentWidth = lerp(restingWidth, (maxWidth - 24.dp).coerceAtLeast(100.dp), searchActiveProgress)
                    val currentHeight = lerp(restingHeight, 48.dp, searchActiveProgress)

                    val isCompact = !state.isSearchActive && (collapseFraction > 0.45f)
                    val placeholder = if (state.isSearchActive || collapseFraction <= 0.45f) {
                        "Search merchant, description, notes..."
                    } else {
                        "Search..."
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = currentLeft, y = currentTop)
                            .width(currentWidth)
                            .height(currentHeight)
                    ) {
                        TransactionsSearchBar(
                            searchQuery = state.searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            isSearchActive = state.isSearchActive,
                            onSearchActiveChange = { viewModel.setSearchActive(it) },
                            isCompact = isCompact,
                            placeholderText = placeholder,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                persistentBottomBar = {
                    TransactionsPersistentFilterStrip(
                        state = state,
                        viewModel = viewModel,
                        onOpenCsv = onOpenCsv
                    )
                }
            )

            AnimatedContent(
                targetState = state.isSearchActive,
                transitionSpec = {
                    fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault())
                },
                modifier = Modifier.fillMaxSize(),
                label = "transactionsMainToSearchTransition"
            ) { searching ->
                if (searching) {
                    val searchStage = when {
                        state.searchQuery.isBlank() -> "PROMPT"
                        state.groupedTransactions.isEmpty() -> "EMPTY"
                        else -> "RESULTS"
                    }
                    AnimatedContent(
                        targetState = searchStage,
                        transitionSpec = {
                            fadeIn(OrbitSprings.effectsDefault()) togetherWith fadeOut(OrbitSprings.effectsDefault())
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "transactionsSearchStageTransition"
                    ) { stage ->
                        if (stage == "PROMPT" || stage == "EMPTY") {
                            TransactionsSearchPromptOrEmpty(searchQuery = state.searchQuery)
                        } else {
                            TransactionsGroupedLazyList(
                                state = state,
                                viewModel = viewModel,
                                onInteraction = { keyboardController?.hide() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = state.isSyncing,
                        onRefresh = { haptics.tick(); viewModel.syncSimpleFin() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            state.isLoading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            state.groupedTransactions.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(heroScrollState.nestedScrollConnection)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyStateCard(
                                        title = if (state.searchQuery.isNotBlank() || state.hasActiveFilters) {
                                            "No matching transactions"
                                        } else {
                                            "No transactions recorded"
                                        },
                                        description = if (state.searchQuery.isNotBlank() || state.hasActiveFilters) {
                                            "Try clearing your search query or adjusting your filters."
                                        } else {
                                            "Pull down to sync with SimpleFIN or tap '+' to record a transaction."
                                        },
                                        icon = OrbitIcons.Transactions,
                                        actionButtonText = if (state.searchQuery.isNotBlank() || state.hasActiveFilters) "Reset Filters" else null,
                                        onActionClick = {
                                            viewModel.setSearchQuery("")
                                            viewModel.clearFilters()
                                        }
                                    )
                                }
                            }
                            else -> {
                                TransactionsGroupedLazyList(
                                    state = state,
                                    viewModel = viewModel,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(heroScrollState.nestedScrollConnection)
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.syncToastMessage != null,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.inverseSurface,
                                tonalElevation = 6.dp,
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (state.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.inverseOnSurface
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Text(
                                        text = state.syncToastMessage ?: "",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        OrbitFloatingActionButton(
            visible = heroScrollState.isFabVisible && !state.isSelectionMode && !state.isSearchActive,
            onClick = { viewModel.openTransactionEditor(null) },
            contentDescription = "Add Transaction",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }
}

@Composable
private fun TransactionsGroupedLazyList(
    state: TransactionsUiState,
    viewModel: TransactionsViewModel,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.groupedTransactions.forEach { group ->
            item(key = "header_${group.monthKey}") {
                MonthGroupHeader(monthLabel = group.monthLabel, count = group.count)
            }

            items(items = group.transactions, key = { it.id }) { txn ->
                SwipeableTransactionRow(
                    transaction = txn,
                    category = state.categoryMap[txn.effectiveCategoryId],
                    accountName = state.accountMap[txn.accountId]?.name ?: txn.accountName,
                    isSelected = state.selectedTransactionIds.contains(txn.id),
                    isSelectionMode = state.isSelectionMode,
                    isExpanded = state.expandedTransactionId == txn.id,
                    onClick = {
                        onInteraction()
                        if (state.isSelectionMode) viewModel.toggleTransactionSelection(txn.id)
                        else viewModel.toggleExpandTransaction(txn.id)
                    },
                    onLongClick = { onInteraction(); viewModel.toggleTransactionSelection(txn.id) },
                    onEdit = { viewModel.openTransactionEditor(txn.id) },
                    onSplit = { viewModel.openSplitSheet(txn.id) },
                    onCreateRule = { viewModel.createRuleFromTransaction(txn) },
                    onMarkRecurring = { viewModel.markTransactionRecurring(txn) },
                    onToggleTransfer = { viewModel.toggleTransfer(txn.id) },
                    onToggleMha = { viewModel.toggleMha(txn.id) },
                    onToggleIgnore = { viewModel.toggleIgnore(txn.id) },
                    onDelete = { viewModel.deleteTransaction(txn.id) },
                    onResetMerchant = { viewModel.resetMerchant(txn.id) },
                    onResetCategory = { viewModel.resetCategory(txn.id) }
                )
            }
        }
    }
}

@Composable
private fun MonthGroupHeader(monthLabel: String, count: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$count ${if (count == 1) "transaction" else "transactions"}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = OrbitTheme.colors.cardBorder,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
