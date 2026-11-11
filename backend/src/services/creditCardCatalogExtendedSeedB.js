// =============================================================================
// creditCardCatalogExtendedSeedB.js - Extended US credit cards (Part B).
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

export const EXTENDED_CARD_CATALOG_B = [
  {
    id: "barclays_jetblue_business", bonusApiCardId: "499685453910108ad817d64f9728eb46", sortRank: 350,
    shortLabel: "JetBlue Business", cardName: "JetBlue Business", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/jetblue-business.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 TrueBlue pts", defaultSubEstimatedValueCents: 72000, defaultSubTargetSpendCents: 600000, subSpendWindowDays: 365, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "JetBlue Purchases", 6), r("Dining & Restaurants", "Restaurants", 2), r("Office & Internet", "Office Supply Stores", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("jetblue-biz-anniv", "5,000 Anniversary TrueBlue Pts", 6000, "ANNUAL", "CARDMEMBER_YEAR", "5,000 bonus TrueBlue points each account anniversary"),
      b("jetblue-biz-bag", "First Checked Bag Free on JetBlue", null, "ANNUAL", "CALENDAR", "First checked bag free + 10% award redemption rebate")
    ]
  },
  {
    id: "barclays_jetblue_plus", bonusApiCardId: "fafac50a80daae929873eca389fafd15", sortRank: 250,
    shortLabel: "JetBlue Plus", cardName: "JetBlue Plus", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/jetblue-plus.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "75,000 TrueBlue pts", defaultSubEstimatedValueCents: 90000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "JetBlue Purchases", 6), r("Dining & Restaurants", "Restaurants", 2), r("Groceries", "Eligible Grocery Stores", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("jetblue-plus-anniv", "5,000 Anniversary TrueBlue Pts", 6000, "ANNUAL", "CARDMEMBER_YEAR", "5,000 bonus TrueBlue points each account anniversary"),
      b("jetblue-plus-bag", "First Checked Bag Free on JetBlue", null, "ANNUAL", "CALENDAR", "First checked bag free + 10% award redemption rebate")
    ]
  },
  {
    id: "barclays_jetblue_premier", bonusApiCardId: "c7698b8b99a60ae3ccdd4d880ff5fe68", sortRank: 250,
    shortLabel: "JetBlue Premier", cardName: "JetBlue Premier", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/barclays/jetblue-premier.png",
    annualFeeCents: 49900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "80,000 TrueBlue pts", defaultSubEstimatedValueCents: 96000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "JetBlue & Paisly Purchases", 6), r("Dining & Restaurants", "Restaurants", 2), r("Groceries", "Eligible Grocery Stores", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("jetblue-prem-paisly", "$300 Annual Paisly Credit ($50 x 6 bookings)", 30000, "ANNUAL", "CALENDAR", "Up to $300/yr ($50 statement credit per $250+ Paisly booking, up to 6x/yr)"),
      b("jetblue-prem-anniv", "5,000 Anniversary TrueBlue Pts", 6000, "ANNUAL", "CARDMEMBER_YEAR", "5,000 bonus TrueBlue points each account anniversary"),
      b("jetblue-prem-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("jetblue-prem-lounge", "JetBlue Lounge & Priority Pass Select Access", null, "ANNUAL", "CALENDAR", "Complimentary JetBlue lounge and Priority Pass Select access")
    ]
  },
  {
    id: "barclays_lufthansa_miles_more", bonusApiCardId: "fdd9a5b1485ab0f88d0abd28c32e3b89", sortRank: 250,
    shortLabel: "Lufthansa Miles & More", cardName: "Lufthansa Miles & More", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/lufthansa-miles-and-more.png",
    annualFeeCents: 8900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 Miles & More", defaultSubEstimatedValueCents: 60000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Lufthansa Group Airlines Purchases", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("lh-companion", "Annual Companion Ticket & 2 Business Lounge Vouchers", null, "ANNUAL", "CARDMEMBER_YEAR", "Annual Economy companion ticket + 2 Lufthansa Business Lounge vouchers")
    ]
  },
  {
    id: "barclays_upromise", bonusApiCardId: "d0fa846548af110fa31bc7bfa25828df", sortRank: 250,
    shortLabel: "Upromise", cardName: "Upromise", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/upromise.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$300 Cash Bonus", defaultSubEstimatedValueCents: 30000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Purchase (1.529% w/ Linked 529 Plan)", 1.53)],
    benefits: []
  },
  {
    id: "barclays_wyndham_rewards_earner", bonusApiCardId: "6496ff471ffd822105f59ea276547110", sortRank: 250,
    shortLabel: "Wyndham Rewards Earner", cardName: "Wyndham Rewards Earner", issuerName: "Barclays", network: "Visa",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: null,
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "30,000 Wyndham Rewards pts", defaultSubEstimatedValueCents: 27000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Wyndham Hotels & Vacations", 5), r("Gas & Transit", "Gas Stations", 5), r("Dining & Restaurants", "Dining", 2), r("Groceries", "Eligible Grocery Stores", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "barclays_wyndham_rewards_earner_business", bonusApiCardId: "ba9027fba78b5ca88e4d9471aa9e31bd", sortRank: 350,
    shortLabel: "Wyndham Rewards Earner Business", cardName: "Wyndham Rewards Earner Business", issuerName: "Barclays", network: "Visa",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/wyndham-rewards-earner-business.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "45,000 Wyndham Rewards pts", defaultSubEstimatedValueCents: 40500, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Wyndham Hotels & Vacations", 8), r("Gas & Transit", "Gas Stations", 8), r("Office & Internet", "Marketing & Advertising Services", 5), r("Utilities", "Business Utilities", 5), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("wyndham-biz-anniv", "15,000 Anniversary Wyndham Rewards Pts", 13500, "ANNUAL", "CARDMEMBER_YEAR", "15,000 bonus points every anniversary year + Wyndham Diamond status")
    ]
  },
  {
    id: "barclays_wyndham_rewards_earner_plus", bonusApiCardId: "b910f422064772c65840288c632cf900", sortRank: 250,
    shortLabel: "Wyndham Rewards Earner Plus", cardName: "Wyndham Rewards Earner Plus", issuerName: "Barclays", network: "Visa",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/wyndham-rewards-earner-plus.png",
    annualFeeCents: 7500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "45,000 Wyndham Rewards pts", defaultSubEstimatedValueCents: 40500, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Wyndham Hotels & Vacations", 6), r("Gas & Transit", "Gas Stations", 6), r("Dining & Restaurants", "Dining", 4), r("Groceries", "Eligible Grocery Stores", 4), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("wyndham-plus-anniv", "7,500 Anniversary Wyndham Rewards Pts", 6750, "ANNUAL", "CARDMEMBER_YEAR", "7,500 bonus points every anniversary year + Wyndham Platinum status")
    ]
  },
  {
    id: "barclays_hawaiian_airlines_world_elite_business", bonusApiCardId: "9cbc127f6a0a485b64a7ddb5ed52d931", sortRank: 350,
    shortLabel: "Hawaiian Airlines World Elite Busine", cardName: "Hawaiian Airlines World Elite Business", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/barclays/hawaiian-airlines-world-elite-business.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 HawaiianMiles", defaultSubEstimatedValueCents: 50000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Hawaiian Airlines Purchases", 3), r("Gas & Transit", "Gas Stations", 2), r("Dining & Restaurants", "Dining", 2), r("Office & Internet", "Office Supply Stores", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "barclays_breeze", bonusApiCardId: "b52f6b684aec5d5b2465c27e6ccd1adc", sortRank: 250,
    shortLabel: "Breeze Easy Visa", cardName: "Breeze Easy Visa Signature", issuerName: "Barclays", network: "Visa",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/breeze.png",
    annualFeeCents: 8900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 BreezePoints", defaultSubEstimatedValueCents: 50000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Breeze Airways Purchases", 5), r("Groceries", "Eligible Grocery Stores", 2), r("Dining & Restaurants", "Restaurants", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("breeze-anniv", "7,500 Anniversary BreezePoints ($500+ Breeze spend)", 7500, "ANNUAL", "CARDMEMBER_YEAR", "7,500 bonus BreezePoints after $500+ annual spend with Breeze")
    ]
  },
  {
    id: "barclays_xbox", bonusApiCardId: "274b5ad3d42b14ff81fb588dd8938aa6", sortRank: 250,
    shortLabel: "Xbox Mastercard", cardName: "Xbox Mastercard", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/xbox.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "5,000 Xbox Card pts ($50 val)", defaultSubEstimatedValueCents: 5000, defaultSubTargetSpendCents: 1, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Microsoft Store Digital Products", 5), r("Streaming & Entertainment", "Eligible Streaming Services", 3), r("Dining & Restaurants", "Eligible Food Delivery Services", 3), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "brex_brex", bonusApiCardId: "be49d60cb01b9d5c3e919e6c570e9a48", sortRank: 250,
    shortLabel: "Brex Corporate", cardName: "Brex Corporate Card", issuerName: "Brex", network: "Mastercard",
    artKey: "hex://#18181B:#3F3F46:#FFFFFF", upstreamImagePath: "/images/brex/brex.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: null,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Gas & Transit", "Rideshare Purchases", 7), r("Travel & Flights", "Brex Travel Portal", 4), r("Dining & Restaurants", "Restaurants", 3), r("Office & Internet", "Recurring Software Subscriptions", 2), r("Everything Else", "Every Other Corporate Purchase", 1)],
    benefits: []
  },
  {
    id: "chase_marriott_bonvoy_bountiful", bonusApiCardId: "fb5e3887059d5d8600b2a5eaed7ddaf7", sortRank: 250,
    shortLabel: "Marriott Bonvoy Bountiful", cardName: "Marriott Bonvoy Bountiful", issuerName: "Chase", network: "Visa",
    artKey: "hex://#4A1525:#7F1D1D:#FFFFFF", upstreamImagePath: "/images/chase/marriott-bonvoy-bountiful.png",
    annualFeeCents: 25000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "85,000 Marriott Bonvoy pts", defaultSubEstimatedValueCents: 68000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Marriott Bonvoy Hotels", 6), r("Dining & Restaurants", "Restaurants ($15k combined cap)", 4, { capCents: 1500000 }), r("Groceries", "Grocery Stores ($15k combined cap)", 4, { capCents: 1500000 }), r("Everything Else", "Everything Else", 2)],
    benefits: [
      b("bountiful-gold", "Marriott Gold Elite & 1,000 Bonus Pts/Stay", null, "ANNUAL", "CALENDAR", "Complimentary Gold Elite status + 50k Free Night after $15k calendar-year spend")
    ]
  },
  {
    id: "chase_marriott_bonvoy_bold", bonusApiCardId: "918c978a06c7c36f9a321b2efa0ff974", sortRank: 250,
    shortLabel: "Marriott Bonvoy Bold", cardName: "Marriott Bonvoy Bold", issuerName: "Chase", network: "Visa",
    artKey: "hex://#4A1525:#7F1D1D:#FFFFFF", upstreamImagePath: "/images/chase/marriott-bonvoy-bold.jpeg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "60,000 Marriott Bonvoy pts + 50k Free Night", defaultSubEstimatedValueCents: 73000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Marriott Bonvoy Hotels", 3), r("Groceries", "Grocery Stores", 2), r("Gas & Transit", "Rideshare & Select Transit", 2), r("Dining & Restaurants", "Select Food Delivery", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "chase_southwest_performance_business", bonusApiCardId: "682a4f0c425d15b5ca34214378cfc3de", sortRank: 350,
    shortLabel: "Southwest Performance Business", cardName: "Southwest Performance Business", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/southwest-rapid-rewards-performance-business.png",
    annualFeeCents: 29900, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 24,
    defaultSubRewardText: "80,000 Rapid Rewards pts", defaultSubEstimatedValueCents: 104000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Southwest Airlines Purchases", 4), r("Gas & Transit", "Gas Stations, Local Transit & Rideshare", 2), r("Dining & Restaurants", "Restaurants", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("sw-perf-biz-anniv", "9,000 Anniversary Rapid Rewards Pts", 11700, "ANNUAL", "CARDMEMBER_YEAR", "9,000 bonus Rapid Rewards points each cardmember anniversary"),
      b("sw-perf-biz-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 fee credit every 4 years")
    ]
  },
  {
    id: "chase_southwest_plus", bonusApiCardId: "32a0c8c7eda25330e2c176589e386d7f", sortRank: 250,
    shortLabel: "Southwest Plus", cardName: "Southwest Plus", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/southwest-rapid-rewards-plus.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "20,000 Rapid Rewards pts", defaultSubEstimatedValueCents: 26000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Southwest Airlines Purchases", 2), r("Gas & Transit", "Gas Stations ($5k combined cap)", 2, { capCents: 500000 }), r("Groceries", "Grocery Stores ($5k combined cap)", 2, { capCents: 500000 }), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("sw-plus-anniv", "3,000 Anniversary Rapid Rewards Pts", 3900, "ANNUAL", "CARDMEMBER_YEAR", "3,000 bonus Rapid Rewards points each cardmember anniversary")
    ]
  },
  {
    id: "chase_southwest_premier", bonusApiCardId: "76e73a4afe8dfc1f8f1be1e345d7eca8", sortRank: 250,
    shortLabel: "Southwest Premier", cardName: "Southwest Premier", issuerName: "Chase", network: "Visa",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/chase/southwest-rapid-rewards-premier.png",
    annualFeeCents: 14900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "30,000 Rapid Rewards pts", defaultSubEstimatedValueCents: 39000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Southwest Airlines Purchases", 3), r("Groceries", "Grocery Stores ($8k combined cap)", 2, { capCents: 800000 }), r("Dining & Restaurants", "Restaurants ($8k combined cap)", 2, { capCents: 800000 }), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("sw-premier-anniv", "6,000 Anniversary Rapid Rewards Pts", 7800, "ANNUAL", "CARDMEMBER_YEAR", "6,000 bonus Rapid Rewards points each cardmember anniversary")
    ]
  },
  {
    id: "chase_southwest_premier_business", bonusApiCardId: "f86d26bad5af78feff0335845003c47e", sortRank: 350,
    shortLabel: "Southwest Premier Business", cardName: "Southwest Premier Business", issuerName: "Chase", network: "Visa",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/chase/southwest-rapid-rewards-premier-business.png",
    annualFeeCents: 14900, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 24,
    defaultSubRewardText: "60,000 Rapid Rewards pts", defaultSubEstimatedValueCents: 78000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Southwest Airlines Purchases", 3), r("Gas & Transit", "Gas Stations & Local Transit ($8k cap)", 2, { capCents: 800000 }), r("Dining & Restaurants", "Restaurants ($8k cap)", 2, { capCents: 800000 }), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("sw-prem-biz-anniv", "6,000 Anniversary Rapid Rewards Pts", 7800, "ANNUAL", "CARDMEMBER_YEAR", "6,000 bonus Rapid Rewards points each cardmember anniversary")
    ]
  },
  {
    id: "chase_united_business", bonusApiCardId: "8101f7827e3a1e797b09653f117b72d9", sortRank: 350,
    shortLabel: "United Business", cardName: "United Business", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/united-business.png",
    annualFeeCents: 15000, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 24,
    defaultSubRewardText: "100,000 United Miles", defaultSubEstimatedValueCents: 120000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "United Purchases", 2), r("Dining & Restaurants", "Restaurants", 2), r("Gas & Transit", "Gas Stations & Local Transit", 2), r("Office & Internet", "Office Supply Stores", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("united-biz-travelbank", "$125 United TravelBank Cash (after 5 $100+ flights)", 12500, "ANNUAL", "CARDMEMBER_YEAR", "$125 TravelBank cash after 5 United flights of $100+ each year"),
      b("united-biz-passes", "2 United Club One-Time Passes", null, "ANNUAL", "CARDMEMBER_YEAR", "Two complimentary United Club one-time passes each anniversary year")
    ]
  },
  {
    id: "chase_united_club", bonusApiCardId: "d2517ce6b72999e14f50c399347100ed", sortRank: 250,
    shortLabel: "United Club", cardName: "United Club", issuerName: "Chase", network: "Visa",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/chase/united-club-infinite.png",
    annualFeeCents: 69500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "110,000 United Miles", defaultSubEstimatedValueCents: 132000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: true,
    rewardCategories: [r("Travel & Flights", "United Purchases", 4), r("Travel & Flights", "All Other Travel", 2), r("Dining & Restaurants", "Dining & Eligible Delivery", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("united-club-hotels", "$200 Annual Renowned Hotels Credit", 20000, "ANNUAL", "CARDMEMBER_YEAR", "Up to $200/yr credit on prepaid Renowned Hotels and Resorts stays"),
      b("united-club-rideshare", "$12/mo Rideshare Credit ($150/yr)", 1200, "MONTHLY", "CALENDAR", "Up to $12/mo ($18 in Dec) in rideshare statement credits"),
      b("united-club-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("united-club-membership", "United Club Membership", null, "ANNUAL", "CARDMEMBER_YEAR", "Full United Club membership included")
    ]
  },
  {
    id: "chase_united_club_business", bonusApiCardId: "80c6aca49a0e1442d51635c316f4a5ff", sortRank: 350,
    shortLabel: "United Club Business", cardName: "United Club Business", issuerName: "Chase", network: "Visa",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/chase/united-club-business.png",
    annualFeeCents: 69500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 24,
    defaultSubRewardText: "100,000 United Miles", defaultSubEstimatedValueCents: 120000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "United Purchases", 2), r("Everything Else", "Every Other Business Purchase", 1.5)],
    benefits: [
      b("united-club-biz-hotels", "$200 Annual Renowned Hotels Credit", 20000, "ANNUAL", "CARDMEMBER_YEAR", "Up to $200/yr credit on prepaid Renowned Hotels and Resorts stays"),
      b("united-club-biz-rideshare", "$12/mo Rideshare Credit ($150/yr)", 1200, "MONTHLY", "CALENDAR", "Up to $12/mo ($18 in Dec) in rideshare statement credits"),
      b("united-club-biz-membership", "United Club Membership", null, "ANNUAL", "CARDMEMBER_YEAR", "Full United Club membership included")
    ]
  },
  {
    id: "chase_united_gateway", bonusApiCardId: "4071699fe4b21cafb02cf8b2c9c57c86", sortRank: 250,
    shortLabel: "United Gateway", cardName: "United Gateway", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/united-gateway.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "30,000 United Miles", defaultSubEstimatedValueCents: 36000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "United Purchases", 2), r("Gas & Transit", "Gas Stations, Local Transit & Commuting", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "chase_ink_business_premier", bonusApiCardId: "94c9b624738adce05fbe0e5bfc5d620e", sortRank: 350,
    shortLabel: "Ink Business Premier", cardName: "Ink Business Premier", issuerName: "Chase", network: "Visa",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/chase/ink-business-premier.png",
    annualFeeCents: 19500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 24,
    defaultSubRewardText: "$1,000 Cash Bonus", defaultSubEstimatedValueCents: 100000, defaultSubTargetSpendCents: 1000000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Chase Travel Portal", 5), r("Office & Internet", "Large Purchases ($5,000+)", 2.5), r("Everything Else", "Every Other Business Purchase", 2)],
    benefits: []
  },
  {
    id: "chase_ihg_premier_business", bonusApiCardId: "bc82fb7022c639f90b9378410127b9e6", sortRank: 350,
    shortLabel: "IHG Premier Business", cardName: "IHG Premier Business", issuerName: "Chase", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/chase/ihg-rewards-premier-business.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 24,
    defaultSubRewardText: "200,000 IHG One Rewards pts", defaultSubEstimatedValueCents: 100000, defaultSubTargetSpendCents: 900000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "IHG Hotels & Resorts", 10), r("Travel & Flights", "Travel Purchases", 5), r("Dining & Restaurants", "Dining", 5), r("Gas & Transit", "Gas Stations", 5), r("Office & Internet", "Office Supply & Social/Search Ads", 5), r("Everything Else", "Everything Else", 3)],
    benefits: [
      b("ihg-biz-fna", "40,000-Pt Anniversary Free Night", 16000, "ANNUAL", "CARDMEMBER_YEAR", "Anniversary Free Night valid up to 40,000 points"),
      b("ihg-biz-travelbank", "$50 Annual United TravelBank Cash", 2500, "SEMI_ANNUAL", "CALENDAR", "$25 United TravelBank Cash deposited every Jan and Jul ($50/yr)"),
      b("ihg-biz-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 fee credit every 4 years")
    ]
  },
  {
    id: "chase_ihg_traveler", bonusApiCardId: "2c6c7e215b4a1e791250d1fb48997e02", sortRank: 250,
    shortLabel: "IHG Traveler", cardName: "IHG Traveler", issuerName: "Chase", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/ihg-rewards-traveler.jpeg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "80,000 IHG One Rewards pts", defaultSubEstimatedValueCents: 40000, defaultSubTargetSpendCents: 200000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "IHG Hotels & Resorts", 5), r("Dining & Restaurants", "Dining", 3), r("Utilities", "Utilities, Internet, Cable & Phone", 3), r("Gas & Transit", "Gas Stations", 3), r("Streaming & Entertainment", "Select Streaming Services", 3), r("Everything Else", "Everything Else", 2)],
    benefits: []
  },
  {
    id: "chase_sapphire_reserve_business", bonusApiCardId: "5d8c589d1e7c1294d04fa6e4a2685624", sortRank: 350,
    shortLabel: "Sapphire Reserve Business", cardName: "Sapphire Reserve Business", issuerName: "Chase", network: "Visa",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/chase/sapphire-reserve-business.jpg",
    annualFeeCents: 79500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 24,
    defaultSubRewardText: "150,000 Ultimate Rewards pts", defaultSubEstimatedValueCents: 225000, defaultSubTargetSpendCents: 2000000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Chase Travel Portal", 8), r("Travel & Flights", "Direct Flights & Hotels", 4), r("Office & Internet", "Social Media & Search Engine Advertising", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("csr-biz-travel", "$300 Annual Travel Credit", 30000, "ANNUAL", "CARDMEMBER_YEAR", "$300 annual reimbursement for business travel purchases"),
      b("csr-biz-edit", "$250 Semi-Annual The Edit Hotel Credit ($500/yr)", 25000, "SEMI_ANNUAL", "CALENDAR", "Up to $250 Jan-Jun and $250 Jul-Dec on prepaid stays with The Edit"),
      b("csr-biz-ziprecruiter", "$200 Semi-Annual ZipRecruiter Credit ($400/yr)", 20000, "SEMI_ANNUAL", "CALENDAR", "Up to $200 semi-annually on ZipRecruiter hiring services"),
      b("csr-biz-gworkspace", "$200 Annual Google Workspace Credit", 20000, "ANNUAL", "CALENDAR", "Up to $200/yr credit on Google Workspace business tools"),
      b("csr-biz-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("csr-biz-lounge", "Sapphire Lounge & Priority Pass Select Access", null, "ANNUAL", "CALENDAR", "Complimentary Chase Sapphire Lounge and Priority Pass Select access")
    ]
  },
  {
    id: "chase_aer_lingus_signature", bonusApiCardId: "dcafcac45d86331a7f6b9cfdb980b81e", sortRank: 250,
    shortLabel: "Aer Lingus Signature", cardName: "Aer Lingus Signature", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/aer-lingus-signature.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "75,000 Avios", defaultSubEstimatedValueCents: 90000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Aer Lingus, British Airways & Iberia", 3), r("Travel & Flights", "Direct Hotel Accommodations", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "chase_aeroplan", bonusApiCardId: "7662e34a89a906b869b93bb12a2e1b3b", sortRank: 250,
    shortLabel: "Aeroplan", cardName: "Aeroplan", issuerName: "Chase", network: "Mastercard",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/aeroplan.jpg",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "85,000 Aeroplan pts", defaultSubEstimatedValueCents: 110500, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Groceries", "Grocery Stores", 3), r("Dining & Restaurants", "Dining & Takeout", 3), r("Travel & Flights", "Air Canada Directly", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("aeroplan-ge", "Global Entry / TSA PreCheck / NEXUS Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 fee credit every 4 years")
    ]
  },
  {
    id: "chase_british_airways_signature", bonusApiCardId: "7b06f0b6a307509b71862ffa5933ef0b", sortRank: 250,
    shortLabel: "British Airways Signature", cardName: "British Airways Signature", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/british-airways.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "90,000 Avios", defaultSubEstimatedValueCents: 108000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "British Airways, Aer Lingus & Iberia", 3), r("Travel & Flights", "Direct Hotel Accommodations", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "chase_disney_premier", bonusApiCardId: "cd81837eb0aedd4752197e044e77381a", sortRank: 250,
    shortLabel: "Disney Premier", cardName: "Disney Premier", issuerName: "Chase", network: "Visa",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/chase/disney.png",
    annualFeeCents: 4900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "$300 Cash Bonus", defaultSubEstimatedValueCents: 30000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Streaming & Entertainment", "Disney+, Hulu & ESPN+ Subscriptions", 5), r("Travel & Flights", "U.S. Disney Locations", 2), r("Groceries", "Grocery Stores", 2), r("Dining & Restaurants", "Restaurants", 2), r("Gas & Transit", "Gas Stations", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "chase_disney", bonusApiCardId: "12a1578aeb5bfcdd3bc794154ad1f6cc", sortRank: 250,
    shortLabel: "Disney", cardName: "Disney", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/disney.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "$150 Cash Bonus", defaultSubEstimatedValueCents: 15000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Purchase (1% Disney Rewards Dollars)", 1)],
    benefits: []
  },
  {
    id: "chase_iberia_signature", bonusApiCardId: "60c2e7b4bc2e526bf38dac5be5f4433c", sortRank: 250,
    shortLabel: "Iberia Signature", cardName: "Iberia Signature", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/iberia-signature.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "75,000 Avios", defaultSubEstimatedValueCents: 90000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Iberia, British Airways & Aer Lingus", 3), r("Travel & Flights", "Direct Hotel Accommodations", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "chase_world_of_hyatt_business", bonusApiCardId: "0d9d833e7298ab0f1892a4035686fa06", sortRank: 250,
    shortLabel: "World of Hyatt Business", cardName: "World of Hyatt Business", issuerName: "Chase", network: "Visa",
    artKey: "preset://sapphire-blue", upstreamImagePath: "/images/chase/world-of-hyatt.png",
    annualFeeCents: 19900, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 24,
    defaultSubRewardText: "60,000 World of Hyatt pts", defaultSubEstimatedValueCents: 108000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Hyatt Hotels & Resorts", 4), r("Office & Internet", "Top 3 Adaptive Business Spend Categories", 2), r("Everything Else", "Fitness Clubs (2x) & Everything Else (1x)", 1)],
    benefits: [
      b("hyatt-biz-credit", "$50 Semi-Annual Hyatt Property Credit ($100/yr)", 5000, "SEMI_ANNUAL", "CARDMEMBER_YEAR", "$50 statement credit up to twice per anniversary year on $50+ Hyatt spend")
    ]
  },
  {
    id: "capital_one_ventureone_business", bonusApiCardId: "c8d364bb32a8d2f6a359cf57a5e58cde", sortRank: 350,
    shortLabel: "VentureOne Business", cardName: "VentureOne Business", issuerName: "Capital One", network: "Mastercard",
    artKey: "hex://#0F2B5B:#2563EB:#FFFFFF", upstreamImagePath: "/images/capitalone/spark-miles.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: true, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 Capital One Miles", defaultSubEstimatedValueCents: 70000, defaultSubTargetSpendCents: 450000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Business Travel Hotels & Rental Cars", 5), r("Everything Else", "Every Business Purchase", 1.25)],
    benefits: []
  },
  {
    id: "capital_one_spark_2_cash_plus", bonusApiCardId: "9bd7d14db16dcc106d2eb062b08bf15f", sortRank: 350,
    shortLabel: "Spark 2% Cash Plus", cardName: "Spark 2% Cash Plus", issuerName: "Capital One", network: "Visa",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/capitalone/spark-cash.webp",
    annualFeeCents: 15000, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "$2,000 Cash Bonus", defaultSubEstimatedValueCents: 200000, defaultSubTargetSpendCents: 3000000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Business Travel Hotels & Rental Cars", 5), r("Everything Else", "Every Business Purchase (No Preset Limit)", 2)],
    benefits: [
      b("spark-plus-refund", "$150 Annual Fee Refund ($150k annual spend)", null, "ANNUAL", "CARDMEMBER_YEAR", "Annual $150 fee refunded every year you spend at least $150,000")
    ]
  },
  {
    id: "capital_one_venture_business", bonusApiCardId: "e48261ce7908f2ed8403245217f44de7", sortRank: 350,
    shortLabel: "Venture Business", cardName: "Venture Business", issuerName: "Capital One", network: "Mastercard",
    artKey: "hex://#0F2B5B:#2563EB:#FFFFFF", upstreamImagePath: "/images/capitalone/spark-miles.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: true, subCooldownMonths: 48,
    defaultSubRewardText: "150,000 Capital One Miles", defaultSubEstimatedValueCents: 210000, defaultSubTargetSpendCents: 3000000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Business Travel Hotels & Rental Cars", 5), r("Everything Else", "Every Business Purchase", 2)],
    benefits: [
      b("venture-biz-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years")
    ]
  },
  {
    id: "capital_one_ventureone", bonusApiCardId: "d0298263cc453d34160558a93fd1bcb2", sortRank: 250,
    shortLabel: "VentureOne", cardName: "VentureOne", issuerName: "Capital One", network: "Mastercard",
    artKey: "hex://#0F2B5B:#2563EB:#FFFFFF", upstreamImagePath: "/images/capitalone/venture-one.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "20,000 Capital One Miles", defaultSubEstimatedValueCents: 28000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Capital One Travel Hotels & Rental Cars", 5), r("Everything Else", "Everything Else", 1.25)],
    benefits: []
  },
  {
    id: "citi_aadvantage_executive", bonusApiCardId: "3543149f6555df6f7e4974923c55d853", sortRank: 250,
    shortLabel: "AAdvantage Executive", cardName: "AAdvantage Executive", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#0F172A:#0D9488:#FFFFFF", upstreamImagePath: "/images/citi/aadvantage-executive.webp",
    annualFeeCents: 59500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "100,000 AAdvantage Miles", defaultSubEstimatedValueCents: 130000, defaultSubTargetSpendCents: 1000000, subSpendWindowDays: 90, isElevatedSubOffer: true,
    rewardCategories: [r("Travel & Flights", "AAdvantage Hotels & Car Rentals", 10), r("Travel & Flights", "American Airlines Purchases (5x after $150k)", 4), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("aa-exec-grubhub", "$10/mo Grubhub Credit ($120/yr)", 1000, "MONTHLY", "CALENDAR", "Up to $10 per billing statement on eligible Grubhub purchases"),
      b("aa-exec-lyft", "$10/mo Lyft Credit ($120/yr after 3 rides)", 1000, "MONTHLY", "CALENDAR", "$10 monthly Lyft credit after taking 3 eligible rides in a month"),
      b("aa-exec-avis", "$120 Annual Avis / Budget Car Rental Credit", 12000, "ANNUAL", "CALENDAR", "Up to $120/yr statement credit on eligible Avis or Budget rentals"),
      b("aa-exec-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("aa-exec-club", "Admirals Club Membership", null, "ANNUAL", "CALENDAR", "Full Admirals Club airport lounge membership")
    ]
  },
  {
    id: "citi_aadvantage_mileup", bonusApiCardId: "196b33e005dd1bc8e1c505f2bf98a1d1", sortRank: 250,
    shortLabel: "AAdvantage MileUp", cardName: "AAdvantage MileUp", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#0F172A:#0D9488:#FFFFFF", upstreamImagePath: "/images/citi/aadvantage-mileup.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "15,000 AAdvantage Miles", defaultSubEstimatedValueCents: 19500, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "American Airlines Purchases", 2), r("Groceries", "Grocery Stores (incl. Delivery)", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "citi_aadvantage_platinum_select_world_elite", bonusApiCardId: "a497d6ba509538db9e3e4a3e5e313fe9", sortRank: 250,
    shortLabel: "AAdvantage Platinum Select World Eli", cardName: "AAdvantage Platinum Select World Elite", issuerName: "Citi", network: "Mastercard",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/citi/aadvantage-platinum-select.webp",
    annualFeeCents: 9900, isAnnualFeeWaived: true, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "80,000 AAdvantage Miles", defaultSubEstimatedValueCents: 104000, defaultSubTargetSpendCents: 350000, subSpendWindowDays: 120, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "American Airlines Purchases", 2), r("Dining & Restaurants", "Restaurants", 2), r("Gas & Transit", "Gas Stations", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("aa-plat-bag", "First Checked Bag Free & $125 Flight Discount ($20k spend)", null, "ANNUAL", "CARDMEMBER_YEAR", "First checked bag free on domestic AA + $125 flight discount after $20k spend")
    ]
  },
  {
    id: "citi_aadvantage_globe", bonusApiCardId: "c6b04cf07dd821ddcfba3dbe398a4b31", sortRank: 250,
    shortLabel: "AAdvantage Globe", cardName: "AAdvantage Globe", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#0F172A:#0D9488:#FFFFFF", upstreamImagePath: "/images/citi/aadvantage-globe.webp",
    annualFeeCents: 35000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "90,000 AAdvantage Miles", defaultSubEstimatedValueCents: 117000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 120, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "AAdvantage Hotels Bookings", 6), r("Travel & Flights", "American Airlines Purchases", 3), r("Dining & Restaurants", "Restaurants", 2), r("Gas & Transit", "Public Transit, Taxis & Rideshare", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("aa-globe-turo", "$240 Annual Turo Car-Sharing Credit ($30/trip)", 24000, "ANNUAL", "CALENDAR", "Up to $30 credit per eligible Turo trip, up to $240 per year"),
      b("aa-globe-splurge", "$100 Annual Splurge Credit (AAdvantage Hotels / Live Nation)", 10000, "ANNUAL", "CALENDAR", "Up to $100/yr credit across chosen Splurge merchants"),
      b("aa-globe-inflight", "$100 Annual AA Inflight Purchases Credit", 10000, "ANNUAL", "CALENDAR", "Up to $100/yr statement credit for AA inflight food, drinks, and Wi-Fi"),
      b("aa-globe-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("aa-globe-passes", "4 Admirals Club Globe Passes & $99 Companion Certificate", null, "ANNUAL", "CARDMEMBER_YEAR", "4 Admirals Club 24-hour passes per year + $99 domestic companion certificate on renewal")
    ]
  },
  {
    id: "citi_aadvantage_business_world_elite", bonusApiCardId: "f6ff3383cc6c52a81eb4715eb8d5de03", sortRank: 350,
    shortLabel: "AAdvantage Business World Elite", cardName: "AAdvantage Business World Elite", issuerName: "Citi", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/citi/aadvantage-platinum-select-business.webp",
    annualFeeCents: 9900, isAnnualFeeWaived: true, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "75,000 AAdvantage Miles", defaultSubEstimatedValueCents: 97500, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 120, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "American Airlines & Eligible Car Rentals", 2), r("Office & Internet", "Telecommunications & Cable/Satellite", 2), r("Gas & Transit", "Gas Stations", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("aa-biz-bag", "First Checked Bag Free on Domestic AA", null, "ANNUAL", "CALENDAR", "First checked bag free on domestic AA itineraries")
    ]
  }
];
