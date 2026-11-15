package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BenefitCadence
import app.overbay.orbit.data.model.CardBenefit
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.CreditCardStrategy
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.RewardCategory
import app.overbay.orbit.data.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreditCardStrategyEngineTest {

    private val refToday = LocalDate(2026, 11, 9)

    private fun creditAccount(
        id: Long,
        name: String,
        openedDate: String? = null,
        isBusinessCard: Boolean = false,
        cardholder: String? = "Player 1",
        isArchived: Boolean = false,
        strategy: CreditCardStrategy = CreditCardStrategy(
            cardholder = cardholder,
            openedDate = openedDate,
            isBusinessCard = isBusinessCard
        ),
        benefits: List<CardBenefit> = emptyList(),
        annualFeeCents: Cents? = null
    ): Account = Account(
        id = id,
        name = name,
        type = AccountType.CREDIT,
        isArchived = isArchived,
        creditCardProfile = CreditCardProfile(
            accountId = id,
            cardName = name,
            annualFeeCents = annualFeeCents,
            benefits = benefits,
            strategy = strategy
        )
    )

    @Test
    fun chase524CountsPersonalCardsWithin24MonthsAndExcludesBusinessCards() {
        val accounts = listOf(
            creditAccount(1L, "Old Sapphire", openedDate = "2024-05-01"), // > 24 mos ago -> excluded
            creditAccount(2L, "Freedom Unlimited", openedDate = "2025-01-15"), // slot 1
            creditAccount(3L, "Ink Business Cash", openedDate = "2025-03-10", isBusinessCard = true), // biz -> excluded
            creditAccount(4L, "Amex Gold", openedDate = "2025-06-20"), // slot 2
            creditAccount(5L, "Closed Citi Custom Cash", openedDate = "2025-09-01", isArchived = true), // slot 3 (closed still counts)
            creditAccount(6L, "Venture X", openedDate = "2026-02-10"), // slot 4
            creditAccount(7L, "Bilt Mastercard", openedDate = "2026-08-05") // slot 5
        )

        val status = CreditCardStrategyEngine.computeChase524Status(accounts, refToday)
        assertEquals(5, status.count)
        assertFalse(status.isUnder524)
        assertEquals(1, status.excludedBusinessCount)
        assertEquals(0, status.missingOpenDateCount)
        assertEquals("2027-01-15", status.nextSlotFreeDate)
        assertEquals("2027-01-15", status.under524DropDate)
    }

    @Test
    fun subProgressSumsQualifyingPurchasesAndExcludesAnnualFeesAndTransfers() {
        val strategy = CreditCardStrategy(
            cardholder = "Player 1",
            openedDate = "2026-10-01",
            subRewardText = "75,000 UR Points",
            subTargetSpendCents = 4000_00L,
            subDeadlineDate = "2027-01-01",
            subManualSpendOffsetCents = 500_00L
        )
        val account = creditAccount(10L, "Sapphire Preferred", strategy = strategy)

        val txns = listOf(
            // Qualifying purchase: $1,200
            Transaction(id = 1L, accountId = 10L, originalDate = "2026-10-15", originalMerchant = "Delta Air Lines", originalAmountCents = -1200_00L),
            // Qualifying purchase: $800
            Transaction(id = 2L, accountId = 10L, originalDate = "2026-10-20", originalMerchant = "Costco Wholesale", originalAmountCents = -800_00L),
            // Refund/return: +$100 (reduces net spend)
            Transaction(id = 3L, accountId = 10L, originalDate = "2026-10-22", originalMerchant = "Costco Return", originalAmountCents = 100_00L),
            // Annual Fee: should be excluded automatically
            Transaction(id = 4L, accountId = 10L, originalDate = "2026-10-02", originalMerchant = "ANNUAL MEMBERSHIP FEE", originalAmountCents = -95_00L),
            // Credit card payment / transfer: excluded
            Transaction(id = 5L, accountId = 10L, originalDate = "2026-10-25", originalMerchant = "Payment Thank You", originalAmountCents = 1500_00L, isTransfer = true)
        )

        val progress = CreditCardStrategyEngine.computeSubProgress(account, txns, refToday)
        assertNotNull(progress)
        // $500 offset + $1,200 + $800 - $100 refund = $2,400
        assertEquals(2400_00L, progress.currentSpendCents)
        assertEquals(1600_00L, progress.remainingSpendCents)
        assertEquals(0.60f, progress.progressRatio, 0.001f)
        assertEquals(SubTrackerStatus.ON_TRACK, progress.status)
    }

    @Test
    fun subEligibilityCalculates48MonthSapphireAndLifetimeRules() {
        val recentSapphire = CreditCardProfile(
            accountId = 1L,
            strategy = CreditCardStrategy(
                openedDate = "2024-06-01",
                subReceivedDate = "2024-08-15",
                subCooldownMonths = 48
            )
        )
        val elig1 = CreditCardStrategyEngine.computeSubEligibility(recentSapphire, refToday)
        assertNotNull(elig1)
        assertFalse(elig1.isEligibleNow)
        assertEquals("2028-08-15", elig1.eligibleDate)

        val oldSapphire = CreditCardProfile(
            accountId = 2L,
            strategy = CreditCardStrategy(
                openedDate = "2021-03-01",
                subReceivedDate = "2021-05-10",
                subCooldownMonths = 48
            )
        )
        val elig2 = CreditCardStrategyEngine.computeSubEligibility(oldSapphire, refToday)
        assertNotNull(elig2)
        assertTrue(elig2.isEligibleNow)
        assertEquals("SUB Eligible Now (Passed May 10)", elig2.statusLabel)

        val amexPlatinum = CreditCardProfile(
            accountId = 3L,
            strategy = CreditCardStrategy(
                openedDate = "2023-01-01",
                subCooldownMonths = LIFETIME_COOLDOWN_MONTHS
            )
        )
        val elig3 = CreditCardStrategyEngine.computeSubEligibility(amexPlatinum, refToday)
        assertNotNull(elig3)
        assertFalse(elig3.isEligibleNow)
        assertEquals("Once per Lifetime", elig3.statusLabel)

        val missingDatesProfile = CreditCardProfile(
            accountId = 4L,
            strategy = CreditCardStrategy(subCooldownMonths = 24)
        )
        val eligMissing = CreditCardStrategyEngine.computeSubEligibility(missingDatesProfile, refToday)
        assertNotNull(eligMissing)
        assertEquals("24-Mo Rule (Set Opened Date)", eligMissing.statusLabel)
    }

    @Test
    fun templateApplyToProfileAppliesDefaultFeeAndCategories() {
        val amexGoldTemplate = CreditCardTemplates.ALL.first { it.id == "amex_gold" }
        val applied = amexGoldTemplate.applyToProfile(CreditCardProfile(accountId = 99L))
        assertEquals(325_00L, applied.annualFeeCents)
        assertTrue(applied.benefits.isNotEmpty())
        assertTrue(applied.rewardCategories.any { it.multiplier == 4.0 })
    }

    @Test
    fun detectHistorySuggestionsFindsEarliestTransactionAndAnnualFeePostDate() {
        val txns = listOf(
            Transaction(id = 1L, accountId = 55L, originalDate = "2025-04-10", originalMerchant = "Whole Foods", originalAmountCents = -64_20L),
            Transaction(id = 2L, accountId = 55L, originalDate = "2025-04-18", originalMerchant = "ANNUAL MEMBERSHIP FEE", originalAmountCents = -95_00L),
            Transaction(id = 3L, accountId = 55L, originalDate = "2026-04-18", originalMerchant = "ANNUAL MEMBERSHIP FEE", originalAmountCents = -95_00L)
        )

        val suggestions = CreditCardStrategyEngine.detectHistorySuggestions(55L, txns, 95_00L)
        assertEquals("2025-04-10", suggestions.suggestedOpenedDate)
        assertEquals("Whole Foods", suggestions.earliestTxnMerchant)
        assertEquals("2026-04-18", suggestions.suggestedAnnualFeeDate)
        assertEquals(95_00L, suggestions.detectedAnnualFeeCents)
    }

    @Test
    fun summarizePortfolioPartitions524ByPerson() {
        val accounts = listOf(
            creditAccount(1L, "Card A1", openedDate = "2025-01-15", cardholder = "Alex"),
            creditAccount(2L, "Card A2", openedDate = "2025-06-20", cardholder = "Alex"),
            creditAccount(3L, "Card A3", openedDate = "2026-02-10", cardholder = "Alex"),
            creditAccount(4L, "Card S1", openedDate = "2025-03-10", cardholder = "Sam"),
            creditAccount(5L, "Card S2", openedDate = "2025-08-05", cardholder = "Sam"),
            creditAccount(6L, "Card S3", openedDate = "2026-01-12", cardholder = "Sam")
        )
        val summary = CreditCardStrategyEngine.summarizePortfolio(accounts, emptyList(), today = refToday)
        assertEquals(2, summary.chase524ByPerson.size)
        val alex524 = summary.chase524ByPerson.first { it.holderKey == "alex" }
        val sam524 = summary.chase524ByPerson.first { it.holderKey == "sam" }
        assertEquals(3, alex524.summary.count)
        assertEquals(3, sam524.summary.count)
        assertTrue(alex524.summary.isUnder524)
        assertTrue(sam524.summary.isUnder524)
    }

    @Test
    fun summarizePortfolioDeDuplicatesPlayerNamesCaseInsensitively() {
        val accounts = listOf(
            creditAccount(1L, "Card 1", openedDate = "2025-01-15", cardholder = "Neal"),
            creditAccount(2L, "Card 2", openedDate = "2025-06-20", cardholder = "neal ")
        )
        val summary = CreditCardStrategyEngine.summarizePortfolio(accounts, emptyList(), today = refToday)
        assertEquals(1, summary.availablePlayers.size)
        assertEquals("neal", summary.availablePlayers.first().first)
        assertEquals("Neal", summary.availablePlayers.first().second)
        assertEquals(1, summary.chase524ByPerson.size)
        assertEquals(2, summary.chase524ByPerson.first().summary.count)
    }

    @Test
    fun computeChase524StatusTracksCardsWithMissingOpenedDate() {
        val accounts = listOf(
            creditAccount(1L, "Active Card No Date", openedDate = null),
            creditAccount(2L, "Normal Card", openedDate = "2025-05-01")
        )
        val status = CreditCardStrategyEngine.computeChase524Status(accounts, refToday)
        assertEquals(1, status.count)
        assertEquals(1, status.missingOpenDateCount)
    }

    @Test
    fun computeSubProgressReturnsNullWhenOpenedDateIsMissing() {
        val account = creditAccount(
            1L, "Card Without Date",
            strategy = CreditCardStrategy(openedDate = null, subTargetSpendCents = 4000_00L)
        )
        val progress = CreditCardStrategyEngine.computeSubProgress(account, emptyList(), refToday)
        assertNull(progress)
    }

    @Test
    fun cardTransactionClassifierFiltersUntaggedPaymentsAndNonSpendCharges() {
        val purchase = Transaction(id = 1L, accountId = 1L, originalDate = "2026-10-05", originalMerchant = "Best Buy", originalAmountCents = -1000_00L)
        val untaggedPayment = Transaction(id = 2L, accountId = 1L, originalDate = "2026-10-10", originalMerchant = "PAYMENT THANK YOU", originalAmountCents = 1500_00L, isTransfer = false)
        val dashPayment = Transaction(id = 6L, accountId = 1L, originalDate = "2026-10-11", originalMerchant = "AUTOMATIC PAYMENT - THANK YOU", originalAmountCents = 2000_00L, isTransfer = false)
        val interest = Transaction(id = 3L, accountId = 1L, originalDate = "2026-10-12", originalMerchant = "PURCHASE INTEREST CHARGE", originalAmountCents = -25_50L)
        val lateFee = Transaction(id = 4L, accountId = 1L, originalDate = "2026-10-15", originalMerchant = "LATE PAYMENT FEE", originalAmountCents = -40_00L)
        val statementCredit = Transaction(id = 5L, accountId = 1L, originalDate = "2026-10-18", originalMerchant = "AMEX UBER STATEMENT CREDIT", originalAmountCents = 15_00L)

        assertEquals(1000_00L, CardTransactionClassifier.bonusSpendDeltaCents(purchase))
        assertEquals(0L, CardTransactionClassifier.bonusSpendDeltaCents(untaggedPayment))
        assertEquals(0L, CardTransactionClassifier.bonusSpendDeltaCents(dashPayment))
        assertEquals(0L, CardTransactionClassifier.bonusSpendDeltaCents(interest))
        assertEquals(0L, CardTransactionClassifier.bonusSpendDeltaCents(lateFee))
        assertEquals(0L, CardTransactionClassifier.bonusSpendDeltaCents(statementCredit))
    }

    @Test
    fun computeSubProgressDistinguishesSpendMetFromReceived() {
        val strategyAwaiting = CreditCardStrategy(
            openedDate = "2026-10-01",
            subTargetSpendCents = 1000_00L,
            subDeadlineDate = "2027-01-01",
            subReceivedDate = null
        )
        val txns = listOf(
            Transaction(id = 1L, accountId = 1L, originalDate = "2026-10-15", originalMerchant = "Apple Store", originalAmountCents = -1000_00L)
        )
        val p1 = CreditCardStrategyEngine.computeSubProgress(creditAccount(1L, "Card", strategy = strategyAwaiting), txns, refToday)
        assertNotNull(p1)
        assertEquals(SubTrackerStatus.SPEND_MET, p1.status)

        val strategyReceived = strategyAwaiting.copy(subReceivedDate = "2026-11-05")
        val p2 = CreditCardStrategyEngine.computeSubProgress(creditAccount(1L, "Card", strategy = strategyReceived), txns, refToday)
        assertNotNull(p2)
        assertEquals(SubTrackerStatus.RECEIVED, p2.status)
    }

    @Test
    fun computeSubProgressPaceFlagsAtRiskWhenRecentSpendStalls() {
        // Target $4,000, opened 2026-08-01 (100 days ago), deadline in 20 days (2026-11-29).
        // $500 spent back in August, $0 in last 30 days.
        val strategy = CreditCardStrategy(
            openedDate = "2026-08-01",
            subTargetSpendCents = 4000_00L,
            subDeadlineDate = "2026-11-29"
        )
        val txns = listOf(
            Transaction(id = 1L, accountId = 1L, originalDate = "2026-08-05", originalMerchant = "Home Depot", originalAmountCents = -500_00L)
        )
        val progress = CreditCardStrategyEngine.computeSubProgress(creditAccount(1L, "Card", strategy = strategy), txns, refToday)
        assertNotNull(progress)
        assertEquals(3500_00L, progress.remainingSpendCents)
        assertEquals(SubTrackerStatus.AT_RISK, progress.status)
    }

    @Test
    fun templateApplyToProfilePreservesUsedPeriodsAndCustomCategories() {
        val existing = CreditCardProfile(
            accountId = 5L,
            benefits = listOf(
                CardBenefit(
                    title = "$10/mo Uber Cash ($120/yr)",
                    annualCreditValueCents = 120_00L,
                    cadence = BenefitCadence.MONTHLY,
                    usedPeriods = listOf("2026-01", "2026-02", "2026-03")
                ),
                CardBenefit(title = "Custom Airport Lounge Pass", annualCreditValueCents = 150_00L)
            ),
            rewardCategories = listOf(
                RewardCategory(category = "Custom Retail", label = "Local Boutiques", multiplier = 3.0)
            )
        )
        val amexGold = CreditCardTemplates.ALL.first { it.id == "amex_gold" }
        val merged = amexGold.applyToProfile(existing)

        val uberBenefit = merged.benefits.first { it.title.contains("Uber Cash") }
        assertEquals(listOf("2026-01", "2026-02", "2026-03"), uberBenefit.usedPeriods)
        assertTrue(merged.benefits.any { it.title == "Custom Airport Lounge Pass" })
        assertTrue(merged.rewardCategories.any { it.label == "Local Boutiques" })
        assertEquals(325_00L, merged.annualFeeCents)
    }

    @Test
    fun normalizeCategoryBucketMatchesWholeWordsWithoutSubwordFalsePositives() {
        assertEquals("Travel & Flights", CreditCardStrategyEngine.normalizeCategoryBucket("Las Vegas Hotels"))
        assertEquals("Dining & Restaurants", CreditCardStrategyEngine.normalizeCategoryBucket("Fine Dining Experiences"))
        assertEquals("Gas & Transit", CreditCardStrategyEngine.normalizeCategoryBucket("Shell Gas & EV Charging"))
        assertEquals("Everything Else (Catch-All)", CreditCardStrategyEngine.normalizeCategoryBucket("Everything Else Base"))
    }

    @Test
    fun summarizePortfolioWithHouseholdMembersAndAuthorizedUsers() {
        val member1 = HouseholdMember(id = 10L, name = "Alex Smith")
        val member2 = HouseholdMember(id = 20L, name = "Sam Smith")

        val accounts = listOf(
            creditAccount(
                1L, "Sapphire Reserve",
                strategy = CreditCardStrategy(
                    cardholderMemberId = 10L,
                    authorizedUserMemberIds = listOf(20L),
                    openedDate = "2025-06-15"
                )
            ),
            creditAccount(
                2L, "Amex Gold",
                strategy = CreditCardStrategy(
                    cardholderMemberId = 20L,
                    openedDate = "2025-09-20"
                )
            ),
            creditAccount(
                3L, "Ink Business Cash",
                isBusinessCard = true,
                strategy = CreditCardStrategy(
                    cardholderMemberId = 10L,
                    authorizedUserMemberIds = listOf(20L),
                    openedDate = "2025-03-10",
                    isBusinessCard = true
                )
            )
        )

        val summary = CreditCardStrategyEngine.summarizePortfolio(
            accounts,
            emptyList(),
            today = refToday,
            householdMembers = listOf(member1, member2)
        )

        assertEquals(2, summary.availablePlayers.size)
        val player1 = summary.availablePlayers.find { it.first == "m:10" }
        val player2 = summary.availablePlayers.find { it.first == "m:20" }
        assertNotNull(player1)
        assertNotNull(player2)
        assertEquals("Alex Smith", player1.second)
        assertEquals("Sam Smith", player2.second)

        val alex524 = summary.chase524ByPerson.find { it.holderKey == "m:10" }
        assertNotNull(alex524)
        assertEquals(1, alex524.summary.count)
        assertEquals(1, alex524.summary.slots.size)
        assertFalse(alex524.summary.slots[0].isAuthorizedUser)

        val sam524 = summary.chase524ByPerson.find { it.holderKey == "m:20" }
        assertNotNull(sam524)
        assertEquals(2, sam524.summary.count)
        val csrSlot = sam524.summary.slots.find { it.accountId == 1L }
        val amexSlot = sam524.summary.slots.find { it.accountId == 2L }
        assertNotNull(csrSlot)
        assertNotNull(amexSlot)
        assertTrue(csrSlot.isAuthorizedUser)
        assertFalse(amexSlot.isAuthorizedUser)
    }

    @Test
    fun summarizePortfolioResolvesMemberNamesAcrossPerksSubsAndBestCards() {
        val member = HouseholdMember(id = 10L, name = "Alex Smith")
        val account = Account(
            id = 77L,
            name = "Sapphire Preferred",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 77L,
                cardName = "Sapphire Preferred",
                annualFeeCents = 95_00L,
                benefits = listOf(
                    CardBenefit(id = "hotel", title = "$50 Hotel Credit", periodValueCents = 50_00L, cadence = BenefitCadence.ANNUAL)
                ),
                rewardCategories = listOf(
                    RewardCategory(category = "Dining & Restaurants", multiplier = 3.0)
                ),
                strategy = CreditCardStrategy(
                    cardholder = null,
                    cardholderMemberId = 10L,
                    openedDate = "2026-10-01",
                    subTargetSpendCents = 4000_00L,
                    subDeadlineDate = "2027-01-01"
                )
            )
        )

        val summary = CreditCardStrategyEngine.summarizePortfolio(
            accounts = listOf(account),
            transactions = emptyList(),
            today = refToday,
            householdMembers = listOf(member)
        )

        assertEquals("Alex Smith", summary.currentPeriodPerks.single().cardholder)
        assertEquals("Alex Smith", summary.activeSubs.single().cardholder)
        assertEquals("Alex Smith", summary.bestCardsByCategory.single().cardholder)
        assertEquals("Alex Smith", summary.chase524ByPerson.single().summary.slots.single().cardholder)
    }

    @Test
    fun computeBestCardsByCategory_preservesRotatingNotesAndPrefersNonRotatingOnTie() {
        val rotatingFirst = Account(
            id = 1L,
            name = "Freedom Flex",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 1L,
                cardName = "Freedom Flex",
                rewardCategories = listOf(
                    RewardCategory(category = "Dining", multiplier = 3.0, isRotating = true, notes = "Q1 bonus"),
                    RewardCategory(category = "Gas Stations", multiplier = 5.0, isRotating = true, notes = "Q4 5% up to \$1,500")
                )
            )
        )
        val fixedSecond = Account(
            id = 2L,
            name = "Sapphire Preferred",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 2L,
                cardName = "Sapphire Preferred",
                rewardCategories = listOf(
                    RewardCategory(category = "Dining", multiplier = 3.0, isRotating = false)
                )
            )
        )

        val picks = CreditCardStrategyEngine.computeBestCardsByCategory(
            accounts = listOf(rotatingFirst, fixedSecond),
            householdMembers = emptyList()
        ).associateBy { it.categoryName }

        val dining = picks["Dining & Restaurants"]
        assertNotNull(dining)
        assertEquals(2L, dining.accountId)
        assertFalse(dining.isRotating)

        val gas = picks["Gas & Transit"]
        assertNotNull(gas)
        assertEquals(1L, gas.accountId)
        assertTrue(gas.isRotating)
        assertEquals("Q4 5% up to \$1,500", gas.notes)
    }

    @Test
    fun computeChase524Status_populatesMissingOpenDateAccountIds() {
        val datedCard = Account(
            id = 10L,
            name = "Card 1",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 10L,
                cardName = "Card 1",
                strategy = CreditCardStrategy(openedDate = "2026-01-15")
            )
        )
        val missing1 = Account(
            id = 11L,
            name = "Missing 1",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 11L,
                cardName = "Missing 1",
                strategy = CreditCardStrategy(openedDate = null)
            )
        )
        val missing2 = Account(
            id = 12L,
            name = "Missing 2",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 12L,
                cardName = "Missing 2",
                strategy = CreditCardStrategy(openedDate = null)
            )
        )

        val summary = CreditCardStrategyEngine.computeChase524Status(
            creditAccounts = listOf(datedCard, missing1, missing2),
            today = refToday
        )

        assertEquals(1, summary.count)
        assertEquals(2, summary.missingOpenDateCount)
        assertEquals(listOf(11L, 12L), summary.missingOpenDateAccountIds)
    }

    @Test
    fun creditCardHubStateCalculator_scopeForPlayerFiltersCreditFeesAndFixTargets() {
        val alex = HouseholdMember(id = 10L, name = "Alex")
        val sam = HouseholdMember(id = 20L, name = "Sam")
        val feeDueSoon = app.overbay.orbit.domain.LocalDateUtils.formatIsoDate(
            app.overbay.orbit.domain.LocalDateUtils.addDays(refToday, 15)
        )

        val alexCard = Account(
            id = 1L,
            name = "Alex Sapphire",
            type = AccountType.CREDIT,
            currentBalanceCents = -100_000L,
            creditCardProfile = CreditCardProfile(
                accountId = 1L,
                cardName = "Alex Sapphire",
                creditLimitCents = 1_000_000L,
                annualFeeCents = 95_00L,
                annualFeePostDate = feeDueSoon,
                statementClosingDay = null,
                paymentDueDay = null,
                strategy = CreditCardStrategy(cardholderMemberId = 10L, openedDate = null)
            )
        )
        val samCard = Account(
            id = 2L,
            name = "Sam Gold",
            type = AccountType.CREDIT,
            currentBalanceCents = -200_000L,
            creditCardProfile = CreditCardProfile(
                accountId = 2L,
                cardName = "Sam Gold",
                creditLimitCents = 500_000L,
                annualFeeCents = 250_00L,
                annualFeePostDate = feeDueSoon,
                statementClosingDay = 15,
                paymentDueDay = 10,
                strategy = CreditCardStrategy(cardholderMemberId = 20L, openedDate = null)
            )
        )

        val samBundle = app.overbay.orbit.ui.accounts.CreditCardHubStateCalculator.calculate(
            accounts = listOf(alexCard, samCard),
            transactions = emptyList(),
            selectedPlayer = "m:20",
            today = refToday,
            householdMembers = listOf(alex, sam)
        )

        assertEquals(setOf(2L), samBundle.playerScopedHub.walletAccountIds)
        assertEquals(200_000L, samBundle.playerScopedHub.householdCredit.totalOwedCents)
        assertEquals(500_000L, samBundle.playerScopedHub.householdCredit.totalLimitCents)
        assertEquals(listOf(2L), samBundle.playerScopedHub.feesAhead.map { it.status.accountId })
        assertEquals(0, samBundle.playerScopedHub.missingBillingDatesCount)
        assertEquals(null, samBundle.playerScopedHub.firstMissingBillingAccountId)
        assertEquals(2L, samBundle.playerScopedHub.firstMissingOpenDateAccountId)

        val alexBundle = app.overbay.orbit.ui.accounts.CreditCardHubStateCalculator.calculate(
            accounts = listOf(alexCard, samCard),
            transactions = emptyList(),
            selectedPlayer = "m:10",
            today = refToday,
            householdMembers = listOf(alex, sam)
        )

        assertEquals(setOf(1L), alexBundle.playerScopedHub.walletAccountIds)
        assertEquals(100_000L, alexBundle.playerScopedHub.householdCredit.totalOwedCents)
        assertEquals(listOf(1L), alexBundle.playerScopedHub.feesAhead.map { it.status.accountId })
        assertEquals(1, alexBundle.playerScopedHub.missingBillingDatesCount)
        assertEquals(1L, alexBundle.playerScopedHub.firstMissingBillingAccountId)
        assertEquals(1L, alexBundle.playerScopedHub.firstMissingOpenDateAccountId)
    }

    @Test
    fun summarizePortfolio_playerFilterIncludesAuthorizedUsersForPerksFeesSubsAndBestCards() {
        val alex = HouseholdMember(id = 10L, name = "Alex")
        val sam = HouseholdMember(id = 20L, name = "Sam")
        val cardWithSamAu = Account(
            id = 1L,
            name = "Alex Gold",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 1L,
                cardName = "Alex Gold",
                annualFeeCents = 250_00L,
                benefits = listOf(
                    CardBenefit(id = "dining", title = "Dining Credit", periodValueCents = 20_00L, cadence = BenefitCadence.MONTHLY)
                ),
                rewardCategories = listOf(
                    RewardCategory(category = "Dining", multiplier = 4.0, rateType = "points")
                ),
                strategy = CreditCardStrategy(
                    cardholderMemberId = 10L,
                    authorizedUserMemberIds = listOf(20L),
                    openedDate = "2026-10-01",
                    subTargetSpendCents = 4000_00L,
                    subDeadlineDate = "2027-01-01"
                )
            )
        )
        val samPrimary = Account(
            id = 2L,
            name = "Sam Preferred",
            type = AccountType.CREDIT,
            creditCardProfile = CreditCardProfile(
                accountId = 2L,
                cardName = "Sam Preferred",
                annualFeeCents = 95_00L,
                benefits = listOf(
                    CardBenefit(id = "hotel", title = "Hotel Credit", periodValueCents = 50_00L, cadence = BenefitCadence.ANNUAL)
                ),
                rewardCategories = listOf(
                    RewardCategory(category = "Travel", multiplier = 3.0, rateType = "points")
                ),
                strategy = CreditCardStrategy(cardholderMemberId = 20L, openedDate = "2025-05-01")
            )
        )

        val samPortfolio = CreditCardStrategyEngine.summarizePortfolio(
            accounts = listOf(cardWithSamAu, samPrimary),
            transactions = emptyList(),
            selectedPlayer = "m:20",
            today = refToday,
            householdMembers = listOf(alex, sam)
        )

        assertEquals(2, samPortfolio.totalCardsCount)
        assertEquals(345_00L, samPortfolio.totalAnnualFeesCents)
        assertEquals(setOf("dining", "hotel"), samPortfolio.currentPeriodPerks.map { it.benefitId }.toSet())
        assertEquals(listOf(1L), samPortfolio.activeSubs.map { it.accountId })
        assertEquals(setOf("Dining & Restaurants", "Travel & Flights"), samPortfolio.bestCardsByCategory.map { it.categoryName }.toSet())
    }

    @Test
    fun creditCardHubStateCalculator_zeroActiveCardsForPlayerDoesNotLeakHouseholdTotals() {
        val alex = HouseholdMember(id = 10L, name = "Alex")
        val sam = HouseholdMember(id = 20L, name = "Sam")
        val feeDueSoon = LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(refToday, 15))
        val alexActive = Account(
            id = 1L,
            name = "Alex Active",
            type = AccountType.CREDIT,
            currentBalanceCents = -150_000L,
            creditCardProfile = CreditCardProfile(
                accountId = 1L,
                cardName = "Alex Active",
                creditLimitCents = 1_000_000L,
                annualFeeCents = 95_00L,
                annualFeePostDate = feeDueSoon,
                statementClosingDay = 12,
                paymentDueDay = 5,
                strategy = CreditCardStrategy(cardholderMemberId = 10L, openedDate = null)
            )
        )
        val samArchived = Account(
            id = 2L,
            name = "Sam Closed",
            type = AccountType.CREDIT,
            isArchived = true,
            currentBalanceCents = -50_000L,
            creditCardProfile = CreditCardProfile(
                accountId = 2L,
                cardName = "Sam Closed",
                creditLimitCents = 500_000L,
                strategy = CreditCardStrategy(cardholderMemberId = 20L, openedDate = "2025-11-01")
            )
        )

        val bundle = app.overbay.orbit.ui.accounts.CreditCardHubStateCalculator.calculate(
            accounts = listOf(alexActive, samArchived),
            transactions = emptyList(),
            selectedPlayer = "m:20",
            today = refToday,
            householdMembers = listOf(alex, sam)
        )
        val uiState = app.overbay.orbit.ui.accounts.AccountsUiState(
            selectedCardPlayer = bundle.portfolio.selectedPlayer,
            cardPortfolioSummary = bundle.portfolio,
            householdCredit = bundle.householdCredit,
            cardCycles = bundle.cardCycles,
            feesAhead = bundle.feesAhead,
            playerScopedHub = bundle.playerScopedHub
        )

        assertTrue(uiState.playerScopedHub.walletAccountIds.isEmpty())
        assertEquals(0L, uiState.effectiveHouseholdCredit?.totalOwedCents)
        assertEquals(0L, uiState.effectiveHouseholdCredit?.totalLimitCents)
        assertTrue(uiState.effectiveFeesAhead.isEmpty())
        assertTrue(uiState.effectiveCardCycles.isEmpty())
        assertNull(uiState.playerScopedHub.firstMissingOpenDateAccountId)
    }
}
