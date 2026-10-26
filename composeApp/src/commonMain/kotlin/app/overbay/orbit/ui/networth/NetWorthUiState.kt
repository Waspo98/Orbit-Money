package app.overbay.orbit.ui.networth

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.MonthlyNetWorthPoint
import app.overbay.orbit.domain.NetWorthSnapshot

enum class NetWorthPeriod(val label: String, val monthsCount: Int) {
    ONE_MONTH("1M", 1),
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    ALL("ALL", 24)
}

data class NetWorthUiState(
    val snapshot: NetWorthSnapshot = NetWorthSnapshot(
        cashCents = 0L,
        investmentsCents = 0L,
        realEstateEquityCents = 0L,
        creditDebtCents = 0L,
        loanDebtCents = 0L,
        otherCents = 0L,
        totalAssetsCents = 0L,
        totalLiabilitiesCents = 0L,
        netWorthCents = 0L
    ),
    val allHistoryPoints: List<MonthlyNetWorthPoint> = emptyList(),
    val filteredPoints: List<MonthlyNetWorthPoint> = emptyList(),
    val selectedPeriod: NetWorthPeriod = NetWorthPeriod.ONE_YEAR,
    val scrubbedPoint: MonthlyNetWorthPoint? = null,
    val assetAccounts: List<Account> = emptyList(),
    val liabilityAccounts: List<Account> = emptyList(),
    val isLoading: Boolean = false
) {
    val activePoint: MonthlyNetWorthPoint?
        get() = scrubbedPoint ?: filteredPoints.lastOrNull()
}
