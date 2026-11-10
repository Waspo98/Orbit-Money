package app.overbay.orbit.domain

import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CreditCardTemplate(
    val id: String,
    val shortLabel: String,
    val cardName: String,
    val issuerName: String,
    val network: String,
    val imageUrl: String,
    val artworkUrl: String? = null,
    val annualFeeCents: Cents,
    val isAnnualFeeWaived: Boolean = false,
    val isBusinessCard: Boolean = false,
    val countsToward524Override: Boolean? = null,
    val subCooldownMonths: Int? = 24,
    val defaultSubRewardText: String? = null,
    val defaultSubEstimatedValueCents: Cents? = null,
    val defaultSubTargetSpendCents: Cents? = null,
    val subSpendWindowDays: Int? = 90,
    val isElevatedSubOffer: Boolean = false,
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

        val computedDeadline = existing.strategy.subDeadlineDate?.takeIf { it.isNotBlank() }
            ?: LocalDateUtils.addDaysIso(existing.strategy.openedDate, subSpendWindowDays)

        return existing.copy(
            cardName = cardName,
            issuerName = issuerName,
            network = network,
            imageUrl = imageUrl,
            artworkUrl = artworkUrl ?: existing.artworkUrl,
            annualFeeCents = annualFeeCents,
            rewardCategories = mergedRewardCategories,
            benefits = mergedBenefits + customBenefits,
            strategy = existing.strategy.copy(
                isBusinessCard = existing.strategy.isBusinessCard || isBusinessCard,
                countsToward524Override = existing.strategy.countsToward524Override ?: countsToward524Override,
                subCooldownMonths = existing.strategy.subCooldownMonths ?: subCooldownMonths,
                subRewardText = existing.strategy.subRewardText ?: defaultSubRewardText,
                subEstimatedValueCents = existing.strategy.subEstimatedValueCents ?: defaultSubEstimatedValueCents,
                subTargetSpendCents = existing.strategy.subTargetSpendCents ?: defaultSubTargetSpendCents,
                subDeadlineDate = computedDeadline
            )
        )
    }
}

internal fun card(
    id: String,
    shortLabel: String,
    cardName: String,
    issuerName: String,
    network: String,
    imageUrl: String,
    artworkUrl: String?,
    annualFeeCents: Cents,
    isAnnualFeeWaived: Boolean = false,
    isBusinessCard: Boolean = false,
    countsToward524Override: Boolean? = null,
    subCooldownMonths: Int? = 24,
    defaultSubRewardText: String? = null,
    defaultSubEstimatedValueCents: Cents? = null,
    defaultSubTargetSpendCents: Cents? = null,
    subSpendWindowDays: Int? = 90,
    isElevatedSubOffer: Boolean = false,
    rewardCategories: List<RewardCategory> = emptyList(),
    benefits: List<CardBenefit> = emptyList()
) = CreditCardTemplate(
    id = id,
    shortLabel = shortLabel,
    cardName = cardName,
    issuerName = issuerName,
    network = network,
    imageUrl = imageUrl,
    artworkUrl = artworkUrl,
    annualFeeCents = annualFeeCents,
    isAnnualFeeWaived = isAnnualFeeWaived,
    isBusinessCard = isBusinessCard,
    countsToward524Override = countsToward524Override,
    subCooldownMonths = subCooldownMonths,
    defaultSubRewardText = defaultSubRewardText,
    defaultSubEstimatedValueCents = defaultSubEstimatedValueCents,
    defaultSubTargetSpendCents = defaultSubTargetSpendCents,
    subSpendWindowDays = subSpendWindowDays,
    isElevatedSubOffer = isElevatedSubOffer,
    rewardCategories = rewardCategories,
    benefits = benefits
)

internal fun r(
    category: String,
    label: String,
    multiplier: Double,
    capCents: Cents? = null,
    isRotating: Boolean = false
) = RewardCategory(
    category = category,
    label = label,
    multiplier = multiplier,
    capCents = capCents,
    isRotating = isRotating
)

internal fun b(
    id: String,
    title: String,
    periodValueCents: Cents?,
    cadence: BenefitCadence = BenefitCadence.ANNUAL,
    anchor: BenefitAnchor = BenefitAnchor.CALENDAR,
    description: String = ""
) = CardBenefit(
    id = id,
    title = title,
    periodValueCents = periodValueCents,
    cadence = cadence,
    anchor = anchor,
    description = description
)

object CreditCardTemplates {

    private val CORE_CARDS: List<CreditCardTemplate> = listOf(
        card(
            id = "csr", shortLabel = "Sapphire Reserve", cardName = "Sapphire Reserve",
            issuerName = "Chase", network = "Visa", imageUrl = "preset://sapphire-blue",
            artworkUrl = "https://www.offeroptimist.com/images/chase/sapphire-reserve.png", annualFeeCents = 79500L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 48,
            defaultSubRewardText = "150,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 225000L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 90,
            isElevatedSubOffer = true,
            rewardCategories = listOf(r("Travel & Flights", "Travel & Dining", 3.0), r("Dining & Restaurants", "Dining", 3.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("csr-travel-credit", "\$300 Annual Travel Credit", 30000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "\$300 annual reimbursement for travel purchases each cardmember year"), b("csr-doordash", "DashPass & \$5/mo DoorDash Promo", 500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Complimentary DashPass & \$5 monthly DoorDash in-app credit"), b("csr-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 statement credit every 4 years for application fee"), b("csr-priority-pass", "Priority Pass Select Lounge Access", null, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Complimentary airport lounge access for cardmember and up to 2 guests"))
        ),
        card(
            id = "csp", shortLabel = "Sapphire Preferred", cardName = "Sapphire Preferred",
            issuerName = "Chase", network = "Visa", imageUrl = "preset://sapphire-blue",
            artworkUrl = "https://www.offeroptimist.com/images/chase/sapphire-preferred.png", annualFeeCents = 9500L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 48,
            defaultSubRewardText = "75,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 112500L,
            defaultSubTargetSpendCents = 500000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Dining & Restaurants", "Dining", 3.0), r("Streaming & Entertainment", "Select Streaming", 3.0), r("Groceries", "Online Groceries", 3.0), r("Travel & Flights", "Travel", 2.0)),
            benefits = listOf(b("csp-hotel-credit", "\$50 Annual Chase Travel Hotel Credit", 5000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "\$50 annual statement credit for hotels booked through Chase Travel"), b("csp-doordash", "\$10/mo DoorDash Non-Restaurant Credit", 1000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$10 monthly DoorDash credit for grocery/convenience orders"))
        ),
        card(
            id = "cfu", shortLabel = "Freedom Unlimited", cardName = "Freedom Unlimited",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#13315C:#2563EB:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/freedom-unlimited.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "25,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 37500L,
            defaultSubTargetSpendCents = 50000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Dining & Restaurants", "Dining", 3.0), r("Drugstores", "Drugstores", 3.0), r("Everything Else", "Everything Else (Catch-All)", 1.5)),
            benefits = listOf()
        ),
        card(
            id = "cff", shortLabel = "Freedom Flex", cardName = "Freedom Flex",
            issuerName = "Chase", network = "Mastercard", imageUrl = "hex://#102A56:#1D4ED8:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/freedom-flex.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "20,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 30000L,
            defaultSubTargetSpendCents = 50000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Quarterly Bonus", "5x Rotating Quarterly (\$1,500 cap)", 5.0, capCents = 150000L, isRotating = true), r("Dining & Restaurants", "Dining", 3.0), r("Drugstores", "Drugstores", 3.0)),
            benefits = listOf()
        ),
        card(
            id = "ink_cash", shortLabel = "Ink Business Cash", cardName = "Ink Business Cash",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#0F172A:#1E3A8A:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/ink-business-cash.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "75,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 112500L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Office & Internet", "Office Supplies, Internet & Phone", 5.0), r("Gas & Transit", "Gas & Restaurants", 2.0)),
            benefits = listOf()
        ),
        card(
            id = "ink_unlimited", shortLabel = "Ink Unlimited", cardName = "Ink Business Unlimited",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#1E293B:#334155:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/ink-business-unlimited.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "75,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 112500L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Everything Else", "Everything Else (Catch-All)", 1.5)),
            benefits = listOf()
        ),
        card(
            id = "ink_preferred", shortLabel = "Ink Preferred", cardName = "Ink Business Preferred",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#0B192C:#1E3E62:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/ink-business-preferred.png", annualFeeCents = 9500L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "100,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 150000L,
            defaultSubTargetSpendCents = 800000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Travel, Shipping, Internet & Ads", 3.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf()
        ),
        card(
            id = "chase_amazon_prime", shortLabel = "Prime Visa", cardName = "Amazon Prime Visa",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#1E293B:#0F172A:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/amazon-prime.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "\$150 Cash Bonus", defaultSubEstimatedValueCents = 15000L,
            defaultSubTargetSpendCents = 1L, subSpendWindowDays = 365,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Shopping", "Amazon.com, Whole Foods & Chase Travel", 5.0), r("Dining & Restaurants", "Restaurants & Gas Stations", 2.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf()
        ),
        card(
            id = "chase_hyatt", shortLabel = "World of Hyatt", cardName = "World of Hyatt",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#111827:#1E3A8A:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/world-of-hyatt.png", annualFeeCents = 9500L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "30,000 World of Hyatt pts", defaultSubEstimatedValueCents = 54000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Hyatt Hotels & Resorts", 4.0), r("Dining & Restaurants", "Dining, Flights & Gym", 2.0)),
            benefits = listOf(b("hyatt-free-night", "Category 1-4 Free Night Award", 18000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "1 free night at any Category 1-4 Hyatt hotel each anniversary year"))
        ),
        card(
            id = "chase_united_explorer", shortLabel = "United Explorer", cardName = "United Explorer",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#0C2340:#1D4F91:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/united-explorer.png", annualFeeCents = 15000L,
            isAnnualFeeWaived = true, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "85,000 United Miles", defaultSubEstimatedValueCents = 102000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 90,
            isElevatedSubOffer = true,
            rewardCategories = listOf(r("Travel & Flights", "United Purchases, Dining & Hotels", 2.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("united-exp-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 credit for Global Entry, TSA PreCheck, or NEXUS"), b("united-exp-club-passes", "2 United Club One-Time Passes", 11800L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Two complimentary United Club one-time passes each anniversary year"))
        ),
        card(
            id = "chase_united_quest", shortLabel = "United Quest", cardName = "United Quest",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#111827:#1E3A8A:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/united-quest.png", annualFeeCents = 35000L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "100,000 United Miles", defaultSubEstimatedValueCents = 120000L,
            defaultSubTargetSpendCents = 400000L, subSpendWindowDays = 90,
            isElevatedSubOffer = true,
            rewardCategories = listOf(r("Travel & Flights", "United Purchases", 3.0), r("Dining & Restaurants", "Dining & Select Streaming", 2.0)),
            benefits = listOf(b("united-quest-credit", "\$125 Annual United Purchase Credit", 12500L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Up to \$125 in statement credits for United purchases each cardmember year"), b("united-quest-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 statement credit every 4 years"))
        ),
        card(
            id = "chase_marriott_boundless", shortLabel = "Marriott Boundless", cardName = "Marriott Bonvoy Boundless",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#4A1525:#7F1D1D:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/marriott-bonvoy-boundless.jpeg", annualFeeCents = 9500L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "200,000 Marriott Bonvoy pts + \$100 Credit", defaultSubEstimatedValueCents = 170000L,
            defaultSubTargetSpendCents = 400000L, subSpendWindowDays = 120,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Marriott Bonvoy Hotels", 6.0), r("Groceries", "Grocery, Gas & Dining (\$6k cap)", 3.0, capCents = 600000L), r("Everything Else", "Everything Else", 2.0)),
            benefits = listOf(b("boundless-free-night", "35,000-Pt Annual Free Night Award", 17500L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Free Night Award worth up to 35,000 points every cardmember anniversary"))
        ),
        card(
            id = "chase_ihg_premier", shortLabel = "IHG One Premier", cardName = "IHG One Rewards Premier",
            issuerName = "Chase", network = "Mastercard", imageUrl = "hex://#1F2937:#0F766E:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/ihg-rewards-premier.jpg", annualFeeCents = 9900L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "140,000 IHG One Rewards pts", defaultSubEstimatedValueCents = 70000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "IHG Hotels & Resorts", 10.0), r("Dining & Restaurants", "Travel, Gas & Dining", 5.0), r("Everything Else", "Everything Else", 3.0)),
            benefits = listOf(b("ihg-free-night", "40,000-Pt Anniversary Free Night", 16000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Anniversary Free Night valid up to 40,000 points"), b("ihg-united-travelbank", "\$50 Annual United TravelBank Cash", 2500L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "\$25 United TravelBank Cash deposited every Jan and Jul (\$50/yr)"), b("ihg-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 fee credit every 4 years"))
        ),
        card(
            id = "chase_sw_priority", shortLabel = "Southwest Priority", cardName = "Southwest Rapid Rewards Priority",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#1E3A8A:#B91C1C:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/southwest-rapid-rewards-priority.png", annualFeeCents = 22900L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "40,000 Rapid Rewards pts", defaultSubEstimatedValueCents = 52000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Southwest Purchases", 3.0), r("Gas & Transit", "Local Transit & Commuting", 2.0)),
            benefits = listOf(b("sw-priority-credit", "\$75 Southwest Annual Travel Credit", 7500L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "\$75 Southwest travel credit each anniversary year"), b("sw-priority-anniv", "7,500 Anniversary Rapid Rewards Pts", 9500L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "7,500 bonus points each cardmember anniversary"))
        ),
        card(
            id = "amex_gold", shortLabel = "Amex Gold", cardName = "American Express Gold",
            issuerName = "American Express", network = "Amex", imageUrl = "preset://amex-gold",
            artworkUrl = "https://www.offeroptimist.com/images/amex/gold.webp", annualFeeCents = 35000L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "60,000 Membership Rewards pts", defaultSubEstimatedValueCents = 90000L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Dining & Restaurants", "Restaurants Worldwide", 4.0), r("Groceries", "U.S. Supermarkets (\$25k cap)", 4.0, capCents = 2500000L), r("Travel & Flights", "Flights Booked Direct", 3.0)),
            benefits = listOf(b("amex-gold-uber-cash", "\$10/mo Uber Cash (\$120/yr)", 1000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$10 monthly Uber Cash for US rides and eats"), b("amex-gold-dining-credit", "\$10/mo Dining Credit (\$120/yr)", 1000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$10 monthly dining credit at Grubhub, Cheesecake Factory, etc."), b("amex-gold-resy-credit", "\$50 Semi-Annual Resy Credit (\$100/yr)", 5000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$50 semi-annually (Jan-Jun, Jul-Dec) for purchases at US Resy restaurants"), b("amex-gold-dunkin-credit", "\$7/mo Dunkin' Credit (\$84/yr)", 700L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$7 monthly statement credit at US Dunkin' locations"))
        ),
        card(
            id = "amex_plat", shortLabel = "Amex Platinum", cardName = "The Platinum Card",
            issuerName = "American Express", network = "Amex", imageUrl = "preset://venture-silver",
            artworkUrl = "https://www.offeroptimist.com/images/amex/platinum.webp", annualFeeCents = 89500L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "100,000 Membership Rewards pts", defaultSubEstimatedValueCents = 150000L,
            defaultSubTargetSpendCents = 800000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Flights & Prepaid Hotels", 5.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("amex-plat-uber-cash", "Uber Cash (\$15/mo + \$20 Dec)", 1500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "+\$20 bonus in December (\$200 total annual value)"), b("amex-plat-digital-entertainment", "\$20/mo Digital Entertainment Credit", 2000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$20/mo statement credit for Disney+, Hulu, ESPN+, Peacock, NYT, WSJ"), b("amex-plat-hotel-credit", "\$200 Hotel Credit (FHR / THC)", 20000L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "\$200 annual statement credit for prepaid Fine Hotels + Resorts or The Hotel Collection"), b("amex-plat-airline-fee", "\$200 Airline Incidental Fee Credit", 20000L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "\$200 annual statement credit for incidental fees with one selected qualifying airline"), b("amex-plat-saks-credit", "\$50 Semi-Annual Saks Credit (\$100/yr)", 5000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$50 statement credit semi-annually (Jan-Jun, Jul-Dec) at Saks Fifth Avenue"), b("amex-plat-clear-plus", "\$199 CLEAR Plus Credit", 19900L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$199 per calendar year statement credit for CLEAR Plus membership"), b("amex-plat-walmart-plus", "\$12.95/mo Walmart+ Membership Credit", 1295L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Monthly statement credit for Walmart+ membership"))
        ),
        card(
            id = "amex_green", shortLabel = "Amex Green", cardName = "American Express Green",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#065F46:#10B981:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/green.webp", annualFeeCents = 15000L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "40,000 Membership Rewards pts", defaultSubEstimatedValueCents = 60000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Travel & Transit", 3.0), r("Dining & Restaurants", "Restaurants Worldwide", 3.0)),
            benefits = listOf(b("amex-green-clear", "\$199 CLEAR Plus Credit", 19900L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$199 annual statement credit for CLEAR Plus membership"))
        ),
        card(
            id = "amex_bcp", shortLabel = "Blue Cash Preferred", cardName = "Blue Cash Preferred",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#0F4C81:#38BDF8:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/blue-cash-preferred.webp", annualFeeCents = 9500L,
            isAnnualFeeWaived = true, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "\$250 Cash Bonus", defaultSubEstimatedValueCents = 25000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Groceries", "U.S. Supermarkets (\$6k cap)", 6.0, capCents = 600000L), r("Streaming & Entertainment", "Select U.S. Streaming", 6.0), r("Gas & Transit", "U.S. Gas Stations & Transit", 3.0)),
            benefits = listOf(b("amex-bcp-disney", "\$7/mo Disney Bundle Credit (\$84/yr)", 700L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$7 monthly statement credit on eligible Disney Bundle subscriptions"))
        ),
        card(
            id = "amex_bce", shortLabel = "Blue Cash Everyday", cardName = "Blue Cash Everyday",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#1E40AF:#60A5FA:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/blue-cash-everyday.webp", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "\$200 Cash Bonus", defaultSubEstimatedValueCents = 20000L,
            defaultSubTargetSpendCents = 200000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Groceries", "U.S. Supermarkets (\$6k cap)", 3.0, capCents = 600000L), r("Shopping", "U.S. Online Retail (\$6k cap)", 3.0, capCents = 600000L), r("Gas & Transit", "U.S. Gas Stations (\$6k cap)", 3.0, capCents = 600000L)),
            benefits = listOf(b("amex-bce-disney", "\$7/mo Disney Bundle Credit", 700L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$7 monthly credit for eligible Disney Bundle subscriptions"), b("amex-bce-homechef", "\$15/mo Home Chef Credit", 1500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$15/mo statement credit on Home Chef meal kits"))
        ),
        card(
            id = "amex_bbp", shortLabel = "Blue Business Plus", cardName = "Blue Business Plus",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#1E3A8A:#3B82F6:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/blue-business-plus.webp", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "15,000 Membership Rewards pts", defaultSubEstimatedValueCents = 22500L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Everything Else", "Every Purchase (\$50k annual cap)", 2.0, capCents = 5000000L)),
            benefits = listOf()
        ),
        card(
            id = "amex_biz_gold", shortLabel = "Amex Business Gold", cardName = "American Express Business Gold",
            issuerName = "American Express", network = "Amex", imageUrl = "preset://amex-gold",
            artworkUrl = "https://www.offeroptimist.com/images/amex/business-gold.webp", annualFeeCents = 37500L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "100,000 Membership Rewards pts", defaultSubEstimatedValueCents = 150000L,
            defaultSubTargetSpendCents = 1500000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Office & Internet", "Top 2 Business Categories (\$150k cap)", 4.0, capCents = 15000000L), r("Travel & Flights", "Amex Travel Purchases", 3.0)),
            benefits = listOf(b("amex-biz-gold-flex", "\$20/mo Flexible Business Credit (\$240/yr)", 2000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$20/mo at FedEx, Grubhub, and Office Supply Stores"), b("amex-biz-gold-walmart", "\$12.95/mo Walmart+ Credit", 1295L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Monthly statement credit for Walmart+ membership"))
        ),
        card(
            id = "amex_biz_plat", shortLabel = "Amex Business Platinum", cardName = "The Business Platinum Card",
            issuerName = "American Express", network = "Amex", imageUrl = "preset://venture-silver",
            artworkUrl = "https://www.offeroptimist.com/images/amex/business-platinum.webp", annualFeeCents = 69500L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "200,000 Membership Rewards pts", defaultSubEstimatedValueCents = 300000L,
            defaultSubTargetSpendCents = 2000000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Flights & Prepaid Hotels on Amex Travel", 5.0), r("Shopping", "Purchases \$5,000+ & Construction/Tech", 1.5)),
            benefits = listOf(b("amex-biz-plat-dell", "\$200 Semi-Annual Dell Credit (\$400/yr)", 20000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$200 Jan-Jun and \$200 Jul-Dec on U.S. Dell purchases"), b("amex-biz-plat-wireless", "\$10/mo Wireless Telephone Credit (\$120/yr)", 1000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$10 monthly credit for U.S. wireless phone service"), b("amex-biz-plat-airline", "\$200 Airline Incidental Fee Credit", 20000L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$200/yr for incidental fees on one selected airline"), b("amex-biz-plat-clear", "\$199 CLEAR Plus Credit", 19900L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$199 per calendar year for CLEAR Plus"))
        ),
        card(
            id = "amex_hilton_aspire", shortLabel = "Hilton Aspire", cardName = "Hilton Honors Aspire",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#0F172A:#1E3A8A:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/hilton-honors-aspire.png", annualFeeCents = 55000L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "150,000 Hilton Honors pts", defaultSubEstimatedValueCents = 75000L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Hilton Hotels & Resorts", 14.0), r("Dining & Restaurants", "Flights, Car Rentals & U.S. Restaurants", 7.0), r("Everything Else", "Everything Else", 3.0)),
            benefits = listOf(b("aspire-resort-credit", "\$200 Semi-Annual Hilton Resort Credit (\$400/yr)", 20000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$200 Jan-Jun and \$200 Jul-Dec at participating Hilton Resorts"), b("aspire-flight-credit", "\$50 Quarterly Flight Credit (\$200/yr)", 5000L, BenefitCadence.QUARTERLY, BenefitAnchor.CALENDAR, "Up to \$50 per quarter on flights booked direct or via Amex Travel"), b("aspire-free-night", "Annual Hilton Free Night Reward", 35000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "1 Free Night Reward every year with card renewal"), b("aspire-clear-plus", "\$199 CLEAR Plus Credit", 19900L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$199/yr statement credit for CLEAR Plus"))
        ),
        card(
            id = "amex_marriott_brilliant", shortLabel = "Marriott Brilliant", cardName = "Marriott Bonvoy Brilliant",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#18181B:#3F3F46:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/marriott-bonvoy-brilliant.webp", annualFeeCents = 65000L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "200,000 Marriott Bonvoy pts", defaultSubEstimatedValueCents = 160000L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 180,
            isElevatedSubOffer = true,
            rewardCategories = listOf(r("Travel & Flights", "Marriott Bonvoy Hotels", 6.0), r("Dining & Restaurants", "Restaurants Worldwide & Direct Flights", 3.0), r("Everything Else", "Everything Else", 2.0)),
            benefits = listOf(b("brilliant-dining", "\$25/mo Worldwide Dining Credit (\$300/yr)", 2500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$25 monthly statement credit at restaurants worldwide"), b("brilliant-free-night", "85,000-Pt Annual Free Night Award", 42500L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "1 Free Night Award worth up to 85,000 points every renewal year"))
        ),
        card(
            id = "amex_delta_gold", shortLabel = "Delta SkyMiles Gold", cardName = "Delta SkyMiles Gold",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#78531A:#B8860B:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/delta-skymiles-gold.jpg", annualFeeCents = 15000L,
            isAnnualFeeWaived = true, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "50,000 Delta SkyMiles + \$400 Credit", defaultSubEstimatedValueCents = 100000L,
            defaultSubTargetSpendCents = 200000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Delta Purchases", 2.0), r("Dining & Restaurants", "Restaurants & U.S. Supermarkets", 2.0)),
            benefits = listOf(b("delta-gold-stays", "\$100 Annual Delta Stays Credit", 10000L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$100 annual credit for prepaid hotels/rentals on Delta Stays"))
        )
    )

    val BUNDLED: List<CreditCardTemplate> =
        CORE_CARDS +
            CreditCardCatalogSeed.ADDITIONAL_CARDS +
            CreditCardCatalogExtendedA.CARDS +
            CreditCardCatalogExtendedB.CARDS

    private val _catalogFlow = MutableStateFlow(BUNDLED)
    val catalogFlow: StateFlow<List<CreditCardTemplate>> = _catalogFlow.asStateFlow()

    val ALL: List<CreditCardTemplate> get() = _catalogFlow.value

    fun findBundledById(id: String): CreditCardTemplate? = BUNDLED.firstOrNull { it.id == id }

    fun resolveArtworkUrl(explicitUrl: String?, cardName: String?, issuerName: String? = null): String? {
        val cleanExplicit = explicitUrl?.trim()?.takeIf { it.isNotBlank() }
        if (cleanExplicit != null && !cleanExplicit.contains("/api/card-catalog/")) {
            return cleanExplicit
        }
        val rawName = cardName?.trim()?.takeIf { it.isNotBlank() } ?: return cleanExplicit
        val normInput = normalizeCardLookup(rawName, issuerName)
        if (normInput.isBlank()) return cleanExplicit

        val catalog = _catalogFlow.value
        val exact = catalog.firstOrNull { tpl ->
            !tpl.artworkUrl.isNullOrBlank() && (
                tpl.id.equals(rawName, ignoreCase = true) ||
                    tpl.cardName.equals(rawName, ignoreCase = true) ||
                    tpl.shortLabel.equals(rawName, ignoreCase = true) ||
                    normalizeCardLookup(tpl.cardName, tpl.issuerName) == normInput ||
                    normalizeCardLookup(tpl.shortLabel, tpl.issuerName) == normInput
                )
        }
        if (exact != null) return exact.artworkUrl

        val containsMatch = catalog.firstOrNull { tpl ->
            if (tpl.artworkUrl.isNullOrBlank()) return@firstOrNull false
            val tplNorm = normalizeCardLookup(tpl.cardName, tpl.issuerName)
            tplNorm.length >= 4 && (normInput.contains(tplNorm) || tplNorm.contains(normInput))
        }
        return containsMatch?.artworkUrl ?: cleanExplicit
    }

    private fun normalizeCardLookup(name: String, issuer: String?): String {
        var s = name.lowercase().trim()
        val prefixes = listOf(
            "american express ", "amex ", "chase ", "capital one ", "citi ", "citibank ",
            "bank of america ", "bofa ", "wells fargo ", "u.s. bank ", "us bank ", "barclays ", "discover "
        )
        for (p in prefixes) {
            if (s.startsWith(p)) {
                s = s.removePrefix(p).trim()
            }
        }
        issuer?.lowercase()?.trim()?.takeIf { it.isNotBlank() }?.let { iss ->
            if (s.startsWith("\$iss ")) s = s.removePrefix("\$iss ").trim()
        }
        s = s.removeSuffix(" credit card").removeSuffix(" card").trim()
        return s.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    fun updateFromRemote(remoteItems: List<CreditCardTemplate>) {
        if (remoteItems.isEmpty()) return
        val bundledById = BUNDLED.associateBy { it.id }
        val mergedRemote = remoteItems.map { remote ->
            val bundled = bundledById[remote.id]
            val resolvedArt = if (remote.artworkUrl.isNullOrBlank() || remote.artworkUrl.contains("/api/card-catalog/")) {
                bundled?.artworkUrl ?: remote.artworkUrl
            } else {
                remote.artworkUrl
            }
            remote.copy(artworkUrl = resolvedArt)
        }
        val remoteIds = mergedRemote.map { it.id }.toSet()
        val fallbackExtras = BUNDLED.filter { it.id !in remoteIds }
        _catalogFlow.value = mergedRemote + fallbackExtras
    }
}
