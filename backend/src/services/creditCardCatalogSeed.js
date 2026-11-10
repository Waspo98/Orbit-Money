// =============================================================================
// creditCardCatalogSeed.js - Curated US credit card catalog seed data.
// =============================================================================

const b = (id, title, cents, cadence = 'ANNUAL', anchor = 'CALENDAR', description = '') => ({
  id, title, period_value: cents / 100, period_value_cents: cents, cadence, anchor, description
});
const r = (category, label, multiplier, extra = {}) => ({ category, label, multiplier, ...extra });

export const CURATED_CARD_CATALOG = [
  // --- CHASE -----------------------------------------------------------------
  {
    id: 'csr', bonusApiCardId: '77e3ef2fc9c35498ba1a3f46a63235cb', sortRank: 10,
    shortLabel: 'Sapphire Reserve', cardName: 'Sapphire Reserve', issuerName: 'Chase', network: 'Visa',
    artKey: 'preset://sapphire-blue', upstreamImagePath: '/images/chase/sapphire-reserve.png',
    annualFeeCents: 55000, subCooldownMonths: 48,
    defaultSubRewardText: '60,000 Ultimate Rewards pts', defaultSubEstimatedValueCents: 90000,
    defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Travel & Dining', 3.0), r('Dining & Restaurants', 'Dining', 3.0), r('Everything Else', 'Everything Else', 1.0)],
    benefits: [
      b('csr-travel-credit', '$300 Annual Travel Credit', 30000, 'ANNUAL', 'CARDMEMBER_YEAR', '$300 annual reimbursement for travel purchases each cardmember year'),
      b('csr-doordash', 'DashPass & $5/mo DoorDash Promo', 500, 'MONTHLY', 'CALENDAR', 'Complimentary DashPass & $5 monthly DoorDash in-app credit'),
      b('csr-global-entry', 'Global Entry / TSA PreCheck Credit', 12000, 'EVERY_4_YEARS', 'CALENDAR', 'Up to $120 statement credit every 4 years for application fee'),
      { id: 'csr-priority-pass', title: 'Priority Pass Select Lounge Access', period_value: null, period_value_cents: null, cadence: 'ANNUAL', anchor: 'CALENDAR', description: 'Complimentary airport lounge access for cardmember and up to 2 guests' }
    ]
  },
  {
    id: 'csp', bonusApiCardId: '57f8a819c3bb4c6aa494016703281f35', sortRank: 11,
    shortLabel: 'Sapphire Preferred', cardName: 'Sapphire Preferred', issuerName: 'Chase', network: 'Visa',
    artKey: 'preset://sapphire-blue', upstreamImagePath: '/images/chase/sapphire-preferred.png',
    annualFeeCents: 9500, subCooldownMonths: 48,
    defaultSubRewardText: '60,000 Ultimate Rewards pts', defaultSubEstimatedValueCents: 90000,
    defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90,
    rewardCategories: [r('Dining & Restaurants', 'Dining', 3.0), r('Streaming & Entertainment', 'Select Streaming', 3.0), r('Groceries', 'Online Groceries', 3.0), r('Travel & Flights', 'Travel', 2.0)],
    benefits: [
      b('csp-hotel-credit', '$50 Annual Chase Travel Hotel Credit', 5000, 'ANNUAL', 'CARDMEMBER_YEAR', '$50 annual statement credit for hotels booked through Chase Travel'),
      b('csp-doordash', '$10/mo DoorDash Non-Restaurant Credit', 1000, 'MONTHLY', 'CALENDAR', '$10 monthly DoorDash credit for grocery/convenience orders')
    ]
  },
  {
    id: 'cfu', bonusApiCardId: '3a9ea798ce73485fc8456280fd4b1816', sortRank: 12,
    shortLabel: 'Freedom Unlimited', cardName: 'Freedom Unlimited', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#13315C:#2563EB:#FFFFFF', upstreamImagePath: '/images/chase/freedom-unlimited.png',
    annualFeeCents: 0, subCooldownMonths: 24,
    defaultSubRewardText: '$200 / 20,000 UR Bonus', defaultSubEstimatedValueCents: 30000,
    defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90,
    rewardCategories: [r('Dining & Restaurants', 'Dining', 3.0), r('Drugstores', 'Drugstores', 3.0), r('Everything Else', 'Everything Else (Catch-All)', 1.5)],
    benefits: []
  },
  {
    id: 'cff', bonusApiCardId: '6ba7f561f6a4f7f2934bec25bd9a3130', sortRank: 13,
    shortLabel: 'Freedom Flex', cardName: 'Freedom Flex', issuerName: 'Chase', network: 'Mastercard',
    artKey: 'hex://#102A56:#1D4ED8:#FFFFFF', upstreamImagePath: '/images/chase/freedom-flex.png',
    annualFeeCents: 0, subCooldownMonths: 24,
    defaultSubRewardText: '$200 / 20,000 UR Bonus', defaultSubEstimatedValueCents: 30000,
    defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90,
    rewardCategories: [r('Quarterly Bonus', '5x Rotating Quarterly ($1,500 cap)', 5.0, { isRotating: true, capCents: 150000 }), r('Dining & Restaurants', 'Dining', 3.0), r('Drugstores', 'Drugstores', 3.0)],
    benefits: []
  },
  {
    id: 'ink_cash', bonusApiCardId: '8ec7f405d03afa62eaeea7e197a29e8a', sortRank: 14,
    shortLabel: 'Ink Business Cash', cardName: 'Ink Business Cash', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#0F172A:#1E3A8A:#FFFFFF', upstreamImagePath: '/images/chase/ink-business-cash.png',
    annualFeeCents: 0, isBusinessCard: true, subCooldownMonths: 24,
    defaultSubRewardText: '75,000 Ultimate Rewards pts', defaultSubEstimatedValueCents: 112500,
    defaultSubTargetSpendCents: 600000, subSpendWindowDays: 180,
    rewardCategories: [r('Office & Internet', 'Office Supplies, Internet & Phone', 5.0), r('Gas & Transit', 'Gas & Restaurants', 2.0)],
    benefits: []
  },
  {
    id: 'ink_unlimited', bonusApiCardId: '4a1c1e46f145b76fcc72ebd391fc5766', sortRank: 15,
    shortLabel: 'Ink Unlimited', cardName: 'Ink Business Unlimited', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#1E293B:#334155:#FFFFFF', upstreamImagePath: '/images/chase/ink-business-unlimited.png',
    annualFeeCents: 0, isBusinessCard: true, subCooldownMonths: 24,
    defaultSubRewardText: '75,000 Ultimate Rewards pts', defaultSubEstimatedValueCents: 112500,
    defaultSubTargetSpendCents: 600000, subSpendWindowDays: 90,
    rewardCategories: [r('Everything Else', 'Everything Else (Catch-All)', 1.5)],
    benefits: []
  },
  {
    id: 'ink_preferred', bonusApiCardId: 'c80cdb77c3eda940e2a36abb4f943c69', sortRank: 16,
    shortLabel: 'Ink Preferred', cardName: 'Ink Business Preferred', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#0B192C:#1E3E62:#FFFFFF', upstreamImagePath: '/images/chase/ink-business-preferred.png',
    annualFeeCents: 9500, isBusinessCard: true, subCooldownMonths: 24,
    defaultSubRewardText: '90,000 Ultimate Rewards pts', defaultSubEstimatedValueCents: 135000,
    defaultSubTargetSpendCents: 800000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Travel, Shipping, Internet & Ads', 3.0), r('Everything Else', 'Everything Else', 1.0)],
    benefits: []
  },
  {
    id: 'chase_amazon_prime', bonusApiCardId: 'f4d8c429f58f9f74944a9108bcae4a22', sortRank: 17,
    shortLabel: 'Prime Visa', cardName: 'Amazon Prime Visa', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#1E293B:#0F172A:#FFFFFF', upstreamImagePath: '/images/chase/amazon-prime.png',
    annualFeeCents: 0, subCooldownMonths: 24,
    defaultSubRewardText: '$200 Amazon Gift Card Instantly', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 0, subSpendWindowDays: 30,
    rewardCategories: [r('Shopping', 'Amazon.com, Whole Foods & Chase Travel', 5.0), r('Dining & Restaurants', 'Restaurants & Gas Stations', 2.0), r('Everything Else', 'Everything Else', 1.0)],
    benefits: []
  },
  {
    id: 'chase_hyatt', bonusApiCardId: 'd378a38b56d4c9bd5e1ad184a6ce84f0', sortRank: 18,
    shortLabel: 'World of Hyatt', cardName: 'World of Hyatt', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#111827:#1E3A8A:#FFFFFF', upstreamImagePath: '/images/chase/world-of-hyatt.png',
    annualFeeCents: 9500, subCooldownMonths: 24,
    defaultSubRewardText: '30,000 Hyatt Bonus Points', defaultSubEstimatedValueCents: 54000,
    defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Hyatt Hotels & Resorts', 4.0), r('Dining & Restaurants', 'Dining, Flights & Gym', 2.0)],
    benefits: [b('hyatt-free-night', 'Category 1-4 Free Night Award', 18000, 'ANNUAL', 'CARDMEMBER_YEAR', '1 free night at any Category 1-4 Hyatt hotel each anniversary year')]
  },
  {
    id: 'chase_united_explorer', bonusApiCardId: '85cd2b7d172e494eefad5b2ab012f21c', sortRank: 19,
    shortLabel: 'United Explorer', cardName: 'United Explorer', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#0C2340:#1D4F91:#FFFFFF', upstreamImagePath: '/images/chase/united-explorer.png',
    annualFeeCents: 15000, isAnnualFeeWaived: true, subCooldownMonths: 24,
    defaultSubRewardText: '60,000 United MileagePlus Miles', defaultSubEstimatedValueCents: 72000,
    defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'United Purchases, Dining & Hotels', 2.0), r('Everything Else', 'Everything Else', 1.0)],
    benefits: [
      b('united-exp-global-entry', 'Global Entry / TSA PreCheck Credit', 12000, 'EVERY_4_YEARS', 'CALENDAR', 'Up to $120 credit for Global Entry, TSA PreCheck, or NEXUS'),
      b('united-exp-club-passes', '2 United Club One-Time Passes', 11800, 'ANNUAL', 'CARDMEMBER_YEAR', 'Two complimentary United Club one-time passes each anniversary year')
    ]
  },
  {
    id: 'chase_united_quest', bonusApiCardId: '34ab99720941eaa2a1671e5b3a996b19', sortRank: 20,
    shortLabel: 'United Quest', cardName: 'United Quest', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#111827:#1E3A8A:#FFFFFF', upstreamImagePath: '/images/chase/united-quest.png',
    annualFeeCents: 35000, subCooldownMonths: 24,
    defaultSubRewardText: '70,000 United MileagePlus Miles', defaultSubEstimatedValueCents: 84000,
    defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'United Purchases', 3.0), r('Dining & Restaurants', 'Dining & Select Streaming', 2.0)],
    benefits: [
      b('united-quest-credit', '$125 Annual United Purchase Credit', 12500, 'ANNUAL', 'CARDMEMBER_YEAR', 'Up to $125 in statement credits for United purchases each cardmember year'),
      b('united-quest-global-entry', 'Global Entry / TSA PreCheck Credit', 12000, 'EVERY_4_YEARS', 'CALENDAR', 'Up to $120 statement credit every 4 years')
    ]
  },
  {
    id: 'chase_marriott_boundless', bonusApiCardId: '6a4a30b866d4d37216b9d586a15ad1cc', sortRank: 21,
    shortLabel: 'Marriott Boundless', cardName: 'Marriott Bonvoy Boundless', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#4A1525:#7F1D1D:#FFFFFF', upstreamImagePath: '/images/chase/marriott-bonvoy-boundless.jpeg',
    annualFeeCents: 9500, subCooldownMonths: 24,
    defaultSubRewardText: '3 Free Night Awards (up to 150k pts)', defaultSubEstimatedValueCents: 60000,
    defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Marriott Bonvoy Hotels', 6.0), r('Groceries', 'Grocery, Gas & Dining ($6k cap)', 3.0, { capCents: 600000 }), r('Everything Else', 'Everything Else', 2.0)],
    benefits: [b('boundless-free-night', '35,000-Pt Annual Free Night Award', 17500, 'ANNUAL', 'CARDMEMBER_YEAR', 'Free Night Award worth up to 35,000 points every cardmember anniversary')]
  },
  {
    id: 'chase_ihg_premier', bonusApiCardId: '40192e08e31f059d91a108ff09ec5dd5', sortRank: 22,
    shortLabel: 'IHG One Premier', cardName: 'IHG One Rewards Premier', issuerName: 'Chase', network: 'Mastercard',
    artKey: 'hex://#1F2937:#0F766E:#FFFFFF', upstreamImagePath: '/images/chase/ihg-rewards-premier.jpg',
    annualFeeCents: 9900, subCooldownMonths: 24,
    defaultSubRewardText: '140,000 IHG One Rewards Points', defaultSubEstimatedValueCents: 70000,
    defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'IHG Hotels & Resorts', 10.0), r('Dining & Restaurants', 'Travel, Gas & Dining', 5.0), r('Everything Else', 'Everything Else', 3.0)],
    benefits: [
      b('ihg-free-night', '40,000-Pt Anniversary Free Night', 16000, 'ANNUAL', 'CARDMEMBER_YEAR', 'Anniversary Free Night valid up to 40,000 points'),
      b('ihg-united-travelbank', '$50 Annual United TravelBank Cash', 2500, 'SEMI_ANNUAL', 'CALENDAR', '$25 United TravelBank Cash deposited every Jan and Jul ($50/yr)'),
      b('ihg-global-entry', 'Global Entry / TSA PreCheck Credit', 12000, 'EVERY_4_YEARS', 'CALENDAR', 'Up to $120 fee credit every 4 years')
    ]
  },
  {
    id: 'chase_sw_priority', bonusApiCardId: '169256a9988f3161381ee6a9ebe6667e', sortRank: 23,
    shortLabel: 'Southwest Priority', cardName: 'Southwest Rapid Rewards Priority', issuerName: 'Chase', network: 'Visa',
    artKey: 'hex://#1E3A8A:#B91C1C:#FFFFFF', upstreamImagePath: '/images/chase/southwest-rapid-rewards-priority.png',
    annualFeeCents: 22900, subCooldownMonths: 24,
    defaultSubRewardText: '50,000 Southwest Rapid Rewards pts', defaultSubEstimatedValueCents: 65000,
    defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Southwest Purchases', 3.0), r('Gas & Transit', 'Local Transit & Commuting', 2.0)],
    benefits: [
      b('sw-priority-credit', '$75 Southwest Annual Travel Credit', 7500, 'ANNUAL', 'CARDMEMBER_YEAR', '$75 Southwest travel credit each anniversary year'),
      b('sw-priority-anniv', '7,500 Anniversary Rapid Rewards Pts', 9500, 'ANNUAL', 'CARDMEMBER_YEAR', '7,500 bonus points each cardmember anniversary')
    ]
  },

  // --- AMERICAN EXPRESS ------------------------------------------------------
  {
    id: 'amex_gold', bonusApiCardId: 'cafe43d37256bec116dff4be6cced2cf', sortRank: 30,
    shortLabel: 'Amex Gold', cardName: 'American Express Gold', issuerName: 'American Express', network: 'Amex',
    artKey: 'preset://amex-gold', upstreamImagePath: '/images/amex/gold.webp',
    annualFeeCents: 32500, subCooldownMonths: 999,
    defaultSubRewardText: '90,000 Membership Rewards pts', defaultSubEstimatedValueCents: 135000,
    defaultSubTargetSpendCents: 600000, subSpendWindowDays: 180,
    rewardCategories: [r('Dining & Restaurants', 'Restaurants Worldwide', 4.0), r('Groceries', 'U.S. Supermarkets ($25k cap)', 4.0, { capCents: 2500000 }), r('Travel & Flights', 'Flights Booked Direct', 3.0)],
    benefits: [
      b('amex-gold-uber-cash', '$10/mo Uber Cash ($120/yr)', 1000, 'MONTHLY', 'CALENDAR', '$10 monthly Uber Cash for US rides and eats'),
      b('amex-gold-dining-credit', '$10/mo Dining Credit ($120/yr)', 1000, 'MONTHLY', 'CALENDAR', '$10 monthly dining credit at Grubhub, Cheesecake Factory, etc.'),
      b('amex-gold-resy-credit', '$50 Semi-Annual Resy Credit ($100/yr)', 5000, 'SEMI_ANNUAL', 'CALENDAR', 'Up to $50 semi-annually (Jan-Jun, Jul-Dec) for purchases at US Resy restaurants'),
      b('amex-gold-dunkin-credit', "$7/mo Dunkin' Credit ($84/yr)", 700, 'MONTHLY', 'CALENDAR', "Up to $7 monthly statement credit at US Dunkin' locations")
    ]
  },
  {
    id: 'amex_plat', bonusApiCardId: 'ec1872dfd8a65491a33bae2da4dd487f', sortRank: 31,
    shortLabel: 'Amex Platinum', cardName: 'The Platinum Card', issuerName: 'American Express', network: 'Amex',
    artKey: 'preset://venture-silver', upstreamImagePath: '/images/amex/platinum.webp',
    annualFeeCents: 69500, subCooldownMonths: 999,
    defaultSubRewardText: '125,000 Membership Rewards pts', defaultSubEstimatedValueCents: 187500,
    defaultSubTargetSpendCents: 800000, subSpendWindowDays: 180,
    rewardCategories: [r('Travel & Flights', 'Flights & Prepaid Hotels', 5.0), r('Everything Else', 'Everything Else', 1.0)],
    benefits: [
      b('amex-plat-uber-cash', 'Uber Cash ($15/mo + $20 Dec)', 1500, 'MONTHLY', 'CALENDAR', '+$20 bonus in December ($200 total annual value)'),
      b('amex-plat-digital-entertainment', '$20/mo Digital Entertainment Credit', 2000, 'MONTHLY', 'CALENDAR', 'Up to $20/mo statement credit for Disney+, Hulu, ESPN+, Peacock, NYT, WSJ'),
      b('amex-plat-hotel-credit', '$200 Hotel Credit (FHR / THC)', 20000, 'ANNUAL', 'CALENDAR', '$200 annual statement credit for prepaid Fine Hotels + Resorts or The Hotel Collection'),
      b('amex-plat-airline-fee', '$200 Airline Incidental Fee Credit', 20000, 'ANNUAL', 'CALENDAR', '$200 annual statement credit for incidental fees with one selected qualifying airline'),
      b('amex-plat-saks-credit', '$50 Semi-Annual Saks Credit ($100/yr)', 5000, 'SEMI_ANNUAL', 'CALENDAR', 'Up to $50 statement credit semi-annually (Jan-Jun, Jul-Dec) at Saks Fifth Avenue'),
      b('amex-plat-clear-plus', '$199 CLEAR Plus Credit', 19900, 'ANNUAL', 'CALENDAR', 'Up to $199 per calendar year statement credit for CLEAR Plus membership'),
      b('amex-plat-walmart-plus', '$12.95/mo Walmart+ Membership Credit', 1295, 'MONTHLY', 'CALENDAR', 'Monthly statement credit for Walmart+ membership')
    ]
  },
  {
    id: 'amex_green', bonusApiCardId: 'ad9a96ed5f730a79d0171f1f8ca92d0c', sortRank: 32,
    shortLabel: 'Amex Green', cardName: 'American Express Green', issuerName: 'American Express', network: 'Amex',
    artKey: 'hex://#065F46:#10B981:#FFFFFF', upstreamImagePath: '/images/amex/green.webp',
    annualFeeCents: 15000, subCooldownMonths: 999,
    defaultSubRewardText: '40,000 Membership Rewards pts', defaultSubEstimatedValueCents: 60000,
    defaultSubTargetSpendCents: 300000, subSpendWindowDays: 180,
    rewardCategories: [r('Travel & Flights', 'Travel & Transit', 3.0), r('Dining & Restaurants', 'Restaurants Worldwide', 3.0)],
    benefits: [b('amex-green-clear', '$199 CLEAR Plus Credit', 19900, 'ANNUAL', 'CALENDAR', 'Up to $199 annual statement credit for CLEAR Plus membership')]
  },
  {
    id: 'amex_bcp', bonusApiCardId: '04abf9273e03fbe626fe80a9ffd53523', sortRank: 33,
    shortLabel: 'Blue Cash Preferred', cardName: 'Blue Cash Preferred', issuerName: 'American Express', network: 'Amex',
    artKey: 'hex://#0F4C81:#38BDF8:#FFFFFF', upstreamImagePath: '/images/amex/blue-cash-preferred.webp',
    annualFeeCents: 9500, isAnnualFeeWaived: true, subCooldownMonths: 999,
    defaultSubRewardText: '$250 Statement Credit', defaultSubEstimatedValueCents: 25000,
    defaultSubTargetSpendCents: 300000, subSpendWindowDays: 180,
    rewardCategories: [r('Groceries', 'U.S. Supermarkets ($6k cap)', 6.0, { capCents: 600000 }), r('Streaming & Entertainment', 'Select U.S. Streaming', 6.0), r('Gas & Transit', 'U.S. Gas Stations & Transit', 3.0)],
    benefits: [b('amex-bcp-disney', '$7/mo Disney Bundle Credit ($84/yr)', 700, 'MONTHLY', 'CALENDAR', '$7 monthly statement credit on eligible Disney Bundle subscriptions')]
  },
  {
    id: 'amex_bce', bonusApiCardId: '6980c320c341292b63273e4362601aa8', sortRank: 34,
    shortLabel: 'Blue Cash Everyday', cardName: 'Blue Cash Everyday', issuerName: 'American Express', network: 'Amex',
    artKey: 'hex://#1E40AF:#60A5FA:#FFFFFF', upstreamImagePath: '/images/amex/blue-cash-everyday.webp',
    annualFeeCents: 0, subCooldownMonths: 999,
    defaultSubRewardText: '$200 Statement Credit', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 200000, subSpendWindowDays: 180,
    rewardCategories: [r('Groceries', 'U.S. Supermarkets ($6k cap)', 3.0, { capCents: 600000 }), r('Shopping', 'U.S. Online Retail ($6k cap)', 3.0, { capCents: 600000 }), r('Gas & Transit', 'U.S. Gas Stations ($6k cap)', 3.0, { capCents: 600000 })],
    benefits: [
      b('amex-bce-disney', '$7/mo Disney Bundle Credit', 700, 'MONTHLY', 'CALENDAR', '$7 monthly credit for eligible Disney Bundle subscriptions'),
      b('amex-bce-homechef', '$15/mo Home Chef Credit', 1500, 'MONTHLY', 'CALENDAR', 'Up to $15/mo statement credit on Home Chef meal kits')
    ]
  },
  {
    id: 'amex_bbp', bonusApiCardId: 'a851f08b98f839a01a7e8fa367d8b8be', sortRank: 35,
    shortLabel: 'Blue Business Plus', cardName: 'Blue Business Plus', issuerName: 'American Express', network: 'Amex',
    artKey: 'hex://#1E3A8A:#3B82F6:#FFFFFF', upstreamImagePath: '/images/amex/blue-business-plus.webp',
    annualFeeCents: 0, isBusinessCard: true, subCooldownMonths: 999,
    defaultSubRewardText: '15,000 Membership Rewards pts', defaultSubEstimatedValueCents: 22500,
    defaultSubTargetSpendCents: 300000, subSpendWindowDays: 90,
    rewardCategories: [r('Everything Else', 'Every Purchase ($50k annual cap)', 2.0, { capCents: 5000000 })],
    benefits: []
  },
  {
    id: 'amex_biz_gold', bonusApiCardId: '191f603ca5af9306d5db0fb208e9a646', sortRank: 36,
    shortLabel: 'Amex Business Gold', cardName: 'American Express Business Gold', issuerName: 'American Express', network: 'Amex',
    artKey: 'preset://amex-gold', upstreamImagePath: '/images/amex/business-gold.webp',
    annualFeeCents: 37500, isBusinessCard: true, subCooldownMonths: 999,
    defaultSubRewardText: '100,000 Membership Rewards pts', defaultSubEstimatedValueCents: 150000,
    defaultSubTargetSpendCents: 1500000, subSpendWindowDays: 90,
    rewardCategories: [r('Office & Internet', 'Top 2 Business Categories ($150k cap)', 4.0, { capCents: 15000000 }), r('Travel & Flights', 'Amex Travel Purchases', 3.0)],
    benefits: [
      b('amex-biz-gold-flex', '$20/mo Flexible Business Credit ($240/yr)', 2000, 'MONTHLY', 'CALENDAR', 'Up to $20/mo at FedEx, Grubhub, and Office Supply Stores'),
      b('amex-biz-gold-walmart', '$12.95/mo Walmart+ Credit', 1295, 'MONTHLY', 'CALENDAR', 'Monthly statement credit for Walmart+ membership')
    ]
  },
  {
    id: 'amex_biz_plat', bonusApiCardId: '656a6a4296d40de4c62cc3ec888e5018', sortRank: 37,
    shortLabel: 'Amex Business Platinum', cardName: 'The Business Platinum Card', issuerName: 'American Express', network: 'Amex',
    artKey: 'preset://venture-silver', upstreamImagePath: '/images/amex/business-platinum.webp',
    annualFeeCents: 69500, isBusinessCard: true, subCooldownMonths: 999,
    defaultSubRewardText: '150,000 Membership Rewards pts', defaultSubEstimatedValueCents: 225000,
    defaultSubTargetSpendCents: 2000000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Flights & Prepaid Hotels on Amex Travel', 5.0), r('Shopping', 'Purchases $5,000+ & Construction/Tech', 1.5)],
    benefits: [
      b('amex-biz-plat-dell', '$200 Semi-Annual Dell Credit ($400/yr)', 20000, 'SEMI_ANNUAL', 'CALENDAR', 'Up to $200 Jan-Jun and $200 Jul-Dec on U.S. Dell purchases'),
      b('amex-biz-plat-wireless', '$10/mo Wireless Telephone Credit ($120/yr)', 1000, 'MONTHLY', 'CALENDAR', '$10 monthly credit for U.S. wireless phone service'),
      b('amex-biz-plat-airline', '$200 Airline Incidental Fee Credit', 20000, 'ANNUAL', 'CALENDAR', 'Up to $200/yr for incidental fees on one selected airline'),
      b('amex-biz-plat-clear', '$199 CLEAR Plus Credit', 19900, 'ANNUAL', 'CALENDAR', 'Up to $199 per calendar year for CLEAR Plus')
    ]
  },
  {
    id: 'amex_hilton_aspire', bonusApiCardId: '59c1e4c423d078c91e9b7c51d72e893d', sortRank: 38,
    shortLabel: 'Hilton Aspire', cardName: 'Hilton Honors Aspire', issuerName: 'American Express', network: 'Amex',
    artKey: 'hex://#0F172A:#1E3A8A:#FFFFFF', upstreamImagePath: '/images/amex/hilton-honors-aspire.png',
    annualFeeCents: 55000, subCooldownMonths: 999,
    defaultSubRewardText: '150,000 Hilton Honors Bonus Points', defaultSubEstimatedValueCents: 75000,
    defaultSubTargetSpendCents: 600000, subSpendWindowDays: 180,
    rewardCategories: [r('Travel & Flights', 'Hilton Hotels & Resorts', 14.0), r('Dining & Restaurants', 'Flights, Car Rentals & U.S. Restaurants', 7.0), r('Everything Else', 'Everything Else', 3.0)],
    benefits: [
      b('aspire-resort-credit', '$200 Semi-Annual Hilton Resort Credit ($400/yr)', 20000, 'SEMI_ANNUAL', 'CALENDAR', 'Up to $200 Jan-Jun and $200 Jul-Dec at participating Hilton Resorts'),
      b('aspire-flight-credit', '$50 Quarterly Flight Credit ($200/yr)', 5000, 'QUARTERLY', 'CALENDAR', 'Up to $50 per quarter on flights booked direct or via Amex Travel'),
      b('aspire-free-night', 'Annual Hilton Free Night Reward', 35000, 'ANNUAL', 'CARDMEMBER_YEAR', '1 Free Night Reward every year with card renewal'),
      b('aspire-clear-plus', '$199 CLEAR Plus Credit', 19900, 'ANNUAL', 'CALENDAR', 'Up to $199/yr statement credit for CLEAR Plus')
    ]
  },
  {
    id: 'amex_marriott_brilliant', bonusApiCardId: '4d2115812f09d194440ef99b1a5210cd', sortRank: 39,
    shortLabel: 'Marriott Brilliant', cardName: 'Marriott Bonvoy Brilliant', issuerName: 'American Express', network: 'Amex',
    artKey: 'hex://#18181B:#3F3F46:#FFFFFF', upstreamImagePath: '/images/amex/marriott-bonvoy-brilliant.webp',
    annualFeeCents: 65000, subCooldownMonths: 999,
    defaultSubRewardText: '95,000 Marriott Bonvoy Points', defaultSubEstimatedValueCents: 76000,
    defaultSubTargetSpendCents: 600000, subSpendWindowDays: 180,
    rewardCategories: [r('Travel & Flights', 'Marriott Bonvoy Hotels', 6.0), r('Dining & Restaurants', 'Restaurants Worldwide & Direct Flights', 3.0), r('Everything Else', 'Everything Else', 2.0)],
    benefits: [
      b('brilliant-dining', '$25/mo Worldwide Dining Credit ($300/yr)', 2500, 'MONTHLY', 'CALENDAR', 'Up to $25 monthly statement credit at restaurants worldwide'),
      b('brilliant-free-night', '85,000-Pt Annual Free Night Award', 42500, 'ANNUAL', 'CARDMEMBER_YEAR', '1 Free Night Award worth up to 85,000 points every renewal year')
    ]
  },
  {
    id: 'amex_delta_gold', bonusApiCardId: '99d4ec3c5bcd51b70b83b4b792af3ef6', sortRank: 40,
    shortLabel: 'Delta SkyMiles Gold', cardName: 'Delta SkyMiles Gold', issuerName: 'American Express', network: 'Amex',
    artKey: 'hex://#78531A:#B8860B:#FFFFFF', upstreamImagePath: '/images/amex/delta-skymiles-gold.jpg',
    annualFeeCents: 15000, isAnnualFeeWaived: true, subCooldownMonths: 999,
    defaultSubRewardText: '50,000 Delta SkyMiles', defaultSubEstimatedValueCents: 60000,
    defaultSubTargetSpendCents: 200000, subSpendWindowDays: 180,
    rewardCategories: [r('Travel & Flights', 'Delta Purchases', 2.0), r('Dining & Restaurants', 'Restaurants & U.S. Supermarkets', 2.0)],
    benefits: [b('delta-gold-stays', '$100 Annual Delta Stays Credit', 10000, 'ANNUAL', 'CALENDAR', 'Up to $100 annual credit for prepaid hotels/rentals on Delta Stays')]
  },
  {
    id: 'amex_delta_reserve', bonusApiCardId: '2a3949c2232cf6646a59531728264a5c', sortRank: 41,
    shortLabel: 'Delta Reserve', cardName: 'Delta SkyMiles Reserve', issuerName: 'American Express', network: 'Amex',
    artKey: 'hex://#2E1065:#581C87:#FFFFFF', upstreamImagePath: '/images/amex/delta-skymiles-reserve.webp',
    annualFeeCents: 65000, subCooldownMonths: 999,
    defaultSubRewardText: '60,000 Delta SkyMiles', defaultSubEstimatedValueCents: 72000,
    defaultSubTargetSpendCents: 500000, subSpendWindowDays: 180,
    rewardCategories: [r('Travel & Flights', 'Delta Purchases', 3.0), r('Everything Else', 'Everything Else', 1.0)],
    benefits: [
      b('delta-res-resy', '$20/mo Resy Credit ($240/yr)', 2000, 'MONTHLY', 'CALENDAR', 'Up to $20 monthly statement credit at U.S. Resy restaurants'),
      b('delta-res-rideshare', '$10/mo Rideshare Credit ($120/yr)', 1000, 'MONTHLY', 'CALENDAR', 'Up to $10 monthly credit with Uber, Lyft, Curb, Revel, and Alto'),
      b('delta-res-stays', '$200 Annual Delta Stays Credit', 20000, 'ANNUAL', 'CALENDAR', 'Up to $200/yr statement credit on Delta Stays prepaid bookings')
    ]
  },

  // --- CAPITAL ONE -----------------------------------------------------------
  {
    id: 'venture_x', bonusApiCardId: '9aa036266b8cc7e7512a630efdecae0b', sortRank: 50,
    shortLabel: 'Venture X', cardName: 'Venture X', issuerName: 'Capital One', network: 'Visa',
    artKey: 'preset://venture-silver', upstreamImagePath: '/images/capitalone/venture-x.webp',
    annualFeeCents: 39500, subCooldownMonths: 48,
    defaultSubRewardText: '75,000 Capital One Miles', defaultSubEstimatedValueCents: 105000,
    defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Portal Hotels & Rental Cars', 10.0), r('Everything Else', 'Everything Else (Catch-All)', 2.0)],
    benefits: [
      b('venture-x-travel-credit', '$300 Annual Capital One Travel Credit', 30000, 'ANNUAL', 'CARDMEMBER_YEAR', '$300 annual credit for bookings through Capital One Travel each cardmember year'),
      b('venture-x-anniversary-miles', '10,000 Anniversary Bonus Miles ($100 val)', 10000, 'ANNUAL', 'CARDMEMBER_YEAR', '10,000 bonus miles starting on first anniversary year (equal to $100 towards travel)'),
      b('venture-x-global-entry', 'Global Entry / TSA PreCheck Credit', 12000, 'EVERY_4_YEARS', 'CALENDAR', 'Up to $120 statement credit for Global Entry or TSA PreCheck application fee every 4 years')
    ]
  },
  {
    id: 'venture_rewards', bonusApiCardId: '4a7a8036e514f187ca7c41b31925251e', sortRank: 51,
    shortLabel: 'Venture Rewards', cardName: 'Venture Rewards', issuerName: 'Capital One', network: 'Visa',
    artKey: 'hex://#1E3A8A:#2563EB:#FFFFFF', upstreamImagePath: '/images/capitalone/venture-rewards.webp',
    annualFeeCents: 9500, subCooldownMonths: 48,
    defaultSubRewardText: '75,000 Capital One Miles', defaultSubEstimatedValueCents: 105000,
    defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Capital One Travel Hotels & Rental Cars', 5.0), r('Everything Else', 'Everything Else (Catch-All)', 2.0)],
    benefits: [b('venture-global-entry', 'Global Entry / TSA PreCheck Credit', 12000, 'EVERY_4_YEARS', 'CALENDAR', 'Up to $120 statement credit every 4 years')]
  },
  {
    id: 'capital_one_savor', bonusApiCardId: '2d16431e0caee645b420cc3d3255b2ee', sortRank: 52,
    shortLabel: 'Capital One Savor', cardName: 'Capital One Savor', issuerName: 'Capital One', network: 'Visa',
    artKey: 'hex://#9A3412:#EA580C:#FFFFFF', upstreamImagePath: '/images/capitalone/savor.webp',
    annualFeeCents: 0, subCooldownMonths: 48,
    defaultSubRewardText: '$200 Cash Bonus', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90,
    rewardCategories: [r('Dining & Restaurants', 'Dining', 3.0), r('Groceries', 'Grocery Stores', 3.0), r('Streaming & Entertainment', 'Entertainment & Popular Streaming', 3.0), r('Everything Else', 'Everything Else', 1.0)],
    benefits: []
  },
  {
    id: 'capital_one_quicksilver', bonusApiCardId: '66f7413248c7f2c61ce1e8008685eac2', sortRank: 53,
    shortLabel: 'Quicksilver', cardName: 'Capital One Quicksilver', issuerName: 'Capital One', network: 'Visa',
    artKey: 'hex://#334155:#64748B:#FFFFFF', upstreamImagePath: '/images/capitalone/quicksilver.webp',
    annualFeeCents: 0, subCooldownMonths: 48,
    defaultSubRewardText: '$200 Cash Bonus', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90,
    rewardCategories: [r('Everything Else', 'Everything Else (Catch-All)', 1.5)],
    benefits: []
  },
  {
    id: 'venture_x_biz', bonusApiCardId: '1d2ff07648fdd4b06d89ef0309f1c4cb', sortRank: 54,
    shortLabel: 'Venture X Business', cardName: 'Venture X Business', issuerName: 'Capital One', network: 'Visa',
    artKey: 'preset://venture-silver', upstreamImagePath: '/images/capitalone/venture-x-business.png',
    annualFeeCents: 39500, isBusinessCard: true, countsToward524Override: false, subCooldownMonths: 48,
    defaultSubRewardText: '150,000 Capital One Miles', defaultSubEstimatedValueCents: 210000,
    defaultSubTargetSpendCents: 3000000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Capital One Travel Hotels & Rental Cars', 10.0), r('Everything Else', 'Everything Else (Catch-All)', 2.0)],
    benefits: [
      b('vx-biz-travel-credit', '$300 Annual Capital One Travel Credit', 30000, 'ANNUAL', 'CARDMEMBER_YEAR', '$300 annual credit for bookings in Capital One Business Travel'),
      b('vx-biz-anniv-miles', '10,000 Anniversary Bonus Miles ($100 val)', 10000, 'ANNUAL', 'CARDMEMBER_YEAR', '10,000 bonus miles every anniversary year')
    ]
  },
  {
    id: 'spark_cash_select', bonusApiCardId: 'd4955a21df61a45ad86c3d60c58b91fc', sortRank: 55,
    shortLabel: 'Spark 1.5% Cash Select', cardName: 'Spark 1.5% Cash Select', issuerName: 'Capital One', network: 'Visa',
    artKey: 'hex://#1F2937:#059669:#FFFFFF', upstreamImagePath: '/images/capitalone/spark-cash.webp',
    annualFeeCents: 0, isBusinessCard: true, countsToward524Override: true, subCooldownMonths: 48,
    defaultSubRewardText: '$500 Cash Bonus', defaultSubEstimatedValueCents: 50000,
    defaultSubTargetSpendCents: 450000, subSpendWindowDays: 90,
    rewardCategories: [r('Everything Else', 'Every Business Purchase', 1.5)],
    benefits: []
  },

  // --- CITI / WELLS FARGO / BILT / BOFA / US BANK / DISCOVER / APPLE ---------
  {
    id: 'citi_strata_premier', bonusApiCardId: 'd7e2e5b5e595ca6510c101349c914513', sortRank: 60,
    shortLabel: 'Citi Strata Premier', cardName: 'Citi Strata Premier', issuerName: 'Citi', network: 'Mastercard',
    artKey: 'hex://#0F172A:#0D9488:#FFFFFF', upstreamImagePath: '/images/citi/strata-premier.png',
    annualFeeCents: 9500, subCooldownMonths: 48,
    defaultSubRewardText: '75,000 ThankYou Points', defaultSubEstimatedValueCents: 105000,
    defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'CitiTravel.com Hotels & Car Rentals', 10.0), r('Dining & Restaurants', 'Restaurants', 3.0), r('Groceries', 'Supermarkets', 3.0), r('Gas & Transit', 'Gas Stations & EV Charging', 3.0), r('Travel & Flights', 'Air Travel & Other Hotels', 3.0)],
    benefits: [b('strata-hotel-credit', '$100 Annual Hotel Benefit', 10000, 'ANNUAL', 'CALENDAR', '$100 off a single hotel stay of $500+ booked through CitiTravel.com each calendar year')]
  },
  {
    id: 'citi_double_cash', bonusApiCardId: '06b9a384d710e73db7f0ded7613c9c4b', sortRank: 61,
    shortLabel: 'Citi Double Cash', cardName: 'Citi Double Cash', issuerName: 'Citi', network: 'Mastercard',
    artKey: 'hex://#0369A1:#0D9488:#FFFFFF', upstreamImagePath: '/images/citi/double-cash.png',
    annualFeeCents: 0, subCooldownMonths: 48,
    defaultSubRewardText: '$200 / 20,000 ThankYou Pts', defaultSubEstimatedValueCents: 28000,
    defaultSubTargetSpendCents: 150000, subSpendWindowDays: 180,
    rewardCategories: [r('Everything Else', 'Everything Else (1% Buy + 1% Pay)', 2.0)],
    benefits: []
  },
  {
    id: 'citi_custom_cash', bonusApiCardId: '49fe6c73c0e823a89346979f50d5b981', sortRank: 62,
    shortLabel: 'Citi Custom Cash', cardName: 'Citi Custom Cash', issuerName: 'Citi', network: 'Mastercard',
    artKey: 'hex://#1E3A8A:#10B981:#FFFFFF', upstreamImagePath: '/images/citi/custom-cash.png',
    annualFeeCents: 0, subCooldownMonths: 48,
    defaultSubRewardText: '$200 / 20,000 ThankYou Pts', defaultSubEstimatedValueCents: 28000,
    defaultSubTargetSpendCents: 150000, subSpendWindowDays: 180,
    rewardCategories: [r('Groceries', 'Top Eligible Spend Category ($500/mo cap)', 5.0, { capCents: 50000 }), r('Everything Else', 'Everything Else', 1.0)],
    benefits: []
  },
  {
    id: 'bilt', bonusApiCardId: 'c077a47639c7b47d1895200c72588ee3', sortRank: 70,
    shortLabel: 'Bilt Mastercard', cardName: 'Bilt Mastercard', issuerName: 'Wells Fargo / Bilt', network: 'Mastercard',
    artKey: 'hex://#09090B:#27272A:#FFFFFF', upstreamImagePath: '/images/wellsfargo/bilt.png',
    annualFeeCents: 0, subCooldownMonths: null,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: null,
    rewardCategories: [r('Dining & Restaurants', 'Dining', 3.0), r('Travel & Flights', 'Travel', 2.0), r('Rent', 'Rent (No Transaction Fee)', 1.0)],
    benefits: []
  },
  {
    id: 'wf_autograph', bonusApiCardId: 'ce08e0bddd774433cb77be87f5d6af76', sortRank: 71,
    shortLabel: 'Wells Fargo Autograph', cardName: 'Wells Fargo Autograph', issuerName: 'Wells Fargo', network: 'Visa',
    artKey: 'hex://#7F1D1D:#B91C1C:#FFFFFF', upstreamImagePath: '/images/wellsfargo/autograph.png',
    annualFeeCents: 0, subCooldownMonths: 48,
    defaultSubRewardText: '20,000 Bonus Points ($200 val)', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90,
    rewardCategories: [r('Dining & Restaurants', 'Restaurants', 3.0), r('Travel & Flights', 'Travel & Transit', 3.0), r('Gas & Transit', 'Gas Stations & EV Charging', 3.0), r('Streaming & Entertainment', 'Popular Streaming & Phone Plans', 3.0)],
    benefits: []
  },
  {
    id: 'wf_autograph_journey', bonusApiCardId: 'd1fcd95962c94d1b56d691fda30acead', sortRank: 72,
    shortLabel: 'Autograph Journey', cardName: 'Wells Fargo Autograph Journey', issuerName: 'Wells Fargo', network: 'Visa',
    artKey: 'hex://#450A0A:#991B1B:#FFFFFF', upstreamImagePath: '/images/wellsfargo/autograph.png',
    annualFeeCents: 9500, subCooldownMonths: 48,
    defaultSubRewardText: '60,000 Bonus Points', defaultSubEstimatedValueCents: 60000,
    defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Hotels (5x) & Airlines (4x)', 5.0), r('Dining & Restaurants', 'Restaurants & Other Travel', 3.0)],
    benefits: [b('wf-journey-air', '$50 Annual Airfare Statement Credit', 5000, 'ANNUAL', 'CARDMEMBER_YEAR', '$50 annual statement credit with $50 minimum airline purchase')]
  },
  {
    id: 'wf_active_cash', bonusApiCardId: 'df2960b75b50536daa6e05a33479b198', sortRank: 73,
    shortLabel: 'Wells Fargo Active Cash', cardName: 'Wells Fargo Active Cash', issuerName: 'Wells Fargo', network: 'Visa',
    artKey: 'hex://#1E293B:#991B1B:#FFFFFF', upstreamImagePath: '/images/wellsfargo/active-cash.png',
    annualFeeCents: 0, subCooldownMonths: 48,
    defaultSubRewardText: '$200 Cash Rewards Bonus', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 50000, subSpendWindowDays: 90,
    rewardCategories: [r('Everything Else', 'Everything Else (Catch-All)', 2.0)],
    benefits: []
  },
  {
    id: 'bofa_customized_cash', bonusApiCardId: 'f2461735a56702c428c209486e2f9e55', sortRank: 80,
    shortLabel: 'BofA Customized Cash', cardName: 'Bank of America Customized Cash Rewards', issuerName: 'Bank of America', network: 'Visa',
    artKey: 'hex://#991B1B:#DC2626:#FFFFFF', upstreamImagePath: '/images/bankofamerica/customized-cash-rewards.jpg',
    annualFeeCents: 0, subCooldownMonths: 24,
    defaultSubRewardText: '$200 Online Cash Rewards Bonus', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90,
    rewardCategories: [r('Shopping', '3% Choice Category ($2,500/qtr cap)', 3.0, { capCents: 250000 }), r('Groceries', 'Grocery Stores & Wholesale Clubs', 2.0)],
    benefits: []
  },
  {
    id: 'bofa_premium_rewards', bonusApiCardId: 'eb8bc1a6f86dc471aa0deeca619fbf59', sortRank: 81,
    shortLabel: 'BofA Premium Rewards', cardName: 'Bank of America Premium Rewards', issuerName: 'Bank of America', network: 'Visa',
    artKey: 'hex://#18181B:#3F3F46:#FFFFFF', upstreamImagePath: '/images/bankofamerica/premium-rewards.png',
    annualFeeCents: 9500, subCooldownMonths: 24,
    defaultSubRewardText: '60,000 Points ($600 Value)', defaultSubEstimatedValueCents: 60000,
    defaultSubTargetSpendCents: 400000, subSpendWindowDays: 90,
    rewardCategories: [r('Travel & Flights', 'Travel & Dining', 2.0), r('Everything Else', 'Everything Else', 1.5)],
    benefits: [
      b('bofa-pr-airline', '$100 Annual Airline Incidental Credit', 10000, 'ANNUAL', 'CALENDAR', 'Up to $100 annual statement credit for qualifying airline incidental fees'),
      b('bofa-pr-ge', 'Global Entry / TSA PreCheck Credit', 10000, 'EVERY_4_YEARS', 'CALENDAR', 'Up to $100 credit every 4 years for TSA PreCheck or Global Entry')
    ]
  },
  {
    id: 'usbank_altitude_go', bonusApiCardId: 'b523a3cc7d6d355737b1c4dda1eb7a04', sortRank: 85,
    shortLabel: 'U.S. Bank Altitude Go', cardName: 'U.S. Bank Altitude Go', issuerName: 'U.S. Bank', network: 'Visa',
    artKey: 'hex://#1E3A8A:#B91C1C:#FFFFFF', upstreamImagePath: '/images/usbank/altitude-go.jpg',
    annualFeeCents: 0, subCooldownMonths: 48,
    defaultSubRewardText: '20,000 Bonus Points ($200 Value)', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90,
    rewardCategories: [r('Dining & Restaurants', 'Dining, Takeout & Restaurant Delivery', 4.0), r('Groceries', 'Grocery Stores, Gas & Streaming', 2.0)],
    benefits: [b('altitude-go-streaming', '$15 Annual Streaming Service Credit', 1500, 'ANNUAL', 'CARDMEMBER_YEAR', '$15 statement credit after 11 consecutive months of eligible streaming purchases')]
  },
  {
    id: 'usbank_cash_plus', bonusApiCardId: '78726438553c1f6b63383f3bac6df57b', sortRank: 86,
    shortLabel: 'U.S. Bank Cash+', cardName: 'U.S. Bank Cash+ Visa Signature', issuerName: 'U.S. Bank', network: 'Visa',
    artKey: 'hex://#0F2B5B:#1D4ED8:#FFFFFF', upstreamImagePath: '/images/usbank/cash-plus.jpg',
    annualFeeCents: 0, subCooldownMonths: 48,
    defaultSubRewardText: '$200 Rewards Bonus', defaultSubEstimatedValueCents: 20000,
    defaultSubTargetSpendCents: 100000, subSpendWindowDays: 90,
    rewardCategories: [r('Utilities', '5% Two Chosen Categories ($2,000/qtr cap)', 5.0, { isRotating: true, capCents: 200000 }), r('Groceries', '2% Everyday Category (Grocery, Gas, or Dining)', 2.0)],
    benefits: []
  },
  {
    id: 'discover_it', bonusApiCardId: 'dae75a5609dea8f39f750f4b225d2c5c', sortRank: 90,
    shortLabel: 'Discover it Cash Back', cardName: 'Discover it Cash Back', issuerName: 'Discover', network: 'Discover',
    artKey: 'preset://discover-plum', upstreamImagePath: '/images/discover/it.webp',
    annualFeeCents: 0, subCooldownMonths: 24,
    defaultSubRewardText: 'Unlimited Cashback Match (First Year)', defaultSubEstimatedValueCents: 15000,
    defaultSubTargetSpendCents: null, subSpendWindowDays: 365,
    rewardCategories: [r('Quarterly Bonus', '5% Rotating Quarterly Categories ($1,500 cap)', 5.0, { isRotating: true, capCents: 150000 }), r('Everything Else', 'Everything Else', 1.0)],
    benefits: []
  },
  {
    id: 'apple_card', bonusApiCardId: null, sortRank: 95,
    shortLabel: 'Apple Card', cardName: 'Apple Card', issuerName: 'Goldman Sachs / Apple', network: 'Mastercard',
    artKey: 'preset://apple-card', upstreamImagePath: null,
    annualFeeCents: 0, subCooldownMonths: null,
    defaultSubRewardText: null, defaultSubEstimatedValueCents: null, defaultSubTargetSpendCents: null, subSpendWindowDays: null,
    rewardCategories: [r('Shopping', 'Apple & Select Daily Cash Partners', 3.0), r('Everything Else', 'Every Apple Pay Purchase', 2.0)],
    benefits: []
  }
];
