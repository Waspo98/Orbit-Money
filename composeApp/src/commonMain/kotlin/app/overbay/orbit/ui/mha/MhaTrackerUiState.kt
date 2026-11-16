package app.overbay.orbit.ui.mha

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.MhaYearTaxRecord
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.MhaTaxCalculationResult

enum class MhaLimitingFactor(val displayName: String, val description: String) {
    ACTUAL_EXPENSES(
        displayName = "Actual Expenses Paid",
        description = "Your housing exclusion is currently limited by what you have spent so far this year."
    ),
    DESIGNATION_CAP(
        displayName = "Church Designation Cap",
        description = "You have spent more than was officially designated by your church. Expenses above this cap are taxable."
    ),
    FAIR_MARKET_RENTAL_VALUE(
        displayName = "Fair Market Rental Value",
        description = "Your housing exclusion cannot exceed the fair rental value of your furnished home plus utilities."
    ),
    UNCAPPED_ACTUAL(
        displayName = "Tracking Actual Expenses",
        description = "Set your Church Designation Cap and Fair Rental Value in Settings to enforce the 3-limit IRS exclusion test."
    )
}

typealias MhaBindingLimit = MhaLimitingFactor

enum class MhaTransactionTabFilter(val label: String) {
    ELIGIBLE("Eligible Housing"),
    REVIEW_QUEUE("Review Queue"),
    ALL("All Expenses")
}

enum class MhaScreenTab(val label: String) {
    TRACKER("Tracker"),
    DEFAULTS("Defaults")
}

data class MhaTransactionItem(
    val transaction: Transaction,
    val category: Category?,
    val account: Account?,
    val isEligible: Boolean,
    val isExplicitOverride: Boolean,
    val isOverriddenByUser: Boolean,
    val eligibilityReason: String = ""
) {
    val id: Long get() = transaction.id
    val effectiveDate: String get() = transaction.effectiveDate
    val effectiveMerchant: String get() = transaction.effectiveMerchant
    val effectiveAmountCents: Cents get() = transaction.effectiveAmountCents
    val date: String get() = effectiveDate
    val merchant: String get() = effectiveMerchant
    val amountCents: Cents get() = effectiveAmountCents
}

data class MhaCategoryTotal(
    val categoryId: Long, // -1L represents Uncategorized
    val name: String,
    val emoji: String,
    val colorHex: String?,
    val totalCents: Cents,
    val count: Int
)

data class MhaMonthGroup(
    val yearMonth: String,
    val monthLabel: String,
    val countedTotalCents: Cents,
    val countedCount: Int,
    val items: List<MhaTransactionItem>
)

data class MhaTrackerUiState(
    val selectedYear: Int = 2026,
    val currentYear: Int = 2026,
    val availableYears: List<Int> = listOf(2026, 2025, 2024),
    val actualEligibleHousingSpentCents: Cents = 0L,
    val designatedCapCents: Cents? = null,
    val fairMarketRentalValueCents: Cents? = null,
    val excludableAllowanceCents: Cents = 0L,
    val limitingFactor: MhaLimitingFactor = MhaLimitingFactor.UNCAPPED_ACTUAL,
    val taxCalculationResult: MhaTaxCalculationResult? = null,
    val projectedAnnualSavingsCents: Cents = 0L,
    val taxProfile: MhaTaxProfile = MhaTaxProfile(),
    val activeYearTaxRecord: MhaYearTaxRecord? = null,
    val isUsingStoredYearRate: Boolean = false,
    val isHistoricalYearWithoutSavedRate: Boolean = false,
    val taxRateSourceLabel: String = "",
    val eligibleTransactions: List<MhaTransactionItem> = emptyList(),
    val reviewQueueTransactions: List<MhaTransactionItem> = emptyList(),
    val allYearOutflows: List<MhaTransactionItem> = emptyList(),
    val visibleTransactions: List<MhaTransactionItem> = emptyList(),
    val groupedVisibleMonths: List<MhaMonthGroup> = emptyList(),
    val visibleTotalCents: Cents = 0L,
    val categoryBreakdown: List<MhaCategoryTotal> = emptyList(),
    val selectedTabFilter: MhaTransactionTabFilter = MhaTransactionTabFilter.ELIGIBLE,
    val selectedCategoryId: Long? = null,
    val showExcludedInList: Boolean = false,
    val inspectingTransaction: MhaTransactionItem? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val showConfigSheet: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val mhaEnabled: Boolean = true,
    val isReadOnlySession: Boolean = false,
    val activeScreenTab: MhaScreenTab = MhaScreenTab.TRACKER
) {
    val mhaDesignationCapCents: Cents? get() = designatedCapCents
    val bindingLimit: MhaBindingLimit get() = limitingFactor
    val taxSavingsResult: MhaTaxCalculationResult? get() = taxCalculationResult
    val actualEligibleExpensesCents: Cents get() = actualEligibleHousingSpentCents
}
