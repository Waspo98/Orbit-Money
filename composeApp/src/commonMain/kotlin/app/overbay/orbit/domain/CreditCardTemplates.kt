package app.overbay.orbit.domain

import app.overbay.orbit.data.model.BenefitAnchor
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
    val asOf: String = "2026-11"
) {
    fun applyToProfile(existing: CreditCardProfile): CreditCardProfile {
        val matchedExistingIds = mutableSetOf<String>()
        val matchedExistingTitles = mutableSetOf<String>()

        val mergedBenefits = benefits.map { templateBenefit ->
            val matched = existing.benefits.firstOrNull { existingBenefit ->
                (existingBenefit.id.isNotBlank() && existingBenefit.id == templateBenefit.id) ||
                existingBenefit.title.trim().equals(templateBenefit.title.trim(), ignoreCase = true)
            }
            if (matched != null) {
                if (matched.id.isNotBlank()) matchedExistingIds.add(matched.id)
                matchedExistingTitles.add(matched.title.trim().lowercase())
                templateBenefit.copy(
                    usage = matched.usage,
                    usedThisYear = matched.usedThisYear,
                    usedPeriods = matched.usedPeriods
                )
            } else {
                templateBenefit
            }
        }

        val customBenefits = existing.benefits.filter { existingBenefit ->
            val idMatch = existingBenefit.id.isNotBlank() && existingBenefit.id in matchedExistingIds
            val titleMatch = existingBenefit.title.trim().lowercase() in matchedExistingTitles
            !idMatch && !titleMatch
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
            benefits = mergedBenefits + customBenefits,
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
                CardBenefit(
                    id = "csr-travel-credit",
                    title = "$300 Annual Travel Credit",
                    periodValueCents = 300_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CARDMEMBER_YEAR,
                    description = "$300 annual reimbursement for travel purchases each cardmember year"
                ),
                CardBenefit(
                    id = "csr-doordash",
                    title = "DashPass & $5/mo DoorDash Promo",
                    periodValueCents = 5_00L,
                    cadence = BenefitCadence.MONTHLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Complimentary DashPass & $5 monthly DoorDash in-app credit"
                ),
                CardBenefit(
                    id = "csr-global-entry",
                    title = "Global Entry / TSA PreCheck Credit",
                    periodValueCents = 120_00L,
                    cadence = BenefitCadence.EVERY_4_YEARS,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $120 statement credit every 4 years for application fee"
                ),
                CardBenefit(
                    id = "csr-priority-pass",
                    title = "Priority Pass Select Lounge Access",
                    periodValueCents = null,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Complimentary airport lounge access for cardmember and up to 2 guests"
                )
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
                CardBenefit(
                    id = "csp-hotel-credit",
                    title = "$50 Annual Chase Travel Hotel Credit",
                    periodValueCents = 50_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CARDMEMBER_YEAR,
                    description = "$50 annual statement credit for hotels booked through Chase Travel"
                ),
                CardBenefit(
                    id = "csp-doordash",
                    title = "$10/mo DoorDash Non-Restaurant Credit",
                    periodValueCents = 10_00L,
                    cadence = BenefitCadence.MONTHLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "$10 monthly DoorDash credit for grocery/convenience orders"
                )
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
                CardBenefit(
                    id = "amex-gold-uber-cash",
                    title = "$10/mo Uber Cash ($120/yr)",
                    periodValueCents = 10_00L,
                    cadence = BenefitCadence.MONTHLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "$10 monthly Uber Cash for US rides and eats"
                ),
                CardBenefit(
                    id = "amex-gold-dining-credit",
                    title = "$10/mo Dining Credit ($120/yr)",
                    periodValueCents = 10_00L,
                    cadence = BenefitCadence.MONTHLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "$10 monthly dining credit at Grubhub, Cheesecake Factory, etc."
                ),
                CardBenefit(
                    id = "amex-gold-resy-credit",
                    title = "$50 Semi-Annual Resy Credit ($100/yr)",
                    periodValueCents = 50_00L,
                    cadence = BenefitCadence.SEMI_ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $50 semi-annually (Jan-Jun, Jul-Dec) for purchases at US Resy restaurants"
                ),
                CardBenefit(
                    id = "amex-gold-dunkin-credit",
                    title = "$7/mo Dunkin' Credit ($84/yr)",
                    periodValueCents = 7_00L,
                    cadence = BenefitCadence.MONTHLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $7 monthly statement credit at US Dunkin' locations"
                )
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
                CardBenefit(
                    id = "amex-plat-uber-cash",
                    title = "Uber Cash ($15/mo + $20 Dec)",
                    periodValueCents = 15_00L,
                    cadence = BenefitCadence.MONTHLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "+$20 bonus in December ($200 total annual value)"
                ),
                CardBenefit(
                    id = "amex-plat-digital-entertainment",
                    title = "$20/mo Digital Entertainment Credit",
                    periodValueCents = 20_00L,
                    cadence = BenefitCadence.MONTHLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $20/mo statement credit for Disney+, Hulu, ESPN+, Peacock, NYT, WSJ"
                ),
                CardBenefit(
                    id = "amex-plat-hotel-credit",
                    title = "$200 Hotel Credit (FHR / THC)",
                    periodValueCents = 200_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "$200 annual statement credit for prepaid Fine Hotels + Resorts or The Hotel Collection"
                ),
                CardBenefit(
                    id = "amex-plat-airline-fee",
                    title = "$200 Airline Incidental Fee Credit",
                    periodValueCents = 200_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "$200 annual statement credit for incidental fees with one selected qualifying airline"
                ),
                CardBenefit(
                    id = "amex-plat-saks-credit",
                    title = "$50 Semi-Annual Saks Credit ($100/yr)",
                    periodValueCents = 50_00L,
                    cadence = BenefitCadence.SEMI_ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $50 statement credit semi-annually (Jan-Jun, Jul-Dec) at Saks Fifth Avenue"
                ),
                CardBenefit(
                    id = "amex-plat-clear-plus",
                    title = "$199 CLEAR Plus Credit",
                    periodValueCents = 199_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $199 per calendar year statement credit for CLEAR Plus membership"
                )
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
                CardBenefit(
                    id = "venture-x-travel-credit",
                    title = "$300 Annual Capital One Travel Credit",
                    periodValueCents = 300_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CARDMEMBER_YEAR,
                    description = "$300 annual credit for bookings through Capital One Travel each cardmember year"
                ),
                CardBenefit(
                    id = "venture-x-anniversary-miles",
                    title = "10,000 Anniversary Bonus Miles ($100 val)",
                    periodValueCents = 100_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CARDMEMBER_YEAR,
                    description = "10,000 bonus miles starting on first anniversary year (equal to $100 towards travel)"
                ),
                CardBenefit(
                    id = "venture-x-global-entry",
                    title = "Global Entry / TSA PreCheck Credit",
                    periodValueCents = 120_00L,
                    cadence = BenefitCadence.EVERY_4_YEARS,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $120 statement credit for Global Entry or TSA PreCheck application fee every 4 years"
                )
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
