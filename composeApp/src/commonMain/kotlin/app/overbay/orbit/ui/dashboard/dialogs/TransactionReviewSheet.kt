package app.overbay.orbit.ui.dashboard.dialogs

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.domain.ReviewQueueCandidate
import app.overbay.orbit.domain.RuleMatcherEngine
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitConfirmationDialog
import app.overbay.orbit.ui.components.OrbitModalSheet
import app.overbay.orbit.ui.components.OrbitSheetHeader
import app.overbay.orbit.ui.dashboard.CategorizeReviewState
import app.overbay.orbit.ui.dashboard.DashboardAction
import app.overbay.orbit.ui.rules.sheets.RuleEditorSheet
import app.overbay.orbit.util.LocalOrbitHaptics

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

            when {
                state.allDone -> ReviewEmptyStateContent(
                    badgeText = "!",
                    title = "PHEW all transactions are categorized for now.",
                    subtitle = "Want to go through more transactions just for fun, ya sicko?",
                    primaryButtonText = "Yes I Need The Dopamine",
                    secondaryButtonText = "No Thanks, I Have Self-Control",
                    onPrimaryClick = { onAction(DashboardAction.StartFunReviewMode) },
                    onSecondaryClick = { onAction(DashboardAction.CloseCategorizeReview) }
                )

                state.isBatchComplete -> ReviewEmptyStateContent(
                    badgeText = "10",
                    title = "Ten down.",
                    subtitle = "Want another stack, or are we calling that a responsible little victory?",
                    primaryButtonText = "View More Transactions",
                    secondaryButtonText = "Done For Now",
                    onPrimaryClick = { onAction(DashboardAction.LoadMoreReviewTransactions) },
                    onSecondaryClick = { onAction(DashboardAction.CloseCategorizeReview) }
                )

                currentTxn != null && state.isCategoryPickerOpen -> CategoryReviewPickerContent(
                    txn = currentTxn,
                    categories = state.categories,
                    onChooseCategory = { catId -> onAction(DashboardAction.RequestConfirmReviewCategory(catId)) },
                    onBack = { onAction(DashboardAction.CloseReviewCategoryPicker) }
                )

                currentTxn != null -> ReviewSwipeStage(
                    currentTxn = currentTxn,
                    currentIndex = state.currentIndex,
                    batchReviewed = state.batchReviewed,
                    category = currentTxn.categoryId?.let { categoryById[it] },
                    onAction = onAction
                )
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val canGoBack = currentIndex > 0

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
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.confirm()
                        onAction(DashboardAction.ApproveReviewTransaction)
                        false
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.tick()
                        onAction(DashboardAction.RejectReviewTransaction)
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            }
        )
        val cardTone = when (dismissState.targetValue) {
            SwipeToDismissBoxValue.StartToEnd -> "yes"
            SwipeToDismissBoxValue.EndToStart -> "no"
            SwipeToDismissBoxValue.Settled -> ""
        }

        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            backgroundContent = {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Change", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text("Confirm", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                .clickable { haptics.tick(); onAction(DashboardAction.RejectReviewTransaction) }
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
                .clickable { haptics.confirm(); onAction(DashboardAction.ApproveReviewTransaction) }
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
