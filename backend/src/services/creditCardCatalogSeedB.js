// =============================================================================
// creditCardCatalogSeedB.js - Curated US credit cards (Part B).
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

export const CURATED_CARD_CATALOG_B = [
  {
    id: "amex_delta_gold", bonusApiCardId: "99d4ec3c5bcd51b70b83b4b792af3ef6", sortRank: 40,
    shortLabel: "Delta SkyMiles Gold", cardName: "Delta SkyMiles Gold", issuerName: "American Express", network: "Amex",
    artKey: "hex://#78531A:#B8860B:#FFFFFF", upstreamImagePath: "/images/amex/delta-skymiles-gold.jpg",
    annualFeeCents: 15000, isAnnualFeeWaived: true, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "50,000 Delta SkyMiles", defaultSubEstimatedValueCents: 60000, defaultSubTargetSpendCents: 200000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Delta Purchases", 2), r("Dining & Restaurants", "Restaurants Worldwide", 2), r("Groceries", "U.S. Supermarkets", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("delta-gold-stays", "$100 Annual Delta Stays Credit", 10000, "ANNUAL", "CALENDAR", "Up to $100 annual credit for prepaid hotels or vacation rentals on Delta Stays"),
      b("delta-gold-bag", "First Checked Bag Free on Delta", null, "ANNUAL", "CALENDAR", "First checked bag free on Delta flights + 15% TakeOff 15 award discount")
    ]
  },
  {
    id: "amex_delta_reserve", bonusApiCardId: "2a3949c2232cf6646a59531728264a5c", sortRank: 41,
    shortLabel: "Delta Reserve", cardName: "Delta SkyMiles Reserve", issuerName: "American Express", network: "Amex",
    artKey: "hex://#2E1065:#581C87:#FFFFFF", upstreamImagePath: "/images/amex/delta-skymiles-reserve.webp",
    annualFeeCents: 65000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "70,000 Delta SkyMiles", defaultSubEstimatedValueCents: 84000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Delta Purchases", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("delta-res-resy", "$20/mo Resy Credit ($240/yr)", 2000, "MONTHLY", "CALENDAR", "Up to $20 monthly statement credit at U.S. Resy restaurants"),
      b("delta-res-rideshare", "$10/mo Rideshare Credit ($120/yr)", 1000, "MONTHLY", "CALENDAR", "Up to $10 monthly credit with Uber, Lyft, Curb, Revel, and Alto"),
      b("delta-res-stays", "$200 Annual Delta Stays Credit", 20000, "ANNUAL", "CALENDAR", "Up to $200/yr statement credit on Delta Stays prepaid bookings"),
      b("delta-res-companion", "Annual First/Comfort+/Main Companion Certificate", null, "ANNUAL", "CARDMEMBER_YEAR", "Annual round-trip Companion Certificate each renewal year + 15 Sky Club visits")
    ]
  },
  {
    id: "venture_x", bonusApiCardId: "9aa036266b8cc7e7512a630efdecae0b", sortRank: 50,
    shortLabel: "Venture X", cardName: "Venture X", issuerName: "Capital One", network: "Visa",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/capitalone/venture-x.webp",
    annualFeeCents: 39500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "75,000 Capital One Miles", defaultSubEstimatedValueCents: 105000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Travel Hotels & Rental Cars", 10), r("Travel & Flights", "Capital One Travel Flights & Vacation Rentals", 5), r("Everything Else", "Everything Else (Catch-All)", 2)],
    benefits: [
      b("venture-x-travel-credit", "$300 Annual Capital One Travel Credit", 30000, "ANNUAL", "CARDMEMBER_YEAR", "$300 annual credit for bookings through Capital One Travel each cardmember year"),
      b("venture-x-anniversary-miles", "10,000 Anniversary Bonus Miles ($100 val)", 10000, "ANNUAL", "CARDMEMBER_YEAR", "10,000 bonus miles every anniversary year starting Year 1"),
      b("venture-x-global-entry", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit for Global Entry or TSA PreCheck every 4 years"),
      b("venture-x-lounge", "Capital One Lounge & Priority Pass Access", null, "ANNUAL", "CALENDAR", "Complimentary Capital One Lounge and Priority Pass Select access")
    ]
  },
  {
    id: "venture_rewards", bonusApiCardId: "4a7a8036e514f187ca7c41b31925251e", sortRank: 51,
    shortLabel: "Venture Rewards", cardName: "Venture Rewards", issuerName: "Capital One", network: "Mastercard",
    artKey: "hex://#1E3A8A:#2563EB:#FFFFFF", upstreamImagePath: "/images/capitalone/venture-rewards.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "75,000 Capital One Miles", defaultSubEstimatedValueCents: 105000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Travel Hotels, Vacation Rentals & Cars", 5), r("Everything Else", "Everything Else (Catch-All)", 2)],
    benefits: [
      b("venture-global-entry", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years")
    ]
  },
  {
    id: "capital_one_savor", bonusApiCardId: "2d16431e0caee645b420cc3d3255b2ee", sortRank: 52,
    shortLabel: "Capital One Savor", cardName: "Capital One Savor", issuerName: "Capital One", network: "Mastercard",
    artKey: "hex://#9A3412:#EA580C:#FFFFFF", upstreamImagePath: "/images/capitalone/savor.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$200 Cash Bonus", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Streaming & Entertainment", "Capital One Entertainment Purchases", 8), r("Travel & Flights", "Capital One Travel Hotels & Rental Cars", 5), r("Dining & Restaurants", "Dining", 3), r("Groceries", "Grocery Stores", 3), r("Streaming & Entertainment", "Entertainment & Popular Streaming", 3), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "capital_one_quicksilver", bonusApiCardId: "66f7413248c7f2c61ce1e8008685eac2", sortRank: 53,
    shortLabel: "Quicksilver", cardName: "Capital One Quicksilver", issuerName: "Capital One", network: "Mastercard",
    artKey: "hex://#334155:#64748B:#FFFFFF", upstreamImagePath: "/images/capitalone/quicksilver.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$200 Cash Bonus", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Travel Hotels & Rental Cars", 5), r("Everything Else", "Everything Else (Catch-All)", 1.5)],
    benefits: []
  },
  {
    id: "venture_x_biz", bonusApiCardId: "1d2ff07648fdd4b06d89ef0309f1c4cb", sortRank: 54,
    shortLabel: "Venture X Business", cardName: "Venture X Business", issuerName: "Capital One", network: "Mastercard",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/capitalone/venture-x-business.png",
    annualFeeCents: 39500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "150,000 Capital One Miles", defaultSubEstimatedValueCents: 210000, defaultSubTargetSpendCents: 3000000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Business Travel Hotels & Cars", 10), r("Travel & Flights", "Capital One Business Travel Flights", 5), r("Everything Else", "Everything Else (Catch-All)", 2)],
    benefits: [
      b("vx-biz-travel-credit", "$300 Annual Capital One Travel Credit", 30000, "ANNUAL", "CARDMEMBER_YEAR", "$300 annual credit for bookings in Capital One Business Travel"),
      b("vx-biz-anniv-miles", "10,000 Anniversary Bonus Miles ($100 val)", 10000, "ANNUAL", "CARDMEMBER_YEAR", "10,000 bonus miles every anniversary year"),
      b("vx-biz-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 fee credit every 4 years")
    ]
  },
  {
    id: "spark_cash_select", bonusApiCardId: "d4955a21df61a45ad86c3d60c58b91fc", sortRank: 55,
    shortLabel: "Spark 1.5% Cash Select", cardName: "Spark 1.5% Cash Select", issuerName: "Capital One", network: "Mastercard",
    artKey: "hex://#1F2937:#059669:#FFFFFF", upstreamImagePath: "/images/capitalone/spark-cash.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: true, subCooldownMonths: 48,
    defaultSubRewardText: "$500 Cash Bonus", defaultSubEstimatedValueCents: 50000, defaultSubTargetSpendCents: 450000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Business Travel Hotels & Rental Cars", 5), r("Everything Else", "Every Business Purchase", 1.5)],
    benefits: []
  },
  {
    id: "citi_strata_premier", bonusApiCardId: "d7e2e5b5e595ca6510c101349c914513", sortRank: 60,
    shortLabel: "Citi Strata Premier", cardName: "Citi Strata Premier", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#0F172A:#0D9488:#FFFFFF", upstreamImagePath: "/images/citi/strata-premier.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "75,000 ThankYou Points", defaultSubEstimatedValueCents: 105000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "CitiTravel.com Hotels, Cars & Attractions", 10), r("Dining & Restaurants", "Restaurants", 3), r("Groceries", "Supermarkets", 3), r("Gas & Transit", "Gas Stations & EV Charging", 3), r("Travel & Flights", "Air Travel & Other Hotels", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("strata-hotel-credit", "$100 Annual Citi Travel Hotel Benefit", 10000, "ANNUAL", "CALENDAR", "$100 off a single hotel stay of $500+ booked through CitiTravel.com each calendar year")
    ]
  },
  {
    id: "citi_double_cash", bonusApiCardId: "06b9a384d710e73db7f0ded7613c9c4b", sortRank: 61,
    shortLabel: "Citi Double Cash", cardName: "Citi Double Cash", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#0369A1:#0D9488:#FFFFFF", upstreamImagePath: "/images/citi/double-cash.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$200 / 20,000 ThankYou Pts", defaultSubEstimatedValueCents: 28000, defaultSubTargetSpendCents: 150000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "CitiTravel.com Hotels, Car Rentals & Attractions", 5), r("Everything Else", "Everything Else (1% Buy + 1% Pay)", 2)],
    benefits: []
  },
  {
    id: "citi_custom_cash", bonusApiCardId: "49fe6c73c0e823a89346979f50d5b981", sortRank: 62,
    shortLabel: "Citi Custom Cash", cardName: "Citi Custom Cash", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#1E3A8A:#10B981:#FFFFFF", upstreamImagePath: "/images/citi/custom-cash.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$200 / 20,000 ThankYou Pts", defaultSubEstimatedValueCents: 28000, defaultSubTargetSpendCents: 150000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Groceries", "Top Eligible Spend Category ($500/mo cap)", 5, { isRotating: true, capCents: 50000 }), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "citi_costco_visa", bonusApiCardId: null, sortRank: 63,
    shortLabel: "Costco Anywhere Visa", cardName: "Costco Anywhere Visa", issuerName: "Citi", network: "Visa",
    artKey: "hex://#1E3A8A:#DC2626:#FFFFFF", upstreamImagePath: null,
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: null,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: null, isElevatedSubOffer: false,
    rewardCategories: [r("Gas & Transit", "Costco Gas (5%) & Other Gas/EV ($7,000/yr cap)", 4, { capCents: 700000 }), r("Dining & Restaurants", "Restaurants Worldwide", 3), r("Travel & Flights", "Eligible Travel Worldwide", 3), r("Groceries", "Costco & Costco.com Purchases", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "bilt", bonusApiCardId: "c077a47639c7b47d1895200c72588ee3", sortRank: 70,
    shortLabel: "Bilt Mastercard", cardName: "Bilt Mastercard", issuerName: "Wells Fargo / Bilt", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/wellsfargo/bilt.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: null,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: null, isElevatedSubOffer: false,
    rewardCategories: [r("Dining & Restaurants", "Dining (6x on Rent Day)", 3), r("Travel & Flights", "Travel (4x on Rent Day)", 2), r("Rent", "Rent Payments (No Transaction Fee, up to 100k pts/yr)", 1), r("Everything Else", "Everything Else (requires 5 txns/cycle)", 1)],
    benefits: []
  },
  {
    id: "wf_autograph", bonusApiCardId: "ce08e0bddd774433cb77be87f5d6af76", sortRank: 71,
    shortLabel: "Wells Fargo Autograph", cardName: "Wells Fargo Autograph", issuerName: "Wells Fargo", network: "Visa",
    artKey: "hex://#7F1D1D:#B91C1C:#FFFFFF", upstreamImagePath: "/images/wellsfargo/autograph.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "20,000 Wells Fargo Rewards pts", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Dining & Restaurants", "Restaurants", 3), r("Travel & Flights", "Travel", 3), r("Gas & Transit", "Gas Stations, EV Charging & Transit", 3), r("Streaming & Entertainment", "Popular Streaming Services", 3), r("Utilities", "Cell Phone Plans", 3), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "wf_autograph_journey", bonusApiCardId: "d1fcd95962c94d1b56d691fda30acead", sortRank: 72,
    shortLabel: "Autograph Journey", cardName: "Wells Fargo Autograph Journey", issuerName: "Wells Fargo", network: "Visa",
    artKey: "hex://#450A0A:#991B1B:#FFFFFF", upstreamImagePath: "/images/wellsfargo/autograph.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 Wells Fargo Rewards pts", defaultSubEstimatedValueCents: 60000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Hotels", 5), r("Travel & Flights", "Airlines", 4), r("Dining & Restaurants", "Restaurants & Other Travel", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("wf-journey-air", "$50 Annual Airfare Statement Credit", 5000, "ANNUAL", "CARDMEMBER_YEAR", "$50 annual statement credit with $50 minimum airline purchase")
    ]
  },
  {
    id: "wf_active_cash", bonusApiCardId: "df2960b75b50536daa6e05a33479b198", sortRank: 73,
    shortLabel: "Wells Fargo Active Cash", cardName: "Wells Fargo Active Cash", issuerName: "Wells Fargo", network: "Visa",
    artKey: "hex://#1E293B:#991B1B:#FFFFFF", upstreamImagePath: "/images/wellsfargo/active-cash.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$200 Cash Rewards Bonus", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Everything Else (Catch-All)", 2)],
    benefits: []
  },
  {
    id: "bofa_customized_cash", bonusApiCardId: "f2461735a56702c428c209486e2f9e55", sortRank: 80,
    shortLabel: "BofA Customized Cash", cardName: "Bank of America Customized Cash Rewards", issuerName: "Bank of America", network: "Visa",
    artKey: "hex://#991B1B:#DC2626:#FFFFFF", upstreamImagePath: "/images/bankofamerica/customized-cash-rewards.jpg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "$200 Online Cash Rewards Bonus", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "3% Choice Category ($2,500/qtr combined cap)", 3, { isRotating: true, capCents: 250000 }), r("Groceries", "Grocery Stores & Wholesale Clubs ($2,500/qtr cap)", 2, { capCents: 250000 }), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "bofa_premium_rewards", bonusApiCardId: "eb8bc1a6f86dc471aa0deeca619fbf59", sortRank: 81,
    shortLabel: "BofA Premium Rewards", cardName: "Bank of America Premium Rewards", issuerName: "Bank of America", network: "Visa",
    artKey: "hex://#18181B:#3F3F46:#FFFFFF", upstreamImagePath: "/images/bankofamerica/premium-rewards.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "60,000 Preferred Rewards pts", defaultSubEstimatedValueCents: 60000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Travel", 2), r("Dining & Restaurants", "Dining", 2), r("Everything Else", "Everything Else", 1.5)],
    benefits: [
      b("bofa-pr-airline", "$100 Annual Airline Incidental Credit", 10000, "ANNUAL", "CALENDAR", "Up to $100 annual statement credit for qualifying airline incidental fees"),
      b("bofa-pr-ge", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 credit every 4 years for TSA PreCheck or Global Entry")
    ]
  },
  {
    id: "usbank_altitude_reserve", bonusApiCardId: null, sortRank: 84,
    shortLabel: "U.S. Bank Altitude Reserve", cardName: "U.S. Bank Altitude Reserve", issuerName: "U.S. Bank", network: "Visa",
    artKey: "preset://obsidian-black", upstreamImagePath: null,
    annualFeeCents: 40000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 Altitude Reserve pts", defaultSubEstimatedValueCents: 75000, defaultSubTargetSpendCents: 450000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Prepaid Travel Center Hotels & Car Rentals", 5), r("Travel & Flights", "Direct Travel & Mobile Wallet Purchases", 3), r("Everything Else", "Mobile Wallet (3x) / Physical Card (1x)", 3)],
    benefits: [
      b("usar-travel-dining", "$325 Annual Travel & Dining Credit", 32500, "ANNUAL", "CARDMEMBER_YEAR", "Up to $325 annual statement credit for eligible travel and dining purchases"),
      b("usar-global-entry", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 statement credit every 4 years"),
      b("usar-priority-pass", "Priority Pass Select (8 Visits/yr)", null, "ANNUAL", "CARDMEMBER_YEAR", "8 complimentary Priority Pass lounge/restaurant visits per membership year")
    ]
  },
  {
    id: "usbank_altitude_go", bonusApiCardId: "b523a3cc7d6d355737b1c4dda1eb7a04", sortRank: 85,
    shortLabel: "U.S. Bank Altitude Go", cardName: "U.S. Bank Altitude Go", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#1E3A8A:#B91C1C:#FFFFFF", upstreamImagePath: "/images/usbank/altitude-go.jpg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "20,000 Altitude Go pts", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Dining & Restaurants", "Dining, Takeout & Delivery ($2,000/qtr cap)", 4, { capCents: 200000 }), r("Groceries", "Grocery Stores", 2), r("Gas & Transit", "Gas Stations & EV Charging", 2), r("Streaming & Entertainment", "Streaming Services", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("altitude-go-streaming", "$15 Annual Streaming Service Credit", 1500, "ANNUAL", "CARDMEMBER_YEAR", "$15 statement credit after 11 consecutive months of eligible streaming purchases")
    ]
  },
  {
    id: "usbank_cash_plus", bonusApiCardId: "78726438553c1f6b63383f3bac6df57b", sortRank: 86,
    shortLabel: "U.S. Bank Cash+", cardName: "U.S. Bank Cash+ Visa Signature", issuerName: "U.S. Bank", network: "Visa",
    artKey: "hex://#0F2B5B:#1D4ED8:#FFFFFF", upstreamImagePath: "/images/usbank/cash-plus.jpg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$200 Rewards Bonus", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Utilities", "5% Two Chosen Categories ($2,000/qtr cap)", 5, { isRotating: true, capCents: 200000 }), r("Groceries", "2% Chosen Everyday Category (Grocery, Gas, or Dining)", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "discover_it", bonusApiCardId: "dae75a5609dea8f39f750f4b225d2c5c", sortRank: 90,
    shortLabel: "Discover it Cash Back", cardName: "Discover it Cash Back", issuerName: "Discover", network: "Discover",
    artKey: "preset://discover-plum", upstreamImagePath: "/images/discover/it.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "Unlimited Cashback Match (First Year)", defaultSubEstimatedValueCents: 15000, defaultSubTargetSpendCents: null, subSpendWindowDays: 365, isElevatedSubOffer: false,
    rewardCategories: [r("Quarterly Bonus", "5% Rotating Quarterly Categories ($1,500 cap)", 5, { isRotating: true, capCents: 150000 }), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "fidelity_rewards_visa", bonusApiCardId: null, sortRank: 92,
    shortLabel: "Fidelity Rewards Visa", cardName: "Fidelity Rewards Visa Signature", issuerName: "Elan / Fidelity", network: "Visa",
    artKey: "preset://emerald-green", upstreamImagePath: null,
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "$150 Cash Bonus", defaultSubEstimatedValueCents: 15000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Purchase (Deposited into Fidelity Account)", 2)],
    benefits: [
      b("fidelity-ge", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 statement credit (10,000 points) for TSA PreCheck or Global Entry every 4 years")
    ]
  },
  {
    id: "robinhood_gold", bonusApiCardId: null, sortRank: 93,
    shortLabel: "Robinhood Gold Card", cardName: "Robinhood Gold Card", issuerName: "Coastal / Robinhood", network: "Visa",
    artKey: "preset://amex-gold", upstreamImagePath: null,
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: null,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: null, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Robinhood Travel Portal", 5), r("Everything Else", "Every Purchase (Catch-All)", 3)],
    benefits: []
  },
  {
    id: "target_circle_card", bonusApiCardId: null, sortRank: 94,
    shortLabel: "Target Circle Card", cardName: "Target Circle Mastercard", issuerName: "TD Bank / Target", network: "Mastercard",
    artKey: "preset://ruby-red", upstreamImagePath: null,
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "$50 Target Circle Bonus", defaultSubEstimatedValueCents: 5000, defaultSubTargetSpendCents: 5000, subSpendWindowDays: 30, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Target & Target.com (5% Instant Discount)", 5), r("Dining & Restaurants", "Dining", 2), r("Gas & Transit", "Gas Stations", 2), r("Everything Else", "Everywhere Else Outside Target", 1)],
    benefits: []
  },
  {
    id: "apple_card", bonusApiCardId: null, sortRank: 95,
    shortLabel: "Apple Card", cardName: "Apple Card", issuerName: "Goldman Sachs / Apple", network: "Mastercard",
    artKey: "preset://apple-card", upstreamImagePath: null,
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: null,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: null, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Apple & Select Daily Cash Partners (Uber, Nike, Walgreens, Exxon)", 3), r("Everything Else", "Every Apple Pay Purchase (1% Physical Card)", 2)],
    benefits: []
  }
];
