package app.overbay.orbit.domain

import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory

data class CreditCardTemplate(
    val id: String,
    val shortLabel: String,
    val cardName: String,
    val issuerName: String,
    val network: String,
    val imageUrl: String,
    val annualFeeCents: Cents,
    val isBusinessCard: Boolean = false,
    val subCooldownMonths: Int? = 24,
    val defaultSubRewardText: String? = null,
    val defaultSubTargetSpendCents: Cents? = null,
    val rewardCategories: List<RewardCategory> = emptyList(),
    val benefits: List<CardBenefit> = emptyList(),
    val asOf: String = "2025-01"
) {
    fun applyToProfile(existing: CreditCardProfile): CreditCardProfile {
        val existingBenefitsByTitle = existing.benefits.associateBy { it.title.trim().lowercase() }
        val mergedBenefits = benefits.map { templateBenefit ->
            val existingBenefit = existingBenefitsByTitle[templateBenefit.title.trim().lowercase()]
            if (existingBenefit != null) {
                templateBenefit.copy(
                    usedThisYear = existingBenefit.usedThisYear,
                    usedPeriods = existingBenefit.usedPeriods
                )
            } else {
                templateBenefit
            }
        } + existing.benefits.filter { existingBenefit ->
            val key = existingBenefit.title.trim().lowercase()
            benefits.none { it.title.trim().lowercase() == key }
        }

        val templateCategoryLabels = rewardCategories.map { it.label.trim().lowercase() }.toSet()
        val mergedRewardCategories = rewardCategories + existing.rewardCategories.filter {
            it.label.trim().lowercase() !in templateCategoryLabels
        }

        return existing.copy(
            cardName = cardName,
            issuerName = issuerName,
            network = network,
            imageUrl = imageUrl,
            annualFeeCents = annualFeeCents,
            rewardCategories = mergedRewardCategories,
            benefits = mergedBenefits,
            strategy = existing.strategy.copy(
                isBusinessCard = existing.strategy.isBusinessCard || isBusinessCard,
                subCooldownMonths = existing.strategy.subCooldownMonths ?: subCooldownMonths,
                subRewardText = existing.strategy.subRewardText ?: defaultSubRewardText,
                subTargetSpendCents = existing.strategy.subTargetSpendCents ?: defaultSubTargetSpendCents
            )
        )
    }
}

object CreditCardTemplates {

    val ALL: List<CreditCardTemplate> = listOf(
        CreditCardTemplate(
            id = "csr",
            shortLabel = "Sapphire Reserve",
            cardName = "Sapphire Reserve",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "preset://sapphire-blue",
            annualFeeCents = 550_00L,
            subCooldownMonths = 48,
            defaultSubRewardText = "60,000 Ultimate Rewards pts",
            defaultSubTargetSpendCents = 4000_00L,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Travel & Dining", multiplier = 3.0),
                RewardCategory(category = "Dining & Restaurants", label = "Dining", multiplier = 3.0),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 1.0)
            ),
            benefits = listOf(
                CardBenefit(title = "$300 Annual Travel Credit", annualCreditValueCents = 300_00L, cadence = BenefitCadence.ANNUAL),
                CardBenefit(title = "DashPass & $5/mo DoorDash Promo", annualCreditValueCents = 60_00L, cadence = BenefitCadence.MONTHLY),
                CardBenefit(title = "Global Entry / TSA PreCheck Credit", annualCreditValueCents = 120_00L, cadence = BenefitCadence.EVERY_4_YEARS),
                CardBenefit(title = "Priority Pass Select Lounge Access", annualCreditValueCents = null, cadence = BenefitCadence.ANNUAL)
            )
        ),
        CreditCardTemplate(
            id = "csp",
            shortLabel = "Sapphire Preferred",
            cardName = "Sapphire Preferred",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "preset://sapphire-blue",
            annualFeeCents = 95_00L,
            subCooldownMonths = 48,
            defaultSubRewardText = "60,000 Ultimate Rewards pts",
            defaultSubTargetSpendCents = 4000_00L,
            rewardCategories = listOf(
                RewardCategory(category = "Dining & Restaurants", label = "Dining", multiplier = 3.0),
                RewardCategory(category = "Streaming & Entertainment", label = "Select Streaming", multiplier = 3.0),
                RewardCategory(category = "Groceries", label = "Online Groceries", multiplier = 3.0),
                RewardCategory(category = "Travel & Flights", label = "Travel", multiplier = 2.0)
            ),
            benefits = listOf(
                CardBenefit(title = "$50 Annual Chase Travel Hotel Credit", annualCreditValueCents = 50_00L, cadence = BenefitCadence.ANNUAL),
                CardBenefit(title = "$10/mo DoorDash Non-Restaurant Credit", annualCreditValueCents = 120_00L, cadence = BenefitCadence.MONTHLY)
            )
        ),
        CreditCardTemplate(
            id = "cfu",
            shortLabel = "Freedom Unlimited",
            cardName = "Freedom Unlimited",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "preset://sapphire-blue",
            annualFeeCents = 0L,
            subCooldownMonths = 24,
            defaultSubRewardText = "$200 / 20,000 UR Bonus",
            defaultSubTargetSpendCents = 500_00L,
            rewardCategories = listOf(
                RewardCategory(category = "Dining & Restaurants", label = "Dining", multiplier = 3.0),
                RewardCategory(category = "Drugstores", label = "Drugstores", multiplier = 3.0),
                RewardCategory(category = "Everything Else", label = "Everything Else (Catch-All)", multiplier = 1.5)
            )
        ),
        CreditCardTemplate(
            id = "cff",
            shortLabel = "Freedom Flex",
            cardName = "Freedom Flex",
            issuerName = "Chase",
            network = "Mastercard",
            imageUrl = "preset://sapphire-blue",
            annualFeeCents = 0L,
            subCooldownMonths = 24,
            defaultSubRewardText = "$200 / 20,000 UR Bonus",
            defaultSubTargetSpendCents = 500_00L,
            rewardCategories = listOf(
                RewardCategory(category = "Quarterly Bonus", label = "5x Rotating Quarterly ($1,500 cap)", multiplier = 5.0, isRotating = true, capCents = 1500_00L),
                RewardCategory(category = "Dining & Restaurants", label = "Dining", multiplier = 3.0),
                RewardCategory(category = "Drugstores", label = "Drugstores", multiplier = 3.0)
            )
        ),
        CreditCardTemplate(
            id = "ink_cash",
            shortLabel = "Ink Business Cash",
            cardName = "Ink Business Cash",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "preset://sapphire-blue",
            annualFeeCents = 0L,
            isBusinessCard = true,
            subCooldownMonths = 24,
            defaultSubRewardText = "75,000 Ultimate Rewards pts",
            defaultSubTargetSpendCents = 6000_00L,
            rewardCategories = listOf(
                RewardCategory(category = "Office & Internet", label = "Office Supplies, Internet & Phone", multiplier = 5.0),
                RewardCategory(category = "Gas & Transit", label = "Gas & Restaurants", multiplier = 2.0)
            )
        ),
        CreditCardTemplate(
            id = "amex_gold",
            shortLabel = "Amex Gold",
            cardName = "American Express Gold",
            issuerName = "American Express",
            network = "Amex",
            imageUrl = "preset://amex-gold",
            annualFeeCents = 325_00L,
            subCooldownMonths = 999,
            defaultSubRewardText = "90,000 Membership Rewards pts",
            defaultSubTargetSpendCents = 6000_00L,
            rewardCategories = listOf(
                RewardCategory(category = "Dining & Restaurants", label = "Restaurants Worldwide", multiplier = 4.0),
                RewardCategory(category = "Groceries", label = "U.S. Supermarkets ($25k cap)", multiplier = 4.0, capCents = 25000_00L),
                RewardCategory(category = "Travel & Flights", label = "Flights Booked Direct", multiplier = 3.0)
            ),
            benefits = listOf(
                CardBenefit(title = "$10/mo Uber Cash ($120/yr)", annualCreditValueCents = 120_00L, cadence = BenefitCadence.MONTHLY),
                CardBenefit(title = "$10/mo Dining Credit ($120/yr)", annualCreditValueCents = 120_00L, cadence = BenefitCadence.MONTHLY),
                CardBenefit(title = "$50 Semi-Annual Resy Credit ($100/yr)", annualCreditValueCents = 100_00L, cadence = BenefitCadence.SEMI_ANNUAL),
                CardBenefit(title = "$7/mo Dunkin' Credit ($84/yr)", annualCreditValueCents = 84_00L, cadence = BenefitCadence.MONTHLY)
            )
        ),
        CreditCardTemplate(
            id = "amex_plat",
            shortLabel = "Amex Platinum",
            cardName = "The Platinum Card",
            issuerName = "American Express",
            network = "Amex",
            imageUrl = "preset://venture-silver",
            annualFeeCents = 695_00L,
            subCooldownMonths = 999,
            defaultSubRewardText = "125,000 Membership Rewards pts",
            defaultSubTargetSpendCents = 8000_00L,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Flights & Prepaid Hotels", multiplier = 5.0),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 1.0)
            ),
            benefits = listOf(
                CardBenefit(title = "Uber Cash ($15/mo + $20 Dec)", annualCreditValueCents = 200_00L, cadence = BenefitCadence.MONTHLY),
                CardBenefit(title = "$20/mo Digital Entertainment Credit", annualCreditValueCents = 240_00L, cadence = BenefitCadence.MONTHLY),
                CardBenefit(title = "$200 Hotel Credit (FHR / THC)", annualCreditValueCents = 200_00L, cadence = BenefitCadence.ANNUAL),
                CardBenefit(title = "$200 Airline Incidental Fee Credit", annualCreditValueCents = 200_00L, cadence = BenefitCadence.ANNUAL),
                CardBenefit(title = "$50 Semi-Annual Saks Credit ($100/yr)", annualCreditValueCents = 100_00L, cadence = BenefitCadence.SEMI_ANNUAL),
                CardBenefit(title = "$199 CLEAR Plus Credit", annualCreditValueCents = 199_00L, cadence = BenefitCadence.ANNUAL)
            )
        ),
        CreditCardTemplate(
            id = "venture_x",
            shortLabel = "Venture X",
            cardName = "Venture X",
            issuerName = "Capital One",
            network = "Visa",
            imageUrl = "preset://venture-silver",
            annualFeeCents = 395_00L,
            subCooldownMonths = 48,
            defaultSubRewardText = "75,000 Capital One Miles",
            defaultSubTargetSpendCents = 4000_00L,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Portal Hotels & Rental Cars", multiplier = 10.0),
                RewardCategory(category = "Everything Else", label = "Everything Else (Catch-All)", multiplier = 2.0)
            ),
            benefits = listOf(
                CardBenefit(title = "$300 Annual Capital One Travel Credit", annualCreditValueCents = 300_00L, cadence = BenefitCadence.ANNUAL),
                CardBenefit(title = "10,000 Anniversary Bonus Miles ($100 val)", annualCreditValueCents = 100_00L, cadence = BenefitCadence.ANNUAL),
                CardBenefit(title = "Global Entry / TSA PreCheck Credit", annualCreditValueCents = 120_00L, cadence = BenefitCadence.EVERY_4_YEARS)
            )
        ),
        CreditCardTemplate(
            id = "bilt",
            shortLabel = "Bilt Mastercard",
            cardName = "Bilt Mastercard",
            issuerName = "Wells Fargo / Bilt",
            network = "Mastercard",
            imageUrl = "preset://apple-card",
            annualFeeCents = 0L,
            subCooldownMonths = null,
            rewardCategories = listOf(
                RewardCategory(category = "Dining & Restaurants", label = "Dining", multiplier = 3.0),
                RewardCategory(category = "Travel & Flights", label = "Travel", multiplier = 2.0),
                RewardCategory(category = "Rent", label = "Rent (No Transaction Fee)", multiplier = 1.0)
            )
        )
    )
}
