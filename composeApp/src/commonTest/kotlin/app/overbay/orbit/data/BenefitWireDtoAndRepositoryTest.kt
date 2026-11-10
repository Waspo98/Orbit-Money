package app.overbay.orbit.data

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BenefitAnchor
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.BenefitUsage
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.BenefitUsageRequestDto
import app.overbay.orbit.data.network.BenefitUsageWireDto
import app.overbay.orbit.data.network.CardBenefitWireDto
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BenefitWireDtoAndRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    // -------------------------------------------------------------------------
    // Wire DTO Serialization / Deserialization Tests
    // -------------------------------------------------------------------------

    @Test
    fun testBenefitUsageWireDtoRoundTrip() {
        val domain = BenefitUsage(
            periodKey = "2026-11",
            amountCents = 1550L,
            usedOn = "2026-11-09"
        )
        val wire = BenefitUsageWireDto.fromDomain(domain)
        assertEquals("2026-11", wire.periodKey)
        assertEquals(15.50, wire.amount)
        assertEquals("2026-11-09", wire.usedOn)

        val decodedDomain = wire.toDomain()
        assertEquals(domain, decodedDomain)

        val serialized = json.encodeToString(wire)
        val deserialized = json.decodeFromString<BenefitUsageWireDto>(serialized)
        assertEquals(wire, deserialized)
    }

    @Test
    fun testCardBenefitWireDtoFromDomainCalculatesAnnualizedFallback() {
        // Monthly $10 perk -> annualized $120
        val monthlyPerk = CardBenefit(
            id = "b_dining",
            title = "$10 Monthly Dining",
            periodValueCents = 1000L,
            cadence = BenefitCadence.MONTHLY,
            anchor = BenefitAnchor.CALENDAR,
            usage = listOf(BenefitUsage("2026-11", 1000L, "2026-11-01"))
        )

        val wire = CardBenefitWireDto.fromDomain(monthlyPerk)
        assertEquals("b_dining", wire.id)
        assertEquals(10.0, wire.periodValue)
        assertEquals("monthly", wire.cadence)
        assertEquals("calendar", wire.anchor)
        assertEquals(1, wire.usage.size)
        assertEquals("2026-11", wire.usage.first().periodKey)

        // Annualized fallback for older clients
        assertEquals(JsonPrimitive(120.0), wire.value)

        // Roundtrip via JSON serialization
        val wireJson = json.encodeToString(wire)
        val decodedWire = json.decodeFromString<CardBenefitWireDto>(wireJson)
        val domainRestored = decodedWire.toDomain()

        assertEquals(monthlyPerk.id, domainRestored.id)
        assertEquals(monthlyPerk.periodValueCents, domainRestored.periodValueCents)
        assertEquals(monthlyPerk.cadence, domainRestored.cadence)
        assertEquals(monthlyPerk.anchor, domainRestored.anchor)
        assertEquals(monthlyPerk.usage, domainRestored.usage)
    }

    @Test
    fun testCardBenefitWireDtoLegacyFormatDeserialization() {
        // Simulates receiving a legacy benefit from an older backend or snapshot without v2 fields
        val legacyJson = """
            {
                "name": "Travel Credit",
                "title": "Travel Credit",
                "value": 300.0,
                "description": "Annual travel reimbursement",
                "category": "Travel",
                "cadence": "annual",
                "used_this_year": true,
                "used_periods": ["2026"]
            }
        """.trimIndent()

        val wire = json.decodeFromString<CardBenefitWireDto>(legacyJson)
        assertEquals("", wire.id)
        assertNull(wire.periodValue)
        assertEquals("calendar", wire.anchor)
        assertTrue(wire.usage.isEmpty())
        assertTrue(wire.usedThisYear)
        assertEquals(listOf("2026"), wire.usedPeriods)

        val domain = wire.toDomain()
        assertEquals("", domain.id)
        assertEquals("Travel Credit", domain.title)
        assertNull(domain.periodValueCents)
        assertEquals(30000L, domain.annualCreditValueCents)
        assertTrue(domain.usedThisYear)
        assertEquals(listOf("2026"), domain.usedPeriods)
    }

    // -------------------------------------------------------------------------
    // OrbitRepository.setCardBenefitUsage Tests
    // -------------------------------------------------------------------------

    @Test
    fun testSetCardBenefitUsageLocalOptimisticUpdate() = runTest(testDispatcher) {
        val benefit = CardBenefit(
            id = "b_uber",
            title = "$15 Uber Cash",
            periodValueCents = 1500L,
            cadence = BenefitCadence.MONTHLY
        )
        val profile = CreditCardProfile(
            id = 1L,
            accountId = 100L,
            cardName = "Platinum",
            benefits = listOf(benefit)
        )
        val account = Account(
            id = 100L,
            name = "Platinum Card",
            type = AccountType.CREDIT,
            creditCardProfile = profile
        )
        repository.saveAccount(account)

        // 1. Apply usage
        val res1 = repository.setCardBenefitUsage(
            accountId = 100L,
            benefitId = "b_uber",
            periodKey = "2026-11",
            amountCents = 1500L,
            usedOn = "2026-11-05"
        )
        assertTrue(res1.isSuccess)

        val acctAfter1 = repository.accounts.value.find { it.id == 100L }
        assertNotNull(acctAfter1)
        val perkAfter1 = acctAfter1.creditCardProfile?.benefits?.find { it.id == "b_uber" }
        assertNotNull(perkAfter1)
        assertEquals(1, perkAfter1.usage.size)
        assertEquals("2026-11", perkAfter1.usage[0].periodKey)
        assertEquals(1500L, perkAfter1.usage[0].amountCents)
        assertEquals("2026-11-05", perkAfter1.usage[0].usedOn)

        // Verify snapshot is also kept in sync
        val snapProfile1 = repository.snapshot.value.creditCardProfiles.find { it.accountId == 100L }
        assertNotNull(snapProfile1)
        assertEquals(1, snapProfile1.benefits.first().usage.size)

        // 2. Partial usage overwrite
        val res2 = repository.setCardBenefitUsage(
            accountId = 100L,
            benefitId = "b_uber",
            periodKey = "2026-11",
            amountCents = 1000L,
            usedOn = "2026-11-08"
        )
        assertTrue(res2.isSuccess)
        val perkAfter2 = repository.accounts.value.find { it.id == 100L }?.creditCardProfile?.benefits?.first()
        assertNotNull(perkAfter2)
        assertEquals(1, perkAfter2.usage.size)
        assertEquals(1000L, perkAfter2.usage[0].amountCents)

        // 3. Clear usage (amountCents = null)
        val resClear = repository.setCardBenefitUsage(
            accountId = 100L,
            benefitId = "b_uber",
            periodKey = "2026-11",
            amountCents = null
        )
        assertTrue(resClear.isSuccess)
        val perkAfterClear = repository.accounts.value.find { it.id == 100L }?.creditCardProfile?.benefits?.first()
        assertNotNull(perkAfterClear)
        assertTrue(perkAfterClear.usage.isEmpty())

        // 4. Missing account returns failure
        val resMissing = repository.setCardBenefitUsage(
            accountId = 9999L,
            benefitId = "b_uber",
            periodKey = "2026-11",
            amountCents = 1000L
        )
        assertTrue(resMissing.isFailure)
    }

    @Test
    fun testSetCardBenefitUsageRollsBackOnRemoteFailure() = runTest(testDispatcher) {
        val benefit = CardBenefit(
            id = "b_dining",
            title = "$10 Dining",
            periodValueCents = 1000L,
            cadence = BenefitCadence.MONTHLY,
            usage = listOf(BenefitUsage("2026-10", 1000L, "2026-10-15"))
        )
        val profile = CreditCardProfile(
            id = 2L,
            accountId = 200L,
            cardName = "Gold Card",
            benefits = listOf(benefit)
        )
        val account = Account(
            id = 200L,
            name = "Gold Card",
            type = AccountType.CREDIT,
            creditCardProfile = profile
        )

        // Switch to SELF_HOSTED with unreachable endpoint to simulate network failure
        repository.setStorageMode(StorageMode.SELF_HOSTED, "http://127.0.0.1:59999")

        // Seed account and profile into the self-hosted local snapshot
        repository.commitSnapshot(
            repository.snapshot.value.copy(
                accounts = listOf(account),
                creditCardProfiles = listOf(profile)
            )
        )

        val res = repository.setCardBenefitUsage(
            accountId = 200L,
            benefitId = "b_dining",
            periodKey = "2026-11",
            amountCents = 1000L,
            usedOn = "2026-11-09"
        )

        // Network call must fail
        assertTrue(res.isFailure)

        // Verification: Local state must be rolled back to the original state
        val rolledBackAccount = repository.accounts.value.find { it.id == 200L }
        assertNotNull(rolledBackAccount)
        val rolledBackPerk = rolledBackAccount.creditCardProfile?.benefits?.find { it.id == "b_dining" }
        assertNotNull(rolledBackPerk)

        // Must still have only the original 2026-10 usage; 2026-11 must NOT be present
        assertEquals(1, rolledBackPerk.usage.size)
        assertEquals("2026-10", rolledBackPerk.usage[0].periodKey)
        assertFalse(rolledBackPerk.usage.any { it.periodKey == "2026-11" })

        // Snapshot profiles must also be restored
        val snapProfile = repository.snapshot.value.creditCardProfiles.find { it.accountId == 200L }
        assertNotNull(snapProfile)
        assertEquals(1, snapProfile.benefits.first().usage.size)
        assertEquals("2026-10", snapProfile.benefits.first().usage.first().periodKey)
    }
}
