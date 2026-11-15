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
import app.overbay.orbit.ui.accounts.cards.CardFormat
import kotlinx.datetime.LocalDate

data class AnnualFeeCardItem(
    val status: CardCycleStatus,
    val ledger: CardValueLedger
)

data class PlayerScopedCreditHub(
    val walletAccountIds: Set<Long> = emptySet(),
    val householdCredit: HouseholdCreditSummary = HouseholdCreditSummary(0L, 0L, null, null),
    val cardCycles: List<CardCycleStatus> = emptyList(),
    val feesAhead: List<AnnualFeeCardItem> = emptyList(),
    val missingBillingDatesCount: Int = 0,
    val firstMissingBillingAccountId: Long? = null,
    val firstMissingOpenDateAccountId: Long? = null
)

data class CreditCardHubBundle(
    val portfolio: CreditCardPortfolioSummary,
    val householdCredit: HouseholdCreditSummary,
    val cardCycles: List<CardCycleStatus>,
    val feesAhead: List<AnnualFeeCardItem>,
    val missingBillingDatesCount: Int,
    val playerScopedHub: PlayerScopedCreditHub,
    val today: LocalDate
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

        val playerScopedHub = scopeForPlayer(
            activeCreditAccounts = activeCreditAccounts,
            cardCycles = cardCycles,
            feesAhead = feesAhead,
            portfolio = portfolio,
            selectedPlayer = selectedPlayer,
            householdMembers = householdMembers
        )

        return CreditCardHubBundle(
            portfolio = portfolio,
            householdCredit = householdCredit,
            cardCycles = cardCycles,
            feesAhead = feesAhead,
            missingBillingDatesCount = missingBillingDatesCount,
            playerScopedHub = playerScopedHub,
            today = today
        )
    }

    fun scopeForPlayer(
        activeCreditAccounts: List<Account>,
        cardCycles: List<CardCycleStatus>,
        feesAhead: List<AnnualFeeCardItem>,
        portfolio: CreditCardPortfolioSummary,
        selectedPlayer: String,
        householdMembers: List<HouseholdMember>
    ): PlayerScopedCreditHub {
        val matchedAccounts = if (selectedPlayer == CreditCardStrategyEngine.ALL_PLAYERS) {
            activeCreditAccounts
        } else {
            activeCreditAccounts.filter { CardFormat.matchesSelectedPlayer(it, selectedPlayer, householdMembers) }
        }
        val walletIds = matchedAccounts.map { it.id }.toSet()
        val matchedCycles = cardCycles.filter { it.accountId in walletIds }
        val scopedCredit = CreditCardCycleEngine.summarizeStatuses(matchedCycles)
        val scopedFees = feesAhead.filter { it.status.accountId in walletIds }
        val missingBillingAccounts = matchedAccounts.filter { acct ->
            val p = acct.creditCardProfile
            p == null || p.statementClosingDay == null || p.paymentDueDay == null
        }
        val firstMissingOpenDateId = if (selectedPlayer == CreditCardStrategyEngine.ALL_PLAYERS) {
            portfolio.chase524ByPerson.firstNotNullOfOrNull { it.summary.missingOpenDateAccountIds.firstOrNull() }
        } else {
            portfolio.chase524ByPerson
                .firstOrNull { it.holderKey == selectedPlayer }
                ?.summary?.missingOpenDateAccountIds?.firstOrNull()
        }

        return PlayerScopedCreditHub(
            walletAccountIds = walletIds,
            householdCredit = scopedCredit,
            cardCycles = matchedCycles,
            feesAhead = scopedFees,
            missingBillingDatesCount = missingBillingAccounts.size,
            firstMissingBillingAccountId = missingBillingAccounts.firstOrNull()?.id,
            firstMissingOpenDateAccountId = firstMissingOpenDateId
        )
    }
}
