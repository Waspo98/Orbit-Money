package app.overbay.orbit.ui.dashboard.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.domain.ReviewQueueCandidate
import app.overbay.orbit.domain.RuleMatcherEngine
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.LocalOrbitSheetDismiss
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.dashboard.CategorizeReviewState
import app.overbay.orbit.ui.dashboard.DashboardAction
import app.overbay.orbit.ui.rules.sheets.RuleEditorSheet
import app.overbay.orbit.util.LocalOrbitHaptics
import kotlinx.coroutines.launch

private enum class ReviewSheetStage {
    ALL_DONE,
    BATCH_COMPLETE,
    CATEGORY_PICKER,
    SWIPE_STAGE
}

/**
 * Interactive "Categorize Recent Transactions" modal dialog matching `TransactionReviewModal` on `main`.
 */
@Composable
fun TransactionReviewSheet(
    state: CategorizeReviewState,
    onAction: (DashboardAction) -> Unit,
    onPreviewRuleMatches: (List<RuleCondition>) -> Int = { conditions ->
        RuleMatcherEngine.countMatches(state.allTransactions, conditions)
    },
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val currentTxn = state.currentTxn

    val categoryById = remember(state.categories) { state.categories.associateBy { it.id } }

    OrbitModalSheet(
        onDismissRequest = { onAction(DashboardAction.CloseCategorizeReview) },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val sheetDismiss = LocalOrbitSheetDismiss.current
        val closeSheet: () -> Unit = {
            val dismissAction = { onAction(DashboardAction.CloseCategorizeReview) }
            sheetDismiss?.invoke(dismissAction) ?: dismissAction()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OrbitSheetHeader(
                title = "Is this category correct?",
                onDismiss = { onAction(DashboardAction.CloseCategorizeReview) }
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.cardBorder)
            Spacer(modifier = Modifier.height(14.dp))

            val reviewStage = when {
                state.allDone -> ReviewSheetStage.ALL_DONE
                state.isBatchComplete -> ReviewSheetStage.BATCH_COMPLETE
                currentTxn != null && state.isCategoryPickerOpen -> ReviewSheetStage.CATEGORY_PICKER
                else -> ReviewSheetStage.SWIPE_STAGE
            }

            AnimatedContent(
                targetState = reviewStage,
                transitionSpec = {
                    (fadeIn(OrbitSprings.FloatEffectsDefault) togetherWith
                        fadeOut(OrbitSprings.FloatEffectsFast)).using(
                        SizeTransform(clip = false) { _, _ -> OrbitSprings.IntSizeSpatialDefault }
                    )
                },
                label = "ReviewSheetStageTransition"
            ) { stage ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (stage) {
                        ReviewSheetStage.ALL_DONE -> ReviewEmptyStateContent(
                            badgeText = "!",
                            title = "PHEW all transactions are categorized for now.",
                            subtitle = "Want to go through more transactions just for fun, ya sicko?",
                            primaryButtonText = "Yes I Need The Dopamine",
                            secondaryButtonText = "No Thanks, I Have Self-Control",
                            onPrimaryClick = { onAction(DashboardAction.StartFunReviewMode) },
                            onSecondaryClick = closeSheet
                        )

                        ReviewSheetStage.BATCH_COMPLETE -> ReviewEmptyStateContent(
                            badgeText = "10",
                            title = "Ten down.",
                            subtitle = "Want another stack, or are we calling that a responsible little victory?",
                            primaryButtonText = "View More Transactions",
                            secondaryButtonText = "Done For Now",
                            onPrimaryClick = { onAction(DashboardAction.LoadMoreReviewTransactions) },
                            onSecondaryClick = closeSheet
                        )

                        ReviewSheetStage.CATEGORY_PICKER -> if (currentTxn != null) {
                            CategoryReviewPickerContent(
                                txn = currentTxn,
                                categories = state.categories,
                                onChooseCategory = { catId -> onAction(DashboardAction.RequestConfirmReviewCategory(catId)) },
                                onBack = { onAction(DashboardAction.CloseReviewCategoryPicker) }
                            )
                        }

                        ReviewSheetStage.SWIPE_STAGE -> if (currentTxn != null) {
                            ReviewSwipeStage(
                                currentTxn = currentTxn,
                                currentIndex = state.currentIndex,
                                batchReviewed = state.batchReviewed,
                                category = currentTxn.categoryId?.let { categoryById[it] },
                                onAction = onAction
                            )
                        }
                    }
                }
            }
        }
    }

    val pendingCategoryId = state.pendingConfirmCategoryId
    if (pendingCategoryId != null && currentTxn != null) {
        val nextCategory = categoryById[pendingCategoryId]
        OrbitConfirmationDialog(
            title = "Confirm Category",
            message = "Change \"${currentTxn.merchant.ifBlank { "this transaction" }}\" to \"${nextCategory?.name ?: "this category"}\"?",
            confirmText = "Change Category",
            dismissText = "Keep Looking",
            onConfirm = { onAction(DashboardAction.ConfirmReviewCategory) },
            onDismiss = { onAction(DashboardAction.CancelConfirmReviewCategory) }
        )
    }

    val ruleTxn = state.ruleDraftTransaction
    if (ruleTxn != null) {
        RuleEditorSheet(
            rule = remember(ruleTxn, state.categories) { makeReviewRuleDraft(ruleTxn, state.categories) },
            categories = state.categories,
            accounts = state.accounts,
            onSave = { savedRule -> onAction(DashboardAction.SaveReviewRule(savedRule)) },
            onPreviewCount = onPreviewRuleMatches,
            onPreviewMatches = { conditions ->
                RuleMatcherEngine.matchingTransactions(state.allTransactions, conditions)
            },
            onDismiss = { onAction(DashboardAction.CloseReviewRuleCreator) },
            sourceTransaction = state.allTransactions.find { it.id == ruleTxn.id }
        )
    }
}

@Composable
private fun ReviewSwipeStage(
    currentTxn: ReviewQueueCandidate,
    currentIndex: Int,
    batchReviewed: Int,
    category: Category?,
    onAction: (DashboardAction) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val colors = OrbitTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val canGoBack = currentIndex > 0
    val thresholdPx = with(density) { 108.dp.toPx() }
    val exitPx = with(density) { 420.dp.toPx() }
    val offsetX = remember(currentTxn.id, currentIndex) { Animatable(0f) }
    var isExiting by remember(currentTxn.id, currentIndex) { mutableStateOf(false) }

    fun triggerApprove() {
        if (isExiting) return
        isExiting = true
        haptics.confirm()
        scope.launch {
            offsetX.animateTo(exitPx, OrbitSprings.FloatSpatialFast)
            onAction(DashboardAction.ApproveReviewTransaction)
        }
    }

    fun triggerReject() {
        if (isExiting) return
        isExiting = true
        haptics.tick()
        scope.launch {
            offsetX.animateTo(-exitPx, OrbitSprings.FloatSpatialFast)
            onAction(DashboardAction.RejectReviewTransaction)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier
                    .size(30.dp)
                    .alpha(if (canGoBack) 1f else 0.36f)
                    .clip(CircleShape)
                    .clickable(enabled = canGoBack) {
                        haptics.tick()
                        onAction(DashboardAction.GoBackReviewTransaction)
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("←", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text("${minOf(batchReviewed + 1, 10)} of 10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Is this category correct?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }

    Spacer(modifier = Modifier.height(14.dp))

    key(currentTxn.id, currentIndex) {
        val cardTone = when {
            offsetX.value > 24f -> "yes"
            offsetX.value < -24f -> "no"
            else -> ""
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.matchParentSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val confirmProgress = (offsetX.value / thresholdPx).coerceIn(0f, 1f)
                Text(
                    text = "Confirm ✓",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.2f + 0.8f * confirmProgress
                        val s = 0.9f + 0.14f * confirmProgress
                        scaleX = s
                        scaleY = s
                    }
                )
                val rejectProgress = (-offsetX.value / thresholdPx).coerceIn(0f, 1f)
                Text(
                    text = "✗ Change",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.expense,
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.2f + 0.8f * rejectProgress
                        val s = 0.9f + 0.14f * rejectProgress
                        scaleX = s
                        scaleY = s
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(currentTxn.id, currentIndex) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (isExiting) return@detectHorizontalDragGestures
                                when {
                                    offsetX.value >= thresholdPx -> triggerApprove()
                                    offsetX.value <= -thresholdPx -> triggerReject()
                                    else -> scope.launch {
                                        offsetX.animateTo(0f, OrbitSprings.FloatSpatialDefault)
                                    }
                                }
                            },
                            onDragCancel = {
                                if (!isExiting) {
                                    scope.launch { offsetX.animateTo(0f, OrbitSprings.FloatSpatialDefault) }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!isExiting) {
                                    change.consume()
                                    scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        translationX = offsetX.value
                        rotationZ = (offsetX.value / 48f).coerceIn(-7f, 7f)
                    },
                contentAlignment = Alignment.Center
            ) {
                ReviewTransactionCardContent(
                    txn = currentTxn,
                    category = category,
                    cardTone = cardTone,
                    modifier = Modifier.widthIn(max = 390.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "← Change",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.expense,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { triggerReject() }
                .padding(vertical = 4.dp, horizontal = 6.dp)
        )
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, colors.cardBorder)) {
            Text("‹  ›", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Text(
            text = "Confirm →",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { triggerApprove() }
                .padding(vertical = 4.dp, horizontal = 6.dp)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))
    HorizontalDivider(color = colors.cardBorder)
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = { haptics.tick(); onAction(DashboardAction.OpenReviewRuleCreator) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text("Create A Rule", fontWeight = FontWeight.SemiBold)
    }
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedButton(
        onClick = { haptics.tick(); onAction(DashboardAction.SkipReviewTransaction) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text("Skip For Now", fontWeight = FontWeight.SemiBold)
    }
}
