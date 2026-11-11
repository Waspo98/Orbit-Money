// =============================================================================
// creditCardCatalogExtendedSeedC.js - Extended US credit cards (Part C).
// =============================================================================

const b = (id, title, cents, cadence = 'ANNUAL', anchor = 'CALENDAR', description = '') => ({
  id,
  name: title,
  title,
  value: cents === null ? null : Math.round((cents * (cadence === 'MONTHLY' ? 12 : cadence === 'QUARTERLY' ? 4 : cadence === 'SEMI_ANNUAL' ? 2 : 1)) / 100),
  period_value: cents === null ? null : cents / 100,
  period_value_cents: cents,
  cadence,
  anchor,
  description
});
const r = (category, label, multiplier, extra = {}) => ({
  category,
  label,
  rate: multiplier,
  multiplier,
  is_rotating: Boolean(extra.isRotating),
  isRotating: Boolean(extra.isRotating),
  cap: extra.capCents != null ? extra.capCents / 100 : null,
  capCents: extra.capCents ?? null,
  type: 'multiplier'
});

export const EXTENDED_CARD_CATALOG_C = [
  {
    id: "citi_strata_elite", bonusApiCardId: "19c94f90056968ebde2882f7bb0f1010", sortRank: 250,
    shortLabel: "Strata Elite", cardName: "Strata Elite", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/citi/strata-elite.webp",
    annualFeeCents: 59500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "75,000 ThankYou Points", defaultSubEstimatedValueCents: 105000, defaultSubTargetSpendCents: 600000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Citi Travel Hotels, Car Rentals & Attractions", 12), r("Travel & Flights", "Citi Travel Airfare", 6), r("Dining & Restaurants", "Restaurants (6x Fri/Sat 6pm-6am ET, 3x Other Times)", 3), r("Everything Else", "Everything Else (Catch-All)", 1.5)],
    benefits: [
      b("strata-elite-hotel", "$300 Annual Citi Travel Hotel Benefit", 30000, "ANNUAL", "CALENDAR", "Up to $300 off a 2+ night hotel stay booked through CitiTravel.com each calendar year"),
      b("strata-elite-splurge", "$200 Annual Splurge Credit (AA, Best Buy, Live Nation, 1stDibs)", 20000, "ANNUAL", "CALENDAR", "Up to $200/yr statement credit across up to 2 chosen Splurge brands"),
      b("strata-elite-blacklane", "$100 Semi-Annual Blacklane Chauffeur Credit ($200/yr)", 10000, "SEMI_ANNUAL", "CALENDAR", "Up to $100 Jan-Jun and $100 Jul-Dec on Blacklane chauffeur rides"),
      b("strata-elite-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("strata-elite-lounge", "Priority Pass Select & 4 Admirals Club Passes", null, "ANNUAL", "CALENDAR", "Priority Pass Select (with 2 guests) + 4 Admirals Club lounge passes per year")
    ]
  },
  {
    id: "citi_strata", bonusApiCardId: "cbc737da5cbd44575dec6325bbbd8688", sortRank: 250,
    shortLabel: "Strata", cardName: "Strata", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#0F172A:#0D9488:#FFFFFF", upstreamImagePath: "/images/citi/strata.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "20,000 ThankYou Points", defaultSubEstimatedValueCents: 28000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Citi Travel Hotels, Car Rentals & Attractions", 5), r("Groceries", "Supermarkets", 3), r("Gas & Transit", "Gas Stations, EV Charging & Select Transit", 3), r("Streaming & Entertainment", "3x Self-Select Category (Streaming, Fitness, Live Ent, Pet)", 3, { isRotating: true }), r("Dining & Restaurants", "Restaurants", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "citi_at_t_points_plus", bonusApiCardId: "d4bbd57e45d6a6ddb445ddec4e781d7c", sortRank: 250,
    shortLabel: "AT&T Points Plus", cardName: "AT&T Points Plus", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#0F172A:#0D9488:#FFFFFF", upstreamImagePath: "/images/citi/att-points-plus.jpg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$250 Cash Bonus", defaultSubEstimatedValueCents: 25000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: true,
    rewardCategories: [r("Gas & Transit", "Gas Stations", 3), r("Groceries", "Grocery Stores (incl. Delivery)", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("att-fiber", "Up to $20/mo AT&T Wireless/Fiber Credit ($1k/mo spend)", 2000, "MONTHLY", "CALENDAR", "$10/mo ($500 spend) or $20/mo ($1,000 spend) credit toward eligible AT&T service")
    ]
  },
  {
    id: "comenity_aaa_daily_advantage", bonusApiCardId: "eb573cdc20d07fc2d60e8528f73cafb0", sortRank: 250,
    shortLabel: "AAA Daily Advantage", cardName: "AAA Daily Advantage", issuerName: "AAA / Comenity", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/comenity/aaa-daily-advantage.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$100 Cash Bonus", defaultSubEstimatedValueCents: 10000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Groceries", "Grocery Stores ($10k/yr combined cap)", 5, { capCents: 1000000 }), r("Gas & Transit", "Gas Stations & EV Charging ($10k cap)", 3, { capCents: 1000000 }), r("Drugstores", "Pharmacy & Wholesale Clubs ($10k cap)", 3, { capCents: 1000000 }), r("Streaming & Entertainment", "Streaming Services ($10k cap)", 3, { capCents: 1000000 }), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "comenity_aaa_travel_advantage", bonusApiCardId: "6862ac82f65ac34d31032dbe0cf1e87b", sortRank: 250,
    shortLabel: "AAA Travel Advantage", cardName: "AAA Travel Advantage", issuerName: "AAA / Comenity", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/comenity/aaa-travel-advantage.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$100 Cash Bonus", defaultSubEstimatedValueCents: 10000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Gas & Transit", "Gas Stations & EV Charging ($7k/yr cap)", 5, { capCents: 700000 }), r("Travel & Flights", "Travel Purchases", 3), r("Dining & Restaurants", "Restaurants", 3), r("Groceries", "Grocery Stores", 3), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "first_latam_airlines", bonusApiCardId: "73015adf7b2ca52331e05203c4ad5462", sortRank: 250,
    shortLabel: "LATAM Airlines", cardName: "LATAM Airlines Mastercard", issuerName: "Cardless", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: null,
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "15,000 LATAM Pass Miles", defaultSubEstimatedValueCents: 15000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "LATAM Airlines Purchases", 3), r("Dining & Restaurants", "Restaurants", 2), r("Gas & Transit", "Ground Transit & Rideshare", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "first_latam_airlines_world_elite", bonusApiCardId: "7e578b54e8976a7b9027124c56b2377f", sortRank: 250,
    shortLabel: "LATAM World Elite", cardName: "LATAM Airlines World Elite", issuerName: "Cardless", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/first/latam-airlines-world-elite.webp",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "40,000 LATAM Pass Miles", defaultSubEstimatedValueCents: 40000, defaultSubTargetSpendCents: 350000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "LATAM Airlines Purchases", 4), r("Dining & Restaurants", "Restaurants", 3), r("Gas & Transit", "Ground Transit & Rideshare", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("latam-we-lounge", "3 Annual LATAM Lounge Passes", null, "ANNUAL", "CARDMEMBER_YEAR", "3 complimentary LATAM VIP Lounge passes per year")
    ]
  },
  {
    id: "first_cardless_qatar_signature", bonusApiCardId: "684de802ca5bf73c791a752537da84c5", sortRank: 250,
    shortLabel: "Qatar Privilege Signature", cardName: "Qatar Airways Privilege Club Signature", issuerName: "Cardless", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/first/cardless-qatar-signature.jpg",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "40,000 Avios", defaultSubEstimatedValueCents: 48000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Qatar Airways Purchases", 4), r("Dining & Restaurants", "Restaurants", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "first_cardless_qatar_infinite", bonusApiCardId: "ea277f6af8a60065a83e5f16569dcce8", sortRank: 250,
    shortLabel: "Qatar Privilege Infinite", cardName: "Qatar Airways Privilege Club Infinite", issuerName: "Cardless", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/first/cardless-qatar-infinite.jpg",
    annualFeeCents: 49900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 Avios", defaultSubEstimatedValueCents: 60000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Qatar Airways Purchases", 5), r("Dining & Restaurants", "Restaurants", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("qatar-gold", "Qatar Privilege Club Gold Status Year 1", null, "ANNUAL", "CARDMEMBER_YEAR", "oneworld Sapphire / Privilege Club Gold status for first year")
    ]
  },
  {
    id: "first_avianca_lifemiles", bonusApiCardId: "1babfb0011a010a6149965531021ad2e", sortRank: 250,
    shortLabel: "Avianca LifeMiles", cardName: "Avianca LifeMiles American Express", issuerName: "Cardless", network: "Amex",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/first/avianca-lifemiles.webp",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "40,000 LifeMiles", defaultSubEstimatedValueCents: 40000, defaultSubTargetSpendCents: 450000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Avianca Purchases", 2), r("Groceries", "Grocery Stores", 2), r("Dining & Restaurants", "Restaurants", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "first_avianca_lifemiles_elite", bonusApiCardId: "0bcc80cc6922e7c7d89e9c9b2efcd9e1", sortRank: 250,
    shortLabel: "Avianca LifeMiles Elite", cardName: "Avianca LifeMiles American Express Elite", issuerName: "Cardless", network: "Amex",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/first/avianca-lifemiles-elite.webp",
    annualFeeCents: 24900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 LifeMiles", defaultSubEstimatedValueCents: 60000, defaultSubTargetSpendCents: 450000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Avianca & Hotel Purchases", 3), r("Dining & Restaurants", "Restaurants", 2), r("Gas & Transit", "Gas, Rideshare & Ground Transit", 2), r("Everything Else", "Everything Else (2x Effective on 1st $1,000/mo)", 1)],
    benefits: [
      b("avianca-silver", "LifeMiles Silver Status & Monthly Miles Match", null, "ANNUAL", "CALENDAR", "Complimentary LifeMiles Silver status + 100% match on first 1,000 miles earned monthly")
    ]
  },
  {
    id: "first_ana_card_usa_plus", bonusApiCardId: "0b3a9849575c68ce5cbf31aad08f015e", sortRank: 350,
    shortLabel: "ANA CARD U.S.A. Plus", cardName: "ANA CARD U.S.A. Plus", issuerName: "FNBO", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/first/ana-card-usa.png",
    annualFeeCents: 8500, isAnnualFeeWaived: true, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "5,000 ANA Mileage Club Miles", defaultSubEstimatedValueCents: 6500, defaultSubTargetSpendCents: 1, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "ANA Airline Purchases", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("ana-renewal", "1,000 Renewal Bonus ANA Miles", 1300, "ANNUAL", "CARDMEMBER_YEAR", "1,000 bonus ANA Mileage Club miles upon annual renewal")
    ]
  },
  {
    id: "fnbo_amtrak_guest_rewards", bonusApiCardId: "1f13c36d3b8218b1a22d5ab050d30b09", sortRank: 250,
    shortLabel: "Amtrak Guest Rewards", cardName: "Amtrak Guest Rewards", issuerName: "FNBO", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/fnbo/amtrak-guest-rewards.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "12,000 Amtrak Guest Rewards pts", defaultSubEstimatedValueCents: 30000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Amtrak Travel Purchases", 2), r("Dining & Restaurants", "Dining", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "fnbo_amtrak_guest_rewards_preferred", bonusApiCardId: "4a2f48392c94bc6b0ef9b6d30153bddc", sortRank: 250,
    shortLabel: "Amtrak Guest Rewards Preferred", cardName: "Amtrak Guest Rewards Preferred", issuerName: "FNBO", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/fnbo/amtrak-guest-rewards-preferred.jpg",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "40,000 Amtrak Guest Rewards pts", defaultSubEstimatedValueCents: 100000, defaultSubTargetSpendCents: 200000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Amtrak Travel Purchases", 3), r("Travel & Flights", "Other Travel", 2), r("Gas & Transit", "Transit & Rideshare", 2), r("Dining & Restaurants", "Dining", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("amtrak-pref-coupons", "Annual Companion Coupon, One-Class Upgrade & Lounge Pass", null, "ANNUAL", "CARDMEMBER_YEAR", "Round-trip companion coupon, 1-class upgrade, and station lounge pass each year")
    ]
  },
  {
    id: "fnbo_best_western_rewards", bonusApiCardId: "1b70fa39672eb066b7a3a5092888e7b2", sortRank: 250,
    shortLabel: "Best Western Rewards", cardName: "Best Western Rewards", issuerName: "FNBO", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/fnbo/best-western-rewards.jpg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "40,000 Best Western Rewards pts", defaultSubEstimatedValueCents: 24000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Best Western Hotels (3x Card + 10x Member)", 3), r("Everything Else", "Everything Else (Catch-All)", 2)],
    benefits: []
  },
  {
    id: "fnbo_best_western_rewards_premium", bonusApiCardId: "cf10cfcfab787460b24ecbf8b76f7bdc", sortRank: 250,
    shortLabel: "Best Western Rewards Premium", cardName: "Best Western Rewards Premium", issuerName: "FNBO", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/fnbo/best-western-rewards-premium.jpg",
    annualFeeCents: 8900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "80,000 Best Western Rewards pts", defaultSubEstimatedValueCents: 48000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Best Western Hotels (10x Card + 10x Member)", 10), r("Everything Else", "Everything Else (Catch-All)", 2)],
    benefits: [
      b("bw-prem-bonus", "40,000 Bonus Pts ($5,000 annual spend)", null, "ANNUAL", "CARDMEMBER_YEAR", "40,000 bonus Best Western points after $5,000 spend each 12 billing cycles")
    ]
  },
  {
    id: "penfed_power_cash_rewards", bonusApiCardId: "879d4dccd8e59ba55a3a688adeb2c773", sortRank: 250,
    shortLabel: "Power Cash Rewards", cardName: "Power Cash Rewards", issuerName: "PenFed", network: "Visa",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/penfed/power-cash-rewards.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$100 Cash Bonus", defaultSubEstimatedValueCents: 10000, defaultSubTargetSpendCents: 150000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Everything Else (2% Honors Advantage / 1.5% Standard)", 2)],
    benefits: []
  },
  {
    id: "penfed_platinum_rewards", bonusApiCardId: "2743ba5aabdadc15aa6bd046f1b71a5c", sortRank: 250,
    shortLabel: "Platinum Rewards", cardName: "Platinum Rewards", issuerName: "PenFed", network: "Visa",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/penfed/platinum-rewards.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "15,000 PenFed Bonus pts", defaultSubEstimatedValueCents: 12750, defaultSubTargetSpendCents: 150000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Gas & Transit", "Gas Paid at Pump & EV Charging", 5), r("Groceries", "Supermarkets", 3), r("Dining & Restaurants", "Restaurants", 3), r("Streaming & Entertainment", "TV, Radio, Cable & Streaming", 3), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "penfed_pathfinder", bonusApiCardId: "9afcb83bfffcdc108fc3ac90a8b384da", sortRank: 250,
    shortLabel: "Pathfinder", cardName: "Pathfinder", issuerName: "PenFed", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/penfed/pathfinder.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 PenFed Bonus pts", defaultSubEstimatedValueCents: 42500, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "All Travel Categories (4x Honors / 3x Standard)", 4), r("Everything Else", "Everything Else", 1.5)],
    benefits: [
      b("pathfinder-air", "$100 Annual Domestic Air Ancillary Credit", 10000, "ANNUAL", "CALENDAR", "Up to $100/yr statement credit for U.S. domestic airline incidental fees"),
      b("pathfinder-ge", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 statement credit every 4 years")
    ]
  },
  {
    id: "pnc_cash_rewards_visa_signature_business", bonusApiCardId: "dfaa58e5c67e69cb711960cfd4567f9c", sortRank: 350,
    shortLabel: "Cash Rewards Visa Signature Business", cardName: "Cash Rewards Visa Signature Business", issuerName: "PNC", network: "Visa",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/pnc/cash-rewards-visa-signature-business.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Business Purchase", 1.5)],
    benefits: []
  },
  {
    id: "synchrony_cathay_pacific", bonusApiCardId: "0d2caa1705cde187692a28873930c605", sortRank: 250,
    shortLabel: "Cathay Pacific", cardName: "Cathay Pacific", issuerName: "Synchrony", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/synchrony/cathay-pacific.jpg",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "38,000 Cathay Asia Miles", defaultSubEstimatedValueCents: 45600, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Cathay Pacific Purchases", 3), r("Dining & Restaurants", "Dining & Food Delivery Worldwide", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "synchrony_virgin_red_rewards", bonusApiCardId: "06b77b791c904e42695dd12747e89cd8", sortRank: 250,
    shortLabel: "Virgin Red Rewards", cardName: "Virgin Red Rewards", issuerName: "Synchrony", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/synchrony/virgin-red-rewards.webp",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 Virgin Points", defaultSubEstimatedValueCents: 78000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Virgin Atlantic, Virgin Hotels & Virgin Voyages", 3), r("Dining & Restaurants", "Dining", 2), r("Groceries", "Grocery Stores", 2), r("Streaming & Entertainment", "Select Streaming Services", 2), r("Gas & Transit", "EV Charging", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("virgin-red-anniv", "5,000 Anniversary Virgin Points", 6500, "ANNUAL", "CARDMEMBER_YEAR", "5,000 bonus Virgin Points every cardmember anniversary")
    ]
  },
  {
    id: "synchrony_verizon_visa", bonusApiCardId: "d2dd7fe800c9788452ec41c3fc3a06fa", sortRank: 250,
    shortLabel: "Verizon Visa", cardName: "Verizon Visa", issuerName: "Synchrony", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/synchrony/verizon-visa.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$50 Cash Bonus", defaultSubEstimatedValueCents: 5000, defaultSubTargetSpendCents: 1, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Groceries", "Grocery Stores", 4), r("Gas & Transit", "Gas Stations & EV Charging", 4), r("Dining & Restaurants", "Dining & Takeout", 4), r("Utilities", "Verizon Purchases (excl. Bill Pay)", 4), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "synchrony_walmart_onepay_cashrewards", bonusApiCardId: "145320d72bf260a11a537333d4425820", sortRank: 250,
    shortLabel: "Walmart OnePay CashRewards", cardName: "Walmart OnePay CashRewards", issuerName: "Synchrony", network: "Mastercard",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/synchrony/walmart-onepay-cashrewards.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$35 Cash Bonus", defaultSubEstimatedValueCents: 3500, defaultSubTargetSpendCents: 7500, subSpendWindowDays: 30, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Walmart & Walmart.com (5% w/ Walmart+ / 3% Standard)", 5), r("Everything Else", "Everywhere Else", 1.5)],
    benefits: []
  },
  {
    id: "synchrony_amazon_prime_store_card", bonusApiCardId: "25e394e76617c0a660b48d7c5c4449ea", sortRank: 250,
    shortLabel: "Amazon Prime Store Card", cardName: "Amazon Prime Store Card", issuerName: "Synchrony", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/synchrony/amazon-prime-store-card.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$60 Cash Bonus", defaultSubEstimatedValueCents: 6000, defaultSubTargetSpendCents: 1, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Amazon.com Purchases (Prime Store Card Only)", 5)],
    benefits: []
  },
  {
    id: "us_bank_business_altitude_power", bonusApiCardId: "3d777bda73262fa43b11626eb383f9ab", sortRank: 350,
    shortLabel: "Business Altitude Power", cardName: "Business Altitude Power", issuerName: "U.S. Bank", network: "Mastercard",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/altitude-power.webp",
    annualFeeCents: 19500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "75,000 Altitude Rewards pts", defaultSubEstimatedValueCents: 75000, defaultSubTargetSpendCents: 1000000, subSpendWindowDays: 120, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Mobile Wallet Purchases ($5,000/qtr cap)", 2.5, { capCents: 500000 }), r("Everything Else", "Every Business Purchase", 2)],
    benefits: []
  },
  {
    id: "us_bank_business_altitude_connect", bonusApiCardId: "1e6ddb097a524ce3b68719ae6c5eb1e8", sortRank: 350,
    shortLabel: "Business Altitude Connect", cardName: "Business Altitude Connect", issuerName: "U.S. Bank", network: "Mastercard",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/business-altitude-connect.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: true, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 Altitude Rewards pts", defaultSubEstimatedValueCents: 60000, defaultSubTargetSpendCents: 600000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Prepaid Travel Center Hotels & Cars", 5), r("Travel & Flights", "Travel ($150k/yr combined cap)", 4, { capCents: 15000000 }), r("Gas & Transit", "Gas & EV Charging ($150k/yr cap)", 4, { capCents: 15000000 }), r("Dining & Restaurants", "Dining", 2), r("Office & Internet", "Cell Phone & Software Subscriptions", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("usb-biz-conn-pp", "Priority Pass Select (4 Visits/yr)", null, "ANNUAL", "CARDMEMBER_YEAR", "4 complimentary Priority Pass lounge visits per year")
    ]
  },
  {
    id: "us_bank_altitude_connect", bonusApiCardId: "9a35bfe5845f7f9909244b1e3549b7e1", sortRank: 250,
    shortLabel: "Altitude Connect", cardName: "Altitude Connect", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/altitude-connect.png",
    annualFeeCents: 0, isAnnualFeeWaived: true, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "20,000 Altitude Connect pts", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: true,
    rewardCategories: [r("Travel & Flights", "Prepaid Travel Center Hotels & Car Rentals", 5), r("Travel & Flights", "Travel ($1,000/qtr combined cap)", 4, { capCents: 100000 }), r("Gas & Transit", "Gas & EV Charging ($1,000/qtr cap)", 4, { capCents: 100000 }), r("Groceries", "Grocery Stores", 2), r("Dining & Restaurants", "Dining", 2), r("Streaming & Entertainment", "Streaming Services", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("usb-conn-ge", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 statement credit every 4 years"),
      b("usb-conn-pp", "Priority Pass Select (4 Visits/yr)", null, "ANNUAL", "CARDMEMBER_YEAR", "4 complimentary Priority Pass airport lounge visits per year")
    ]
  },
  {
    id: "us_bank_business_leverage", bonusApiCardId: "4899141df2aa46191c192c6f216b74cf", sortRank: 350,
    shortLabel: "Business Leverage", cardName: "Business Leverage", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/business-leverage.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: true, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "$750 Cash Bonus", defaultSubEstimatedValueCents: 75000, defaultSubTargetSpendCents: 750000, subSpendWindowDays: 120, isElevatedSubOffer: false,
    rewardCategories: [r("Office & Internet", "Top 2 Automatic Monthly Business Categories", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "us_bank_business_triple_cash_rewards", bonusApiCardId: "a9fe40efecc22039a980c039bb6841be", sortRank: 350,
    shortLabel: "Business Triple Cash Rewards", cardName: "Business Triple Cash Rewards", issuerName: "U.S. Bank", network: "Mastercard",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/usbank/business-triple-cash-rewards.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "$750 Cash Bonus", defaultSubEstimatedValueCents: 75000, defaultSubTargetSpendCents: 450000, subSpendWindowDays: 150, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Prepaid Travel Center Hotels & Car Rentals", 5), r("Gas & Transit", "Gas Stations & EV Charging ($200- max txn)", 3), r("Office & Internet", "Office Supply Stores & Cell Phone Service", 3), r("Dining & Restaurants", "Restaurants", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("triple-cash-software", "$100 Annual Software Subscription Credit", 10000, "ANNUAL", "CARDMEMBER_YEAR", "$100 annual statement credit after 11 consecutive months of eligible software spend")
    ]
  },
  {
    id: "us_bank_skypass_blue", bonusApiCardId: "90d9bd31d33bc6cfbdb1eb0962cbce57", sortRank: 250,
    shortLabel: "SKYPASS SkyBlue", cardName: "SKYPASS Visa SkyBlue", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/skypass.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "10,000 Korean Air SKYPASS Miles", defaultSubEstimatedValueCents: 13000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Direct Hotel Stays", 2), r("Dining & Restaurants", "Restaurants", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "us_bank_skypass", bonusApiCardId: "824f4be5b9f38164fb8f44522467876e", sortRank: 250,
    shortLabel: "SKYPASS Visa", cardName: "SKYPASS Visa Signature", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/skypass.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "40,000 Korean Air SKYPASS Miles", defaultSubEstimatedValueCents: 52000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Korean Air, Direct Hotels & Car Rentals", 2), r("Dining & Restaurants", "Restaurants", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("skypass-coupon", "$50 Annual Korean Air Ticket Coupon & 2 KAL Lounge Passes", 5000, "ANNUAL", "CARDMEMBER_YEAR", "$50 Korean Air ticket discount coupon + 2 KAL Business Lounge coupons each renewal")
    ]
  },
  {
    id: "us_bank_skypass_select", bonusApiCardId: "aceef720d7ec6cf06ee46865fc39d612", sortRank: 250,
    shortLabel: "SKYPASS Select", cardName: "SKYPASS Select Visa Signature", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/skypass-select.png",
    annualFeeCents: 45000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 Korean Air SKYPASS Miles", defaultSubEstimatedValueCents: 78000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Korean Air Purchases", 3), r("Travel & Flights", "Other Airlines, Direct Hotels & Car Rentals", 2), r("Dining & Restaurants", "Restaurants", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("skypass-sel-travel", "$200 Annual Travel Credit", 20000, "ANNUAL", "CARDMEMBER_YEAR", "Up to $200 annual reimbursement on airlines, hotels, or car rentals"),
      b("skypass-sel-flight", "$50 Annual Korean Air Flight Discount", 5000, "ANNUAL", "CARDMEMBER_YEAR", "$50 discount coupon for Korean Air ticket purchase"),
      b("skypass-sel-ge", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 statement credit every 4 years")
    ]
  },
  {
    id: "us_bank_skypass_business", bonusApiCardId: "cf146925642ec392eee6c924cfc7ec0d", sortRank: 350,
    shortLabel: "SKYPASS Business", cardName: "SKYPASS Visa Business", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/skypass-business.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 Korean Air SKYPASS Miles", defaultSubEstimatedValueCents: 65000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Korean Air, Direct Hotels & Car Rentals", 2), r("Office & Internet", "Office Supply Stores & Telecom", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("skypass-biz-ge", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 statement credit every 4 years")
    ]
  },
  {
    id: "us_bank_shopper_cash_rewards", bonusApiCardId: "8de694e89b9c26c9f72fc7450c66665b", sortRank: 250,
    shortLabel: "Shopper Cash Rewards", cardName: "Shopper Cash Rewards", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/usbank/shopper-cash-rewards.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: true, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$250 Cash Bonus", defaultSubEstimatedValueCents: 25000, defaultSubTargetSpendCents: 200000, subSpendWindowDays: 120, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "6% Two Chosen Retailers ($1,500/qtr combined cap)", 6, { isRotating: true, capCents: 150000 }), r("Utilities", "3% Chosen Everyday Category ($1,500/qtr cap)", 3, { isRotating: true, capCents: 150000 }), r("Everything Else", "Everything Else", 1.5)],
    benefits: []
  },
  {
    id: "wells_fargo_signify_business_cash", bonusApiCardId: "429640c66f9a2e3bd077978aa5a1f0aa", sortRank: 350,
    shortLabel: "Signify Business Cash", cardName: "Signify Business Cash", issuerName: "Wells Fargo", network: "Mastercard",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/wellsfargo/signify-business-cash.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "$500 Cash Bonus", defaultSubEstimatedValueCents: 50000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Business Purchase", 2)],
    benefits: []
  },
  {
    id: "wells_fargo_choice_privileges", bonusApiCardId: "2745fd62ecf1bba1056f7933b1ab285e", sortRank: 250,
    shortLabel: "Choice Privileges", cardName: "Choice Privileges", issuerName: "Wells Fargo", network: "Mastercard",
    artKey: "hex://#7F1D1D:#B91C1C:#FFFFFF", upstreamImagePath: "/images/wellsfargo/choice-privileges.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 Choice Privileges pts", defaultSubEstimatedValueCents: 36000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Choice Hotels Properties", 5), r("Gas & Transit", "Gas Stations", 3), r("Groceries", "Grocery Stores", 3), r("Utilities", "Home Improvement & Cell Phone Plans", 3), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "wells_fargo_choice_privileges_select", bonusApiCardId: "c68bed03d88b439b70c905e490757271", sortRank: 250,
    shortLabel: "Choice Privileges Select", cardName: "Choice Privileges Select", issuerName: "Wells Fargo", network: "Mastercard",
    artKey: "hex://#7F1D1D:#B91C1C:#FFFFFF", upstreamImagePath: "/images/wellsfargo/choice-privileges-select.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 Choice Privileges pts", defaultSubEstimatedValueCents: 36000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Choice Hotels Properties", 10), r("Gas & Transit", "Gas Stations", 5), r("Groceries", "Grocery Stores", 5), r("Utilities", "Home Improvement & Cell Phone Plans", 5), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("choice-select-anniv", "30,000 Anniversary Choice Privileges Pts", 18000, "ANNUAL", "CARDMEMBER_YEAR", "30,000 bonus Choice Privileges points every renewal year"),
      b("choice-select-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years")
    ]
  },
  {
    id: "wells_fargo_attune", bonusApiCardId: "8b60912df8dd8406cbbf96469c4cfaec", sortRank: 250,
    shortLabel: "Attune", cardName: "Attune", issuerName: "Wells Fargo", network: "Mastercard",
    artKey: "hex://#7F1D1D:#B91C1C:#FFFFFF", upstreamImagePath: "/images/wellsfargo/attune.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$100 Cash Bonus", defaultSubEstimatedValueCents: 10000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Streaming & Entertainment", "Self-Care, Fitness, Live Entertainment & Sports", 4), r("Gas & Transit", "Public Transit & EV Charging", 4), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "wells_fargo_expedia_one_key", bonusApiCardId: "546dccf995e97f42d656d069c95efacf", sortRank: 250,
    shortLabel: "One Key Card", cardName: "One Key Card", issuerName: "Wells Fargo", network: "Mastercard",
    artKey: "hex://#7F1D1D:#B91C1C:#FFFFFF", upstreamImagePath: "/images/wellsfargo/one-key.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$250 OneKeyCash", defaultSubEstimatedValueCents: 25000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Expedia, Hotels.com & Vrbo", 3), r("Gas & Transit", "Gas Stations", 3), r("Groceries", "Grocery Stores", 3), r("Dining & Restaurants", "Dining", 3), r("Everything Else", "Everything Else", 1.5)],
    benefits: []
  },
  {
    id: "wells_fargo_expedia_one_key_dfdf46", bonusApiCardId: "dfdf46ff6cbfcea7b738c7092e00af78", sortRank: 250,
    shortLabel: "One Key+ Card", cardName: "One Key+ Card", issuerName: "Wells Fargo", network: "Mastercard",
    artKey: "hex://#7F1D1D:#B91C1C:#FFFFFF", upstreamImagePath: "/images/wellsfargo/one-key.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$350 OneKeyCash", defaultSubEstimatedValueCents: 35000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Expedia, Hotels.com & Vrbo", 3), r("Gas & Transit", "Gas Stations", 3), r("Groceries", "Grocery Stores", 3), r("Dining & Restaurants", "Dining", 3), r("Everything Else", "Everything Else (Catch-All)", 2)],
    benefits: [
      b("onekey-plus-anniv", "$100 Anniversary OneKeyCash", 10000, "ANNUAL", "CARDMEMBER_YEAR", "$100 in OneKeyCash every cardmember anniversary"),
      b("onekey-plus-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years")
    ]
  },
  {
    id: "web_bank_gemini", bonusApiCardId: "eb105ba41f6ad2ea3b5f151f03f3dcfa", sortRank: 250,
    shortLabel: "Gemini Credit Card", cardName: "Gemini Credit Card", issuerName: "WebBank", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/web-bank/gemini.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$200 Cash Bonus", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Gas & Transit", "Gas at Pump, EV Charging & Transit ($300/mo cap)", 4, { capCents: 30000 }), r("Dining & Restaurants", "Dining ($6,000/yr cap)", 3, { capCents: 600000 }), r("Groceries", "Groceries", 2), r("Everything Else", "Everything Else (Instant Crypto Rewards)", 1)],
    benefits: []
  }
];
