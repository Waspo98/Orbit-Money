package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.setCardBenefitUsage
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.ui.accounts.cards.CardFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class PerkPeriodFilter(val label: String) {
    ALL("All"),
    EXPIRING_SOON("Expiring \u22647d"),
    MONTHLY("Monthly"),
    ANNUAL_PLUS("Annual+")
}

enum class PerkToggleOrigin {
    CARD_PERKS,
    ACCOUNTS
}

data class PerkToggleUndoEvent(
    val eventId: Long,
    val accountId: Long,
    val benefitId: String,
    val benefitTitle: String,
    val previousAmountCents: Cents?,
    val newAmountCents: Cents?,
    val isMonetaryValue: Boolean = true,
    val origin: PerkToggleOrigin = PerkToggleOrigin.CARD_PERKS
)

/**
 * Formats the snackbar confirmation copy for [event], omitting dollar amounts for
 * `$0` / non-monetary benefits that store `1L` cent solely as a boolean usage flag.
 */
fun formatPerkToggleUndoMessage(event: PerkToggleUndoEvent): String {
    val amount = event.newAmountCents ?: 0L
    return when {
        amount <= 0L -> "Cleared ${event.benefitTitle}"
        event.isMonetaryValue -> "Marked ${event.benefitTitle} used (${CardFormat.formatAdaptiveCents(amount)})"
        else -> "Marked ${event.benefitTitle} used"
    }
}

data class CardPerksUiOptionsState(
    val showUsedPerks: Boolean = false,
    val showCardPerksCustomize: Boolean = false,
    val perkPeriodFilter: PerkPeriodFilter = PerkPeriodFilter.ALL,
    val whichCardQuery: String = "",
    val chase524SelectedHolderKey: String? = null,
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
    private val _showCardPerksCustomize = MutableStateFlow(false)
    private val _perkPeriodFilter = MutableStateFlow(PerkPeriodFilter.ALL)
    private val _whichCardQuery = MutableStateFlow("")
    private val _chase524SelectedHolderKey = MutableStateFlow<String?>(null)
    private val _lastPerkUndoEvent = MutableStateFlow<PerkToggleUndoEvent?>(null)
    internal val currentPerkUndoEvent: PerkToggleUndoEvent? get() = _lastPerkUndoEvent.value
    private var nextUndoEventId = 1L

    private val coreControlsFlow = combine(
        _showUsedPerks,
        _showCardPerksCustomize,
        _perkPeriodFilter
    ) { used, customize, filter -> Triple(used, customize, filter) }

    private val screenControlsFlow = combine(
        _whichCardQuery,
        _chase524SelectedHolderKey,
        _lastPerkUndoEvent
    ) { query, holderKey, undo ->
        ScreenControlsBundle(query, holderKey, undo)
    }

    val uiOptionsFlow: Flow<CardPerksUiOptionsState> = combine(
        coreControlsFlow,
        screenControlsFlow
    ) { (used, customize, filter), screen ->
        CardPerksUiOptionsState(
            showUsedPerks = used,
            showCardPerksCustomize = customize,
            perkPeriodFilter = filter,
            whichCardQuery = screen.query,
            chase524SelectedHolderKey = screen.holderKey,
            lastPerkUndoEvent = screen.undo
        )
    }

    fun toggleShowUsedPerks() { _showUsedPerks.value = !_showUsedPerks.value }
    fun setCardPerksCustomize(visible: Boolean) { _showCardPerksCustomize.value = visible }
    fun selectPerkFilter(filter: PerkPeriodFilter) { _perkPeriodFilter.value = filter }
    fun setWhichCardQuery(query: String) { _whichCardQuery.value = query }
    fun selectChase524Holder(holderKey: String) { _chase524SelectedHolderKey.value = holderKey }
    fun dismissPerkUndo(origin: PerkToggleOrigin? = null) {
        if (origin == null) {
            _lastPerkUndoEvent.value = null
        } else {
            _lastPerkUndoEvent.update { current -> if (current?.origin == origin) null else current }
        }
    }

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

    fun toggleCardBenefit(
        account: Account?,
        benefitId: String,
        today: LocalDate,
        origin: PerkToggleOrigin = PerkToggleOrigin.CARD_PERKS
    ) {
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
                    newAmountCents = targetAmount,
                    isMonetaryValue = periodValue > 0L,
                    origin = origin
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
        val query: String,
        val holderKey: String?,
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
