// =============================================================================
// creditCardCatalogExtendedSeed.js - Extended US credit card catalog barrel.
// =============================================================================

import { EXTENDED_CARD_CATALOG_A } from './creditCardCatalogExtendedSeedA.js';
import { EXTENDED_CARD_CATALOG_B } from './creditCardCatalogExtendedSeedB.js';
import { EXTENDED_CARD_CATALOG_C } from './creditCardCatalogExtendedSeedC.js';

export const EXTENDED_CARD_CATALOG = [
  ...EXTENDED_CARD_CATALOG_A,
  ...EXTENDED_CARD_CATALOG_B,
  ...EXTENDED_CARD_CATALOG_C
];
