package app.overbay.orbit.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.ReviewQueueEngine
import app.overbay.orbit.domain.RuleMatcherEngine
import app.overbay.orbit.domain.UpcomingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ViewModel orchestrating data transformations and user mutations for the M3E Dashboard.
 * Keeps under the 600-line ceiling by delegating snapshot transformation to [DashboardDataTransform].
 */
class DashboardViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val currentMonth = LocalDateUtils.currentYearMonth()
    private val _selectedMonth = MutableStateFlow(currentMonth)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isCustomizing = MutableStateFlow(false)
    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    private val _hiddenBiggestIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _focusedGoalId = MutableStateFlow<Long?>(null)
    private val _retirementAge = MutableStateFlow(67)
    private val _retirementPreset = MutableStateFlow("balanced")
    private val _reviewState = MutableStateFlow(CategorizeReviewState())

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            repository.snapshot,
            _selectedMonth,
            _isRefreshing,
            _isCustomizing,
            _syncErrorMessage
        ) { snapshot, selectedMonth, isRefreshing, isCustomizing, syncError ->
            TransformInput1(snapshot, selectedMonth, isRefreshing, isCustomizing, syncError)
        },
        combine(
            _hiddenBiggestIds,
            _focusedGoalId,
            _retirementAge,
            _retirementPreset,
            _reviewState
        ) { hiddenBiggestIds, focusedGoalId, retirementAge, retirementPreset, reviewState ->
            TransformInput2(hiddenBiggestIds, focusedGoalId, retirementAge, retirementPreset, reviewState)
        }
    ) { input1, input2 ->
        DashboardDataTransform.buildDashboardUiState(
            snapshot = input1.snapshot,
            selectedMonth = input1.selectedMonth,
            currentMonth = currentMonth,
            isRefreshing = input1.isRefreshing,
            isCustomizing = input1.isCustomizing,
            syncError = input1.syncError,
            hiddenBiggestIds = input2.hiddenBiggestIds,
            focusedGoalId = input2.focusedGoalId,
            retirementAge = input2.retirementAge,
            retirementPreset = input2.retirementPreset,
            categorizeReview = input2.reviewState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.initial()
    )

    // -------------------------------------------------------------------------
    // Period Navigation
    // -------------------------------------------------------------------------

    fun onPreviousMonth() {
        _selectedMonth.value = LocalDateUtils.addMonthsToYearMonth(_selectedMonth.value, -1)
    }

    fun onNextMonth() {
        val next = LocalDateUtils.addMonthsToYearMonth(_selectedMonth.value, 1)
        if (next <= currentMonth) {
            _selectedMonth.value = next
        }
    }

    fun onResetToCurrentMonth() {
        _selectedMonth.value = currentMonth
    }

    // -------------------------------------------------------------------------
    // Sync & Refresh
    // -------------------------------------------------------------------------

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncErrorMessage.value = null
            when (val result = repository.sync(force = true)) {
                is SyncResult.Error -> _syncErrorMessage.value = result.message
                else -> {}
            }
            _isRefreshing.value = false
        }
    }

    fun dismissSyncError() {
        _syncErrorMessage.value = null
    }

    // -------------------------------------------------------------------------
    // Card Customization Actions
    // -------------------------------------------------------------------------

    fun openCustomization() {
        _isCustomizing.value = true
    }

    fun closeCustomization() {
        _isCustomizing.value = false
    }

    private fun normalizedWidgets(): MutableList<DashboardWidgetConfig> {
        val raw = repository.preferences.value.dashboardWidgets.ifEmpty {
            OrbitPreferences.defaultDashboardWidgets()
        }
        return DashboardCardType.entries.map { type ->
            val existing = raw.find {
                it.widgetId.equals(type.widgetId, ignoreCase = true) ||
                    DashboardCardType.fromWidgetId(it.widgetId) == type
            }
            DashboardWidgetConfig(
                widgetId = type.widgetId,
                title = existing?.title ?: type.defaultTitle,
                isVisible = existing?.isVisible ?: true,
                sortOrder = existing?.sortOrder ?: type.defaultSortOrder,
                wideSpan = (existing?.wideSpan ?: type.defaultWideSpan).let { if (it == 2) 2 else 1 }
            )
        }.sortedBy { it.sortOrder }
            .mapIndexed { idx, item -> item.copy(sortOrder = idx) }
            .toMutableList()
    }

    fun toggleCardVisibility(type: DashboardCardType, isVisible: Boolean? = null) {
        viewModelScope.launch {
            val widgets = normalizedWidgets()
            val index = widgets.indexOfFirst { DashboardCardType.fromWidgetId(it.widgetId) == type }
            if (index >= 0) {
                val current = widgets[index]
                val newVisibility = isVisible ?: !current.isVisible
                widgets[index] = current.copy(isVisible = newVisibility)
            }
            val updated = repository.preferences.value.copy(dashboardWidgets = widgets)
            repository.updatePreferences(updated)
        }
    }

    fun setCardWideSpan(type: DashboardCardType, wideSpan: Int) {
        viewModelScope.launch {
            val normalizedSpan = if (wideSpan == 2) 2 else 1
            val widgets = normalizedWidgets()
            val index = widgets.indexOfFirst { DashboardCardType.fromWidgetId(it.widgetId) == type }
            if (index >= 0) {
                widgets[index] = widgets[index].copy(wideSpan = normalizedSpan)
                val updated = repository.preferences.value.copy(dashboardWidgets = widgets)
                repository.updatePreferences(updated)
            }
        }
    }

    fun moveCard(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = normalizedWidgets()
            if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                val reordered = current.mapIndexed { idx, config -> config.copy(sortOrder = idx) }
                val updated = repository.preferences.value.copy(dashboardWidgets = reordered)
                repository.updatePreferences(updated)
            }
        }
    }

    fun moveCardUp(cardType: DashboardCardType) {
        val currentWidgets = normalizedWidgets()
        val index = currentWidgets.indexOfFirst { DashboardCardType.fromWidgetId(it.widgetId) == cardType }
        if (index > 0) {
            moveCard(index, index - 1)
        }
    }

    fun moveCardDown(cardType: DashboardCardType) {
        val currentWidgets = normalizedWidgets()
        val index = currentWidgets.indexOfFirst { DashboardCardType.fromWidgetId(it.widgetId) == cardType }
        if (index >= 0 && index < currentWidgets.lastIndex) {
            moveCard(index, index + 1)
        }
    }

    fun resetCardsToDefault() {
        viewModelScope.launch {
            val updated = repository.preferences.value.copy(
                dashboardWidgets = OrbitPreferences.defaultDashboardWidgets()
            )
            repository.updatePreferences(updated)
        }
    }

    // -------------------------------------------------------------------------
    // Card-Specific Actions
    // -------------------------------------------------------------------------

    fun markUpcomingPaid(upcomingItemId: Long, occurrenceDate: String) {
        viewModelScope.launch {
            val item = repository.upcomingItems.value.find { it.id == upcomingItemId } ?: return@launch
            val isExpense = item.direction.lowercase() != "income"
            val signedAmount = if (isExpense) -abs(item.amountCents) else abs(item.amountCents)

            val paidTxn = Transaction(
                accountId = item.accountId ?: repository.preferences.value.defaultAccountId ?: 1L,
                originalDate = occurrenceDate,
                originalMerchant = item.merchant ?: item.name,
                originalAmountCents = signedAmount,
                originalCategoryId = item.categoryId,
                source = "upcoming_reconcile",
                editSource = EditSource.SYSTEM
            )
            repository.saveTransaction(paidTxn)

            val nextDate = UpcomingEngine.advanceInterval(item, occurrenceDate)
            repository.saveUpcoming(item.copy(nextDate = nextDate))
        }
    }

    fun hideBiggestTransaction(transactionId: Long) {
        _hiddenBiggestIds.value = _hiddenBiggestIds.value + transactionId
    }

    fun resetHiddenBiggestTransactions() {
        _hiddenBiggestIds.value = emptySet()
    }

    fun setGoalFocus(goalId: Long) {
        _focusedGoalId.value = goalId
    }

    fun setRetirementAge(age: Int) {
        _retirementAge.value = age.coerceIn(50, 80)
    }

    fun setRetirementPreset(preset: String) {
        _retirementPreset.value = preset
    }

    fun openCategorizeReview() {
        loadReviewQueue(funMode = false)
    }

    fun closeCategorizeReview() {
        _reviewState.value = CategorizeReviewState(isOpen = false)
    }

    private fun loadReviewQueue(
        funMode: Boolean,
        handledOverride: Set<Long>? = null
    ) {
        val snap = repository.snapshot.value
        val excluded = handledOverride ?: snap.preferences.handledReviewTransactionIds.toSet()
        val items = ReviewQueueEngine.buildReviewQueue(
            transactions = snap.transactions,
            categories = snap.categories,
            accounts = snap.accounts,
            excludedIds = excluded,
            funMode = funMode,
            limit = 10
        )
        _reviewState.value = CategorizeReviewState(
            isOpen = true,
            funMode = funMode,
            items = items,
            currentIndex = 0,
            batchReviewed = 0,
            allDone = items.isEmpty(),
            isCategoryPickerOpen = false,
            pendingConfirmCategoryId = null,
            ruleDraftTransaction = null
        )
    }

    private suspend fun recordHandledReviewId(transactionId: Long): Set<Long> {
        val currentPrefs = repository.preferences.value
        val existing = currentPrefs.handledReviewTransactionIds
        val updatedIds = if (transactionId in existing) existing else existing + transactionId
        if (updatedIds != existing) {
            repository.updatePreferences(currentPrefs.copy(handledReviewTransactionIds = updatedIds))
        }
        return updatedIds.toSet()
    }

    fun approveReviewTransaction() {
        val state = _reviewState.value
        val current = state.currentTxn ?: return
        viewModelScope.launch {
            recordHandledReviewId(current.id)
        }
        _reviewState.value = state.copy(
            currentIndex = state.currentIndex + 1,
            batchReviewed = state.batchReviewed + 1,
            isCategoryPickerOpen = false,
            pendingConfirmCategoryId = null,
            ruleDraftTransaction = null
        )
    }

    fun rejectReviewTransaction() {
        val state = _reviewState.value
        if (state.currentTxn == null) return
        _reviewState.value = state.copy(
            isCategoryPickerOpen = true,
            pendingConfirmCategoryId = null
        )
    }

    fun closeReviewCategoryPicker() {
        val state = _reviewState.value
        _reviewState.value = state.copy(
            isCategoryPickerOpen = false,
            pendingConfirmCategoryId = null
        )
    }

    fun requestConfirmReviewCategory(categoryId: Long) {
        val state = _reviewState.value
        if (state.currentTxn == null) return
        _reviewState.value = state.copy(pendingConfirmCategoryId = categoryId)
    }

    fun cancelConfirmReviewCategory() {
        _reviewState.value = _reviewState.value.copy(pendingConfirmCategoryId = null)
    }

    fun confirmReviewCategory() {
        val state = _reviewState.value
        val current = state.currentTxn ?: return
        val categoryId = state.pendingConfirmCategoryId ?: return
        applyCategoryAndAdvance(current.id, categoryId)
    }

    fun applyCategory(transactionId: Long, categoryId: Long) {
        applyCategoryAndAdvance(transactionId, categoryId)
    }

    private fun applyCategoryAndAdvance(transactionId: Long, categoryId: Long) {
        val state = _reviewState.value
        val category = repository.categories.value.find { it.id == categoryId }
        val updatedItems = state.items.map { item ->
            if (item.id == transactionId) {
                item.copy(
                    categoryId = categoryId,
                    categoryName = category?.name ?: item.categoryName,
                    categoryColor = category?.color ?: item.categoryColor,
                    categoryIcon = category?.icon ?: item.categoryIcon
                )
            } else {
                item
            }
        }
        val shouldAdvance = state.currentTxn?.id == transactionId
        _reviewState.value = state.copy(
            items = updatedItems,
            currentIndex = if (shouldAdvance) state.currentIndex + 1 else state.currentIndex,
            batchReviewed = if (shouldAdvance) state.batchReviewed + 1 else state.batchReviewed,
            isCategoryPickerOpen = false,
            pendingConfirmCategoryId = null
        )
        viewModelScope.launch {
            repository.editTransaction(id = transactionId, categoryId = categoryId)
            recordHandledReviewId(transactionId)
        }
    }

    fun skipReviewTransaction() {
        approveReviewTransaction()
    }

    fun goBackReviewTransaction() {
        val state = _reviewState.value
        if (state.currentIndex <= 0) return
        _reviewState.value = state.copy(
            currentIndex = (state.currentIndex - 1).coerceAtLeast(0),
            batchReviewed = (state.batchReviewed - 1).coerceAtLeast(0),
            isCategoryPickerOpen = false,
            pendingConfirmCategoryId = null
        )
    }

    fun loadMoreReviewTransactions() {
        loadReviewQueue(funMode = _reviewState.value.funMode)
    }

    fun startFunReviewMode() {
        loadReviewQueue(funMode = true)
    }

    fun openReviewRuleCreator() {
        val state = _reviewState.value
        val current = state.currentTxn ?: return
        _reviewState.value = state.copy(ruleDraftTransaction = current)
    }

    fun closeReviewRuleCreator() {
        _reviewState.value = _reviewState.value.copy(ruleDraftTransaction = null)
    }

    fun saveReviewRule(rule: TransactionRule) {
        val state = _reviewState.value
        val current = state.currentTxn
        _reviewState.value = state.copy(
            currentIndex = if (current != null) state.currentIndex + 1 else state.currentIndex,
            batchReviewed = if (current != null) state.batchReviewed + 1 else state.batchReviewed,
            ruleDraftTransaction = null,
            isCategoryPickerOpen = false,
            pendingConfirmCategoryId = null
        )
        viewModelScope.launch {
            repository.saveRule(rule)
            repository.runRulesOnAllTransactions()
            if (current != null) {
                recordHandledReviewId(current.id)
            }
        }
    }

    fun previewRuleMatchCount(conditions: List<RuleCondition>): Int {
        if (conditions.isEmpty()) return 0
        return RuleMatcherEngine.countMatches(repository.transactions.value, conditions)
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            DashboardAction.Refresh -> refresh()
            DashboardAction.PreviousMonth -> onPreviousMonth()
            DashboardAction.NextMonth -> onNextMonth()
            DashboardAction.OpenCustomizeSheet -> openCustomization()
            DashboardAction.CloseCustomizeSheet -> closeCustomization()
            is DashboardAction.ToggleCardVisibility -> toggleCardVisibility(action.cardType)
            is DashboardAction.SetCardWideSpan -> setCardWideSpan(action.cardType, action.wideSpan)
            is DashboardAction.MoveCard -> moveCard(action.fromIndex, action.toIndex)
            is DashboardAction.MoveCardUp -> moveCardUp(action.cardType)
            is DashboardAction.MoveCardDown -> moveCardDown(action.cardType)
            DashboardAction.ResetCardsToDefault -> resetCardsToDefault()
            is DashboardAction.MarkBillPaid -> markUpcomingPaid(action.upcomingItemId, action.dueDateIso)
            is DashboardAction.HideBiggestTransaction -> hideBiggestTransaction(action.transactionId)
            DashboardAction.ResetHiddenBiggestTransactions -> resetHiddenBiggestTransactions()
            is DashboardAction.SetGoalFocus -> setGoalFocus(action.goalId)
            is DashboardAction.SetRetirementAge -> setRetirementAge(action.age)
            is DashboardAction.SetRetirementPreset -> setRetirementPreset(action.presetKey)
            DashboardAction.OpenCategorizeReview -> openCategorizeReview()
            DashboardAction.CloseCategorizeReview -> closeCategorizeReview()
            is DashboardAction.ApplyCategory -> applyCategory(action.transactionId, action.categoryId)
            DashboardAction.ApproveReviewTransaction -> approveReviewTransaction()
            DashboardAction.RejectReviewTransaction -> rejectReviewTransaction()
            DashboardAction.CloseReviewCategoryPicker -> closeReviewCategoryPicker()
            is DashboardAction.RequestConfirmReviewCategory -> requestConfirmReviewCategory(action.categoryId)
            DashboardAction.CancelConfirmReviewCategory -> cancelConfirmReviewCategory()
            DashboardAction.ConfirmReviewCategory -> confirmReviewCategory()
            DashboardAction.SkipReviewTransaction -> skipReviewTransaction()
            DashboardAction.GoBackReviewTransaction -> goBackReviewTransaction()
            DashboardAction.LoadMoreReviewTransactions -> loadMoreReviewTransactions()
            DashboardAction.StartFunReviewMode -> startFunReviewMode()
            DashboardAction.OpenReviewRuleCreator -> openReviewRuleCreator()
            DashboardAction.CloseReviewRuleCreator -> closeReviewRuleCreator()
            is DashboardAction.SaveReviewRule -> saveReviewRule(action.rule)
        }
    }

    private data class TransformInput1(
        val snapshot: app.overbay.orbit.data.model.OrbitWorkspaceSnapshot,
        val selectedMonth: String,
        val isRefreshing: Boolean,
        val isCustomizing: Boolean,
        val syncError: String?
    )

    private data class TransformInput2(
        val hiddenBiggestIds: Set<Long>,
        val focusedGoalId: Long?,
        val retirementAge: Int,
        val retirementPreset: String,
        val reviewState: CategorizeReviewState
    )
}
