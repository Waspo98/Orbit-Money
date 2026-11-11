// =============================================================================
// creditCardCatalogExtendedSeedA.js - Extended US credit cards (Part A).
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

export const EXTENDED_CARD_CATALOG_A = [
  {
    id: "american_express_delta_skymiles_blue", bonusApiCardId: "90436ebe63417596fbc6cb5b7e5a2e8c", sortRank: 250,
    shortLabel: "Delta SkyMiles Blue", cardName: "Delta SkyMiles Blue", issuerName: "American Express", network: "Amex",
    artKey: "hex://#1E3A8A:#3B82F6:#FFFFFF", upstreamImagePath: "/images/amex/delta-skymiles-blue.jpg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "10,000 Delta SkyMiles", defaultSubEstimatedValueCents: 12000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Delta Purchases", 2), r("Dining & Restaurants", "Restaurants Worldwide", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "american_express_delta_skymiles_gold_business", bonusApiCardId: "509a09cbd31edd8df85928d044ef7802", sortRank: 350,
    shortLabel: "Delta SkyMiles Gold Business", cardName: "Delta SkyMiles Gold Business", issuerName: "American Express", network: "Amex",
    artKey: "preset://amex-gold", upstreamImagePath: "/images/amex/delta-skymiles-gold-business.webp",
    annualFeeCents: 15000, isAnnualFeeWaived: true, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: "60,000 Delta SkyMiles", defaultSubEstimatedValueCents: 72000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Delta Purchases", 2), r("Office & Internet", "U.S. Shipping & Advertising ($50k combined cap)", 2, { capCents: 5000000 }), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("delta-gold-biz-stays", "$150 Annual Delta Stays Credit", 15000, "ANNUAL", "CALENDAR", "Up to $150/yr statement credit for prepaid hotels or vacation rentals on Delta Stays"),
      b("delta-gold-biz-bag", "First Checked Bag Free on Delta", null, "ANNUAL", "CALENDAR", "First checked bag free + 15% TakeOff 15 award discount")
    ]
  },
  {
    id: "american_express_delta_skymiles_platinum", bonusApiCardId: "bdf4ae17ff6808592f785fff3ae81a2a", sortRank: 250,
    shortLabel: "Delta SkyMiles Platinum", cardName: "Delta SkyMiles Platinum", issuerName: "American Express", network: "Amex",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/amex/delta-skymiles-platinum.webp",
    annualFeeCents: 35000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "60,000 Delta SkyMiles", defaultSubEstimatedValueCents: 72000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Delta Purchases & Direct Hotels", 3), r("Dining & Restaurants", "Restaurants Worldwide", 2), r("Groceries", "U.S. Supermarkets", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("delta-plat-stays", "$150 Annual Delta Stays Credit", 15000, "ANNUAL", "CALENDAR", "Up to $150/yr credit for prepaid Delta Stays bookings"),
      b("delta-plat-resy", "$10/mo Resy Credit ($120/yr)", 1000, "MONTHLY", "CALENDAR", "Up to $10 monthly credit at U.S. Resy restaurants"),
      b("delta-plat-rideshare", "$10/mo Rideshare Credit ($120/yr)", 1000, "MONTHLY", "CALENDAR", "Up to $10 monthly credit on U.S. rideshare purchases"),
      b("delta-plat-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("delta-plat-companion", "Annual Main Cabin Companion Certificate", null, "ANNUAL", "CARDMEMBER_YEAR", "Annual round-trip Main Cabin Companion Certificate each renewal year")
    ]
  },
  {
    id: "american_express_delta_skymiles_platinum_business", bonusApiCardId: "1e9c3dae029612a7a40adf6144bf43d4", sortRank: 350,
    shortLabel: "Delta SkyMiles Platinum Business", cardName: "Delta SkyMiles Platinum Business", issuerName: "American Express", network: "Amex",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/amex/delta-skymiles-platinum-business.webp",
    annualFeeCents: 35000, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: "70,000 Delta SkyMiles", defaultSubEstimatedValueCents: 84000, defaultSubTargetSpendCents: 600000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Delta Purchases & Direct Hotels", 3), r("Gas & Transit", "Transit, U.S. Shipping & Purchases $5,000+ ($100k cap)", 1.5, { capCents: 10000000 }), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("delta-plat-biz-stays", "$200 Annual Delta Stays Credit", 20000, "ANNUAL", "CALENDAR", "Up to $200/yr credit for prepaid Delta Stays bookings"),
      b("delta-plat-biz-resy", "$10/mo Resy Credit ($120/yr)", 1000, "MONTHLY", "CALENDAR", "Up to $10 monthly credit at U.S. Resy restaurants"),
      b("delta-plat-biz-rideshare", "$10/mo Rideshare Credit ($120/yr)", 1000, "MONTHLY", "CALENDAR", "Up to $10 monthly credit on U.S. rideshare purchases"),
      b("delta-plat-biz-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("delta-plat-biz-companion", "Annual Main Cabin Companion Certificate", null, "ANNUAL", "CARDMEMBER_YEAR", "Annual round-trip Main Cabin Companion Certificate each renewal year")
    ]
  },
  {
    id: "american_express_delta_skymiles_reserve_business", bonusApiCardId: "a8032dfcc9bc6af82eb1a0a487e710b5", sortRank: 350,
    shortLabel: "Delta SkyMiles Reserve Business", cardName: "Delta SkyMiles Reserve Business", issuerName: "American Express", network: "Amex",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/amex/delta-skymiles-reserve-business.webp",
    annualFeeCents: 65000, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: "80,000 Delta SkyMiles", defaultSubEstimatedValueCents: 96000, defaultSubTargetSpendCents: 1000000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Delta Purchases", 3), r("Office & Internet", "Transit, U.S. Shipping & Office Supply", 1.5), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("delta-res-biz-stays", "$250 Annual Delta Stays Credit", 25000, "ANNUAL", "CALENDAR", "Up to $250/yr credit for prepaid Delta Stays bookings"),
      b("delta-res-biz-resy", "$20/mo Resy Credit ($240/yr)", 2000, "MONTHLY", "CALENDAR", "Up to $20 monthly credit at U.S. Resy restaurants"),
      b("delta-res-biz-rideshare", "$10/mo Rideshare Credit ($120/yr)", 1000, "MONTHLY", "CALENDAR", "Up to $10 monthly credit on U.S. rideshare purchases"),
      b("delta-res-biz-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("delta-res-biz-companion", "Annual Companion Certificate & 15 Sky Club Visits", null, "ANNUAL", "CARDMEMBER_YEAR", "First/Comfort+/Main Companion Certificate upon renewal + 15 Delta Sky Club visits")
    ]
  },
  {
    id: "american_express_blue_business_cash", bonusApiCardId: "a37efd1da4e7f0d468c87d3b6f021250", sortRank: 350,
    shortLabel: "Blue Business Cash", cardName: "Blue Business Cash", issuerName: "American Express", network: "Amex",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/amex/blue-business-cash.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: "$250 Cash Bonus", defaultSubEstimatedValueCents: 25000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Business Purchase ($50k annual cap)", 2, { capCents: 5000000 })],
    benefits: []
  },
  {
    id: "american_express_blue_cash_preferred_morgan_stanley", bonusApiCardId: "b628058009b9f1a24093028750e18112", sortRank: 250,
    shortLabel: "Blue Cash Preferred (Morgan Stanley)", cardName: "Blue Cash Preferred (Morgan Stanley)", issuerName: "American Express", network: "Amex",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/amex/blue-cash-preferred-morgan-stanley.png",
    annualFeeCents: 9500, isAnnualFeeWaived: true, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "$250 Cash Bonus", defaultSubEstimatedValueCents: 25000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Groceries", "U.S. Supermarkets ($6k cap)", 6, { capCents: 600000 }), r("Streaming & Entertainment", "Select U.S. Streaming", 6), r("Gas & Transit", "U.S. Gas Stations & Transit", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("ms-bcp-disney", "$10/mo Disney Streaming Credit ($120/yr)", 1000, "MONTHLY", "CALENDAR", "Up to $10 monthly statement credit on eligible Disney+, Hulu, or ESPN+ subscriptions")
    ]
  },
  {
    id: "american_express_business_green", bonusApiCardId: "aff8fcfdc6a69cef4936e1e474820516", sortRank: 350,
    shortLabel: "Business Green", cardName: "Business Green", issuerName: "American Express", network: "Amex",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/amex/business-green.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: "15,000 Membership Rewards pts", defaultSubEstimatedValueCents: 22500, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Flights & Prepaid Hotels on Amex Travel", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "american_express_cash_magnet", bonusApiCardId: "27ebb66ce240c9dcf6e1d13d63e319b4", sortRank: 250,
    shortLabel: "Cash Magnet", cardName: "Cash Magnet", issuerName: "American Express", network: "Amex",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/amex/cash-magnet.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Purchase (Catch-All)", 1.5)],
    benefits: []
  },
  {
    id: "american_express_hilton_honors", bonusApiCardId: "0710239dc6fbd92077bce7118572e217", sortRank: 250,
    shortLabel: "Hilton Honors", cardName: "Hilton Honors", issuerName: "American Express", network: "Amex",
    artKey: "hex://#1E3A8A:#3B82F6:#FFFFFF", upstreamImagePath: "/images/amex/hilton-honors.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "80,000 Hilton Honors pts", defaultSubEstimatedValueCents: 40000, defaultSubTargetSpendCents: 200000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Hilton Hotels & Resorts", 7), r("Dining & Restaurants", "U.S. Restaurants", 5), r("Groceries", "U.S. Supermarkets", 5), r("Gas & Transit", "U.S. Gas Stations", 5), r("Everything Else", "Everything Else", 3)],
    benefits: []
  },
  {
    id: "american_express_hilton_honors_business", bonusApiCardId: "434df114291a974c876d58cf79158376", sortRank: 350,
    shortLabel: "Hilton Honors Business", cardName: "Hilton Honors Business", issuerName: "American Express", network: "Amex",
    artKey: "hex://#1E3A8A:#3B82F6:#FFFFFF", upstreamImagePath: "/images/amex/hilton-honors-business.png",
    annualFeeCents: 19500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: "130,000 Hilton Honors pts", defaultSubEstimatedValueCents: 65000, defaultSubTargetSpendCents: 600000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Hilton Hotels & Resorts", 12), r("Everything Else", "Eligible Business Purchases ($100k annual cap)", 5, { capCents: 10000000 })],
    benefits: [
      b("hilton-biz-credit", "$60 Quarterly Hilton Credit ($240/yr)", 6000, "QUARTERLY", "CALENDAR", "Up to $60 statement credit each quarter for direct Hilton property purchases")
    ]
  },
  {
    id: "american_express_hilton_honors_surpass", bonusApiCardId: "f6c6aa50b533b758df7be5151d805571", sortRank: 250,
    shortLabel: "Hilton Honors Surpass", cardName: "Hilton Honors Surpass", issuerName: "American Express", network: "Amex",
    artKey: "hex://#1E3A8A:#3B82F6:#FFFFFF", upstreamImagePath: "/images/amex/hilton-honors-surpass.jpg",
    annualFeeCents: 15000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "130,000 Hilton Honors pts", defaultSubEstimatedValueCents: 65000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Hilton Hotels & Resorts", 12), r("Dining & Restaurants", "U.S. Restaurants", 6), r("Groceries", "U.S. Supermarkets", 6), r("Gas & Transit", "U.S. Gas Stations", 6), r("Online Shopping", "U.S. Online Retail Purchases", 4), r("Everything Else", "Everything Else", 3)],
    benefits: [
      b("hilton-surpass-credit", "$50 Quarterly Hilton Credit ($200/yr)", 5000, "QUARTERLY", "CALENDAR", "Up to $50 statement credit each quarter on direct Hilton purchases")
    ]
  },
  {
    id: "american_express_marriott_bonvoy_business", bonusApiCardId: "1129280627d633940e575deabdaa105f", sortRank: 350,
    shortLabel: "Marriott Bonvoy Business", cardName: "Marriott Bonvoy Business", issuerName: "American Express", network: "Amex",
    artKey: "hex://#4A1525:#7F1D1D:#FFFFFF", upstreamImagePath: "/images/amex/marriott-bonvoy-business.webp",
    annualFeeCents: 12500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: "3 Free Night Awards (50k pts each)", defaultSubEstimatedValueCents: 60000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Marriott Bonvoy Hotels", 6), r("Dining & Restaurants", "Restaurants Worldwide", 4), r("Gas & Transit", "U.S. Gas Stations", 4), r("Office & Internet", "U.S. Wireless Phone & Shipping", 4), r("Everything Else", "Everything Else", 2)],
    benefits: [
      b("marriott-biz-fna", "35,000-Pt Annual Free Night Award", 17500, "ANNUAL", "CARDMEMBER_YEAR", "1 Free Night Award worth up to 35,000 points every renewal year")
    ]
  },
  {
    id: "american_express_platinum_schwab", bonusApiCardId: "7eb93f5f9ab64e1c12b82ec8822c25ef", sortRank: 250,
    shortLabel: "Platinum (Schwab)", cardName: "Platinum (Schwab)", issuerName: "American Express", network: "Amex",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/amex/platinum-schwab.webp",
    annualFeeCents: 89500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "80,000 Membership Rewards pts", defaultSubEstimatedValueCents: 120000, defaultSubTargetSpendCents: 800000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Direct Flights & Prepaid Amex Travel Hotels ($500k cap)", 5, { capCents: 50000000 }), r("Everything Else", "Everything Else (1.1 cpp Schwab Cash-Out)", 1)],
    benefits: [
      b("schwab-plat-hotel", "$300 Semi-Annual Hotel Credit ($600/yr FHR/THC)", 30000, "SEMI_ANNUAL", "CALENDAR", "Up to $300 Jan-Jun and $300 Jul-Dec on prepaid Fine Hotels + Resorts or The Hotel Collection"),
      b("schwab-plat-resy", "$100 Quarterly Resy Dining Credit ($400/yr)", 10000, "QUARTERLY", "CALENDAR", "Up to $100 per quarter at U.S. Resy restaurants"),
      b("schwab-plat-lulu", "$75 Quarterly Lululemon Credit ($300/yr)", 7500, "QUARTERLY", "CALENDAR", "Up to $75 per quarter on eligible U.S. Lululemon purchases"),
      b("schwab-plat-ent", "$25/mo Digital Entertainment Credit ($300/yr)", 2500, "MONTHLY", "CALENDAR", "Up to $25/mo credit on eligible streaming and news subscriptions"),
      b("schwab-plat-uber", "Uber Cash ($15/mo + $20 Dec)", 1500, "MONTHLY", "CALENDAR", "$200 total annual Uber Cash"),
      b("schwab-plat-airline", "$200 Airline Incidental Fee Credit", 20000, "ANNUAL", "CALENDAR", "Up to $200/yr for incidental fees on one selected airline"),
      b("schwab-plat-clear", "$209 CLEAR Plus Credit", 20900, "ANNUAL", "CALENDAR", "Up to $209/yr statement credit for CLEAR Plus")
    ]
  },
  {
    id: "american_express_platinum_morgan_stanley", bonusApiCardId: "ab55a96910ffc7f31fc1605c99430ec5", sortRank: 250,
    shortLabel: "Platinum (Morgan Stanley)", cardName: "Platinum (Morgan Stanley)", issuerName: "American Express", network: "Amex",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/amex/platinum-morgan-stanley.webp",
    annualFeeCents: 89500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "80,000 Membership Rewards pts", defaultSubEstimatedValueCents: 120000, defaultSubTargetSpendCents: 800000, subSpendWindowDays: 180, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Direct Flights & Prepaid Amex Travel Hotels ($500k cap)", 5, { capCents: 50000000 }), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("ms-plat-hotel", "$300 Semi-Annual Hotel Credit ($600/yr FHR/THC)", 30000, "SEMI_ANNUAL", "CALENDAR", "Up to $300 Jan-Jun and $300 Jul-Dec on prepaid Fine Hotels + Resorts or The Hotel Collection"),
      b("ms-plat-resy", "$100 Quarterly Resy Dining Credit ($400/yr)", 10000, "QUARTERLY", "CALENDAR", "Up to $100 per quarter at U.S. Resy restaurants"),
      b("ms-plat-lulu", "$75 Quarterly Lululemon Credit ($300/yr)", 7500, "QUARTERLY", "CALENDAR", "Up to $75 per quarter on eligible U.S. Lululemon purchases"),
      b("ms-plat-ent", "$25/mo Digital Entertainment Credit ($300/yr)", 2500, "MONTHLY", "CALENDAR", "Up to $25/mo credit on eligible streaming and news subscriptions"),
      b("ms-plat-uber", "Uber Cash ($15/mo + $20 Dec)", 1500, "MONTHLY", "CALENDAR", "$200 total annual Uber Cash"),
      b("ms-plat-airline", "$200 Airline Incidental Fee Credit", 20000, "ANNUAL", "CALENDAR", "Up to $200/yr for incidental fees on one selected airline"),
      b("ms-plat-au", "1st Additional Platinum Card Included ($195 val)", null, "ANNUAL", "CARDMEMBER_YEAR", "First Additional Platinum Card at no annual fee for Morgan Stanley clients")
    ]
  },
  {
    id: "american_express_marriott_bonvoy_bevy", bonusApiCardId: "653b14a655f4edc9bfc0332e9370e139", sortRank: 250,
    shortLabel: "Marriott Bonvoy Bevy", cardName: "Marriott Bonvoy Bevy", issuerName: "American Express", network: "Amex",
    artKey: "hex://#4A1525:#7F1D1D:#FFFFFF", upstreamImagePath: "/images/amex/marriott-bonvoy-bevy.png",
    annualFeeCents: 25000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "175,000 Marriott Bonvoy pts", defaultSubEstimatedValueCents: 140000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 180, isElevatedSubOffer: true,
    rewardCategories: [r("Travel & Flights", "Marriott Bonvoy Hotels", 6), r("Dining & Restaurants", "Restaurants Worldwide ($15k combined cap)", 4, { capCents: 1500000 }), r("Groceries", "U.S. Supermarkets ($15k combined cap)", 4, { capCents: 1500000 }), r("Everything Else", "Everything Else", 2)],
    benefits: [
      b("bevy-gold", "Marriott Gold Elite & 1,000 Bonus Pts/Stay", null, "ANNUAL", "CALENDAR", "Complimentary Gold Elite status + 50k Free Night after $15k calendar-year spend")
    ]
  },
  {
    id: "american_express_lowes", bonusApiCardId: "9bf8d331ae97a8ddc47ef3d22dcaf213", sortRank: 350,
    shortLabel: "Lowe's Business", cardName: "Lowe's Business Rewards", issuerName: "American Express", network: "Amex",
    artKey: "hex://#1E3A8A:#3B82F6:#FFFFFF", upstreamImagePath: "/images/amex/lowes-business.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Lowe's Purchases (5% Instant Discount + 2x Pts)", 2), r("Dining & Restaurants", "U.S. Restaurants", 2), r("Office & Internet", "U.S. Office Supply & Wireless Phone", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "american_express_schwab_investor", bonusApiCardId: "a3b091353bd1a45ad58f75e5287f1fd6", sortRank: 250,
    shortLabel: "Schwab Investor", cardName: "Schwab Investor", issuerName: "American Express", network: "Amex",
    artKey: "hex://#1E3A8A:#3B82F6:#FFFFFF", upstreamImagePath: "/images/amex/schwab-investor.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 999,
    defaultSubRewardText: "$200 Cash Bonus", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Purchase (Auto-Deposited to Schwab)", 1.5)],
    benefits: []
  },
  {
    id: "american_express_amazon_business", bonusApiCardId: "15cc2b7c31596c0112cfba30180ab739", sortRank: 350,
    shortLabel: "Amazon Business Prime", cardName: "Amazon Business Prime", issuerName: "American Express", network: "Amex",
    artKey: "hex://#1E3A8A:#3B82F6:#FFFFFF", upstreamImagePath: null,
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 999,
    defaultSubRewardText: "$100 Cash Bonus", defaultSubEstimatedValueCents: 10000, defaultSubTargetSpendCents: 1, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Amazon Business, AWS & Whole Foods ($120k cap)", 5, { capCents: 12000000 }), r("Dining & Restaurants", "U.S. Restaurants", 2), r("Gas & Transit", "U.S. Gas Stations", 2), r("Utilities", "U.S. Wireless Telephone Services", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "american_express_rakuten", bonusApiCardId: "6ca9fd6caab5364f72928dd5ba04bb58", sortRank: 350,
    shortLabel: "Rakuten Amex", cardName: "Rakuten American Express Card", issuerName: "American Express", network: "Amex",
    artKey: "hex://#1E3A8A:#3B82F6:#FFFFFF", upstreamImagePath: "/images/amex/rakuten.jpg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 24,
    defaultSubRewardText: "$25 Cash Bonus", defaultSubEstimatedValueCents: 2500, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Online Shopping", "Rakuten Shopping Portal ($7,000/yr cap)", 4, { capCents: 700000 }), r("Dining & Restaurants", "Dining", 2), r("Groceries", "Groceries", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "bank_of_america_air_france_klm", bonusApiCardId: "f2d769efdbe2b9d8734787bc24297be6", sortRank: 250,
    shortLabel: "Air France KLM", cardName: "Air France KLM", issuerName: "Bank of America", network: "Mastercard",
    artKey: "hex://#7F1D1D:#DC2626:#FFFFFF", upstreamImagePath: "/images/bankofamerica/air-france-klm.webp",
    annualFeeCents: 8900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "70,000 Flying Blue Miles + $100 Credit", defaultSubEstimatedValueCents: 94000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: true,
    rewardCategories: [r("Travel & Flights", "Air France, KLM & SkyTeam Airline Purchases", 3), r("Everything Else", "Everything Else (Catch-All)", 1.5)],
    benefits: [
      b("afklm-anniv", "5,000 Anniversary Flying Blue Miles", 6000, "ANNUAL", "CARDMEMBER_YEAR", "5,000 bonus miles every anniversary after $50+ in purchases")
    ]
  },
  {
    id: "bank_of_america_premium_rewards_elite", bonusApiCardId: "20594480e9f5656485a12e13389f60c7", sortRank: 250,
    shortLabel: "Premium Rewards Elite", cardName: "Premium Rewards Elite", issuerName: "Bank of America", network: "Visa",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/bankofamerica/premium-rewards-elite.png",
    annualFeeCents: 55000, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "75,000 Preferred Rewards pts", defaultSubEstimatedValueCents: 75000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Travel Purchases", 2), r("Dining & Restaurants", "Dining Purchases", 2), r("Everything Else", "Everything Else", 1.5)],
    benefits: [
      b("bofa-pre-airline", "$300 Annual Airline Incidental Credit", 30000, "ANNUAL", "CALENDAR", "Up to $300/yr automatic credit for qualifying airline incidental fees"),
      b("bofa-pre-lifestyle", "$150 Annual Lifestyle Convenience Credit", 15000, "ANNUAL", "CALENDAR", "Up to $150/yr for streaming, food delivery, rideshare, and fitness subscriptions"),
      b("bofa-pre-ge", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 credit every 4 years"),
      b("bofa-pre-pp", "4 Priority Pass Select Memberships", null, "ANNUAL", "CALENDAR", "Up to 4 complimentary Priority Pass Select memberships")
    ]
  },
  {
    id: "bank_of_america_travel_rewards", bonusApiCardId: "fecfaab2c32f3edd5d97eafd8f3f19d6", sortRank: 250,
    shortLabel: "Travel Rewards", cardName: "Travel Rewards", issuerName: "Bank of America", network: "Visa",
    artKey: "hex://#7F1D1D:#DC2626:#FFFFFF", upstreamImagePath: "/images/bankofamerica/travel-rewards.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "25,000 Travel Rewards pts", defaultSubEstimatedValueCents: 25000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Bank of America Travel Center", 3), r("Everything Else", "Everything Else (Catch-All)", 1.5)],
    benefits: []
  },
  {
    id: "bank_of_america_unlimited_cash_rewards", bonusApiCardId: "98db04658d4deee69293bd67bd2fd341", sortRank: 250,
    shortLabel: "Unlimited Cash Rewards", cardName: "Unlimited Cash Rewards", issuerName: "Bank of America", network: "Visa",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/bankofamerica/unlimited-cash-rewards.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$200 Cash Bonus", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Everything Else (Catch-All)", 1.5)],
    benefits: []
  },
  {
    id: "bank_of_america_business_advantage_customized_cash", bonusApiCardId: "4f6d71dd4d06ff960cae0990b5efda21", sortRank: 350,
    shortLabel: "Business Advantage Customized Cash", cardName: "Business Advantage Customized Cash", issuerName: "Bank of America", network: "Mastercard",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/bankofamerica/business-advantage-customized-cash.jpg",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "$500 Cash Bonus", defaultSubEstimatedValueCents: 50000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Office & Internet", "3% Choice Business Category ($50k/yr combined cap)", 3, { isRotating: true, capCents: 5000000 }), r("Dining & Restaurants", "Dining ($50k/yr combined cap)", 2, { capCents: 5000000 }), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "bank_of_america_business_advantage_unlimited_cash", bonusApiCardId: "a99aa675065459b90144275ac18d9ab1", sortRank: 350,
    shortLabel: "Business Advantage Unlimited Cash", cardName: "Business Advantage Unlimited Cash", issuerName: "Bank of America", network: "Mastercard",
    artKey: "hex://#065F46:#059669:#FFFFFF", upstreamImagePath: "/images/bankofamerica/business-advantage-unlimited-cash.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "$500 Cash Bonus", defaultSubEstimatedValueCents: 50000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Every Business Purchase", 1.5)],
    benefits: []
  },
  {
    id: "bank_of_america_business_advantage_travel_rewards", bonusApiCardId: "1e75cf9ca2ccded9a621d5e8f5630c3c", sortRank: 350,
    shortLabel: "Business Advantage Travel Rewards", cardName: "Business Advantage Travel Rewards", issuerName: "Bank of America", network: "Mastercard",
    artKey: "hex://#7F1D1D:#DC2626:#FFFFFF", upstreamImagePath: "/images/bankofamerica/business-advantage-travel-rewards.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 Travel Rewards pts", defaultSubEstimatedValueCents: 50000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Bank of America Travel Center", 3), r("Everything Else", "Every Business Purchase", 1.5)],
    benefits: []
  },
  {
    id: "bank_of_america_platinum_plus", bonusApiCardId: "9dce80bda519a8616ca4eb288d7cc693", sortRank: 350,
    shortLabel: "Platinum Plus", cardName: "Platinum Plus", issuerName: "Bank of America", network: "Mastercard",
    artKey: "preset://venture-silver", upstreamImagePath: "/images/bankofamerica/platinum-plus.webp",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "$300 Cash Bonus", defaultSubEstimatedValueCents: 30000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Everything Else", "Standard Business Credit Purchases", 1)],
    benefits: []
  },
  {
    id: "bank_of_america_free_spirit", bonusApiCardId: "541d121bd20b148d43a8c896d0de6830", sortRank: 250,
    shortLabel: "Free Spirit Travel More", cardName: "Free Spirit Travel More", issuerName: "Bank of America", network: "Mastercard",
    artKey: "hex://#7F1D1D:#DC2626:#FFFFFF", upstreamImagePath: "/images/bankofamerica/free-spirit.webp",
    annualFeeCents: 7900, isAnnualFeeWaived: true, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "65,000 Free Spirit pts + $100 Voucher", defaultSubEstimatedValueCents: 75000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: true,
    rewardCategories: [r("Travel & Flights", "Spirit Airlines Purchases", 3), r("Dining & Restaurants", "Eligible Dining", 2), r("Groceries", "Eligible Grocery Stores", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("spirit-voucher", "$100 Companion Flight Voucher ($5k annual spend)", null, "ANNUAL", "CARDMEMBER_YEAR", "$100 Companion Flight Voucher each anniversary after $5,000 in purchases")
    ]
  },
  {
    id: "bank_of_america_atmos_rewards_ascent", bonusApiCardId: "12a1230229f936ee5f7b2d333e796ab8", sortRank: 250,
    shortLabel: "Atmos Rewards Ascent", cardName: "Atmos Rewards Ascent", issuerName: "Bank of America", network: "Visa",
    artKey: "hex://#7F1D1D:#DC2626:#FFFFFF", upstreamImagePath: "/images/bankofamerica/atmos-rewards-ascent.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "85,000 Atmos Rewards pts + $100 Credit", defaultSubEstimatedValueCents: 120500, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 120, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Alaska Airlines & Hawaiian Airlines Purchases", 3), r("Gas & Transit", "Gas, EV Charging, Local Transit & Rideshare", 2), r("Streaming & Entertainment", "Cable & Select Streaming Services", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("atmos-ascent-bag", "Free Checked Bag & $99 Companion Fare ($6k spend)", null, "ANNUAL", "CARDMEMBER_YEAR", "Free first checked bag + $99 Companion Fare after $6,000 anniversary spend")
    ]
  },
  {
    id: "bank_of_america_atmos_rewards_summit", bonusApiCardId: "2878b32104d7bf242496a8af56f33952", sortRank: 250,
    shortLabel: "Atmos Rewards Summit", cardName: "Atmos Rewards Summit", issuerName: "Bank of America", network: "Visa",
    artKey: "hex://#7F1D1D:#DC2626:#FFFFFF", upstreamImagePath: "/images/bankofamerica/atmos-rewards-summit.webp",
    annualFeeCents: 39500, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "100,000 Atmos Rewards pts + 25k Companion Award", defaultSubEstimatedValueCents: 162500, defaultSubTargetSpendCents: 600000, subSpendWindowDays: 90, isElevatedSubOffer: true,
    rewardCategories: [r("Travel & Flights", "Alaska Airlines, Hawaiian Airlines & Foreign Purchases", 3), r("Dining & Restaurants", "Dining Worldwide", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("atmos-summit-companion", "25,000-Pt Annual Global Companion Award", 32500, "ANNUAL", "CARDMEMBER_YEAR", "Annual 25,000-point Global Companion Award included each year"),
      b("atmos-summit-ge", "Global Entry / TSA PreCheck Credit", 12000, "EVERY_4_YEARS", "CALENDAR", "Up to $120 statement credit every 4 years"),
      b("atmos-summit-lounge", "8 Alaska Lounge Passes (2 per Quarter)", null, "QUARTERLY", "CALENDAR", "Two Alaska Lounge passes each calendar quarter")
    ]
  },
  {
    id: "bank_of_america_atmos_rewards_business", bonusApiCardId: "9e42d6cf9c6dcfda0cf7d5f5f015285d", sortRank: 350,
    shortLabel: "Atmos Rewards Business", cardName: "Atmos Rewards Business", issuerName: "Bank of America", network: "Visa",
    artKey: "hex://#7F1D1D:#DC2626:#FFFFFF", upstreamImagePath: "/images/bankofamerica/atmos-rewards-ascent.webp",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "80,000 Atmos Rewards pts + $100 Credit", defaultSubEstimatedValueCents: 114000, defaultSubTargetSpendCents: 400000, subSpendWindowDays: 120, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Alaska Airlines & Hawaiian Airlines Purchases", 3), r("Gas & Transit", "Gas, EV Charging, Shipping & Local Transit", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("atmos-biz-bag", "Free Checked Bag & $99 Companion Fare ($6k spend)", null, "ANNUAL", "CARDMEMBER_YEAR", "Free first checked bag + $99 Companion Fare after $6,000 anniversary spend")
    ]
  },
  {
    id: "barclays_aadvantage_aviator_red_world_elite", bonusApiCardId: "bb54057b74adc9514885254a82fd8634", sortRank: 250,
    shortLabel: "AAdvantage Aviator Red World Elite", cardName: "AAdvantage Aviator Red World Elite", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/barclays/aadvantage-aviator-red-world-elite.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "60,000 AAdvantage Miles", defaultSubEstimatedValueCents: 78000, defaultSubTargetSpendCents: 100, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "American Airlines Purchases", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("aviator-red-wifi", "$25 Annual Inflight Wi-Fi Credit", 2500, "ANNUAL", "CARDMEMBER_YEAR", "Up to $25/yr statement credits on American Airlines inflight Wi-Fi"),
      b("aviator-red-bag", "First Checked Bag Free on Domestic AA", null, "ANNUAL", "CALENDAR", "First checked bag free for cardmember and up to 4 companions")
    ]
  },
  {
    id: "barclays_aadvantage_aviator_world_elite_business", bonusApiCardId: "4b3dee677718c19aace2e927e6e26704", sortRank: 350,
    shortLabel: "AAdvantage Aviator World Elite Busin", cardName: "AAdvantage Aviator World Elite Business", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/barclays/aadvantage-aviator-world-elite-business.png",
    annualFeeCents: 9500, isAnnualFeeWaived: false, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: "65,000 AAdvantage Miles", defaultSubEstimatedValueCents: 84500, defaultSubTargetSpendCents: 200000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "American Airlines & Eligible Car Rentals", 2), r("Office & Internet", "Office Supply & Telecom Purchases", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("aviator-biz-bag", "First Checked Bag Free on Domestic AA", null, "ANNUAL", "CALENDAR", "First checked bag free on domestic AA itineraries")
    ]
  },
  {
    id: "barclays_aarp_essential_rewards", bonusApiCardId: "f2fcdd7db964fb3c55f6df704f5678c8", sortRank: 250,
    shortLabel: "AARP Essential Rewards", cardName: "AARP Essential Rewards", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/aarp-essential-rewards.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$100 Cash Bonus", defaultSubEstimatedValueCents: 10000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Gas & Transit", "Gas Stations", 3), r("Drugstores", "Drugstores (excl. Target/Walmart)", 3), r("Everything Else", "Medical Expenses (2%) & Everything Else (1%)", 1)],
    benefits: []
  },
  {
    id: "barclays_aarp_travel_rewards", bonusApiCardId: "fc80c887a321dde1faa7e9282c813683", sortRank: 250,
    shortLabel: "AARP Travel Rewards", cardName: "AARP Travel Rewards", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/aarp-travel-rewards.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "$100 Cash Bonus", defaultSubEstimatedValueCents: 10000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Airfare, Hotel Stays & Car Rentals", 3), r("Dining & Restaurants", "Restaurants & Cafe/Delivery", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "barclays_carnival_world_mastercard", bonusApiCardId: "eddb365c3098a64170b9e4fb322b2a05", sortRank: 250,
    shortLabel: "Carnival World Mastercard", cardName: "Carnival World Mastercard", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/carnival-world-mastercard.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "20,000 Carnival FunPoints", defaultSubEstimatedValueCents: 20000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Carnival & World Leading Cruise Lines", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "barclays_emirates_skywards_premium_world_elite", bonusApiCardId: "990a75f557890d22402183b20a8fe09d", sortRank: 250,
    shortLabel: "Emirates Skywards Premium World Elit", cardName: "Emirates Skywards Premium World Elite", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/barclays/emirates-skywards-premium-world-elite.png",
    annualFeeCents: 49900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "70,000 Skywards Miles", defaultSubEstimatedValueCents: 70000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Emirates Purchases", 3), r("Travel & Flights", "Airlines, Hotels & Car Rentals", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("emirates-prem-ge", "Global Entry / TSA PreCheck Credit", 10000, "EVERY_4_YEARS", "CALENDAR", "Up to $100 statement credit every 4 years"),
      b("emirates-prem-pp", "Priority Pass Select & Emirates Gold Year 1", null, "ANNUAL", "CALENDAR", "Complimentary Priority Pass Select lounge access")
    ]
  },
  {
    id: "barclays_emirates_skywards_rewards_world_elite", bonusApiCardId: "e33253806e1da76436e527e3c7b6179d", sortRank: 250,
    shortLabel: "Emirates Skywards Rewards World Elit", cardName: "Emirates Skywards Rewards World Elite", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/barclays/emirates-skywards-rewards-world-elite.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "40,000 Skywards Miles", defaultSubEstimatedValueCents: 40000, defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Emirates Purchases", 3), r("Travel & Flights", "Airlines, Hotels & Car Rentals", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  },
  {
    id: "barclays_frontier_airlines_world", bonusApiCardId: "e33d8701c8e81d5befe82ec5ed5cda23", sortRank: 250,
    shortLabel: "Frontier Airlines World", cardName: "Frontier Airlines World", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/frontier-airlines-world.png",
    annualFeeCents: 9900, isAnnualFeeWaived: true, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "50,000 Frontier Miles", defaultSubEstimatedValueCents: 50000, defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "FlyFrontier.com Purchases", 5), r("Dining & Restaurants", "Restaurants", 3), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("frontier-voucher", "$100 Flight Voucher ($2,500 annual spend)", null, "ANNUAL", "CARDMEMBER_YEAR", "$100 Frontier flight voucher after $2,500 spend in cardmember year")
    ]
  },
  {
    id: "barclays_hawaiian_airlines_world_elite", bonusApiCardId: "44effaf95f470d31302d6f366207a543", sortRank: 250,
    shortLabel: "Hawaiian Airlines World Elite", cardName: "Hawaiian Airlines World Elite", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#09090B:#27272A:#FFFFFF", upstreamImagePath: "/images/barclays/hawaiian-airlines-world-elite.png",
    annualFeeCents: 9900, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "80,000 HawaiianMiles", defaultSubEstimatedValueCents: 80000, defaultSubTargetSpendCents: 500000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "Hawaiian Airlines & Alaska Airlines Purchases", 3), r("Dining & Restaurants", "Dining", 2), r("Groceries", "Eligible Grocery Stores", 2), r("Gas & Transit", "Gas Stations", 2), r("Everything Else", "Everything Else", 1)],
    benefits: [
      b("hawaiian-companion", "2 Free Checked Bags & $100 Annual Companion Discount", null, "ANNUAL", "CARDMEMBER_YEAR", "2 free checked bags + $100 annual companion ticket discount")
    ]
  },
  {
    id: "barclays_jetblue", bonusApiCardId: "d760441e47fada8604f084285fbaeb39", sortRank: 250,
    shortLabel: "JetBlue", cardName: "JetBlue", issuerName: "Barclays", network: "Mastercard",
    artKey: "hex://#0C4A6E:#0284C7:#FFFFFF", upstreamImagePath: "/images/barclays/jetblue.png",
    annualFeeCents: 0, isAnnualFeeWaived: false, isBusinessCard: false, countsToward524Override: null, subCooldownMonths: 48,
    defaultSubRewardText: "10,000 TrueBlue pts", defaultSubEstimatedValueCents: 12000, defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90, isElevatedSubOffer: false,
    rewardCategories: [r("Travel & Flights", "JetBlue Purchases", 3), r("Dining & Restaurants", "Restaurants", 2), r("Groceries", "Eligible Grocery Stores", 2), r("Everything Else", "Everything Else", 1)],
    benefits: []
  }
];
