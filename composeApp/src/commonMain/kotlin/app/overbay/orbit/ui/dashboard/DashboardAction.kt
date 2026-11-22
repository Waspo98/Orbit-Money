package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.model.TransactionRule

sealed interface DashboardAction {
    data object Refresh : DashboardAction
    data object PreviousMonth : DashboardAction
    data object NextMonth : DashboardAction
    data object OpenCustomizeSheet : DashboardAction
    data object CloseCustomizeSheet : DashboardAction
    data class ToggleCardVisibility(val cardType: DashboardCardType) : DashboardAction
    data class SetCardWideSpan(val cardType: DashboardCardType, val wideSpan: Int) : DashboardAction
    data class MoveCard(val fromIndex: Int, val toIndex: Int) : DashboardAction
    data class ReorderCards(val orderedTypes: List<DashboardCardType>) : DashboardAction
    data object ResetCardsToDefault : DashboardAction
    data object DismissEmptyWorkspaceCard : DashboardAction
    data class MarkBillPaid(val upcomingItemId: Long, val dueDateIso: String) : DashboardAction
    data class HideBiggestTransaction(val transactionId: Long) : DashboardAction
    data object ResetHiddenBiggestTransactions : DashboardAction
    data class SetGoalFocus(val goalId: Long) : DashboardAction
    data class SetRetirementAge(val age: Int) : DashboardAction
    data class SetRetirementPreset(val presetKey: String) : DashboardAction
    data object OpenCategorizeReview : DashboardAction
    data object CloseCategorizeReview : DashboardAction
    data object ApproveReviewTransaction : DashboardAction
    data object RejectReviewTransaction : DashboardAction
    data object CloseReviewCategoryPicker : DashboardAction
    data class RequestConfirmReviewCategory(val categoryId: Long) : DashboardAction
    data object CancelConfirmReviewCategory : DashboardAction
    data object ConfirmReviewCategory : DashboardAction
    data object SkipReviewTransaction : DashboardAction
    data object GoBackReviewTransaction : DashboardAction
    data object LoadMoreReviewTransactions : DashboardAction
    data object StartFunReviewMode : DashboardAction
    data object OpenReviewRuleCreator : DashboardAction
    data object CloseReviewRuleCreator : DashboardAction
    data class SaveReviewRule(val rule: TransactionRule) : DashboardAction

    // Recent Transaction Quick Actions & Overlays
    data class ToggleExpandRecentTransaction(val transactionId: Long) : DashboardAction
    data class OpenRecentCategoryPicker(val transactionId: Long) : DashboardAction
    data object CloseRecentCategoryPicker : DashboardAction
    data class SetRecentTransactionCategory(val transactionId: Long, val categoryId: Long?) : DashboardAction
    data class ToggleTransactionTransfer(val transactionId: Long, val isTransfer: Boolean) : DashboardAction
    data class ToggleTransactionIgnored(val transactionId: Long, val isIgnored: Boolean) : DashboardAction
    data class ToggleTransactionMha(val transactionId: Long, val isEligible: Boolean) : DashboardAction
    data class RequestRecentTransactionConfirm(
        val transactionId: Long,
        val merchant: String,
        val kind: RecentTransactionConfirmKind
    ) : DashboardAction
    data object DismissRecentTransactionConfirm : DashboardAction
    data object ConfirmRecentTransactionAction : DashboardAction
    data object DismissRecentTransactionError : DashboardAction
    data class ReportMerchantLogoStatus(val merchantKey: String, val status: String) : DashboardAction
}
