package app.overbay.orbit.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.LocalDateUtils
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
    private val _isReviewModalOpen = MutableStateFlow(false)

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
            _isReviewModalOpen
        ) { hiddenBiggestIds, focusedGoalId, retirementAge, retirementPreset, isReviewModalOpen ->
            TransformInput2(hiddenBiggestIds, focusedGoalId, retirementAge, retirementPreset, isReviewModalOpen)
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
            isReviewModalOpen = input2.isReviewModalOpen
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
        _isReviewModalOpen.value = true
    }

    fun closeCategorizeReview() {
        _isReviewModalOpen.value = false
    }

    fun applyCategory(transactionId: Long, categoryId: Long) {
        viewModelScope.launch {
            val txn = repository.transactions.value.find { it.id == transactionId } ?: return@launch
            val updated = txn.copy(
                editedCategoryId = categoryId,
                editSource = EditSource.USER
            )
            repository.saveTransaction(updated)
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
        val isReviewModalOpen: Boolean
    )
}
