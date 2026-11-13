package app.overbay.orbit.ui.accounts.cards

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils

enum class CardEditorStep { PICK_TEMPLATE, FORM }
enum class WelcomeBonusMode { NONE, WORKING, EARNED }

enum class CardEditorTab(val label: String) {
    CARD("Card"),
    BILLING("Billing"),
    REWARDS("Rewards"),
    STRATEGY("Strategy")
}

data class CardEditorDraft(
    val accountId: Long,
    val step: CardEditorStep = CardEditorStep.FORM,
    val activeTab: CardEditorTab = CardEditorTab.CARD,
    val accountName: String = "",
    val accountInstitution: String = "",
    // Appearance & Identity (hybrid artworkUrl + CardArtPalette fallback)
    val cardName: String = "",
    val issuer: String = "",
    val network: String = "Visa",
    val artKey: String? = null,
    val artworkUrl: String? = null,
    // Billing & Cycle
    val creditLimitCents: Cents? = null,
    val statementCloseDay: Int? = null,
    val statementCloseDayText: String = "",
    val paymentDueDay: Int? = null,
    val paymentDueDayText: String = "",
    val aprBps: Int? = null,
    val aprText: String = "",
    val hasPromo: Boolean = false,
    val promoAprBps: Int? = null,
    val promoAprText: String = "",
    val promoEndDate: String? = null,
    // Annual Fee
    val hasAnnualFee: Boolean = false,
    val annualFeeCents: Cents? = null,
    val annualFeePostDate: String? = null,
    // Strategy & Household
    val cardholderMemberId: Long? = null,
    val cardholder: String? = null,
    val openedDate: String? = null,
    val closedDate: String? = null,
    val isBusinessCard: Boolean = false,
    val countsToward524Override: Boolean? = null,
    // Welcome Bonus
    val subRewardText: String? = null,
    val subEstimatedValueCents: Cents? = null,
    val targetSpendCents: Cents? = null,
    val targetDeadlineDate: String? = null,
    val subManualSpendOffsetCents: Cents = 0L,
    val subCompleted: Boolean = false,
    val subReceivedDate: String? = null,
    val subCooldownMonths: Int? = null,
    val subSpendWindowDays: Int? = null,
    // Lists & Notes
    val benefits: List<CardBenefit> = emptyList(),
    val rewardCategories: List<RewardCategory> = emptyList(),
    val rewardRowIds: List<Long> = emptyList(),
    val nextRewardRowId: Long = 1L,
    val authorizedUsers: List<String> = emptyList(),
    val authorizedUserMemberIds: List<Long> = emptyList(),
    val notes: String? = null,
    // UI Controls & baselines
    val templateQuery: String = "",
    val templateCategoryFilter: String = "All",
    val editingPerkId: String? = null,
    val editingRewardIndex: Int? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val showDiscardConfirm: Boolean = false,
    val lastDeletedBenefit: CardBenefit? = null,
    val originalProfile: CreditCardProfile? = null,
    val originalAccountName: String = "",
    val originalAccountInstitution: String = ""
) {
    val welcomeBonusMode: WelcomeBonusMode
        get() = when {
            subReceivedDate != null || subCompleted -> WelcomeBonusMode.EARNED
            targetSpendCents != null || !subRewardText.isNullOrBlank() -> WelcomeBonusMode.WORKING
            else -> WelcomeBonusMode.NONE
        }

    val errors: Map<String, String>
        get() {
            val errs = mutableMapOf<String, String>()
            if (statementCloseDayText.isNotBlank()) {
                val day = statementCloseDayText.toIntOrNull()
                if (day == null || day !in 1..31) errs["statementCloseDay"] = "Closing day must be between 1 and 31"
            }
            if (paymentDueDayText.isNotBlank()) {
                val day = paymentDueDayText.toIntOrNull()
                if (day == null || day !in 1..31) errs["paymentDueDay"] = "Due day must be between 1 and 31"
            }
            if (aprText.isNotBlank() && parseBps(aprText) == null) {
                errs["apr"] = "APR must be between 0% and 100%"
            }
            if (hasPromo) {
                if (promoAprText.isNotBlank() && parseBps(promoAprText) == null) {
                    errs["promoApr"] = "Promo APR must be between 0% and 100%"
                }
                if (promoEndDate.isNullOrBlank() || LocalDateUtils.parseIsoDate(promoEndDate) == null) {
                    errs["promoEndDate"] = "Valid promo end date is required"
                }
            }
            if (!openedDate.isNullOrBlank() && LocalDateUtils.parseIsoDate(openedDate) == null) {
                errs["openedDate"] = "Invalid opened date format"
            }
            if (!targetDeadlineDate.isNullOrBlank() && LocalDateUtils.parseIsoDate(targetDeadlineDate) == null) {
                errs["targetDeadlineDate"] = "Invalid deadline date format"
            }
            if (hasAnnualFee && !annualFeePostDate.isNullOrBlank() && LocalDateUtils.parseIsoDate(annualFeePostDate) == null) {
                errs["annualFeePostDate"] = "Invalid annual fee post date"
            }
            return errs
        }

    val isValid: Boolean get() = errors.isEmpty()

    fun tabForErrorKey(key: String): CardEditorTab = when (key) {
        "statementCloseDay", "paymentDueDay", "apr", "promoApr", "promoEndDate", "annualFeePostDate" -> CardEditorTab.BILLING
        "openedDate", "targetDeadlineDate" -> CardEditorTab.STRATEGY
        else -> CardEditorTab.CARD
    }

    fun tabHasErrors(tab: CardEditorTab): Boolean = errors.keys.any { tabForErrorKey(it) == tab }

    val firstErrorBanner: Pair<CardEditorTab?, String>?
        get() = saveError?.takeIf { it.isNotBlank() }?.let { null to it }
            ?: errors.entries.firstOrNull()?.let { (key, msg) -> tabForErrorKey(key) to msg }

    val isDirty: Boolean
        get() = toProfile() != (originalProfile ?: CreditCardProfile(accountId = accountId)) ||
            accountName != originalAccountName ||
            accountInstitution != originalAccountInstitution

    fun toProfile(): CreditCardProfile {
        val base = originalProfile ?: CreditCardProfile(accountId = accountId)
        val feeCents = if (hasAnnualFee && (annualFeeCents ?: 0L) > 0L) annualFeeCents else null
        val limitCents = if ((creditLimitCents ?: 0L) > 0L) creditLimitCents else null
        val feePost = if (hasAnnualFee) annualFeePostDate?.takeIf { it.isNotBlank() } else null

        return base.copy(
            cardName = cardName.trim().ifBlank { null },
            issuerName = issuer.trim().ifBlank { null },
            network = network.trim().ifBlank { null },
            imageUrl = artKey?.trim()?.ifBlank { null },
            artworkUrl = artworkUrl?.trim()?.ifBlank { null },
            creditLimitCents = limitCents,
            annualFeeCents = feeCents,
            annualFeePostDate = feePost,
            statementClosingDay = statementCloseDayText.toIntOrNull()?.takeIf { it in 1..31 },
            paymentDueDay = paymentDueDayText.toIntOrNull()?.takeIf { it in 1..31 },
            aprBps = aprText.takeIf { it.isNotBlank() }?.let { parseBps(it) },
            promoAprBps = if (hasPromo) promoAprText.takeIf { it.isNotBlank() }?.let { parseBps(it) } else null,
            promoEndDate = if (hasPromo) promoEndDate?.trim()?.ifBlank { null } else null,
            benefits = benefits,
            rewardCategories = rewardCategories,
            authorizedUsers = authorizedUsers,
            notes = notes?.trim()?.ifBlank { null },
            strategy = base.strategy.copy(
                cardholderMemberId = cardholderMemberId,
                cardholder = cardholder?.trim()?.ifBlank { null },
                openedDate = openedDate?.trim()?.ifBlank { null },
                closedDate = closedDate?.trim()?.ifBlank { null },
                isBusinessCard = isBusinessCard,
                countsToward524Override = countsToward524Override,
                subRewardText = subRewardText?.trim()?.ifBlank { null },
                subEstimatedValueCents = subEstimatedValueCents,
                subTargetSpendCents = targetSpendCents,
                subDeadlineDate = targetDeadlineDate?.trim()?.ifBlank { null },
                subManualSpendOffsetCents = subManualSpendOffsetCents,
                subCompleted = subCompleted,
                subReceivedDate = subReceivedDate?.trim()?.ifBlank { null },
                subCooldownMonths = subCooldownMonths,
                authorizedUserMemberIds = authorizedUserMemberIds
            )
        )
    }

    companion object {
        fun fromAccountAndProfile(account: Account, profile: CreditCardProfile?): CardEditorDraft {
            val p = profile ?: CreditCardProfile(accountId = account.id)
            val strat = p.strategy
            val isInitialSetup = p.cardName.isNullOrBlank() && p.benefits.isEmpty() && p.rewardCategories.isEmpty()

            val draft = CardEditorDraft(
                accountId = account.id,
                step = if (isInitialSetup) CardEditorStep.PICK_TEMPLATE else CardEditorStep.FORM,
                accountName = account.name,
                accountInstitution = account.institution.orEmpty(),
                cardName = p.cardName.orEmpty(),
                issuer = p.issuerName.orEmpty(),
                network = p.network ?: "Visa",
                artKey = p.imageUrl,
                artworkUrl = p.artworkUrl,
                creditLimitCents = p.creditLimitCents,
                statementCloseDay = p.statementClosingDay, statementCloseDayText = p.statementClosingDay?.toString().orEmpty(),
                paymentDueDay = p.paymentDueDay, paymentDueDayText = p.paymentDueDay?.toString().orEmpty(),
                aprBps = p.aprBps, aprText = formatBpsForInput(p.aprBps),
                hasPromo = p.promoAprBps != null || !p.promoEndDate.isNullOrBlank(),
                promoAprBps = p.promoAprBps, promoAprText = formatBpsForInput(p.promoAprBps), promoEndDate = p.promoEndDate,
                hasAnnualFee = (p.annualFeeCents ?: 0L) > 0L || p.annualFeePostDate != null,
                annualFeeCents = p.annualFeeCents,
                annualFeePostDate = p.annualFeePostDate,
                cardholderMemberId = strat.cardholderMemberId, cardholder = strat.cardholder,
                openedDate = strat.openedDate, closedDate = strat.closedDate,
                isBusinessCard = strat.isBusinessCard, countsToward524Override = strat.countsToward524Override,
                subRewardText = strat.subRewardText, subEstimatedValueCents = strat.subEstimatedValueCents,
                targetSpendCents = strat.subTargetSpendCents,
                targetDeadlineDate = strat.subDeadlineDate, subManualSpendOffsetCents = strat.subManualSpendOffsetCents,
                subCompleted = strat.subCompleted, subReceivedDate = strat.subReceivedDate, subCooldownMonths = strat.subCooldownMonths,
                benefits = p.benefits,
                rewardCategories = p.rewardCategories,
                rewardRowIds = List(p.rewardCategories.size) { it + 1L },
                nextRewardRowId = p.rewardCategories.size + 1L,
                authorizedUsers = p.authorizedUsers,
                authorizedUserMemberIds = strat.authorizedUserMemberIds,
                notes = p.notes,
                originalProfile = profile,
                originalAccountName = account.name,
                originalAccountInstitution = account.institution.orEmpty()
            )
            return draft.copy(originalProfile = draft.toProfile())
        }

        fun parseBps(text: String): Int? {
            val clean = text.trim().removeSuffix("%").trim()
            if (clean.isBlank()) return null
            val parts = clean.split('.')
            val whole = parts[0].toIntOrNull() ?: return null
            if (whole < 0 || whole > 100) return null
            val frac = when {
                parts.size == 1 -> 0
                parts.size == 2 -> {
                    val f = parts[1]
                    when (f.length) {
                        0 -> 0
                        1 -> (f + "0").toIntOrNull() ?: return null
                        2 -> f.toIntOrNull() ?: return null
                        else -> f.take(2).toIntOrNull() ?: return null
                    }
                }
                else -> return null
            }
            val total = whole * 100 + frac
            return if (total in 0..10000) total else null
        }

        fun formatBpsForInput(bps: Int?): String {
            if (bps == null) return ""
            val whole = bps / 100
            val frac = bps % 100
            return if (frac == 0) "$whole" else "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}"
        }
    }
}
