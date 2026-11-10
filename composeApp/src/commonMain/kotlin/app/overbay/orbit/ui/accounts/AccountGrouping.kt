package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType

fun groupAccounts(accounts: List<Account>): List<AccountGroupDisplay> {
    val map = listOf(
        AccountTypeCategory.CASH_AND_CHECKING to ("Cash & Checking" to accounts.filter { it.type in setOf(AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH) }),
        AccountTypeCategory.CREDIT_CARDS to ("Credit Cards" to accounts.filter { it.type == AccountType.CREDIT }),
        AccountTypeCategory.INVESTMENTS to ("Investments" to accounts.filter { it.type == AccountType.INVESTMENT }),
        AccountTypeCategory.REAL_ESTATE to ("Real Estate" to accounts.filter { it.type == AccountType.MORTGAGE && it.estimatedValueCents != null }),
        AccountTypeCategory.LOANS to ("Loans & Mortgages" to accounts.filter { it.type == AccountType.LOAN || (it.type == AccountType.MORTGAGE && it.estimatedValueCents == null) }),
        AccountTypeCategory.OTHER to ("Other" to accounts.filter { it.type == AccountType.OTHER })
    )
    return map.mapNotNull { (cat, pair) ->
        val (title, list) = pair
        if (list.isEmpty()) null
        else AccountGroupDisplay(
            category = cat,
            title = title,
            accounts = list,
            totalCents = if (cat == AccountTypeCategory.REAL_ESTATE) list.sumOf { it.homeEquityCents ?: 0L } else list.sumOf { it.currentBalanceCents }
        )
    }
}
