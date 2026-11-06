package app.overbay.orbit.ui.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MonthlyNetWorthPoint
import app.overbay.orbit.domain.NetWorthEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs

class NetWorthViewModel(
    private val repository: OrbitRepository,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _selectedHorizon = MutableStateFlow(NetWorthHorizon.TWO_YEARS)
    private val _scrubbedPoint = MutableStateFlow<MonthlyNetWorthPoint?>(null)

    private val baseStateFlow = combine(
        repository.accounts,
        repository.transactions,
        repository.snapshot,
        _selectedHorizon
    ) { accounts, transactions, snapshot, horizon ->
        val activeAccounts = accounts.filterNot { it.isArchived }
        val currentSnapshot = NetWorthEngine.computeCurrentSnapshot(activeAccounts)

        val latestMonth = LocalDateUtils.currentYearMonth()
        val engineMonths = if (horizon == NetWorthHorizon.ALL_TIME) {
            Int.MAX_VALUE
        } else {
            maxOf(horizon.monthsCount, 13)
        }
        val allHistory = NetWorthEngine.reconstructHistory(
            accounts = activeAccounts,
            transactions = transactions,
            balanceRecords = snapshot.balanceRecords,
            monthsCount = engineMonths,
            latestMonth = latestMonth
        )

        val filtered = if (horizon == NetWorthHorizon.ALL_TIME) {
            allHistory
        } else {
            allHistory.takeLast(horizon.monthsCount)
        }

        val assetAccounts = activeAccounts.filter { it.type.isAsset }
        val liabilityAccounts = activeAccounts.filter { it.type.isLiability }

        val thisMonthDeltaCents = allHistory.lastOrNull()?.deltaFromPreviousCents ?: 0L

        val currentYear = latestMonth.take(4)
        val priorYearDecMonth = "${(currentYear.toIntOrNull() ?: 2026) - 1}-12"
        val ytdBaselinePoint = allHistory.find { it.month == priorYearDecMonth }
            ?: allHistory.firstOrNull { it.month.startsWith(currentYear) }
            ?: allHistory.firstOrNull()
        val ytdDeltaCents = if (ytdBaselinePoint != null) {
            currentSnapshot.netWorthCents - ytdBaselinePoint.snapshot.netWorthCents
        } else {
            thisMonthDeltaCents
        }

        val totalCombinedBalance = currentSnapshot.totalAssetsCents + currentSnapshot.totalLiabilitiesCents
        val debtSharePercent = if (totalCombinedBalance > 0L) {
            (currentSnapshot.totalLiabilitiesCents.toDouble() / totalCombinedBalance.toDouble()) * 100.0
        } else 0.0

        val largestAsset = assetAccounts.maxByOrNull {
            NetWorthEngine.accountContributionCents(it, it.currentBalanceCents)
        }
        val largestLiability = liabilityAccounts.maxByOrNull {
            abs(it.currentBalanceCents)
        }
        val trackedAccountCount = activeAccounts.size

        val topContributingAccounts = activeAccounts.map { acct ->
            val signedContribution = NetWorthEngine.accountContributionCents(acct, acct.currentBalanceCents)
            val absContribution = abs(signedContribution)
            val impactPct = if (totalCombinedBalance > 0L) {
                (absContribution.toDouble() / totalCombinedBalance.toDouble()) * 100.0
            } else 0.0
            AccountContributionItem(
                account = acct,
                absoluteContributionCents = absContribution,
                signedContributionCents = signedContribution,
                impactPercent = impactPct
            )
        }.sortedByDescending { it.absoluteContributionCents }.take(8)

        NetWorthUiState(
            snapshot = currentSnapshot,
            allHistoryPoints = allHistory,
            filteredPoints = filtered,
            selectedHorizon = horizon,
            scrubbedPoint = null,
            accounts = activeAccounts,
            assetAccounts = assetAccounts,
            liabilityAccounts = liabilityAccounts,
            thisMonthDeltaCents = thisMonthDeltaCents,
            ytdDeltaCents = ytdDeltaCents,
            debtSharePercent = debtSharePercent,
            largestAsset = largestAsset,
            largestLiability = largestLiability,
            trackedAccountCount = trackedAccountCount,
            topContributingAccounts = topContributingAccounts,
            isLoading = false
        )
    }.flowOn(computeDispatcher)

    val uiState: StateFlow<NetWorthUiState> = combine(
        baseStateFlow,
        _scrubbedPoint
    ) { baseState, scrubbed ->
        baseState.copy(scrubbedPoint = scrubbed)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetWorthUiState(isLoading = true)
    )

    fun setHorizon(horizon: NetWorthHorizon) {
        _selectedHorizon.value = horizon
        _scrubbedPoint.value = null
    }

    fun setPeriod(period: NetWorthHorizon) = setHorizon(period)

    fun selectScrubPoint(point: MonthlyNetWorthPoint?) {
        _scrubbedPoint.value = point
    }
}
