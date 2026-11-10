package app.overbay.orbit.ui.accounts.cards

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.saveCreditCardProfile
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.CreditCardTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CardEditorController(
    private val repository: OrbitRepository
) {
    private val _draftState = MutableStateFlow<CardEditorDraft?>(null)
    val draftState: StateFlow<CardEditorDraft?> = _draftState.asStateFlow()
    val draft: StateFlow<CardEditorDraft?> get() = draftState

    fun startDraft(account: Account, profile: CreditCardProfile? = null) {
        _draftState.value = CardEditorDraft.fromAccountAndProfile(account, profile ?: account.creditCardProfile)
    }

    fun open(account: Account) {
        startDraft(account, account.creditCardProfile)
    }

    fun clearDraft() {
        _draftState.value = null
    }

    fun confirmDiscard() {
        clearDraft()
    }

    fun requestClose() {
        val current = _draftState.value ?: return
        if (current.isDirty) {
            _draftState.update { it?.copy(showDiscardConfirm = true) }
        } else {
            clearDraft()
        }
    }

    fun dismissDiscardConfirm() {
        _draftState.update { it?.copy(showDiscardConfirm = false) }
    }

    fun skipTemplate() {
        _draftState.update { it?.copy(step = CardEditorStep.FORM) }
    }

    fun updateDraft(transform: (CardEditorDraft) -> CardEditorDraft) {
        _draftState.update { current -> current?.let(transform) }
    }

    fun applyTemplate(template: CreditCardTemplate) {
        _draftState.update { current ->
            if (current == null) return@update null
            val baseProfile = current.toProfile()
            val applied = template.applyToProfile(baseProfile)

            current.copy(
                step = CardEditorStep.FORM,
                cardName = applied.cardName.orEmpty(),
                issuer = applied.issuerName.orEmpty(),
                network = applied.network ?: "Visa",
                artKey = applied.imageUrl,
                annualFeeCents = applied.annualFeeCents,
                annualFeeText = applied.annualFeeCents?.let { (it / 100).toString() }.orEmpty(),
                hasAnnualFee = (applied.annualFeeCents ?: 0L) > 0L,
                benefits = applied.benefits,
                rewardCategories = applied.rewardCategories,
                isBusinessCard = applied.strategy.isBusinessCard,
                subCooldownMonths = applied.strategy.subCooldownMonths,
                subRewardText = applied.strategy.subRewardText,
                targetSpendCents = applied.strategy.subTargetSpendCents,
                targetSpendText = applied.strategy.subTargetSpendCents?.let { (it / 100).toString() }.orEmpty()
            )
        }
    }

    fun addBenefit(benefit: CardBenefit) {
        val resolved = if (benefit.id.isBlank()) benefit.copy(id = CreditCardBenefitEngine.newBenefitId()) else benefit
        _draftState.update { it?.copy(benefits = it.benefits + resolved) }
    }

    fun updateBenefit(benefit: CardBenefit) {
        _draftState.update { current ->
            if (current == null) return@update null
            val updated = current.benefits.map { if (it.id == benefit.id) benefit else it }
            current.copy(benefits = updated)
        }
    }

    fun removeBenefit(benefitId: String) {
        _draftState.update { current ->
            if (current == null) return@update null
            val target = current.benefits.find { it.id == benefitId }
            val remaining = current.benefits.filterNot { it.id == benefitId }
            current.copy(benefits = remaining, lastDeletedBenefit = target)
        }
    }

    fun undoRemoveBenefit() {
        _draftState.update { current ->
            val toRestore = current?.lastDeletedBenefit ?: return@update current
            current.copy(benefits = current.benefits + toRestore, lastDeletedBenefit = null)
        }
    }

    fun upsertBenefit(benefit: CardBenefit) {
        val current = _draftState.value ?: return
        if (current.benefits.any { it.id == benefit.id }) {
            updateBenefit(benefit)
        } else {
            addBenefit(benefit)
        }
    }

    fun addReward(reward: RewardCategory) {
        _draftState.update { it?.copy(rewardCategories = it.rewardCategories + reward) }
    }

    fun updateReward(index: Int, reward: RewardCategory) {
        _draftState.update { current ->
            if (current == null || index !in current.rewardCategories.indices) return@update current
            val updated = current.rewardCategories.toMutableList().also { it[index] = reward }
            current.copy(rewardCategories = updated)
        }
    }

    fun removeReward(index: Int) {
        _draftState.update { current ->
            if (current == null || index !in current.rewardCategories.indices) return@update current
            val updated = current.rewardCategories.toMutableList().also { it.removeAt(index) }
            current.copy(rewardCategories = updated)
        }
    }

    fun upsertReward(index: Int?, reward: RewardCategory) {
        if (index != null && index >= 0) {
            updateReward(index, reward)
        } else {
            addReward(reward)
        }
    }

    fun setWelcomeBonusMode(mode: WelcomeBonusMode) {
        _draftState.update { current ->
            if (current == null) return@update null
            when (mode) {
                WelcomeBonusMode.NONE -> current.copy(
                    subRewardText = null,
                    subEstimatedValueCents = null,
                    targetSpendCents = null,
                    targetSpendText = "",
                    targetDeadlineDate = null,
                    subCompleted = false,
                    subReceivedDate = null
                )
                WelcomeBonusMode.WORKING -> current.copy(
                    subCompleted = false,
                    subReceivedDate = null
                )
                WelcomeBonusMode.EARNED -> current.copy(
                    subCompleted = true
                )
            }
        }
    }

    fun buildProfile(): CreditCardProfile? {
        val current = _draftState.value ?: return null
        return if (current.isValid) current.toProfile() else null
    }

    suspend fun save(
        accountNameOverride: String? = null,
        institutionOverride: String? = null
    ): Result<CreditCardProfile> {
        val current = _draftState.value ?: return Result.failure(IllegalStateException("No card draft active"))
        if (!current.isValid) {
            return Result.failure(IllegalArgumentException("Draft has validation errors: ${current.errors.values.joinToString(", ")}"))
        }

        _draftState.update { it?.copy(isSaving = true) }
        val profile = current.toProfile()
        val acctName = accountNameOverride ?: current.accountName.takeIf { it.isNotBlank() }
        val acctInst = institutionOverride ?: current.accountInstitution.takeIf { it.isNotBlank() }

        return repository.saveCreditCardProfile(
            accountId = current.accountId,
            profile = profile,
            accountName = acctName,
            accountInstitution = acctInst
        ).onSuccess {
            clearDraft()
        }.onFailure {
            _draftState.update { it?.copy(isSaving = false) }
        }
    }
}
