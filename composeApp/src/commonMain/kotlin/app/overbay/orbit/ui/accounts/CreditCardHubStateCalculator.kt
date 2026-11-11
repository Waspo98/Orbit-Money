package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.CardValueLedger
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.CreditCardCycleEngine
import app.overbay.orbit.domain.CreditCardPortfolioSummary
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.HouseholdCreditSummary
import kotlinx.datetime.LocalDate

data class AnnualFeeCardItem(
    val status: CardCycleStatus,
    val ledger: CardValueLedger
)

data class CreditCardHubBundle(
    val portfolio: CreditCardPortfolioSummary,
    val householdCredit: HouseholdCreditSummary,
    val cardCycles: List<CardCycleStatus>,
    val feesAhead: List<AnnualFeeCardItem>,
    val missingBillingDatesCount: Int
)

object CreditCardHubStateCalculator {

    fun calculate(
        accounts: List<Account>,
        transactions: List<Transaction>,
        selectedPlayer: String,
        today: LocalDate,
        householdMembers: List<HouseholdMember>
    ): CreditCardHubBundle {
        val (householdCredit, cardCycles) = CreditCardCycleEngine.summarize(
            accounts = accounts,
            txns = transactions,
            today = today,
            householdMembers = householdMembers
        )
        val portfolio = CreditCardStrategyEngine.summarizePortfolio(
            accounts = accounts,
            transactions = transactions,
            selectedPlayer = selectedPlayer,
            today = today,
            householdMembers = householdMembers
        )

        val activeCreditAccounts = accounts.filter { it.type == AccountType.CREDIT && !it.isArchived }
        val accountMap = activeCreditAccounts.associateBy { it.id }

        val feesAhead = cardCycles
            .filter { it.annualFeeCents > 0L && it.daysUntilAnnualFee != null && it.daysUntilAnnualFee in 0..90 }
            .sortedBy { it.daysUntilAnnualFee }
            .mapNotNull { cycle ->
                val acct = accountMap[cycle.accountId] ?: return@mapNotNull null
                val profile = acct.creditCardProfile ?: return@mapNotNull null
                val ledger = CreditCardBenefitEngine.ledger(profile, today)
                AnnualFeeCardItem(status = cycle, ledger = ledger)
            }

        val missingBillingDatesCount = activeCreditAccounts.count { acct ->
            val p = acct.creditCardProfile
            p == null || p.statementClosingDay == null || p.paymentDueDay == null
        }

        return CreditCardHubBundle(
            portfolio = portfolio,
            householdCredit = householdCredit,
            cardCycles = cardCycles,
            feesAhead = feesAhead,
            missingBillingDatesCount = missingBillingDatesCount
        )
    }
}
