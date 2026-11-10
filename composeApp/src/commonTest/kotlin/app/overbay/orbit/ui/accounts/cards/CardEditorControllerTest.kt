package app.overbay.orbit.ui.accounts.cards

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.domain.CreditCardTemplates
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CardEditorControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var controller: CardEditorController

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        controller = CardEditorController(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testDraftLifecycle_openAndClear() {
        val account = Account(
            id = 100L,
            name = "My Card",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(accountId = 100L, cardName = "Initial Card")
        )

        assertNull(controller.draft.value)
        controller.open(account)

        val draft = controller.draft.value
        assertNotNull(draft)
        assertEquals(100L, draft.accountId)
        assertEquals("Initial Card", draft.cardName)

        controller.clearDraft()
        assertNull(controller.draft.value)
    }

    @Test
    fun testDraftLifecycle_requestCloseDirtyTracking() {
        val account = Account(id = 101L, name = "Clean Card", type = AccountType.CREDIT)
        controller.open(account)

        // Clean draft: requestClose() should clear immediately without discard prompt
        assertFalse(controller.draft.value!!.isDirty)
        controller.requestClose()
        assertNull(controller.draft.value)

        // Dirty draft: requestClose() should trigger showDiscardConfirm
        controller.open(account)
        controller.updateDraft { it.copy(cardName = "Dirty Name") }
        assertTrue(controller.draft.value!!.isDirty)

        controller.requestClose()
        assertNotNull(controller.draft.value)
        assertTrue(controller.draft.value!!.showDiscardConfirm)

        controller.dismissDiscardConfirm()
        assertFalse(controller.draft.value!!.showDiscardConfirm)

        controller.confirmDiscard()
        assertNull(controller.draft.value)
    }

    @Test
    fun testBpsAprParsingAndFormatting() {
        // Valid percentages
        assertEquals(2499, CardEditorDraft.parseBps("24.99"))
        assertEquals(2499, CardEditorDraft.parseBps("24.99%"))
        assertEquals(2490, CardEditorDraft.parseBps("24.9"))
        assertEquals(2490, CardEditorDraft.parseBps("24.9%"))
        assertEquals(2400, CardEditorDraft.parseBps("24"))
        assertEquals(0, CardEditorDraft.parseBps("0"))
        assertEquals(0, CardEditorDraft.parseBps("0%"))
        assertEquals(10000, CardEditorDraft.parseBps("100%"))
        assertEquals(2499, CardEditorDraft.parseBps("24.999%"))

        // Edge cases: >100%, negative, non-numeric, empty/blank
        assertNull(CardEditorDraft.parseBps("100.01"))
        assertNull(CardEditorDraft.parseBps("100.01%"))
        assertNull(CardEditorDraft.parseBps("200%"))
        assertNull(CardEditorDraft.parseBps("-1"))
        assertNull(CardEditorDraft.parseBps("-5"))
        assertNull(CardEditorDraft.parseBps("-24.99%"))
        assertNull(CardEditorDraft.parseBps("abc"))
        assertNull(CardEditorDraft.parseBps("24.a%"))
        assertNull(CardEditorDraft.parseBps(""))
        assertNull(CardEditorDraft.parseBps("   "))
        assertNull(CardEditorDraft.parseBps("%"))

        assertEquals("24.99", CardEditorDraft.formatBpsForInput(2499))
        assertEquals("24.9", CardEditorDraft.formatBpsForInput(2490))
        assertEquals("24", CardEditorDraft.formatBpsForInput(2400))
        assertEquals("0", CardEditorDraft.formatBpsForInput(0))
        assertEquals("", CardEditorDraft.formatBpsForInput(null))
    }

    @Test
    fun testDayValidationAndDraftErrors() {
        val account = Account(id = 102L, name = "Validation Card", type = AccountType.CREDIT)
        controller.open(account)

        // Valid boundary days: 1 and 31
        controller.updateDraft {
            it.copy(
                statementCloseDayText = "1",
                paymentDueDayText = "31",
                aprText = "19.99"
            )
        }
        assertTrue(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.isEmpty())

        // Out of range closing days: 0, 32, -5, "abc"
        controller.updateDraft { it.copy(statementCloseDayText = "0") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("statementCloseDay"))

        controller.updateDraft { it.copy(statementCloseDayText = "32") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("statementCloseDay"))

        controller.updateDraft { it.copy(statementCloseDayText = "-5") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("statementCloseDay"))

        controller.updateDraft { it.copy(statementCloseDayText = "abc") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("statementCloseDay"))

        // Out of range due days: 0, 32, -5, "abc"
        controller.updateDraft { it.copy(statementCloseDayText = "15", paymentDueDayText = "0") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("paymentDueDay"))

        controller.updateDraft { it.copy(paymentDueDayText = "32") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("paymentDueDay"))

        controller.updateDraft { it.copy(paymentDueDayText = "-5") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("paymentDueDay"))

        controller.updateDraft { it.copy(paymentDueDayText = "abc") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("paymentDueDay"))

        // Invalid APR
        controller.updateDraft { it.copy(paymentDueDayText = "10", aprText = "invalid") }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("apr"))

        // Promo requiring valid promo end date
        controller.updateDraft { it.copy(aprText = "19.99", hasPromo = true, promoEndDate = null) }
        assertFalse(controller.draft.value!!.isValid)
        assertTrue(controller.draft.value!!.errors.containsKey("promoEndDate"))

        controller.updateDraft { it.copy(promoEndDate = "2027-06-01") }
        assertTrue(controller.draft.value!!.isValid)
    }

    @Test
    fun testTemplateApplication() {
        val account = Account(id = 103L, name = "Template Card", institution = "Orbit Bank", type = AccountType.CREDIT)
        controller.open(account)

        // Initial setup starts on PICK_TEMPLATE
        assertEquals(CardEditorStep.PICK_TEMPLATE, controller.draft.value!!.step)

        // User enters billing info before template selection
        controller.updateDraft {
            it.copy(
                statementCloseDayText = "18",
                paymentDueDayText = "12",
                aprText = "21.49"
            )
        }

        val template = CreditCardTemplates.ALL.first()
        controller.applyTemplate(template)

        val draft = controller.draft.value!!
        assertEquals(CardEditorStep.FORM, draft.step)
        assertEquals(template.cardName, draft.cardName)
        assertEquals(template.issuerName, draft.issuer)
        assertEquals(template.imageUrl, draft.artKey)
        assertEquals(template.annualFeeCents, draft.annualFeeCents)
        assertEquals(template.benefits.size, draft.benefits.size)
        assertEquals(template.rewardCategories.size, draft.rewardCategories.size)

        // Merging logic: user fields preserved
        assertEquals("18", draft.statementCloseDayText)
        assertEquals("12", draft.paymentDueDayText)
        assertEquals("21.49", draft.aprText)
        assertEquals("Template Card", draft.accountName)
        assertEquals("Orbit Bank", draft.accountInstitution)
    }

    @Test
    fun testBenefitMutations() {
        val account = Account(id = 104L, name = "Perks Card", type = AccountType.CREDIT)
        controller.open(account)

        val perk = CardBenefit(id = "perk_1", title = "Dining Credit", periodValueCents = 1000L)
        controller.addBenefit(perk)
        assertEquals(1, controller.draft.value!!.benefits.size)

        // Update perk
        val updatedPerk = perk.copy(periodValueCents = 2000L)
        controller.updateBenefit(updatedPerk)
        assertEquals(2000L, controller.draft.value!!.benefits.first().periodValueCents)

        // Upsert existing perk
        controller.upsertBenefit(updatedPerk.copy(title = "Updated Dining Credit"))
        assertEquals("Updated Dining Credit", controller.draft.value!!.benefits.first().title)

        // Remove perk and undo
        controller.removeBenefit("perk_1")
        assertEquals(0, controller.draft.value!!.benefits.size)
        assertEquals("perk_1", controller.draft.value!!.lastDeletedBenefit?.id)

        controller.undoRemoveBenefit()
        assertEquals(1, controller.draft.value!!.benefits.size)
        assertNull(controller.draft.value!!.lastDeletedBenefit)
    }

    @Test
    fun testRewardMutations() {
        val account = Account(id = 105L, name = "Rewards Card", type = AccountType.CREDIT)
        controller.open(account)

        val reward = RewardCategory(category = "Dining", multiplier = 3.0)
        controller.addReward(reward)
        assertEquals(1, controller.draft.value!!.rewardCategories.size)

        controller.updateReward(0, reward.copy(multiplier = 4.0))
        assertEquals(4.0, controller.draft.value!!.rewardCategories[0].multiplier)

        controller.upsertReward(0, reward.copy(multiplier = 5.0))
        assertEquals(5.0, controller.draft.value!!.rewardCategories[0].multiplier)

        controller.removeReward(0)
        assertEquals(0, controller.draft.value!!.rewardCategories.size)
    }

    @Test
    fun testWelcomeBonusModeTransitions() {
        val account = Account(id = 106L, name = "SUB Card", type = AccountType.CREDIT)
        controller.open(account)

        controller.setWelcomeBonusMode(WelcomeBonusMode.WORKING)
        controller.updateDraft { it.copy(subRewardText = "60k Pts", targetSpendCents = 400000L) }
        assertEquals(WelcomeBonusMode.WORKING, controller.draft.value!!.welcomeBonusMode)

        controller.setWelcomeBonusMode(WelcomeBonusMode.EARNED)
        assertTrue(controller.draft.value!!.subCompleted)
        assertEquals(WelcomeBonusMode.EARNED, controller.draft.value!!.welcomeBonusMode)

        controller.setWelcomeBonusMode(WelcomeBonusMode.NONE)
        assertEquals(WelcomeBonusMode.NONE, controller.draft.value!!.welcomeBonusMode)
        assertNull(controller.draft.value!!.subRewardText)
        assertNull(controller.draft.value!!.targetSpendCents)
    }

    @Test
    fun testSavePersistsToRepository() = runTest(testDispatcher) {
        val cardAcct = Account(id = 107L, name = "Save Card", type = AccountType.CREDIT)
        repository.saveAccount(cardAcct)

        controller.open(cardAcct)
        controller.updateDraft {
            it.copy(
                cardName = "Persisted Platinum",
                issuer = "Orbit Bank",
                statementCloseDayText = "15",
                paymentDueDayText = "10",
                aprText = "18.5"
            )
        }

        val result = controller.save()
        assertTrue(result.isSuccess)
        assertNull(controller.draft.value)

        val updatedAcct = repository.accounts.value.find { it.id == 107L }
        assertNotNull(updatedAcct)
        val profile = updatedAcct.creditCardProfile
        assertNotNull(profile)
        assertEquals("Persisted Platinum", profile.cardName)
        assertEquals("Orbit Bank", profile.issuerName)
        assertEquals(15, profile.statementClosingDay)
        assertEquals(10, profile.paymentDueDay)
        assertEquals(1850, profile.aprBps)
    }

    @Test
    fun testSaveFailureRetainsDraft() = runTest(testDispatcher) {
        val cardAcct = Account(id = 108L, name = "Save Failure Card", type = AccountType.CREDIT)
        controller.open(cardAcct)

        controller.updateDraft {
            it.copy(
                cardName = "Draft Should Be Retained",
                statementCloseDayText = "99" // Validation error (day out of range 1..31)
            )
        }

        val result = controller.save()
        assertTrue(result.isFailure, "Save must fail when draft has validation errors")
        assertNotNull(controller.draft.value, "Draft must not be cleared on save failure")
        assertEquals("Draft Should Be Retained", controller.draft.value!!.cardName)
        assertEquals("99", controller.draft.value!!.statementCloseDayText)
        assertFalse(controller.draft.value!!.isSaving)
    }

    @Test
    fun testSmartAutoFillDeadlineEstimatedValueAnd524Override() {
        val account = Account(id = 109L, name = "New Business Card", institution = null, type = AccountType.CREDIT)
        controller.open(account)

        val sparkTemplate = CreditCardTemplates.ALL.first { it.id == "spark_cash_select" }
        controller.applyTemplate(sparkTemplate)

        val draftAfterTemplate = controller.draft.value!!
        assertEquals("Capital One", draftAfterTemplate.accountInstitution)
        assertEquals(sparkTemplate.defaultSubEstimatedValueCents, draftAfterTemplate.subEstimatedValueCents)
        assertEquals(true, draftAfterTemplate.countsToward524Override)
        assertEquals(90, draftAfterTemplate.subSpendWindowDays)
        assertNull(draftAfterTemplate.targetDeadlineDate)
        assertNotNull(draftAfterTemplate.artworkUrl)

        // Setting openedDate automatically populates targetDeadlineDate (+90 days)
        controller.setOpenedDate("2026-01-01")
        assertEquals("2026-04-01", controller.draft.value!!.targetDeadlineDate)
    }

    @Test
    fun testFilterCardTemplatesAndHexPalette() {
        assertTrue(CreditCardTemplates.BUNDLED.size >= 150, "Expected 150+ bundled cards, got ${CreditCardTemplates.BUNDLED.size}")
        assertTrue(
            CreditCardTemplates.BUNDLED.count { !it.artworkUrl.isNullOrBlank() } >= 150,
            "Expected 150+ bundled cards with real artwork URLs"
        )
        assertEquals(
            "https://www.offeroptimist.com/images/chase/sapphire-reserve.png",
            CreditCardTemplates.resolveArtworkUrl(null, "Chase Sapphire Reserve", "Chase")
        )

        val chaseCards = filterCardTemplates(CreditCardTemplates.BUNDLED, "", "Chase")
        assertTrue(chaseCards.size >= 25)
        assertTrue(chaseCards.all { it.issuerName == "Chase" })

        val bizCards = filterCardTemplates(CreditCardTemplates.BUNDLED, "", "Business")
        assertTrue(bizCards.isNotEmpty())
        assertTrue(bizCards.all { it.isBusinessCard })

        val noFeeAmex = filterCardTemplates(CreditCardTemplates.BUNDLED, "Blue", "\$0 Fee")
        assertTrue(noFeeAmex.any { it.id == "amex_bce" })

        val resolved = app.overbay.orbit.theme.CardArtPalette.resolveColors(
            key = "hex://#0F172A:#0D9488:#FFFFFF",
            fallbackStart = androidx.compose.ui.graphics.Color.Black,
            fallbackEnd = androidx.compose.ui.graphics.Color.Black,
            fallbackText = androidx.compose.ui.graphics.Color.Black
        )
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0F172A), resolved.first)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0D9488), resolved.second)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFFFFF), resolved.third)
    }

    @Test
    fun testAuthorizedUsersAndPrimaryCardholderExclusion() = runTest(testDispatcher) {
        val cardAcct = Account(id = 110L, name = "Household Card", type = AccountType.CREDIT)
        repository.saveAccount(cardAcct)

        controller.open(cardAcct)
        controller.selectPrimaryCardholder(10L, "Alex")
        controller.toggleAuthorizedMember(20L, "Sam")
        controller.addCustomAuthorizedUser("Jordan")
        // Attempt to add primary cardholder as custom or member AU — must be ignored
        controller.toggleAuthorizedMember(10L, "Alex")
        controller.addCustomAuthorizedUser("alex")

        val draft1 = controller.draft.value!!
        assertEquals(10L, draft1.cardholderMemberId)
        assertEquals("Alex", draft1.cardholder)
        assertEquals(listOf(20L), draft1.authorizedUserMemberIds)
        assertEquals(listOf("Jordan"), draft1.authorizedUsers)
        assertTrue(draft1.isDirty)

        // Switching primary cardholder to Sam (20L) automatically removes Sam from authorizedUserMemberIds
        controller.selectPrimaryCardholder(20L, "Sam")
        val draft2 = controller.draft.value!!
        assertEquals(20L, draft2.cardholderMemberId)
        assertTrue(draft2.authorizedUserMemberIds.isEmpty())
        assertEquals(listOf("Jordan"), draft2.authorizedUsers)

        // Add Alex as household AU, persist via save(), and verify repository round-trip
        controller.toggleAuthorizedMember(10L, "Alex")
        val result = controller.save()
        assertTrue(result.isSuccess)

        val savedProfile = repository.accounts.value.find { it.id == 110L }?.creditCardProfile
        assertNotNull(savedProfile)
        assertEquals(20L, savedProfile.strategy.cardholderMemberId)
        assertEquals("Sam", savedProfile.strategy.cardholder)
        assertEquals(listOf(10L), savedProfile.strategy.authorizedUserMemberIds)
        assertEquals(listOf("Jordan"), savedProfile.authorizedUsers)
    }
}
