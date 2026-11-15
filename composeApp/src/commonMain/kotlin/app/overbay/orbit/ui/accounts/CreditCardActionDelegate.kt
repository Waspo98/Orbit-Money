package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.setCardBenefitUsage
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.LocalDateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class PerkPeriodFilter(val label: String) {
    ALL("All"),
    EXPIRING_SOON("Expiring \u22647d"),
    MONTHLY("Monthly"),
    ANNUAL_PLUS("Annual+")
}

data class PerkToggleUndoEvent(
    val eventId: Long,
    val accountId: Long,
    val benefitId: String,
    val benefitTitle: String,
    val previousAmountCents: Cents?,
    val newAmountCents: Cents?
)

data class CardPerksUiOptionsState(
    val showUsedPerks: Boolean = false,
    val showWhichCardWhere: Boolean = false,
    val selectedRewardCategory: String? = null,
    val showCardPerksCustomize: Boolean = false,
    val perkPeriodFilter: PerkPeriodFilter = PerkPeriodFilter.ALL,
    val whichCardQuery: String = "",
    val lastPerkUndoEvent: PerkToggleUndoEvent? = null
)

/**
 * Owns configuration-proof UI control state and perk mutation/undo actions for the
 * Credit Card Tracker and Card Perks hub, keeping [AccountsViewModel] within its line ceiling.
 */
class CreditCardActionDelegate(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope,
    private val onSyncError: (String) -> Unit
) {
    private val _showUsedPerks = MutableStateFlow(false)
    private val _showWhichCardWhere = MutableStateFlow(false)
    private val _selectedRewardCategory = MutableStateFlow<String?>(null)
    private val _showCardPerksCustomize = MutableStateFlow(false)
    private val _perkPeriodFilter = MutableStateFlow(PerkPeriodFilter.ALL)
    private val _whichCardQuery = MutableStateFlow("")
    private val _lastPerkUndoEvent = MutableStateFlow<PerkToggleUndoEvent?>(null)
    private var nextUndoEventId = 1L

    private val coreControlsFlow = combine(
        _showUsedPerks,
        _showWhichCardWhere,
        _selectedRewardCategory
    ) { used, which, cat -> Triple(used, which, cat) }

    private val screenControlsFlow = combine(
        _showCardPerksCustomize,
        _perkPeriodFilter,
        _whichCardQuery,
        _lastPerkUndoEvent
    ) { customize, filter, query, undo ->
        ScreenControlsBundle(customize, filter, query, undo)
    }

    val uiOptionsFlow: Flow<CardPerksUiOptionsState> = combine(
        coreControlsFlow,
        screenControlsFlow
    ) { (used, which, cat), screen ->
        CardPerksUiOptionsState(
            showUsedPerks = used,
            showWhichCardWhere = which,
            selectedRewardCategory = cat,
            showCardPerksCustomize = screen.customize,
            perkPeriodFilter = screen.filter,
            whichCardQuery = screen.query,
            lastPerkUndoEvent = screen.undo
        )
    }

    fun toggleShowUsedPerks() { _showUsedPerks.value = !_showUsedPerks.value }
    fun toggleShowWhichCardWhere() { _showWhichCardWhere.value = !_showWhichCardWhere.value }
    fun selectRewardCategory(category: String?) { _selectedRewardCategory.value = category }
    fun setCardPerksCustomize(visible: Boolean) { _showCardPerksCustomize.value = visible }
    fun selectPerkFilter(filter: PerkPeriodFilter) { _perkPeriodFilter.value = filter }
    fun setWhichCardQuery(query: String) { _whichCardQuery.value = query }
    fun dismissPerkUndo() { _lastPerkUndoEvent.value = null }

    fun updateCardPerksWidgets(transform: (List<DashboardWidgetConfig>) -> List<DashboardWidgetConfig>) {
        scope.launch {
            val prefs = repository.preferences.value
            repository.updatePreferences(prefs.copy(cardPerksWidgets = transform(prefs.cardPerksWidgets)))
        }
    }

    fun setCardRewardsToolsEnabled(enabled: Boolean?) {
        scope.launch {
            val prefs = repository.preferences.value
            repository.updatePreferences(prefs.copy(cardRewardsToolsEnabled = enabled))
        }
    }

    fun setCardBenefitUsage(
        account: Account?,
        benefitId: String,
        amountCents: Cents?,
        today: LocalDate
    ) {
        val rawProfile = account?.creditCardProfile ?: return
        val profile = CreditCardBenefitEngine.upgradeProfile(rawProfile, today)
        val benefit = profile.benefits.find { it.id == benefitId } ?: return
        val opened = profile.strategy.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
        val period = CreditCardBenefitEngine.currentPeriod(benefit, opened, today)
        scope.launch {
            repository.setCardBenefitUsage(
                accountId = account.id,
                benefitId = benefitId,
                periodKey = period.key,
                amountCents = amountCents,
                usedOn = if (amountCents != null && amountCents > 0L) LocalDateUtils.formatIsoDate(today) else null
            ).onFailure { e ->
                onSyncError("Couldn't update perk: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun toggleCardBenefit(account: Account?, benefitId: String, today: LocalDate) {
        val rawProfile = account?.creditCardProfile ?: return
        val profile = CreditCardBenefitEngine.upgradeProfile(rawProfile, today)
        val benefit = profile.benefits.find { it.id == benefitId } ?: return
        val opened = profile.strategy.openedDate?.let { LocalDateUtils.parseIsoDate(it) }
        val period = CreditCardBenefitEngine.currentPeriod(benefit, opened, today)
        val used = CreditCardBenefitEngine.usedCents(benefit, period)
        val periodValue = CreditCardBenefitEngine.periodValueCents(benefit)
        val targetAmount = computeNextToggleUsage(used, periodValue)
        val previousAmount: Cents? = if (used > 0L) used else null
        scope.launch {
            repository.setCardBenefitUsage(
                accountId = account.id,
                benefitId = benefitId,
                periodKey = period.key,
                amountCents = targetAmount,
                usedOn = if (targetAmount != null && targetAmount > 0L) LocalDateUtils.formatIsoDate(today) else null
            ).onSuccess {
                _lastPerkUndoEvent.value = PerkToggleUndoEvent(
                    eventId = nextUndoEventId++,
                    accountId = account.id,
                    benefitId = benefitId,
                    benefitTitle = benefit.title,
                    previousAmountCents = previousAmount,
                    newAmountCents = targetAmount
                )
            }.onFailure { e ->
                onSyncError("Couldn't update perk: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun undoLastPerkToggle(account: Account?, event: PerkToggleUndoEvent, today: LocalDate) {
        _lastPerkUndoEvent.value = null
        setCardBenefitUsage(account, event.benefitId, event.previousAmountCents, today)
    }

    private data class ScreenControlsBundle(
        val customize: Boolean,
        val filter: PerkPeriodFilter,
        val query: String,
        val undo: PerkToggleUndoEvent?
    )

    companion object {
        /**
         * Resolves the 3-state perk toggle target:
         * 0 used -> full credit; partial used -> full credit; full used -> clear (`null`).
         */
        fun computeNextToggleUsage(usedCents: Cents, periodValueCents: Cents): Cents? {
            val isFullyUsed = if (periodValueCents > 0L) usedCents >= periodValueCents else usedCents > 0L
            return when {
                isFullyUsed -> null
                periodValueCents > 0L -> periodValueCents
                else -> 1L
            }
        }
    }
}
