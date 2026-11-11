package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.domain.RealEstatePropertyEngine

fun groupAccounts(accounts: List<Account>): List<AccountGroupDisplay> {
    val propertyUnits = RealEstatePropertyEngine.buildPropertyUnits(accounts)
    val realEstateIds = RealEstatePropertyEngine.realEstateMemberAccountIds(accounts)
    val realEstateAccounts = propertyUnits.flatMap { it.allMemberAccounts }.distinctBy { it.id }

    val map = listOf(
        AccountTypeCategory.CASH_AND_CHECKING to ("Cash & Checking" to accounts.filter {
            it.id !in realEstateIds && it.type in setOf(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH)
        }),
        AccountTypeCategory.CREDIT_CARDS to ("Credit Cards" to accounts.filter {
            it.id !in realEstateIds && it.type == AccountType.CREDIT
        }),
        AccountTypeCategory.INVESTMENTS to ("Investments" to accounts.filter {
            it.id !in realEstateIds && it.type == AccountType.INVESTMENT
        }),
        AccountTypeCategory.REAL_ESTATE to ("Real Estate" to realEstateAccounts),
        AccountTypeCategory.LOANS to ("Loans & Mortgages" to accounts.filter {
            it.id !in realEstateIds && (it.type == AccountType.LOAN || it.type == AccountType.MORTGAGE)
        }),
        AccountTypeCategory.OTHER to ("Other" to accounts.filter {
            it.id !in realEstateIds && it.type == AccountType.OTHER
        })
    )
    return map.mapNotNull { (cat, pair) ->
        val (title, list) = pair
        if (list.isEmpty()) null
        else AccountGroupDisplay(
            category = cat,
            title = title,
            accounts = list,
            totalCents = if (cat == AccountTypeCategory.REAL_ESTATE) {
                propertyUnits.sumOf { it.netEquityCents }
            } else {
                list.sumOf { it.currentBalanceCents }
            },
            propertyUnits = if (cat == AccountTypeCategory.REAL_ESTATE) propertyUnits else emptyList()
        )
    }
}
