package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.CardCycleStatus
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.CreditCardPortfolioSummary
import app.overbay.orbit.domain.CreditCardStrategyEngine
import app.overbay.orbit.domain.HouseholdCreditSummary
import app.overbay.orbit.ui.accounts.cards.CardEditorDraft

enum class AccountTypeCategory(val displayName: String) {
    CASH_AND_CHECKING("Cash & Checking"),
    CREDIT_CARDS("Credit Cards"),
    INVESTMENTS("Investments"),
    REAL_ESTATE("Real Estate"),
    LOANS("Loans & Mortgages"),
    OTHER("Other")
}

data class AccountGroupDisplay(
    val category: AccountTypeCategory,
    val title: String,
    val accounts: List<Account>,
    val totalCents: Cents
)

data class PerkUsageTarget(
    val accountId: Long,
    val benefitId: String,
    val title: String = "",
    val periodLabel: String = "",
    val valueCents: Cents = 0L,
    val initialUsedCents: Cents = 0L
)

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val accountGroups: List<AccountGroupDisplay> = emptyList(),
    val totalAssetsCents: Cents = 0L,
    val totalLiabilitiesCents: Cents = 0L,
    val netWorthCents: Cents = 0L,
    val cashCents: Cents = 0L,
    val creditCardCents: Cents = 0L,
    val investmentsCents: Cents = 0L,
    val showArchived: Boolean = false,
    val isReorderMode: Boolean = false,
    val selectedAccountId: Long? = null,
    val selectedAccountTransactions: List<Transaction> = emptyList(),
    val editingAccount: Account? = null,
    val showEditorSheet: Boolean = false,
    val cardEditorDraft: CardEditorDraft? = null,
    val householdMembers: List<HouseholdMember> = emptyList(),
    val mergingAccount: Account? = null,
    val showMergeDialog: Boolean = false,
    val historyAccount: Account? = null,
    val showHistorySheet: Boolean = false,
    val historyBalanceRecords: List<BalanceRecord> = emptyList(),
    val showAddRecordDialog: Boolean = false,
    val addRecordAccount: Account? = null,
    val isPhoneViewingDetail: Boolean = false,
    val deleteConfirmAccountId: Long? = null,
    val deleteWarning524Date: String? = null,
    val deleteBlockedAccount: Account? = null,
    val deleteBlockedTransactionCount: Int = 0,
    val accountGroupWideSpans: Map<String, Int> = emptyMap(),
    val isSyncing: Boolean = false,
    val syncErrorMessage: String? = null,
    val isLoading: Boolean = false,
    val mhaEnabled: Boolean = false,
    val rewardsToolsEnabled: Boolean = false,
    val perkUsageTarget: PerkUsageTarget? = null,
    val selectedCardPlayer: String = CreditCardStrategyEngine.ALL_PLAYERS,
    val cardPortfolioSummary: CreditCardPortfolioSummary = CreditCardPortfolioSummary(),
    val householdCredit: HouseholdCreditSummary? = null,
    val cardCycles: List<CardCycleStatus> = emptyList(),
    val feesAhead: List<AnnualFeeCardItem> = emptyList(),
    val missingBillingDatesCount: Int = 0,
    val showUsedPerks: Boolean = false,
    val showWhichCardWhere: Boolean = false,
    val selectedRewardCategory: String? = null,
    val cardPerksWidgets: List<DashboardWidgetConfig> = OrbitPreferences.defaultCardPerksWidgets()
) {
    val selectedAccount: Account?
        get() = accounts.find { it.id == selectedAccountId }

    val showCardProfileSheet: Boolean
        get() = cardEditorDraft != null

    val editingCardProfile: CreditCardProfile?
        get() = cardEditorDraft?.toProfile() ?: cardEditorDraft?.originalProfile

    val cardProfileAccountId: Long?
        get() = cardEditorDraft?.accountId
}
