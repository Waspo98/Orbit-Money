package app.overbay.orbit.ui.networth

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MonthlyNetWorthPoint
import app.overbay.orbit.domain.NetWorthSnapshot

enum class NetWorthHorizon(val monthsCount: Int, val label: String) {
    ONE_YEAR(12, "1Y"),
    TWO_YEARS(24, "2Y"),
    THREE_YEARS(36, "3Y"),
    FIVE_YEARS(60, "5Y"),
    TEN_YEARS(120, "10Y"),
    ALL_TIME(Int.MAX_VALUE, "All");

    companion object {
        val ALL = ALL_TIME
    }
}

typealias NetWorthPeriod = NetWorthHorizon

data class AccountContributionItem(
    val account: Account,
    val absoluteContributionCents: Cents,
    val signedContributionCents: Cents,
    val impactPercent: Double
)

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
    val selectedHorizon: NetWorthHorizon = NetWorthHorizon.TWO_YEARS,
    val scrubbedPoint: MonthlyNetWorthPoint? = null,
    val accounts: List<Account> = emptyList(),
    val assetAccounts: List<Account> = emptyList(),
    val liabilityAccounts: List<Account> = emptyList(),
    val thisMonthDeltaCents: Cents = 0L,
    val ytdDeltaCents: Cents = 0L,
    val debtSharePercent: Double = 0.0,
    val largestAsset: Account? = null,
    val largestLiability: Account? = null,
    val trackedAccountCount: Int = 0,
    val topContributingAccounts: List<AccountContributionItem> = emptyList(),
    val isLoading: Boolean = false
) {
    val selectedPeriod: NetWorthHorizon get() = selectedHorizon

    val activePoint: MonthlyNetWorthPoint?
        get() = scrubbedPoint ?: filteredPoints.lastOrNull()
}
