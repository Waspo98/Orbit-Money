package app.overbay.orbit.ui.mha

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.MhaTaxProfile
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

data class MhaTransactionItem(
    val transaction: Transaction,
    val category: Category?,
    val account: Account?,
    val isEligible: Boolean,
    val isExplicitOverride: Boolean,
    val isOverriddenByUser: Boolean
) {
    val id: Long get() = transaction.id
    val effectiveDate: String get() = transaction.effectiveDate
    val effectiveMerchant: String get() = transaction.effectiveMerchant
    val effectiveAmountCents: Cents get() = transaction.effectiveAmountCents
    val date: String get() = effectiveDate
    val merchant: String get() = effectiveMerchant
    val amountCents: Cents get() = effectiveAmountCents
}

data class MhaTrackerUiState(
    val selectedYear: Int = 2026,
    val availableYears: List<Int> = listOf(2026, 2025, 2024),
    val actualEligibleHousingSpentCents: Cents = 0L,
    val designatedCapCents: Cents? = null,
    val fairMarketRentalValueCents: Cents? = null,
    val excludableAllowanceCents: Cents = 0L,
    val limitingFactor: MhaLimitingFactor = MhaLimitingFactor.UNCAPPED_ACTUAL,
    val taxCalculationResult: MhaTaxCalculationResult? = null,
    val projectedAnnualSavingsCents: Cents = 0L,
    val taxProfile: MhaTaxProfile = MhaTaxProfile(),
    val eligibleTransactions: List<MhaTransactionItem> = emptyList(),
    val reviewQueueTransactions: List<MhaTransactionItem> = emptyList(),
    val allYearOutflows: List<MhaTransactionItem> = emptyList(),
    val selectedTabFilter: MhaTransactionTabFilter = MhaTransactionTabFilter.ELIGIBLE,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val searchQuery: String = "",
    val showConfigSheet: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val mhaDesignationCapCents: Cents? get() = designatedCapCents
    val bindingLimit: MhaBindingLimit get() = limitingFactor
    val taxSavingsResult: MhaTaxCalculationResult? get() = taxCalculationResult

    val visibleTransactions: List<MhaTransactionItem>
        get() {
            val list = when (selectedTabFilter) {
                MhaTransactionTabFilter.ELIGIBLE -> eligibleTransactions
                MhaTransactionTabFilter.REVIEW_QUEUE -> reviewQueueTransactions
                MhaTransactionTabFilter.ALL -> allYearOutflows
            }
            if (searchQuery.isBlank()) return list
            val query = searchQuery.trim().lowercase()
            return list.filter { item ->
                item.merchant.lowercase().contains(query) ||
                    (item.category?.name?.lowercase()?.contains(query) == true) ||
                    (item.account?.name?.lowercase()?.contains(query) == true) ||
                    (item.transaction.notes?.lowercase()?.contains(query) == true)
            }
        }
}
