package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.BenefitUsage
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.MoneyUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RewardCategoryWireDto(
    val label: String = "",
    val category: String? = null,
    val rate: Double? = null,
    val multiplier: Double? = null,
    @SerialName("is_rotating") @Serializable(with = FlexibleBooleanSerializer::class) val isRotating: Boolean = false,
    @SerialName("isRotating") @Serializable(with = FlexibleBooleanSerializer::class) val isRotatingCamel: Boolean = false,
    val cap: Double? = null,
    @SerialName("cap_cents") val capCentsSnake: Long? = null,
    @SerialName("capCents") val capCentsCamel: Long? = null,
    val type: String? = "multiplier",
    val notes: String? = null
) {
    fun toDomain(): RewardCategory = RewardCategory(
        category = category ?: label,
        multiplier = rate ?: multiplier ?: 1.0,
        isRotating = isRotating || isRotatingCamel,
        capCents = cap?.let { MoneyUtils.dollarsToCents(it) } ?: capCentsSnake ?: capCentsCamel,
        description = notes,
        label = label,
        rateType = type ?: "multiplier",
        notes = notes
    )

    companion object {
        fun fromDomain(d: RewardCategory): RewardCategoryWireDto = RewardCategoryWireDto(
            label = d.label.ifBlank { d.category },
            category = d.category.takeIf { it.isNotBlank() },
            rate = d.multiplier,
            isRotating = d.isRotating,
            cap = d.capCents?.let { MoneyUtils.centsToDollars(it) },
            type = d.rateType,
            notes = d.notes ?: d.description
        )
    }
}

@Serializable
data class BenefitUsageWireDto(
    @SerialName("period_key") val periodKey: String = "",
    val amount: Double = 0.0,
    @SerialName("used_on") val usedOn: String? = null
) {
    fun toDomain(): BenefitUsage = BenefitUsage(
        periodKey = periodKey,
        amountCents = MoneyUtils.dollarsToCents(amount),
        usedOn = usedOn
    )

    companion object {
        fun fromDomain(d: BenefitUsage): BenefitUsageWireDto = BenefitUsageWireDto(
            periodKey = d.periodKey,
            amount = MoneyUtils.centsToDollars(d.amountCents),
            usedOn = d.usedOn
        )
    }
}

@Serializable
data class BenefitUsageRequestDto(
    @SerialName("period_key") val periodKey: String,
    val amount: Double?,
    @SerialName("used_on") val usedOn: String? = null
)

@Serializable
data class CardBenefitWireDto(
    val id: String = "",
    val name: String = "",
    val title: String = "",
    val value: JsonElement? = null,
    @SerialName("period_value") val periodValue: Double? = null,
    @SerialName("period_value_cents") val periodValueCentsWire: Long? = null,
    val description: String? = null,
    val category: String? = null,
    val cadence: String? = "annual",
    val anchor: String? = "calendar",
    val usage: List<BenefitUsageWireDto> = emptyList(),
    @SerialName("used_this_year") @Serializable(with = FlexibleBooleanSerializer::class) val usedThisYear: Boolean = false,
    @SerialName("used_periods") val usedPeriods: List<String> = emptyList()
) {
    fun toDomain(): CardBenefit = CardBenefit(
        id = id,
        title = title.ifBlank { name },
        description = description,
        periodValueCents = periodValue?.let { MoneyUtils.dollarsToCents(it) } ?: periodValueCentsWire,
        annualCreditValueCents = (value as? JsonPrimitive)?.content?.toDoubleOrNull()?.let { MoneyUtils.dollarsToCents(it) },
        usedThisYear = usedThisYear,
        category = category,
        valueText = (value as? JsonPrimitive)?.content,
        cadence = BenefitCadence.fromStorage(cadence),
        anchor = BenefitAnchor.fromStorage(anchor),
        usage = usage.map { it.toDomain() },
        usedPeriods = usedPeriods
    )

    companion object {
        fun fromDomain(d: CardBenefit): CardBenefitWireDto {
            val annualizedDollars = when {
                d.periodValueCents != null -> {
                    val months = d.cadence.periodMonths.coerceAtLeast(1)
                    MoneyUtils.centsToDollars(d.periodValueCents * 12 / months)
                }
                d.annualCreditValueCents != null -> MoneyUtils.centsToDollars(d.annualCreditValueCents)
                else -> null
            }
            return CardBenefitWireDto(
                id = d.id,
                name = d.title,
                title = d.title,
                value = annualizedDollars?.let { JsonPrimitive(it) } ?: d.valueText?.let { JsonPrimitive(it) },
                periodValue = d.periodValueCents?.let { MoneyUtils.centsToDollars(it) },
                description = d.description,
                category = d.category,
                cadence = d.cadence.storageKey,
                anchor = d.anchor.storageKey,
                usage = d.usage.map { BenefitUsageWireDto.fromDomain(it) },
                usedThisYear = d.usedThisYear,
                usedPeriods = d.usedPeriods
            )
        }
    }
}

@Serializable
data class CreditCardStrategyWireDto(
    @SerialName("cardholder_member_id") val cardholderMemberId: Long? = null,
    @SerialName("authorized_user_member_ids") val authorizedUserMemberIds: List<Long> = emptyList(),
    val cardholder: String? = null,
    @SerialName("opened_date") val openedDate: String? = null,
    @SerialName("closed_date") val closedDate: String? = null,
    @SerialName("is_business_card") @Serializable(with = FlexibleBooleanSerializer::class) val isBusinessCard: Boolean = false,
    @SerialName("counts_toward_524_override") @Serializable(with = FlexibleNullableBooleanSerializer::class) val countsToward524Override: Boolean? = null,
    @SerialName("product_changed_from") val productChangedFrom: String? = null,
    @SerialName("sub_reward_text") val subRewardText: String? = null,
    @SerialName("sub_estimated_value") val subEstimatedValue: Double? = null,
    @SerialName("sub_target_spend") val subTargetSpend: Double? = null,
    @SerialName("sub_deadline_date") val subDeadlineDate: String? = null,
    @SerialName("sub_manual_spend_offset") val subManualSpendOffset: Double = 0.0,
    @SerialName("sub_completed") @Serializable(with = FlexibleBooleanSerializer::class) val subCompleted: Boolean = false,
    @SerialName("sub_received_date") val subReceivedDate: String? = null,
    @SerialName("sub_cooldown_months") val subCooldownMonths: Int? = null
) {
    fun toDomain(): CreditCardStrategy = CreditCardStrategy(
        cardholderMemberId = cardholderMemberId,
        authorizedUserMemberIds = authorizedUserMemberIds,
        cardholder = cardholder,
        openedDate = openedDate,
        closedDate = closedDate,
        isBusinessCard = isBusinessCard,
        countsToward524Override = countsToward524Override,
        productChangedFrom = productChangedFrom,
        subRewardText = subRewardText,
        subEstimatedValueCents = subEstimatedValue?.let { MoneyUtils.dollarsToCents(it) },
        subTargetSpendCents = subTargetSpend?.let { MoneyUtils.dollarsToCents(it) },
        subDeadlineDate = subDeadlineDate,
        subManualSpendOffsetCents = MoneyUtils.dollarsToCents(subManualSpendOffset),
        subCompleted = subCompleted,
        subReceivedDate = subReceivedDate,
        subCooldownMonths = subCooldownMonths
    )

    companion object {
        fun fromDomain(d: CreditCardStrategy): CreditCardStrategyWireDto = CreditCardStrategyWireDto(
            cardholderMemberId = d.cardholderMemberId,
            authorizedUserMemberIds = d.authorizedUserMemberIds,
            cardholder = d.cardholder,
            openedDate = d.openedDate,
            closedDate = d.closedDate,
            isBusinessCard = d.isBusinessCard,
            countsToward524Override = d.countsToward524Override,
            productChangedFrom = d.productChangedFrom,
            subRewardText = d.subRewardText,
            subEstimatedValue = d.subEstimatedValueCents?.let { MoneyUtils.centsToDollars(it) },
            subTargetSpend = d.subTargetSpendCents?.let { MoneyUtils.centsToDollars(it) },
            subDeadlineDate = d.subDeadlineDate,
            subManualSpendOffset = MoneyUtils.centsToDollars(d.subManualSpendOffsetCents),
            subCompleted = d.subCompleted,
            subReceivedDate = d.subReceivedDate,
            subCooldownMonths = d.subCooldownMonths
        )
    }
}

@Serializable
data class CreditCardProfileUpsertRequestDto(
    @SerialName("account_name") val accountName: String,
    @SerialName("account_institution") val accountInstitution: String? = null,
    @SerialName("card_name") val cardName: String? = null,
    @SerialName("issuer_name") val issuerName: String? = null,
    val network: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("annual_fee") val annualFee: Double? = null,
    @SerialName("annual_fee_post_date") val annualFeePostDate: String? = null,
    @SerialName("credit_limit") val creditLimit: Double? = null,
    @SerialName("statement_closing_day") val statementClosingDay: Int? = null,
    @SerialName("payment_due_day") val paymentDueDay: Int? = null,
    @SerialName("apr_bps") val aprBps: Int? = null,
    @SerialName("promo_apr_bps") val promoAprBps: Int? = null,
    @SerialName("promo_end_date") val promoEndDate: String? = null,
    @SerialName("authorized_users") val authorizedUsers: List<String> = emptyList(),
    @SerialName("reward_categories") val rewardCategories: List<RewardCategoryWireDto> = emptyList(),
    val benefits: List<CardBenefitWireDto> = emptyList(),
    val strategy: CreditCardStrategyWireDto? = null,
    val notes: String? = null
)

@Serializable
data class CreditCardProfileMutationResponseDto(
    val success: Boolean = true,
    val account: AccountDto? = null,
    val profile: CreditCardProfileDto? = null
)

@Serializable
data class CardCatalogItemDto(
    val id: String,
    @SerialName("short_label") val shortLabel: String = "",
    @SerialName("card_name") val cardName: String = "",
    @SerialName("issuer_name") val issuerName: String = "",
    val network: String = "Visa",
    @SerialName("image_url") val imageUrl: String = "preset://sapphire-blue",
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("direct_artwork_url") val directArtworkUrl: String? = null,
    @SerialName("annual_fee_cents") val annualFeeCents: Long = 0L,
    @SerialName("is_annual_fee_waived") val isAnnualFeeWaived: Boolean = false,
    @SerialName("is_business_card") val isBusinessCard: Boolean = false,
    @SerialName("counts_toward_524_override") val countsToward524Override: Boolean? = null,
    @SerialName("sub_cooldown_months") val subCooldownMonths: Int? = 24,
    @SerialName("default_sub_reward_text") val defaultSubRewardText: String? = null,
    @SerialName("default_sub_estimated_value_cents") val defaultSubEstimatedValueCents: Long? = null,
    @SerialName("default_sub_target_spend_cents") val defaultSubTargetSpendCents: Long? = null,
    @SerialName("sub_spend_window_days") val subSpendWindowDays: Int? = 90,
    @SerialName("is_elevated_sub_offer") val isElevatedSubOffer: Boolean = false,
    @SerialName("reward_categories") val rewardCategories: List<RewardCategoryWireDto> = emptyList(),
    val benefits: List<CardBenefitWireDto> = emptyList(),
    @SerialName("as_of") val asOf: String = "2026-11"
)

@Serializable
data class CardCatalogResponseDto(
    val items: List<CardCatalogItemDto> = emptyList()
)
