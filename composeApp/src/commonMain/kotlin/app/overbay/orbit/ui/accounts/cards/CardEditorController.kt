package app.overbay.orbit.ui.accounts.cards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.refreshCardCatalog
import app.overbay.orbit.data.saveCreditCardProfile
import app.overbay.orbit.domain.CreditCardBenefitEngine
import app.overbay.orbit.domain.CreditCardTemplate
import app.overbay.orbit.domain.LocalDateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

class CardEditorController(
    private val repository: OrbitRepository
) {
    private val catalogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _draftState = MutableStateFlow<CardEditorDraft?>(null)
    val draftState: StateFlow<CardEditorDraft?> = _draftState.asStateFlow()
    val draft: StateFlow<CardEditorDraft?> get() = draftState
    var currentDraft by mutableStateOf<CardEditorDraft?>(null)
        private set

    private fun setDraftValue(next: CardEditorDraft?) {
        _draftState.value = next
        Snapshot.withMutableSnapshot { currentDraft = next }
    }

    private inline fun modifyDraft(transform: (CardEditorDraft?) -> CardEditorDraft?) {
        val next = _draftState.updateAndGet(transform)
        Snapshot.withMutableSnapshot { currentDraft = next }
    }

    fun startDraft(account: Account, profile: CreditCardProfile? = null) {
        setDraftValue(CardEditorDraft.fromAccountAndProfile(account, profile ?: account.creditCardProfile))
        catalogScope.launch {
            runCatching { repository.refreshCardCatalog() }
        }
    }

    fun open(account: Account) {
        startDraft(account, account.creditCardProfile)
    }

    fun clearDraft() {
        setDraftValue(null)
    }

    fun confirmDiscard() {
        clearDraft()
    }

    fun requestClose() {
        val current = _draftState.value ?: return
        if (current.isDirty) {
            modifyDraft { it?.copy(showDiscardConfirm = true) }
        } else {
            clearDraft()
        }
    }

    fun dismissDiscardConfirm() {
        modifyDraft { it?.copy(showDiscardConfirm = false) }
    }

    fun skipTemplate() {
        modifyDraft { it?.copy(step = CardEditorStep.FORM) }
    }

    fun selectTab(tab: CardEditorTab) {
        modifyDraft { it?.copy(activeTab = tab) }
    }

    fun updateDraft(transform: (CardEditorDraft) -> CardEditorDraft) {
        modifyDraft { current -> current?.let(transform) }
    }

    fun applyTemplate(template: CreditCardTemplate) {
        modifyDraft { current ->
            if (current == null) return@modifyDraft null
            val baseProfile = current.toProfile()
            val applied = template.applyToProfile(baseProfile)
            val resolvedInst = current.accountInstitution.takeIf { it.isNotBlank() } ?: template.issuerName

            current.copy(
                step = CardEditorStep.FORM,
                accountInstitution = resolvedInst,
                cardName = applied.cardName.orEmpty(),
                issuer = applied.issuerName.orEmpty(),
                network = applied.network ?: "Visa",
                artKey = applied.imageUrl,
                artworkUrl = applied.artworkUrl,
                annualFeeCents = applied.annualFeeCents,
                annualFeeText = applied.annualFeeCents?.let { (it / 100).toString() }.orEmpty(),
                hasAnnualFee = (applied.annualFeeCents ?: 0L) > 0L,
                benefits = applied.benefits,
                rewardCategories = applied.rewardCategories,
                isBusinessCard = applied.strategy.isBusinessCard,
                countsToward524Override = applied.strategy.countsToward524Override,
                subCooldownMonths = applied.strategy.subCooldownMonths,
                subSpendWindowDays = template.subSpendWindowDays,
                subRewardText = applied.strategy.subRewardText,
                subEstimatedValueCents = applied.strategy.subEstimatedValueCents,
                targetSpendCents = applied.strategy.subTargetSpendCents,
                targetSpendText = applied.strategy.subTargetSpendCents?.let { (it / 100).toString() }.orEmpty(),
                targetDeadlineDate = applied.strategy.subDeadlineDate
            )
        }
    }

    fun setOpenedDate(isoDate: String?) {
        modifyDraft { current ->
            if (current == null) return@modifyDraft null
            val autoDeadline = if (current.targetDeadlineDate.isNullOrBlank() && !isoDate.isNullOrBlank()) {
                LocalDateUtils.addDaysIso(isoDate, current.subSpendWindowDays ?: 90)
            } else {
                current.targetDeadlineDate
            }
            current.copy(openedDate = isoDate, targetDeadlineDate = autoDeadline)
        }
    }

    fun selectPrimaryCardholder(memberId: Long?, name: String?) {
        val cleanName = name?.trim()?.ifBlank { null }
        modifyDraft { current ->
            if (current == null) return@modifyDraft null
            val filteredMemberIds = if (memberId != null) {
                current.authorizedUserMemberIds.filterNot { it == memberId }
            } else current.authorizedUserMemberIds
            val filteredNames = if (cleanName != null) {
                current.authorizedUsers.filterNot { it.equals(cleanName, ignoreCase = true) }
            } else current.authorizedUsers
            current.copy(
                cardholderMemberId = memberId,
                cardholder = cleanName,
                authorizedUserMemberIds = filteredMemberIds,
                authorizedUsers = filteredNames
            )
        }
    }

    fun toggleAuthorizedMember(memberId: Long, memberName: String) {
        modifyDraft { current ->
            if (current == null || current.cardholderMemberId == memberId) return@modifyDraft current
            val exists = current.authorizedUserMemberIds.contains(memberId)
            val updatedIds = if (exists) {
                current.authorizedUserMemberIds - memberId
            } else {
                (current.authorizedUserMemberIds + memberId).distinct()
            }
            val cleanedCustom = current.authorizedUsers.filterNot { it.equals(memberName.trim(), ignoreCase = true) }
            current.copy(authorizedUserMemberIds = updatedIds, authorizedUsers = cleanedCustom)
        }
    }

    fun addCustomAuthorizedUser(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        modifyDraft { current ->
            if (current == null) return@modifyDraft null
            if (current.cardholder?.equals(clean, ignoreCase = true) == true) return@modifyDraft current
            if (current.authorizedUsers.any { it.equals(clean, ignoreCase = true) }) return@modifyDraft current
            current.copy(authorizedUsers = current.authorizedUsers + clean)
        }
    }

    fun removeCustomAuthorizedUser(name: String) {
        modifyDraft { current ->
            if (current == null) return@modifyDraft null
            current.copy(authorizedUsers = current.authorizedUsers.filterNot { it.equals(name, ignoreCase = true) })
        }
    }

    fun addBenefit(benefit: CardBenefit) {
        val resolved = if (benefit.id.isBlank()) benefit.copy(id = CreditCardBenefitEngine.newBenefitId()) else benefit
        modifyDraft { it?.copy(benefits = it.benefits + resolved) }
    }

    fun updateBenefit(benefit: CardBenefit) {
        modifyDraft { current ->
            if (current == null) return@modifyDraft null
            val updated = current.benefits.map { if (it.id == benefit.id) benefit else it }
            current.copy(benefits = updated)
        }
    }

    fun removeBenefit(benefitId: String) {
        modifyDraft { current ->
            if (current == null) return@modifyDraft null
            val target = current.benefits.find { it.id == benefitId }
            val remaining = current.benefits.filterNot { it.id == benefitId }
            current.copy(benefits = remaining, lastDeletedBenefit = target)
        }
    }

    fun undoRemoveBenefit() {
        modifyDraft { current ->
            val toRestore = current?.lastDeletedBenefit ?: return@modifyDraft current
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
        modifyDraft { it?.copy(rewardCategories = it.rewardCategories + reward) }
    }

    fun updateReward(index: Int, reward: RewardCategory) {
        modifyDraft { current ->
            if (current == null || index !in current.rewardCategories.indices) return@modifyDraft current
            val updated = current.rewardCategories.toMutableList().also { it[index] = reward }
            current.copy(rewardCategories = updated)
        }
    }

    fun removeReward(index: Int) {
        modifyDraft { current ->
            if (current == null || index !in current.rewardCategories.indices) return@modifyDraft current
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
        modifyDraft { current ->
            if (current == null) return@modifyDraft null
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

        modifyDraft { it?.copy(isSaving = true) }
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
            modifyDraft { it?.copy(isSaving = false) }
        }
    }
}
