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
    val asOf: String = "2026-11-v2"
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
            defaultSubRewardText = "100,000 Ultimate Rewards pts + \$500 Travel Promo", defaultSubEstimatedValueCents = 200000L,
            defaultSubTargetSpendCents = 500000L, subSpendWindowDays = 90,
            isElevatedSubOffer = true,
            rewardCategories = listOf(r("Travel & Flights", "Chase Travel Portal", 8.0), r("Travel & Flights", "Direct Flights & Hotels", 4.0), r("Dining & Restaurants", "Worldwide Dining", 3.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("csr-travel-credit", "\$300 Annual Travel Credit", 30000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "\$300 annual reimbursement for travel purchases each cardmember year"), b("csr-the-edit", "\$250 Semi-Annual The Edit Hotel Credit (\$500/yr)", 25000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$250 Jan-Jun and \$250 Jul-Dec on prepaid stays with The Edit by Chase Travel"), b("csr-exclusive-tables", "\$150 Semi-Annual Exclusive Tables Credit (\$300/yr)", 15000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$150 semi-annually at Sapphire Reserve Exclusive Tables restaurants"), b("csr-stubhub", "\$150 Semi-Annual StubHub Credit (\$300/yr)", 15000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$150 semi-annually on StubHub and viagogo ticket purchases"), b("csr-doordash", "DashPass & \$5/mo DoorDash Promo", 500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Complimentary DashPass & \$5 monthly DoorDash restaurant promo"), b("csr-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 statement credit every 4 years for application fee"), b("csr-priority-pass", "Sapphire Lounge & Priority Pass Select Access", null, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Complimentary Chase Sapphire Lounge and Priority Pass Select access with up to 2 guests"))
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
            rewardCategories = listOf(r("Travel & Flights", "Chase Travel Portal", 5.0), r("Dining & Restaurants", "Dining Worldwide", 3.0), r("Streaming & Entertainment", "Select Streaming Services", 3.0), r("Groceries", "Online Groceries", 3.0), r("Travel & Flights", "Other Travel Purchases", 2.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("csp-hotel-credit", "\$50 Annual Chase Travel Hotel Credit", 5000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "\$50 annual statement credit for hotels booked through Chase Travel"), b("csp-doordash", "\$10/mo DoorDash Non-Restaurant Credit", 1000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$10 monthly DoorDash credit for grocery and convenience orders"))
        ),
        card(
            id = "cfu", shortLabel = "Freedom Unlimited", cardName = "Freedom Unlimited",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#13315C:#2563EB:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/freedom-unlimited.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "\$200 / 20,000 UR Bonus", defaultSubEstimatedValueCents = 30000L,
            defaultSubTargetSpendCents = 50000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Chase Travel Portal", 5.0), r("Dining & Restaurants", "Dining & Takeout", 3.0), r("Drugstores", "Drugstores", 3.0), r("Everything Else", "Everything Else (Catch-All)", 1.5)),
            benefits = emptyList()
        ),
        card(
            id = "cff", shortLabel = "Freedom Flex", cardName = "Freedom Flex",
            issuerName = "Chase", network = "Mastercard", imageUrl = "hex://#102A56:#1D4ED8:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/freedom-flex.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "\$200 / 20,000 UR Bonus", defaultSubEstimatedValueCents = 30000L,
            defaultSubTargetSpendCents = 50000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Quarterly Bonus", "5x Rotating Quarterly (\$1,500 cap)", 5.0, capCents = 150000L, isRotating = true), r("Travel & Flights", "Chase Travel Portal", 5.0), r("Dining & Restaurants", "Dining & Takeout", 3.0), r("Drugstores", "Drugstores", 3.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = emptyList()
        ),
        card(
            id = "chase_freedom_classic", shortLabel = "Freedom (Classic Visa)", cardName = "Freedom (Classic Visa)",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#13315C:#1E40AF:#FFFFFF",
            artworkUrl = null, annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = null, defaultSubEstimatedValueCents = null,
            defaultSubTargetSpendCents = null, subSpendWindowDays = null,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Quarterly Bonus", "5x Rotating Quarterly (\$1,500 cap)", 5.0, capCents = 150000L, isRotating = true), r("Everything Else", "Everything Else", 1.0)),
            benefits = emptyList()
        ),
        card(
            id = "ink_cash", shortLabel = "Ink Business Cash", cardName = "Ink Business Cash",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#0F172A:#1E3A8A:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/ink-business-cash.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = false, subCooldownMonths = 24,
            defaultSubRewardText = "75,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 112500L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Office & Internet", "Office Supplies, Internet, Cable & Phone (\$25k cap)", 5.0, capCents = 2500000L), r("Gas & Transit", "Gas Stations (\$25k combined cap)", 2.0, capCents = 2500000L), r("Dining & Restaurants", "Restaurants (\$25k combined cap)", 2.0, capCents = 2500000L), r("Everything Else", "Everything Else", 1.0)),
            benefits = emptyList()
        ),
        card(
            id = "ink_unlimited", shortLabel = "Ink Unlimited", cardName = "Ink Business Unlimited",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#1E293B:#334155:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/ink-business-unlimited.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = false, subCooldownMonths = 24,
            defaultSubRewardText = "75,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 112500L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Everything Else", "Everything Else (Catch-All)", 1.5)),
            benefits = emptyList()
        ),
        card(
            id = "ink_preferred", shortLabel = "Ink Preferred", cardName = "Ink Business Preferred",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#0B192C:#1E3E62:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/ink-business-preferred.png", annualFeeCents = 9500L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = false, subCooldownMonths = 24,
            defaultSubRewardText = "90,000 Ultimate Rewards pts", defaultSubEstimatedValueCents = 135000L,
            defaultSubTargetSpendCents = 800000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Travel (\$150k combined cap)", 3.0, capCents = 15000000L), r("Office & Internet", "Shipping, Internet, Cable, Phone & Ads (\$150k cap)", 3.0, capCents = 15000000L), r("Everything Else", "Everything Else", 1.0)),
            benefits = emptyList()
        ),
        card(
            id = "chase_amazon_prime", shortLabel = "Prime Visa", cardName = "Prime Visa",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#1E293B:#0F172A:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/amazon-prime.png", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "\$200 Amazon Gift Card Instantly", defaultSubEstimatedValueCents = 20000L,
            defaultSubTargetSpendCents = 0L, subSpendWindowDays = 30,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Online Shopping", "Amazon.com, Amazon Fresh & Whole Foods", 5.0), r("Travel & Flights", "Chase Travel Portal", 5.0), r("Dining & Restaurants", "Restaurants", 2.0), r("Gas & Transit", "Gas Stations & Local Transit", 2.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = emptyList()
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
            rewardCategories = listOf(r("Travel & Flights", "Hyatt Hotels & Resorts", 4.0), r("Dining & Restaurants", "Restaurants", 2.0), r("Travel & Flights", "Direct Flights", 2.0), r("Gas & Transit", "Local Transit & Commuting", 2.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("hyatt-free-night", "Category 1-4 Annual Free Night Award", 18000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "1 free night at any Category 1-4 Hyatt hotel each anniversary year"))
        ),
        card(
            id = "chase_united_explorer", shortLabel = "United Explorer", cardName = "United Explorer",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#0C2340:#1D4F91:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/united-explorer.png", annualFeeCents = 15000L,
            isAnnualFeeWaived = true, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "60,000 United MileagePlus Miles", defaultSubEstimatedValueCents = 72000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "United Purchases & Direct Hotels", 2.0), r("Dining & Restaurants", "Dining & Delivery Services", 2.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("united-exp-hotels", "\$100 Annual United Hotels Credit", 10000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Up to \$100 in credits (\$50 x 2) on prepaid stays via United Hotels"), b("united-exp-rideshare", "\$5/mo Rideshare Credit (\$60/yr)", 500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$5 monthly statement credit on rideshare purchases after enrollment"), b("united-exp-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 credit for Global Entry, TSA PreCheck, or NEXUS"), b("united-exp-club-passes", "2 United Club One-Time Passes", null, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Two complimentary United Club one-time passes each anniversary year"))
        ),
        card(
            id = "chase_united_quest", shortLabel = "United Quest", cardName = "United Quest",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#111827:#1E3A8A:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/united-quest.png", annualFeeCents = 35000L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "70,000 United MileagePlus Miles", defaultSubEstimatedValueCents = 84000L,
            defaultSubTargetSpendCents = 400000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "United Purchases", 3.0), r("Travel & Flights", "All Other Travel", 2.0), r("Dining & Restaurants", "Dining", 2.0), r("Streaming & Entertainment", "Select Streaming Services", 2.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("united-quest-travelbank", "\$200 Annual United TravelBank Cash", 20000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "\$200 in United TravelBank cash each account anniversary year"), b("united-quest-hotels", "\$150 Annual Renowned Hotels Credit", 15000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Up to \$150 credit on prepaid stays at Renowned Hotels and Resorts"), b("united-quest-rideshare", "\$8/mo Rideshare Credit (\$100/yr)", 800L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$8/mo (\$12 in Dec) in rideshare statement credits after enrollment"), b("united-quest-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 statement credit every 4 years"))
        ),
        card(
            id = "chase_marriott_boundless", shortLabel = "Marriott Boundless", cardName = "Marriott Bonvoy Boundless",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#4A1525:#7F1D1D:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/marriott-bonvoy-boundless.jpeg", annualFeeCents = 9500L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "3 Free Night Awards (50k pts each)", defaultSubEstimatedValueCents = 60000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Marriott Bonvoy Hotels", 6.0), r("Groceries", "Grocery, Gas & Dining (\$6k combined cap)", 3.0, capCents = 600000L), r("Gas & Transit", "Gas Stations (\$6k combined cap)", 3.0, capCents = 600000L), r("Dining & Restaurants", "Dining (\$6k combined cap)", 3.0, capCents = 600000L), r("Everything Else", "Everything Else", 2.0)),
            benefits = listOf(b("boundless-free-night", "35,000-Pt Annual Free Night Award", 17500L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Free Night Award worth up to 35,000 points every cardmember anniversary"))
        ),
        card(
            id = "chase_ritz_carlton", shortLabel = "Ritz-Carlton", cardName = "The Ritz-Carlton Credit Card",
            issuerName = "Chase", network = "Visa", imageUrl = "preset://obsidian-black",
            artworkUrl = null, annualFeeCents = 45000L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = null, defaultSubEstimatedValueCents = null,
            defaultSubTargetSpendCents = null, subSpendWindowDays = null,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Marriott Bonvoy Hotels", 6.0), r("Dining & Restaurants", "Dining, Car Rentals & Direct Flights", 3.0), r("Travel & Flights", "Direct Flights & Car Rentals", 3.0), r("Everything Else", "Everything Else", 2.0)),
            benefits = listOf(b("ritz-travel-credit", "\$300 Annual Airline Incidental Credit", 30000L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$300 annual reimbursement for qualifying airline incidental charges"), b("ritz-free-night", "85,000-Pt Annual Free Night Award", 42500L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "1 Free Night Award worth up to 85,000 points each cardmember anniversary"), b("ritz-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 statement credit every 4 years"), b("ritz-lounge", "Sapphire Lounge & Priority Pass Select Access", null, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Complimentary Chase Sapphire Lounge and Priority Pass Select access with unlimited guests"))
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
            rewardCategories = listOf(r("Travel & Flights", "IHG Hotels & Resorts", 10.0), r("Travel & Flights", "Travel Purchases", 5.0), r("Dining & Restaurants", "Dining", 5.0), r("Gas & Transit", "Gas Stations", 5.0), r("Everything Else", "Everything Else", 3.0)),
            benefits = listOf(b("ihg-free-night", "40,000-Pt Anniversary Free Night", 16000L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "Anniversary Free Night valid up to 40,000 points"), b("ihg-united-travelbank", "\$50 Annual United TravelBank Cash", 2500L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "\$25 United TravelBank Cash deposited every Jan and Jul (\$50/yr)"), b("ihg-global-entry", "Global Entry / TSA PreCheck Credit", 12000L, BenefitCadence.EVERY_4_YEARS, BenefitAnchor.CALENDAR, "Up to \$120 fee credit every 4 years"))
        ),
        card(
            id = "chase_sw_priority", shortLabel = "Southwest Priority", cardName = "Southwest Rapid Rewards Priority",
            issuerName = "Chase", network = "Visa", imageUrl = "hex://#1E3A8A:#B91C1C:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/chase/southwest-rapid-rewards-priority.png", annualFeeCents = 22900L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 24,
            defaultSubRewardText = "50,000 Rapid Rewards pts", defaultSubEstimatedValueCents = 65000L,
            defaultSubTargetSpendCents = 100000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Southwest Airlines Purchases", 4.0), r("Gas & Transit", "Gas Stations", 2.0), r("Dining & Restaurants", "Restaurants", 2.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("sw-priority-anniv", "7,500 Anniversary Rapid Rewards Pts", 9750L, BenefitCadence.ANNUAL, BenefitAnchor.CARDMEMBER_YEAR, "7,500 bonus points each cardmember anniversary"))
        ),
        card(
            id = "amex_gold", shortLabel = "Amex Gold", cardName = "American Express Gold",
            issuerName = "American Express", network = "Amex", imageUrl = "preset://amex-gold",
            artworkUrl = "https://www.offeroptimist.com/images/amex/gold.webp", annualFeeCents = 32500L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "90,000 Membership Rewards pts", defaultSubEstimatedValueCents = 135000L,
            defaultSubTargetSpendCents = 600000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Dining & Restaurants", "Restaurants Worldwide (\$50k cap)", 4.0, capCents = 5000000L), r("Groceries", "U.S. Supermarkets (\$25k cap)", 4.0, capCents = 2500000L), r("Travel & Flights", "Flights Booked Direct or Amex Travel", 3.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("amex-gold-uber-cash", "\$10/mo Uber Cash (\$120/yr)", 1000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$10 monthly Uber Cash for US rides and Uber Eats"), b("amex-gold-dining-credit", "\$10/mo Dining Credit (\$120/yr)", 1000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$10 monthly dining credit at Grubhub, Five Guys, Cheesecake Factory, Wine.com, Goldbelly"), b("amex-gold-resy-credit", "\$50 Semi-Annual Resy Credit (\$100/yr)", 5000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$50 semi-annually (Jan-Jun, Jul-Dec) at U.S. Resy restaurants"), b("amex-gold-dunkin-credit", "\$7/mo Dunkin' Credit (\$84/yr)", 700L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$7 monthly statement credit at U.S. Dunkin' locations"))
        ),
        card(
            id = "amex_plat", shortLabel = "Amex Platinum", cardName = "The Platinum Card",
            issuerName = "American Express", network = "Amex", imageUrl = "preset://venture-silver",
            artworkUrl = "https://www.offeroptimist.com/images/amex/platinum.webp", annualFeeCents = 89500L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "125,000 Membership Rewards pts", defaultSubEstimatedValueCents = 187500L,
            defaultSubTargetSpendCents = 800000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Travel & Flights", "Direct Flights & Prepaid Amex Travel Hotels (\$500k cap)", 5.0, capCents = 50000000L), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("amex-plat-hotel-credit", "\$300 Semi-Annual Hotel Credit (\$600/yr FHR/THC)", 30000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$300 Jan-Jun and \$300 Jul-Dec on prepaid Fine Hotels + Resorts or The Hotel Collection"), b("amex-plat-resy-credit", "\$100 Quarterly Resy Dining Credit (\$400/yr)", 10000L, BenefitCadence.QUARTERLY, BenefitAnchor.CALENDAR, "Up to \$100 per quarter at U.S. Resy restaurants"), b("amex-plat-lululemon", "\$75 Quarterly Lululemon Credit (\$300/yr)", 7500L, BenefitCadence.QUARTERLY, BenefitAnchor.CALENDAR, "Up to \$75 per quarter on eligible U.S. Lululemon purchases"), b("amex-plat-digital-entertainment", "\$25/mo Digital Entertainment Credit (\$300/yr)", 2500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$25/mo statement credit for Disney+, Hulu, ESPN+, Peacock, Paramount+, YouTube, NYT, WSJ"), b("amex-plat-uber-cash", "Uber Cash (\$15/mo + \$20 Dec)", 1500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "+\$20 bonus in December (\$200 total annual Uber Cash)"), b("amex-plat-uber-one", "\$120 Annual Uber One Credit", 12000L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$120/yr statement credit for Uber One membership"), b("amex-plat-airline-fee", "\$200 Airline Incidental Fee Credit", 20000L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "\$200 annual statement credit for incidental fees with one selected airline"), b("amex-plat-saks-credit", "\$50 Semi-Annual Saks Credit (\$100/yr)", 5000L, BenefitCadence.SEMI_ANNUAL, BenefitAnchor.CALENDAR, "Up to \$50 semi-annually (Jan-Jun, Jul-Dec) at Saks Fifth Avenue"), b("amex-plat-clear-plus", "\$209 CLEAR Plus Credit", 20900L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$209 per calendar year statement credit for CLEAR Plus membership"), b("amex-plat-walmart-plus", "\$12.95/mo Walmart+ Membership Credit", 1295L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Monthly statement credit for Walmart+ membership"))
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
            rewardCategories = listOf(r("Travel & Flights", "Travel Worldwide", 3.0), r("Gas & Transit", "Transit & Rideshare", 3.0), r("Dining & Restaurants", "Restaurants Worldwide", 3.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("amex-green-clear", "\$209 CLEAR Plus Credit", 20900L, BenefitCadence.ANNUAL, BenefitAnchor.CALENDAR, "Up to \$209 annual statement credit for CLEAR Plus membership"))
        ),
        card(
            id = "amex_bcp", shortLabel = "Blue Cash Preferred", cardName = "Blue Cash Preferred",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#0F4C81:#38BDF8:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/blue-cash-preferred.webp", annualFeeCents = 9500L,
            isAnnualFeeWaived = true, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "\$250 Statement Credit", defaultSubEstimatedValueCents = 25000L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Groceries", "U.S. Supermarkets (\$6k cap)", 6.0, capCents = 600000L), r("Streaming & Entertainment", "Select U.S. Streaming", 6.0), r("Gas & Transit", "U.S. Gas Stations & Transit", 3.0), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("amex-bcp-disney", "\$10/mo Disney Streaming Credit (\$120/yr)", 1000L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$10 monthly statement credit on eligible Disney+, Hulu, or ESPN+ subscriptions"))
        ),
        card(
            id = "amex_bce", shortLabel = "Blue Cash Everyday", cardName = "Blue Cash Everyday",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#1E40AF:#60A5FA:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/blue-cash-everyday.webp", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = false,
            countsToward524Override = null, subCooldownMonths = 999,
            defaultSubRewardText = "\$200 Statement Credit", defaultSubEstimatedValueCents = 20000L,
            defaultSubTargetSpendCents = 200000L, subSpendWindowDays = 180,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Groceries", "U.S. Supermarkets (\$6k cap)", 3.0, capCents = 600000L), r("Online Shopping", "U.S. Online Retail (\$6k cap)", 3.0, capCents = 600000L), r("Gas & Transit", "U.S. Gas Stations (\$6k cap)", 3.0, capCents = 600000L), r("Everything Else", "Everything Else", 1.0)),
            benefits = listOf(b("amex-bce-disney", "\$7/mo Disney Streaming Credit (\$84/yr)", 700L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "\$7 monthly credit for eligible Disney Bundle subscriptions"), b("amex-bce-homechef", "\$15/mo Home Chef Credit (\$180/yr)", 1500L, BenefitCadence.MONTHLY, BenefitAnchor.CALENDAR, "Up to \$15/mo statement credit on Home Chef meal kits"))
        ),
        card(
            id = "amex_bbp", shortLabel = "Blue Business Plus", cardName = "Blue Business Plus",
            issuerName = "American Express", network = "Amex", imageUrl = "hex://#1E3A8A:#3B82F6:#FFFFFF",
            artworkUrl = "https://www.offeroptimist.com/images/amex/blue-business-plus.webp", annualFeeCents = 0L,
            isAnnualFeeWaived = false, isBusinessCard = true,
            countsToward524Override = false, subCooldownMonths = 999,
            defaultSubRewardText = "15,000 Membership Rewards pts", defaultSubEstimatedValueCents = 22500L,
            defaultSubTargetSpendCents = 300000L, subSpendWindowDays = 90,
            isElevatedSubOffer = false,
            rewardCategories = listOf(r("Everything Else", "Every Business Purchase (\$50k annual cap)", 2.0, capCents = 5000000L)),
            benefits = emptyList()
        )
    )

    val BUNDLED: List<CreditCardTemplate> =
        CORE_CARDS +
            CreditCardCatalogSeed.ADDITIONAL_CARDS +
            CreditCardCatalogExtendedA.CARDS +
            CreditCardCatalogExtendedB.CARDS +
            CreditCardCatalogExtendedC.CARDS +
            CreditCardCatalogExtendedD.CARDS

    private val _catalogFlow = MutableStateFlow(BUNDLED)
    val catalogFlow: StateFlow<List<CreditCardTemplate>> = _catalogFlow.asStateFlow()

    val ALL: List<CreditCardTemplate> get() = _catalogFlow.value

    fun findBundledById(id: String): CreditCardTemplate? = BUNDLED.firstOrNull { it.id == id }

    internal fun resetForTesting() {
        _catalogFlow.value = BUNDLED
    }

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
            if (s.startsWith("$iss ")) s = s.removePrefix("$iss ").trim()
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
            if (bundled == null) {
                remote.copy(artworkUrl = resolvedArt)
            } else {
                val isStaleServer = !remote.asOf.endsWith("-v2") || remote.asOf < bundled.asOf
                val remoteSubValid = remote.defaultSubRewardText?.contains('_') != true &&
                    (remote.defaultSubEstimatedValueCents == null || remote.defaultSubEstimatedValueCents in 1_000L..500_000L)
                if (isStaleServer) {
                    bundled.copy(
                        artworkUrl = resolvedArt ?: bundled.artworkUrl,
                        isAnnualFeeWaived = remote.isAnnualFeeWaived || bundled.isAnnualFeeWaived,
                        defaultSubRewardText = if (remoteSubValid) remote.defaultSubRewardText ?: bundled.defaultSubRewardText else bundled.defaultSubRewardText,
                        defaultSubEstimatedValueCents = if (remoteSubValid) remote.defaultSubEstimatedValueCents ?: bundled.defaultSubEstimatedValueCents else bundled.defaultSubEstimatedValueCents,
                        defaultSubTargetSpendCents = if (remoteSubValid) remote.defaultSubTargetSpendCents ?: bundled.defaultSubTargetSpendCents else bundled.defaultSubTargetSpendCents,
                        subSpendWindowDays = if (remoteSubValid) remote.subSpendWindowDays ?: bundled.subSpendWindowDays else bundled.subSpendWindowDays,
                        isElevatedSubOffer = if (remoteSubValid) remote.isElevatedSubOffer else bundled.isElevatedSubOffer
                    )
                } else {
                    val hasOnlyPlaceholderMultipliers = remote.rewardCategories.isEmpty() ||
                        (remote.rewardCategories.size == 1 && remote.rewardCategories[0].multiplier <= 1.0 && bundled.rewardCategories.any { it.multiplier > 1.0 })
                    remote.copy(
                        artworkUrl = resolvedArt,
                        rewardCategories = if (hasOnlyPlaceholderMultipliers) bundled.rewardCategories else remote.rewardCategories,
                        defaultSubRewardText = if (remoteSubValid) remote.defaultSubRewardText else bundled.defaultSubRewardText,
                        defaultSubEstimatedValueCents = if (remoteSubValid) remote.defaultSubEstimatedValueCents else bundled.defaultSubEstimatedValueCents
                    )
                }
            }
        }
        val remoteIds = mergedRemote.map { it.id }.toSet()
        val fallbackExtras = BUNDLED.filter { it.id !in remoteIds }
        _catalogFlow.value = mergedRemote + fallbackExtras
    }
}
