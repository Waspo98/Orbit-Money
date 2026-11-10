package app.overbay.orbit.domain

import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.RewardCategory

internal object CreditCardCatalogSeed {

    val ADDITIONAL_CARDS: List<CreditCardTemplate> = listOf(
        CreditCardTemplate(
            id = "ink_unlimited",
            shortLabel = "Ink Unlimited",
            cardName = "Ink Business Unlimited",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "hex://#1E293B:#334155:#FFFFFF",
            annualFeeCents = 0L,
            isBusinessCard = true,
            subCooldownMonths = 24,
            defaultSubRewardText = "75,000 Ultimate Rewards pts",
            defaultSubEstimatedValueCents = 1125_00L,
            defaultSubTargetSpendCents = 6000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Everything Else", label = "Everything Else (Catch-All)", multiplier = 1.5)
            )
        ),
        CreditCardTemplate(
            id = "ink_preferred",
            shortLabel = "Ink Preferred",
            cardName = "Ink Business Preferred",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "hex://#0B192C:#1E3E62:#FFFFFF",
            annualFeeCents = 95_00L,
            isBusinessCard = true,
            subCooldownMonths = 24,
            defaultSubRewardText = "90,000 Ultimate Rewards pts",
            defaultSubEstimatedValueCents = 1350_00L,
            defaultSubTargetSpendCents = 8000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Travel, Shipping, Internet & Ads", multiplier = 3.0),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 1.0)
            )
        ),
        CreditCardTemplate(
            id = "chase_amazon_prime",
            shortLabel = "Prime Visa",
            cardName = "Amazon Prime Visa",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "hex://#1E293B:#0F172A:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 24,
            defaultSubRewardText = "$200 Amazon Gift Card Instantly",
            defaultSubEstimatedValueCents = 200_00L,
            defaultSubTargetSpendCents = 0L,
            subSpendWindowDays = 30,
            rewardCategories = listOf(
                RewardCategory(category = "Shopping", label = "Amazon.com, Whole Foods & Chase Travel", multiplier = 5.0),
                RewardCategory(category = "Dining & Restaurants", label = "Restaurants & Gas Stations", multiplier = 2.0),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 1.0)
            )
        ),
        CreditCardTemplate(
            id = "chase_hyatt",
            shortLabel = "World of Hyatt",
            cardName = "World of Hyatt",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "hex://#111827:#1E3A8A:#FFFFFF",
            annualFeeCents = 95_00L,
            subCooldownMonths = 24,
            defaultSubRewardText = "30,000 Hyatt Bonus Points",
            defaultSubEstimatedValueCents = 540_00L,
            defaultSubTargetSpendCents = 3000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Hyatt Hotels & Resorts", multiplier = 4.0),
                RewardCategory(category = "Dining & Restaurants", label = "Dining, Flights & Gym", multiplier = 2.0)
            ),
            benefits = listOf(
                CardBenefit(
                    id = "hyatt-free-night",
                    title = "Category 1-4 Free Night Award",
                    periodValueCents = 180_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CARDMEMBER_YEAR,
                    description = "1 free night at any Category 1-4 Hyatt hotel each anniversary year"
                )
            )
        ),
        CreditCardTemplate(
            id = "chase_united_explorer",
            shortLabel = "United Explorer",
            cardName = "United Explorer",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "hex://#0C2340:#1D4F91:#FFFFFF",
            annualFeeCents = 150_00L,
            isAnnualFeeWaived = true,
            subCooldownMonths = 24,
            defaultSubRewardText = "60,000 United MileagePlus Miles",
            defaultSubEstimatedValueCents = 720_00L,
            defaultSubTargetSpendCents = 3000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "United Purchases, Dining & Hotels", multiplier = 2.0),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 1.0)
            ),
            benefits = listOf(
                CardBenefit(
                    id = "united-exp-global-entry",
                    title = "Global Entry / TSA PreCheck Credit",
                    periodValueCents = 120_00L,
                    cadence = BenefitCadence.EVERY_4_YEARS,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $120 credit for Global Entry, TSA PreCheck, or NEXUS"
                ),
                CardBenefit(
                    id = "united-exp-club-passes",
                    title = "2 United Club One-Time Passes",
                    periodValueCents = 118_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CARDMEMBER_YEAR,
                    description = "Two complimentary United Club one-time passes each anniversary year"
                )
            )
        ),
        CreditCardTemplate(
            id = "chase_marriott_boundless",
            shortLabel = "Marriott Boundless",
            cardName = "Marriott Bonvoy Boundless",
            issuerName = "Chase",
            network = "Visa",
            imageUrl = "hex://#4A1525:#7F1D1D:#FFFFFF",
            annualFeeCents = 95_00L,
            subCooldownMonths = 24,
            defaultSubRewardText = "3 Free Night Awards (up to 150k pts)",
            defaultSubEstimatedValueCents = 600_00L,
            defaultSubTargetSpendCents = 3000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Marriott Bonvoy Hotels", multiplier = 6.0),
                RewardCategory(category = "Groceries", label = "Grocery, Gas & Dining ($6k cap)", multiplier = 3.0, capCents = 6000_00L),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 2.0)
            ),
            benefits = listOf(
                CardBenefit(
                    id = "boundless-free-night",
                    title = "35,000-Pt Annual Free Night Award",
                    periodValueCents = 175_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CARDMEMBER_YEAR,
                    description = "Free Night Award worth up to 35,000 points every cardmember anniversary"
                )
            )
        ),
        CreditCardTemplate(
            id = "chase_ihg_premier",
            shortLabel = "IHG One Premier",
            cardName = "IHG One Rewards Premier",
            issuerName = "Chase",
            network = "Mastercard",
            imageUrl = "hex://#1F2937:#0F766E:#FFFFFF",
            annualFeeCents = 99_00L,
            subCooldownMonths = 24,
            defaultSubRewardText = "140,000 IHG One Rewards Points",
            defaultSubEstimatedValueCents = 700_00L,
            defaultSubTargetSpendCents = 3000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "IHG Hotels & Resorts", multiplier = 10.0),
                RewardCategory(category = "Dining & Restaurants", label = "Travel, Gas & Dining", multiplier = 5.0),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 3.0)
            ),
            benefits = listOf(
                CardBenefit(
                    id = "ihg-free-night",
                    title = "40,000-Pt Anniversary Free Night",
                    periodValueCents = 160_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CARDMEMBER_YEAR,
                    description = "Anniversary Free Night valid up to 40,000 points"
                ),
                CardBenefit(
                    id = "ihg-united-travelbank",
                    title = "$50 Annual United TravelBank Cash",
                    periodValueCents = 25_00L,
                    cadence = BenefitCadence.SEMI_ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "$25 United TravelBank Cash deposited every Jan and Jul ($50/yr)"
                )
            )
        ),
        CreditCardTemplate(
            id = "amex_green",
            shortLabel = "Amex Green",
            cardName = "American Express Green",
            issuerName = "American Express",
            network = "Amex",
            imageUrl = "hex://#065F46:#10B981:#FFFFFF",
            annualFeeCents = 150_00L,
            subCooldownMonths = 999,
            defaultSubRewardText = "40,000 Membership Rewards pts",
            defaultSubEstimatedValueCents = 600_00L,
            defaultSubTargetSpendCents = 3000_00L,
            subSpendWindowDays = 180,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Travel & Transit", multiplier = 3.0),
                RewardCategory(category = "Dining & Restaurants", label = "Restaurants Worldwide", multiplier = 3.0)
            ),
            benefits = listOf(
                CardBenefit(
                    id = "amex-green-clear",
                    title = "$199 CLEAR Plus Credit",
                    periodValueCents = 199_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $199 annual statement credit for CLEAR Plus membership"
                )
            )
        ),
        CreditCardTemplate(
            id = "amex_bcp",
            shortLabel = "Blue Cash Preferred",
            cardName = "Blue Cash Preferred",
            issuerName = "American Express",
            network = "Amex",
            imageUrl = "hex://#0F4C81:#38BDF8:#FFFFFF",
            annualFeeCents = 95_00L,
            isAnnualFeeWaived = true,
            subCooldownMonths = 999,
            defaultSubRewardText = "$250 Statement Credit",
            defaultSubEstimatedValueCents = 250_00L,
            defaultSubTargetSpendCents = 3000_00L,
            subSpendWindowDays = 180,
            rewardCategories = listOf(
                RewardCategory(category = "Groceries", label = "U.S. Supermarkets ($6k cap)", multiplier = 6.0, capCents = 6000_00L),
                RewardCategory(category = "Streaming & Entertainment", label = "Select U.S. Streaming", multiplier = 6.0),
                RewardCategory(category = "Gas & Transit", label = "U.S. Gas Stations & Transit", multiplier = 3.0)
            ),
            benefits = listOf(
                CardBenefit(
                    id = "amex-bcp-disney",
                    title = "$7/mo Disney Bundle Credit ($84/yr)",
                    periodValueCents = 7_00L,
                    cadence = BenefitCadence.MONTHLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "$7 monthly statement credit on eligible Disney Bundle subscriptions"
                )
            )
        ),
        CreditCardTemplate(
            id = "amex_bce",
            shortLabel = "Blue Cash Everyday",
            cardName = "Blue Cash Everyday",
            issuerName = "American Express",
            network = "Amex",
            imageUrl = "hex://#1E40AF:#60A5FA:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 999,
            defaultSubRewardText = "$200 Statement Credit",
            defaultSubEstimatedValueCents = 200_00L,
            defaultSubTargetSpendCents = 2000_00L,
            subSpendWindowDays = 180,
            rewardCategories = listOf(
                RewardCategory(category = "Groceries", label = "U.S. Supermarkets ($6k cap)", multiplier = 3.0, capCents = 6000_00L),
                RewardCategory(category = "Shopping", label = "U.S. Online Retail ($6k cap)", multiplier = 3.0, capCents = 6000_00L),
                RewardCategory(category = "Gas & Transit", label = "U.S. Gas Stations ($6k cap)", multiplier = 3.0, capCents = 6000_00L)
            )
        ),
        CreditCardTemplate(
            id = "amex_bbp",
            shortLabel = "Blue Business Plus",
            cardName = "Blue Business Plus",
            issuerName = "American Express",
            network = "Amex",
            imageUrl = "hex://#1E3A8A:#3B82F6:#FFFFFF",
            annualFeeCents = 0L,
            isBusinessCard = true,
            subCooldownMonths = 999,
            defaultSubRewardText = "15,000 Membership Rewards pts",
            defaultSubEstimatedValueCents = 225_00L,
            defaultSubTargetSpendCents = 3000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Everything Else", label = "Every Purchase ($50k annual cap)", multiplier = 2.0, capCents = 50000_00L)
            )
        ),
        CreditCardTemplate(
            id = "amex_hilton_aspire",
            shortLabel = "Hilton Aspire",
            cardName = "Hilton Honors Aspire",
            issuerName = "American Express",
            network = "Amex",
            imageUrl = "hex://#0F172A:#1E3A8A:#FFFFFF",
            annualFeeCents = 550_00L,
            subCooldownMonths = 999,
            defaultSubRewardText = "150,000 Hilton Honors Bonus Points",
            defaultSubEstimatedValueCents = 750_00L,
            defaultSubTargetSpendCents = 6000_00L,
            subSpendWindowDays = 180,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Hilton Hotels & Resorts", multiplier = 14.0),
                RewardCategory(category = "Dining & Restaurants", label = "Flights, Car Rentals & U.S. Restaurants", multiplier = 7.0),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 3.0)
            ),
            benefits = listOf(
                CardBenefit(
                    id = "aspire-resort-credit",
                    title = "$200 Semi-Annual Hilton Resort Credit ($400/yr)",
                    periodValueCents = 200_00L,
                    cadence = BenefitCadence.SEMI_ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $200 Jan-Jun and $200 Jul-Dec at participating Hilton Resorts"
                ),
                CardBenefit(
                    id = "aspire-flight-credit",
                    title = "$50 Quarterly Flight Credit ($200/yr)",
                    periodValueCents = 50_00L,
                    cadence = BenefitCadence.QUARTERLY,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "Up to $50 per quarter on flights booked direct or via Amex Travel"
                )
            )
        ),
        CreditCardTemplate(
            id = "venture_rewards",
            shortLabel = "Venture Rewards",
            cardName = "Venture Rewards",
            issuerName = "Capital One",
            network = "Visa",
            imageUrl = "hex://#1E3A8A:#2563EB:#FFFFFF",
            annualFeeCents = 95_00L,
            subCooldownMonths = 48,
            defaultSubRewardText = "75,000 Capital One Miles",
            defaultSubEstimatedValueCents = 1050_00L,
            defaultSubTargetSpendCents = 4000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Travel & Flights", label = "Capital One Travel Hotels & Rental Cars", multiplier = 5.0),
                RewardCategory(category = "Everything Else", label = "Everything Else (Catch-All)", multiplier = 2.0)
            )
        ),
        CreditCardTemplate(
            id = "capital_one_savor",
            shortLabel = "Capital One Savor",
            cardName = "Capital One Savor",
            issuerName = "Capital One",
            network = "Visa",
            imageUrl = "hex://#9A3412:#EA580C:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 48,
            defaultSubRewardText = "$200 Cash Bonus",
            defaultSubEstimatedValueCents = 200_00L,
            defaultSubTargetSpendCents = 500_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Dining & Restaurants", label = "Dining", multiplier = 3.0),
                RewardCategory(category = "Groceries", label = "Grocery Stores", multiplier = 3.0),
                RewardCategory(category = "Streaming & Entertainment", label = "Entertainment & Popular Streaming", multiplier = 3.0)
            )
        ),
        CreditCardTemplate(
            id = "spark_cash_select",
            shortLabel = "Spark 1.5% Cash Select",
            cardName = "Spark 1.5% Cash Select",
            issuerName = "Capital One",
            network = "Visa",
            imageUrl = "hex://#1F2937:#059669:#FFFFFF",
            annualFeeCents = 0L,
            isBusinessCard = true,
            countsToward524Override = true,
            subCooldownMonths = 48,
            defaultSubRewardText = "$500 Cash Bonus",
            defaultSubEstimatedValueCents = 500_00L,
            defaultSubTargetSpendCents = 4500_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Everything Else", label = "Every Business Purchase", multiplier = 1.5)
            )
        ),
        CreditCardTemplate(
            id = "citi_strata_premier",
            shortLabel = "Citi Strata Premier",
            cardName = "Citi Strata Premier",
            issuerName = "Citi",
            network = "Mastercard",
            imageUrl = "hex://#0F172A:#0D9488:#FFFFFF",
            annualFeeCents = 95_00L,
            subCooldownMonths = 48,
            defaultSubRewardText = "75,000 ThankYou Points",
            defaultSubEstimatedValueCents = 1050_00L,
            defaultSubTargetSpendCents = 4000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Dining & Restaurants", label = "Restaurants", multiplier = 3.0),
                RewardCategory(category = "Groceries", label = "Supermarkets", multiplier = 3.0),
                RewardCategory(category = "Gas & Transit", label = "Gas Stations & EV Charging", multiplier = 3.0),
                RewardCategory(category = "Travel & Flights", label = "Air Travel & Hotels", multiplier = 3.0)
            ),
            benefits = listOf(
                CardBenefit(
                    id = "strata-hotel-credit",
                    title = "$100 Annual Hotel Benefit",
                    periodValueCents = 100_00L,
                    cadence = BenefitCadence.ANNUAL,
                    anchor = BenefitAnchor.CALENDAR,
                    description = "$100 off a single hotel stay of $500+ booked through CitiTravel.com"
                )
            )
        ),
        CreditCardTemplate(
            id = "citi_double_cash",
            shortLabel = "Citi Double Cash",
            cardName = "Citi Double Cash",
            issuerName = "Citi",
            network = "Mastercard",
            imageUrl = "hex://#0369A1:#0D9488:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 48,
            defaultSubRewardText = "$200 / 20,000 ThankYou Pts",
            defaultSubEstimatedValueCents = 280_00L,
            defaultSubTargetSpendCents = 1500_00L,
            subSpendWindowDays = 180,
            rewardCategories = listOf(
                RewardCategory(category = "Everything Else", label = "Everything Else (1% Buy + 1% Pay)", multiplier = 2.0)
            )
        ),
        CreditCardTemplate(
            id = "citi_custom_cash",
            shortLabel = "Citi Custom Cash",
            cardName = "Citi Custom Cash",
            issuerName = "Citi",
            network = "Mastercard",
            imageUrl = "hex://#1E3A8A:#10B981:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 48,
            defaultSubRewardText = "$200 / 20,000 ThankYou Pts",
            defaultSubEstimatedValueCents = 280_00L,
            defaultSubTargetSpendCents = 1500_00L,
            subSpendWindowDays = 180,
            rewardCategories = listOf(
                RewardCategory(category = "Groceries", label = "Top Eligible Spend Category ($500/mo cap)", multiplier = 5.0, capCents = 500_00L)
            )
        ),
        CreditCardTemplate(
            id = "wf_autograph",
            shortLabel = "Wells Fargo Autograph",
            cardName = "Wells Fargo Autograph",
            issuerName = "Wells Fargo",
            network = "Visa",
            imageUrl = "hex://#7F1D1D:#B91C1C:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 48,
            defaultSubRewardText = "20,000 Bonus Points ($200 val)",
            defaultSubEstimatedValueCents = 200_00L,
            defaultSubTargetSpendCents = 1000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Dining & Restaurants", label = "Restaurants", multiplier = 3.0),
                RewardCategory(category = "Travel & Flights", label = "Travel & Transit", multiplier = 3.0),
                RewardCategory(category = "Gas & Transit", label = "Gas Stations & EV Charging", multiplier = 3.0)
            )
        ),
        CreditCardTemplate(
            id = "wf_active_cash",
            shortLabel = "Wells Fargo Active Cash",
            cardName = "Wells Fargo Active Cash",
            issuerName = "Wells Fargo",
            network = "Visa",
            imageUrl = "hex://#1E293B:#991B1B:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 48,
            defaultSubRewardText = "$200 Cash Rewards Bonus",
            defaultSubEstimatedValueCents = 200_00L,
            defaultSubTargetSpendCents = 500_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Everything Else", label = "Everything Else (Catch-All)", multiplier = 2.0)
            )
        ),
        CreditCardTemplate(
            id = "bofa_customized_cash",
            shortLabel = "BofA Customized Cash",
            cardName = "Bank of America Customized Cash Rewards",
            issuerName = "Bank of America",
            network = "Visa",
            imageUrl = "hex://#991B1B:#DC2626:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 24,
            defaultSubRewardText = "$200 Online Cash Rewards Bonus",
            defaultSubEstimatedValueCents = 200_00L,
            defaultSubTargetSpendCents = 1000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Shopping", label = "3% Choice Category ($2,500/qtr cap)", multiplier = 3.0, capCents = 2500_00L),
                RewardCategory(category = "Groceries", label = "Grocery Stores & Wholesale Clubs", multiplier = 2.0)
            )
        ),
        CreditCardTemplate(
            id = "usbank_altitude_go",
            shortLabel = "U.S. Bank Altitude Go",
            cardName = "U.S. Bank Altitude Go",
            issuerName = "U.S. Bank",
            network = "Visa",
            imageUrl = "hex://#1E3A8A:#B91C1C:#FFFFFF",
            annualFeeCents = 0L,
            subCooldownMonths = 48,
            defaultSubRewardText = "20,000 Bonus Points ($200 Value)",
            defaultSubEstimatedValueCents = 200_00L,
            defaultSubTargetSpendCents = 1000_00L,
            subSpendWindowDays = 90,
            rewardCategories = listOf(
                RewardCategory(category = "Dining & Restaurants", label = "Dining, Takeout & Delivery", multiplier = 4.0),
                RewardCategory(category = "Groceries", label = "Grocery Stores, Gas & Streaming", multiplier = 2.0)
            )
        ),
        CreditCardTemplate(
            id = "discover_it",
            shortLabel = "Discover it Cash Back",
            cardName = "Discover it Cash Back",
            issuerName = "Discover",
            network = "Discover",
            imageUrl = "preset://discover-plum",
            annualFeeCents = 0L,
            subCooldownMonths = 24,
            defaultSubRewardText = "Unlimited Cashback Match (First Year)",
            defaultSubEstimatedValueCents = 150_00L,
            defaultSubTargetSpendCents = null,
            subSpendWindowDays = 365,
            rewardCategories = listOf(
                RewardCategory(category = "Quarterly Bonus", label = "5% Rotating Quarterly Categories ($1,500 cap)", multiplier = 5.0, isRotating = true, capCents = 1500_00L),
                RewardCategory(category = "Everything Else", label = "Everything Else", multiplier = 1.0)
            )
        ),
        CreditCardTemplate(
            id = "apple_card",
            shortLabel = "Apple Card",
            cardName = "Apple Card",
            issuerName = "Goldman Sachs / Apple",
            network = "Mastercard",
            imageUrl = "preset://apple-card",
            annualFeeCents = 0L,
            subCooldownMonths = null,
            rewardCategories = listOf(
                RewardCategory(category = "Shopping", label = "Apple & Select Daily Cash Partners", multiplier = 3.0),
                RewardCategory(category = "Everything Else", label = "Every Apple Pay Purchase", multiplier = 2.0)
            )
        )
    )
}
