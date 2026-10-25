package app.overbay.orbit.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.BudgetEngine
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.NetWorthEngine
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
 */
class DashboardViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val currentMonth = LocalDateUtils.currentYearMonth()
    private val _selectedMonth = MutableStateFlow(currentMonth)
    private val _isRefreshing = MutableStateFlow(false)
    private val _isCustomizing = MutableStateFlow(false)
    private val _syncErrorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.snapshot,
        _selectedMonth,
        _isRefreshing,
        _isCustomizing,
        _syncErrorMessage
    ) { snapshot, selectedMonth, isRefreshing, isCustomizing, syncError ->
        buildDashboardUiState(
            snapshot = snapshot,
            selectedMonth = selectedMonth,
            currentMonth = currentMonth,
            isRefreshing = isRefreshing,
            isCustomizing = isCustomizing,
            syncError = syncError
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

    fun toggleCardVisibility(type: DashboardCardType) {
        viewModelScope.launch {
            val widgets = repository.preferences.value.dashboardWidgets.toMutableList()
            val index = widgets.indexOfFirst { it.widgetId.equals(type.widgetId, ignoreCase = true) }
            if (index >= 0) {
                val current = widgets[index]
                widgets[index] = current.copy(isVisible = !current.isVisible)
            } else {
                widgets.add(DashboardWidgetConfig(type.widgetId, type.defaultTitle, isVisible = false, sortOrder = widgets.size))
            }
            val updated = repository.preferences.value.copy(dashboardWidgets = widgets)
            repository.updatePreferences(updated)
        }
    }

    fun toggleCardVisibility(type: DashboardCardType, isVisible: Boolean) {
        viewModelScope.launch {
            val widgets = repository.preferences.value.dashboardWidgets.toMutableList()
            val index = widgets.indexOfFirst { it.widgetId.equals(type.widgetId, ignoreCase = true) }
            if (index >= 0) {
                widgets[index] = widgets[index].copy(isVisible = isVisible)
            } else {
                widgets.add(DashboardWidgetConfig(type.widgetId, type.defaultTitle, isVisible = isVisible, sortOrder = widgets.size))
            }
            val updated = repository.preferences.value.copy(dashboardWidgets = widgets)
            repository.updatePreferences(updated)
        }
    }

    fun moveCard(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = repository.preferences.value.dashboardWidgets.sortedBy { it.sortOrder }.toMutableList()
            if (fromIndex in current.indices && toIndex in current.indices) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                val reordered = current.mapIndexed { idx, config -> config.copy(sortOrder = idx) }
                val updated = repository.preferences.value.copy(dashboardWidgets = reordered)
                repository.updatePreferences(updated)
            }
        }
    }

    fun moveCardUp(cardType: DashboardCardType) {
        val currentWidgets = repository.preferences.value.dashboardWidgets.sortedBy { it.sortOrder }
        val index = currentWidgets.indexOfFirst { it.widgetId.equals(cardType.widgetId, ignoreCase = true) }
        if (index > 0) {
            moveCard(index, index - 1)
        }
    }

    fun moveCardDown(cardType: DashboardCardType) {
        val currentWidgets = repository.preferences.value.dashboardWidgets.sortedBy { it.sortOrder }
        val index = currentWidgets.indexOfFirst { it.widgetId.equals(cardType.widgetId, ignoreCase = true) }
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
    // Bill Quick Actions
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

    // -------------------------------------------------------------------------
    // Transform Pipeline
    // -------------------------------------------------------------------------

    private fun buildDashboardUiState(
        snapshot: OrbitWorkspaceSnapshot,
        selectedMonth: String,
        currentMonth: String,
        isRefreshing: Boolean,
        isCustomizing: Boolean,
        syncError: String?
    ): DashboardUiState {
        val todayIso = LocalDateUtils.todayIsoDate()
        val todayPlus14 = LocalDateUtils.parseIsoDate(todayIso)?.let {
            LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(it, 14))
        } ?: todayIso

        // 1. Cashflow Hero
        val activeTxns = snapshot.transactions.filter { !it.effectiveIsIgnored && !it.effectiveIsTransfer }
        val monthTxns = activeTxns.filter { it.effectiveDate.take(7) == selectedMonth }
        var incomeCents: Cents = 0L
        var expenseCents: Cents = 0L
        for (t in monthTxns) {
            val amt = t.effectiveAmountCents
            if (amt > 0) incomeCents += amt else if (amt < 0) expenseCents += -amt
        }
        val netCents = incomeCents - expenseCents
        val savingsRate = if (incomeCents > 0L) (netCents.toDouble() / incomeCents.toDouble()) * 100.0 else 0.0

        val priorMonth = LocalDateUtils.addMonthsToYearMonth(selectedMonth, -1)
        val priorTxns = activeTxns.filter { it.effectiveDate.take(7) == priorMonth }
        var priorIncome: Cents = 0L
        var priorExpense: Cents = 0L
        for (t in priorTxns) {
            val amt = t.effectiveAmountCents
            if (amt > 0) priorIncome += amt else if (amt < 0) priorExpense += -amt
        }
        val priorNet = priorIncome - priorExpense
        val netDeltaPct = if (priorNet != 0L) {
            ((netCents - priorNet).toDouble() / abs(priorNet).toDouble()) * 100.0
        } else null

        val cashflowState = CashflowHeroState(
            selectedMonth = selectedMonth,
            monthLabel = LocalDateUtils.formatMonthLabel(selectedMonth),
            isCurrentMonth = selectedMonth == currentMonth,
            canGoNextMonth = selectedMonth < currentMonth,
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            netCents = netCents,
            savingsRatePercent = savingsRate,
            priorMonthNetCents = priorNet,
            netDeltaPercent = netDeltaPct
        )

        // 2. Budget Pulse
        val budgetOverview = BudgetEngine.calculateMonthlyOverview(
            month = selectedMonth,
            categories = snapshot.categories,
            budgets = snapshot.budgets,
            transactions = snapshot.transactions,
            todayDate = todayIso
        )
        val budgetPulseState = BudgetPulseState(
            totalBudgetedCents = budgetOverview.totalBudgetedCents,
            totalSpentCents = budgetOverview.totalSpentCents,
            remainingCents = budgetOverview.totalRemainingCents,
            utilizationRatio = budgetOverview.utilizationRatio,
            dailySafeSpendCents = budgetOverview.dailySafeSpend.dailyCents,
            dailySafeSpendDetail = budgetOverview.dailySafeSpend.detail,
            dailySafeSpendTone = budgetOverview.dailySafeSpend.tone,
            paceStatus = if (budgetOverview.totalBudgetedCents <= 0L) app.overbay.orbit.data.model.BudgetPaceStatus.NO_BUDGET
            else if (budgetOverview.totalSpentCents > budgetOverview.totalBudgetedCents) app.overbay.orbit.data.model.BudgetPaceStatus.OVER_BUDGET
            else if (budgetOverview.utilizationRatio >= 0.85) app.overbay.orbit.data.model.BudgetPaceStatus.WATCH
            else app.overbay.orbit.data.model.BudgetPaceStatus.ON_TRACK,
            hasBudgets = budgetOverview.totalBudgetedCents > 0L,
            topBudgets = budgetOverview.budgeted.take(3)
        )

        // 3. Upcoming Bills (within 14 days)
        val activeUpcoming = snapshot.upcomingItems.filter { it.status == UpcomingStatus.ACTIVE }
        val categoryById = snapshot.categories.associateBy { it.id }
        val upcomingOccurrences = mutableListOf<UpcomingBillItem>()

        for (item in activeUpcoming) {
            val dates = UpcomingEngine.generateDates(item, todayIso, todayPlus14)
            val effectiveDates = if (dates.isNotEmpty()) dates else listOf(item.nextDate).filter { it in todayIso..todayPlus14 }
            val cat = item.categoryId?.let { categoryById[it] }
            val isExpense = item.direction.lowercase() != "income"

            for (date in effectiveDates) {
                val days = LocalDateUtils.daysBetween(todayIso, date) ?: 0
                val dueLabel = when {
                    days == 0 -> "Today"
                    days == 1 -> "Tomorrow"
                    days < 0 -> "${-days}d overdue"
                    else -> "In $days days"
                }
                upcomingOccurrences.add(
                    UpcomingBillItem(
                        upcomingItemId = item.id,
                        name = item.name,
                        merchant = item.merchant,
                        amountCents = item.amountCents,
                        isExpense = isExpense,
                        dueDateIso = date,
                        dueLabel = dueLabel,
                        daysUntilDue = days,
                        isDueSoon = days in 0..3,
                        isOverdue = days < 0,
                        categoryName = cat?.name,
                        categoryColor = cat?.color,
                        categoryIcon = cat?.icon
                    )
                )
            }
        }
        val top3Bills = upcomingOccurrences.sortedBy { it.dueDateIso }.take(3)
        val upcomingState = UpcomingBillsState(
            upcomingCount = upcomingOccurrences.size,
            totalUpcomingOutflowCents = top3Bills.filter { it.isExpense }.sumOf { it.amountCents },
            totalUpcomingInflowCents = top3Bills.filter { !it.isExpense }.sumOf { it.amountCents },
            items = top3Bills
        )

        // 4. Recent Transactions (last 5)
        val accountById = snapshot.accounts.associateBy { it.id }
        val recentList = snapshot.transactions
            .filter { !it.effectiveIsIgnored }
            .sortedWith(compareByDescending<Transaction> { it.effectiveDate }.thenByDescending { it.id })
            .take(5)
            .map { txn ->
                val cat = txn.effectiveCategoryId?.let { categoryById[it] }
                val acct = accountById[txn.accountId]
                val dateDisplay = LocalDateUtils.parseIsoDate(txn.effectiveDate)?.let {
                    LocalDateUtils.formatRelativeOrDisplayDate(it)
                } ?: txn.effectiveDate

                DashboardRecentTransactionItem(
                    id = txn.id,
                    merchant = txn.effectiveMerchant,
                    amountCents = txn.effectiveAmountCents,
                    isExpense = txn.isExpense,
                    formattedAmount = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                    dateIso = txn.effectiveDate,
                    dateDisplay = dateDisplay,
                    categoryName = cat?.name ?: txn.categoryName ?: "Uncategorized",
                    categoryColor = cat?.color ?: txn.categoryColor,
                    categoryIcon = cat?.icon ?: txn.categoryIcon,
                    accountName = acct?.name ?: txn.accountName,
                    isPending = txn.isPending
                )
            }
        val recentState = RecentTransactionsState(transactions = recentList)

        // 5. Accounts Overview
        val netWorthSnap = NetWorthEngine.computeCurrentSnapshot(snapshot.accounts)
        val accountsState = AccountsOverviewState(
            totalNetWorthCents = netWorthSnap.netWorthCents,
            totalAssetsCents = netWorthSnap.totalAssetsCents,
            totalLiabilitiesCents = netWorthSnap.totalLiabilitiesCents,
            liquidCents = netWorthSnap.cashCents,
            investmentsCents = netWorthSnap.investmentsCents,
            propertyCents = netWorthSnap.realEstateEquityCents,
            debtCents = netWorthSnap.totalLiabilitiesCents,
            accountCount = snapshot.accounts.count { !it.isArchived }
        )

        // 6. Savings Goals
        val activeGoals = snapshot.goals.filter { !it.isCompleted }
        val completedGoals = snapshot.goals.filter { it.isCompleted }
        val totalSaved = snapshot.goals.sumOf { it.currentAmountCents }
        val totalTarget = snapshot.goals.sumOf { it.targetAmountCents }
        val overallRatio = if (totalTarget > 0L) (totalSaved.toDouble() / totalTarget.toDouble()).coerceIn(0.0, 1.0) else 0.0

        val goalsState = GoalsProgressState(
            activeGoalsCount = activeGoals.size,
            completedGoalsCount = completedGoals.size,
            totalSavedCents = totalSaved,
            totalTargetCents = totalTarget,
            overallProgressRatio = overallRatio,
            goals = snapshot.goals.sortedBy { it.sortOrder }.take(3).map { g ->
                DashboardGoalItem(
                    id = g.id,
                    name = g.name,
                    currentAmountCents = g.currentAmountCents,
                    targetAmountCents = g.targetAmountCents,
                    progressRatio = g.progressRatio,
                    remainingAmountCents = g.remainingAmountCents,
                    isCompleted = g.isCompleted,
                    targetDate = g.targetDate
                )
            }
        )

        // 7. Cards Display & Customization
        val rawWidgets = snapshot.preferences.dashboardWidgets.ifEmpty {
            OrbitPreferences.defaultDashboardWidgets()
        }
        val cardConfigs = DashboardCardType.entries.map { type ->
            val existing = rawWidgets.find { it.widgetId.equals(type.widgetId, ignoreCase = true) }
            DashboardCardConfigItem(
                type = type,
                title = existing?.title ?: type.defaultTitle,
                isVisible = existing?.isVisible ?: true,
                sortOrder = existing?.sortOrder ?: type.defaultSortOrder
            )
        }.sortedBy { it.sortOrder }

        val visibleCards = cardConfigs.filter { it.isVisible }.map { it.type }

        return DashboardUiState(
            isLoading = false,
            isRefreshing = isRefreshing,
            selectedMonth = selectedMonth,
            cashflow = cashflowState,
            budgetPulse = budgetPulseState,
            upcoming = upcomingState,
            recentTransactions = recentState,
            accounts = accountsState,
            goals = goalsState,
            visibleCards = visibleCards,
            customization = DashboardCustomizationState(
                isCustomizing = isCustomizing,
                cardConfigs = cardConfigs
            ),
            syncErrorMessage = syncError
        )
    }
}
