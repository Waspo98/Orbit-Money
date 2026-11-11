package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import kotlin.math.abs

/**
 * Represents a unified Real Estate Property card (Option C), combining a property
 * valuation anchor with zero, one, or multiple linked mortgage/HELOC loans.
 */
data class RealEstatePropertyUnit(
    val anchorAccount: Account,
    val title: String,
    val estimatedValueCents: Cents,
    val linkedLoans: List<Account>,
    val totalDebtCents: Cents,
    val netEquityCents: Cents,
    val equityFraction: Float
) {
    val allMemberAccounts: List<Account>
        get() = (listOf(anchorAccount) + linkedLoans).distinctBy { it.id }

    val isPaidOff: Boolean
        get() = linkedLoans.isEmpty() || totalDebtCents == 0L
}

object RealEstatePropertyEngine {

    /**
     * True when [account] serves as the root property card in the Real Estate group.
     */
    fun isPropertyAnchor(account: Account): Boolean {
        if (account.linkedPropertyAccountId != null) return false
        return account.isProperty || (account.type == AccountType.MORTGAGE && account.estimatedValueCents != null)
    }

    /**
     * Builds ordered [RealEstatePropertyUnit] cards from a list of accounts.
     * Supports both single-row mortgage+property accounts (e.g., 2104 Doubletree Lane)
     * and standalone properties with 0..N linked loans (Mortgage + HELOC / Refinance).
     */
    fun buildPropertyUnits(accounts: List<Account>): List<RealEstatePropertyUnit> {
        val anchors = accounts.filter(::isPropertyAnchor)
        if (anchors.isEmpty()) return emptyList()

        val anchorIds = anchors.map { it.id }.toSet()
        val childrenByAnchorId = accounts
            .filter { acct ->
                val parentId = acct.linkedPropertyAccountId
                parentId != null && parentId in anchorIds && acct.id != parentId
            }
            .groupBy { it.linkedPropertyAccountId!! }

        return anchors.map { anchor ->
            val propValue = if (anchor.isProperty) {
                anchor.estimatedValueCents ?: anchor.currentBalanceCents.coerceAtLeast(0L)
            } else {
                anchor.estimatedValueCents ?: 0L
            }

            val childLoans = childrenByAnchorId[anchor.id].orEmpty()
            val loans = if (anchor.type == AccountType.MORTGAGE && !anchor.isProperty) {
                listOf(anchor) + childLoans
            } else {
                childLoans
            }

            val totalDebt = loans.sumOf { abs(it.currentBalanceCents) }
            val netEquity = propValue - totalDebt
            val fraction = if (propValue > 0L) {
                (netEquity.toFloat() / propValue.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val resolvedTitle = anchor.propertyName?.trim()?.takeIf { it.isNotEmpty() } ?: anchor.name

            RealEstatePropertyUnit(
                anchorAccount = anchor,
                title = resolvedTitle,
                estimatedValueCents = propValue,
                linkedLoans = loans,
                totalDebtCents = totalDebt,
                netEquityCents = netEquity,
                equityFraction = fraction
            )
        }
    }

    /**
     * Returns the set of all account IDs participating in any [RealEstatePropertyUnit]
     * (both property anchors and their linked child loans).
     */
    fun realEstateMemberAccountIds(accounts: List<Account>): Set<Long> {
        val anchors = accounts.filter(::isPropertyAnchor)
        if (anchors.isEmpty()) return emptySet()
        val anchorIds = anchors.map { it.id }.toSet()
        val childIds = accounts
            .filter { it.linkedPropertyAccountId != null && it.linkedPropertyAccountId in anchorIds }
            .map { it.id }
        return anchorIds + childIds
    }

    /**
     * Human-friendly label for a nested loan row inside a [RealEstatePropertyUnit] card.
     * Avoids repeating the property street address when the anchor is a combined mortgage.
     */
    fun loanDisplayLabel(loan: Account, unit: RealEstatePropertyUnit): String {
        if (loan.id == unit.anchorAccount.id) {
            return loan.institution?.trim()?.takeIf { it.isNotEmpty() } ?: "${loan.name} Mortgage"
        }
        return loan.name
    }
}
