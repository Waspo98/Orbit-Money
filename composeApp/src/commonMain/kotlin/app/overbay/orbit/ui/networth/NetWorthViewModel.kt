package app.overbay.orbit.ui.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MonthlyNetWorthPoint
import app.overbay.orbit.domain.NetWorthEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NetWorthViewModel(
    private val repository: OrbitRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(NetWorthPeriod.ONE_YEAR)
    private val _scrubbedPoint = MutableStateFlow<MonthlyNetWorthPoint?>(null)

    val uiState: StateFlow<NetWorthUiState> = combine(
        repository.accounts,
        repository.transactions,
        _selectedPeriod,
        _scrubbedPoint
    ) { accounts, transactions, period, scrubbed ->
        val activeAccounts = accounts.filterNot { it.isArchived }
        val snapshot = NetWorthEngine.computeCurrentSnapshot(activeAccounts)

        val latestMonth = LocalDateUtils.currentYearMonth()
        val allHistory = NetWorthEngine.reconstructHistory(
            accounts = activeAccounts,
            transactions = transactions,
            monthsCount = 24,
            latestMonth = latestMonth
        )

        val countToTake = when (period) {
            NetWorthPeriod.ONE_MONTH -> 2
            NetWorthPeriod.THREE_MONTHS -> 3
            NetWorthPeriod.SIX_MONTHS -> 6
            NetWorthPeriod.ONE_YEAR -> 12
            NetWorthPeriod.ALL -> 24
        }
        val filtered = allHistory.takeLast(countToTake)

        val assetAccounts = activeAccounts.filter { it.type.isAsset }
        val liabilityAccounts = activeAccounts.filter { it.type.isLiability }

        NetWorthUiState(
            snapshot = snapshot,
            allHistoryPoints = allHistory,
            filteredPoints = filtered,
            selectedPeriod = period,
            scrubbedPoint = scrubbed,
            assetAccounts = assetAccounts,
            liabilityAccounts = liabilityAccounts,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetWorthUiState(isLoading = true)
    )

    fun setPeriod(period: NetWorthPeriod) {
        _selectedPeriod.value = period
        _scrubbedPoint.value = null
    }

    fun selectScrubPoint(point: MonthlyNetWorthPoint?) {
        _scrubbedPoint.value = point
    }
}
