package app.overbay.orbit.domain

import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.data.network.RewardCategoryWireDto
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreditCardCatalogAuditTest {

    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun tearDown() {
        CreditCardTemplates.resetForTesting()
    }

    @Test
    fun bundledCatalogContainsAll178VerifiedCardsWithStrictInvariants() {
        val bundled = CreditCardTemplates.BUNDLED
        assertEquals(178, bundled.size)
        assertEquals(178, bundled.map { it.id }.toSet().size)

        val validNetworks = setOf("Visa", "Mastercard", "Amex", "Discover")
        val seenBenefitIds = mutableSetOf<String>()

        for (card in bundled) {
            assertTrue(card.shortLabel.isNotBlank(), "Blank shortLabel on ${card.id}")
            assertTrue(card.cardName.isNotBlank(), "Blank cardName on ${card.id}")
            assertTrue(card.issuerName.isNotBlank(), "Blank issuerName on ${card.id}")
            assertTrue(card.network in validNetworks, "Invalid network ${card.network} on ${card.id}")
            assertTrue(card.annualFeeCents in 0L..100_000L, "Invalid AF ${card.annualFeeCents} on ${card.id}")
            assertTrue(card.rewardCategories.isNotEmpty(), "Missing rewardCategories on ${card.id}")

            for (rc in card.rewardCategories) {
                assertFalse(rc.label.contains("Base Rate"), "Placeholder Base Rate on ${card.id}: ${rc.label}")
                assertTrue(rc.multiplier in 1.0..20.0, "Invalid multiplier ${rc.multiplier} on ${card.id}")
            }

            card.defaultSubRewardText?.let { sub ->
                assertFalse(sub.contains('_'), "Snake_case SUB text on ${card.id}: $sub")
                assertFalse(Regex("\\$[0-9]{5,}").containsMatchIn(sub), "Corrupted SUB text on ${card.id}: $sub")
            }
            card.defaultSubEstimatedValueCents?.let { cents ->
                assertTrue(cents in 1_000L..300_000L, "Out-of-bounds SUB value $cents on ${card.id}")
            }

            for (benefit in card.benefits) {
                assertTrue(benefit.id.isNotBlank(), "Blank benefit id on ${card.id}")
                assertTrue(seenBenefitIds.add(benefit.id), "Duplicate benefit id ${benefit.id} on ${card.id}")
                assertFalse(Regex("^\\$[0-9]+\\s+\\$[0-9]+").containsMatchIn(benefit.title), "Duplicate dollar prefix on ${card.id}: ${benefit.title}")
                assertFalse(Regex("^\\$[0-9]{4,}\\b").containsMatchIn(benefit.title), "Points-as-dollars title on ${card.id}: ${benefit.title}")
                benefit.periodValueCents?.let { cents ->
                    assertTrue(cents in 100L..50_000L, "Out-of-bounds benefit cents $cents on ${card.id}/${benefit.id}")
                }
            }
        }
    }

    @Test
    fun freedomClassicVisaVsFreedomFlexMastercardAndHouseholdStaplesAreAccurate() {
        val byId = CreditCardTemplates.BUNDLED.associateBy { it.id }

        val cff = assertNotNull(byId["cff"])
        assertEquals("Mastercard", cff.network)
        assertTrue(cff.rewardCategories.any { it.isRotating && it.multiplier == 5.0 })

        val freedomClassic = assertNotNull(byId["chase_freedom_classic"])
        assertEquals("Visa", freedomClassic.network)
        assertEquals(0L, freedomClassic.annualFeeCents)
        assertTrue(freedomClassic.rewardCategories.any { it.isRotating && it.multiplier == 5.0 })

        val costco = assertNotNull(byId["citi_costco_visa"])
        assertEquals("Visa", costco.network)
        assertEquals(0L, costco.annualFeeCents)
        assertTrue(costco.rewardCategories.any { it.multiplier == 4.0 })

        assertNotNull(byId["chase_ritz_carlton"]).also {
            assertEquals("Visa", it.network)
            assertEquals(450_00L, it.annualFeeCents)
        }
        assertNotNull(byId["usbank_altitude_reserve"]).also {
            assertEquals("Visa", it.network)
            assertEquals(400_00L, it.annualFeeCents)
        }
        assertNotNull(byId["fidelity_rewards_visa"]).also {
            assertEquals("Visa", it.network)
            assertEquals(2.0, it.rewardCategories.first().multiplier)
        }
        assertNotNull(byId["robinhood_gold"]).also {
            assertEquals("Visa", it.network)
            assertTrue(it.rewardCategories.any { rc -> rc.multiplier == 3.0 })
        }
        assertNotNull(byId["target_circle_card"]).also {
            assertEquals("Mastercard", it.network)
            assertTrue(it.rewardCategories.any { rc -> rc.multiplier == 5.0 })
        }
    }

    @Test
    fun verifiedNetworkAnnualFeeAndBusinessFlagFixesArePresent() {
        val byId = CreditCardTemplates.BUNDLED.associateBy { it.id }

        assertEquals("Mastercard", byId.getValue("capital_one_savor").network)
        assertEquals("Mastercard", byId.getValue("capital_one_quicksilver").network)
        assertEquals("Mastercard", byId.getValue("venture_rewards").network)
        assertEquals("Mastercard", byId.getValue("venture_x_biz").network)
        assertEquals("Mastercard", byId.getValue("spark_cash_select").network)
        assertEquals("Mastercard", byId.getValue("capital_one_ventureone").network)
        assertEquals("Mastercard", byId.getValue("capital_one_venture_business").network)
        assertEquals("Mastercard", byId.getValue("capital_one_ventureone_business").network)
        assertEquals("Visa", byId.getValue("venture_x").network)
        assertEquals("Visa", byId.getValue("capital_one_spark_2_cash_plus").network)
        assertEquals("Mastercard", byId.getValue("wells_fargo_choice_privileges").network)
        assertEquals("Mastercard", byId.getValue("wells_fargo_choice_privileges_select").network)
        assertEquals("Mastercard", byId.getValue("synchrony_cathay_pacific").network)

        assertEquals(325_00L, byId.getValue("amex_gold").annualFeeCents)
        assertEquals(795_00L, byId.getValue("csr").annualFeeCents)
        assertEquals(895_00L, byId.getValue("amex_plat").annualFeeCents)
        assertEquals(895_00L, byId.getValue("amex_biz_plat").annualFeeCents)
        assertEquals(150_00L, byId.getValue("american_express_hilton_honors_surpass").annualFeeCents)
        assertEquals(195_00L, byId.getValue("american_express_hilton_honors_business").annualFeeCents)
        assertEquals(650_00L, byId.getValue("american_express_delta_skymiles_reserve_business").annualFeeCents)
        assertEquals(0L, byId.getValue("us_bank_skypass_blue").annualFeeCents)
        assertEquals(99_00L, byId.getValue("us_bank_skypass").annualFeeCents)
        assertEquals(249_00L, byId.getValue("first_avianca_lifemiles_elite").annualFeeCents)

        assertTrue(byId.getValue("chase_world_of_hyatt_business").isBusinessCard)
        assertTrue(byId.getValue("brex_brex").isBusinessCard)
        assertFalse(byId.getValue("american_express_rakuten").isBusinessCard)
        assertFalse(byId.getValue("first_ana_card_usa_plus").isBusinessCard)

        val primeStore = byId.getValue("synchrony_amazon_prime_store_card")
        assertFalse(primeStore.rewardCategories.any { it.category == "Everything Else" && it.multiplier >= 5.0 })
    }

    @Test
    fun rewardCategoryWireDtoDeserializesBothLegacyMultiplierAndCanonicalRate() {
        val legacyJson = """{"category":"Dining & Restaurants","label":"Restaurants","multiplier":4.0,"isRotating":true,"capCents":2500000}"""
        val legacyParsed = json.decodeFromString<RewardCategoryWireDto>(legacyJson).toDomain()
        assertEquals(4.0, legacyParsed.multiplier)
        assertTrue(legacyParsed.isRotating)
        assertEquals(2_500_000L, legacyParsed.capCents)

        val canonicalJson = """{"category":"Groceries","label":"Supermarkets","rate":6.0,"is_rotating":false,"cap":6000.0}"""
        val canonicalParsed = json.decodeFromString<RewardCategoryWireDto>(canonicalJson).toDomain()
        assertEquals(6.0, canonicalParsed.multiplier)
        assertFalse(canonicalParsed.isRotating)
        assertEquals(600_000L, canonicalParsed.capCents)
    }

    @Test
    fun updateFromRemoteProtectsCuratedFieldsAgainstStaleServerSchema() {
        val staleSavor = CreditCardTemplates.findBundledById("capital_one_savor")!!.copy(
            network = "Visa",
            rewardCategories = listOf(RewardCategory(category = "Everything Else", label = "Base Rate", multiplier = 1.0)),
            defaultSubRewardText = "\$250 Cash Bonus",
            defaultSubEstimatedValueCents = 250_00L,
            asOf = "2026-11"
        )
        CreditCardTemplates.updateFromRemote(listOf(staleSavor))

        val resolved = CreditCardTemplates.ALL.first { it.id == "capital_one_savor" }
        assertEquals("Mastercard", resolved.network)
        assertTrue(resolved.rewardCategories.any { it.multiplier == 3.0 })
        assertEquals("\$250 Cash Bonus", resolved.defaultSubRewardText)
        assertEquals(250_00L, resolved.defaultSubEstimatedValueCents)
    }

    @Test
    fun resolveTemplateStripsSimpleFinAccountMasksWithoutCrossCardCollision() {
        val cfuArt = CreditCardTemplates.resolveArtworkUrl(
            explicitUrl = null,
            cardName = "Chase Freedom Unlimited (9876)",
            issuerName = "Chase Bank"
        )
        assertEquals("https://www.offeroptimist.com/images/chase/freedom-unlimited.png", cfuArt)

        val classicTpl = CreditCardTemplates.resolveTemplate(
            cardName = "Chase Freedom (4085)",
            issuerName = "Chase Bank"
        )
        assertNotNull(classicTpl)
        assertEquals("chase_freedom_classic", classicTpl.id)
        assertEquals("hex://#13315C:#1E40AF:#FFFFFF", classicTpl.imageUrl)

        val classicArt = CreditCardTemplates.resolveArtworkUrl(
            explicitUrl = null,
            cardName = "Chase Freedom (4085)",
            issuerName = "Chase Bank"
        )
        assertEquals(null, classicArt)
    }
}
