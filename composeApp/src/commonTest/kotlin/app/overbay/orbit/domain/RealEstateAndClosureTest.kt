package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.InstitutionLogoDefaults
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.ui.accounts.AccountClosureController
import app.overbay.orbit.ui.accounts.ClosureUiOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealEstateAndClosureTest {

    @Test
    fun buildPropertyUnits_handlesCombinedMortgageAndLinkedHeloc() {
        val doubletree = Account(
            id = 10L,
            name = "2104 Doubletree Lane",
            type = AccountType.MORTGAGE,
            institution = "New American Funding",
            currentBalanceCents = -396_774_50L,
            estimatedValueCents = 450_000_00L
        )
        val heloc = Account(
            id = 11L,
            name = "Doubletree HELOC",
            type = AccountType.LOAN,
            institution = "Chase Bank",
            currentBalanceCents = -10_000_00L,
            linkedPropertyAccountId = 10L
        )

        val units = RealEstatePropertyEngine.buildPropertyUnits(listOf(doubletree, heloc))
        assertEquals(1, units.size)

        val unit = units.single()
        assertEquals("2104 Doubletree Lane", unit.title)
        assertEquals(450_000_00L, unit.estimatedValueCents)
        assertEquals(2, unit.linkedLoans.size)
        assertEquals("New American Funding", RealEstatePropertyEngine.loanDisplayLabel(doubletree, unit))
        assertEquals("Doubletree HELOC", RealEstatePropertyEngine.loanDisplayLabel(heloc, unit))
        assertEquals(406_774_50L, unit.totalDebtCents)
        assertEquals(43_225_50L, unit.netEquityCents)
        assertFalse(unit.isPaidOff)
    }

    @Test
    fun netWorthEngine_matchesCombinedVsStandalonePropertyWithLinkedMortgage() {
        val combined = listOf(
            Account(
                id = 1L,
                name = "2104 Doubletree Lane",
                type = AccountType.MORTGAGE,
                currentBalanceCents = -396_774_50L,
                estimatedValueCents = 450_000_00L
            )
        )

        val split = listOf(
            Account(
                id = 2L,
                name = "2104 Doubletree Lane",
                type = AccountType.OTHER,
                isProperty = true,
                estimatedValueCents = 450_000_00L
            ),
            Account(
                id = 3L,
                name = "New American Funding",
                type = AccountType.MORTGAGE,
                currentBalanceCents = -396_774_50L,
                linkedPropertyAccountId = 2L
            )
        )

        val snapCombined = NetWorthEngine.computeCurrentSnapshot(combined)
        val snapSplit = NetWorthEngine.computeCurrentSnapshot(split)

        assertEquals(53_225_50L, snapCombined.realEstateEquityCents)
        assertEquals(snapCombined.realEstateEquityCents, snapSplit.realEstateEquityCents)
        assertEquals(snapCombined.totalAssetsCents, snapSplit.totalAssetsCents)
        assertEquals(snapCombined.totalLiabilitiesCents, snapSplit.totalLiabilitiesCents)
        assertEquals(snapCombined.netWorthCents, snapSplit.netWorthCents)
    }

    @Test
    fun closureDetector_catchesParkRidgePayoffAndMissingSimpleFin() {
        val parkRidge = Account(
            id = 20L,
            name = "2008 Park Ridge (8143)",
            type = AccountType.MORTGAGE,
            institution = "Chase Bank",
            currentBalanceCents = -74_571_57L,
            estimatedValueCents = 300_000_00L,
            isManual = false,
            simplefinAccountId = "sf-8143"
        )
        val payoffTxn = Transaction(
            id = 501L,
            accountId = 20L,
            originalDate = "2026-10-20",
            originalAmountCents = 74_255_42L,
            originalMerchant = "Sale of Park Ridge"
        )

        val candidates = AccountClosureDetector.detectCandidates(
            accounts = listOf(parkRidge),
            transactions = listOf(payoffTxn),
            todayIso = "2026-11-11"
        )
        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals(ClosureReason.LARGE_PAYOFF_CREDIT, candidate.reason)
        assertTrue(candidate.hasPropertyContext)
        assertNotNull(candidate.payoffTransaction)

        // Dismissed prompt stays quiet until a newer SimpleFIN missing signal arrives
        val dismissed = parkRidge.copy(closurePromptDismissedAt = "2026-11-11T15:00:00")
        assertTrue(
            AccountClosureDetector.detectCandidates(listOf(dismissed), listOf(payoffTxn), "2026-11-11").isEmpty()
        )

        val newlyMissing = dismissed.copy(simplefinMissingSince = "2026-11-12T06:00:00")
        val reflagged = AccountClosureDetector.detectCandidates(listOf(newlyMissing), listOf(payoffTxn), "2026-11-12")
        assertEquals(1, reflagged.size)
        assertEquals(ClosureReason.MISSING_FROM_SIMPLEFIN, reflagged.single().reason)
    }

    @Test
    fun closureSelection_defaultsSmartlyAndSwitchesSelectedResolution() {
        val parkRidge = Account(
            id = 20L,
            name = "2008 Park Ridge (8143)",
            type = AccountType.MORTGAGE,
            currentBalanceCents = -74_571_57L,
            estimatedValueCents = 300_000_00L
        )
        val saleTxn = Transaction(
            id = 501L,
            accountId = 20L,
            originalDate = "2026-10-20",
            originalAmountCents = 74_255_42L,
            originalMerchant = "Sale of Park Ridge"
        )
        val regularPayoffTxn = saleTxn.copy(originalMerchant = "Mortgage Principal Payoff")

        val saleCandidate = AccountClosureDetector
            .detectCandidates(listOf(parkRidge), listOf(saleTxn), "2026-11-11")
            .single()
        assertEquals(AccountClosureResolution.SOLD_PROPERTY, saleCandidate.defaultResolution())

        val payoffCandidate = AccountClosureDetector
            .detectCandidates(listOf(parkRidge), listOf(regularPayoffTxn), "2026-11-11")
            .single()
        assertEquals(AccountClosureResolution.PAID_OFF_KEEP_PROPERTY, payoffCandidate.defaultResolution())

        // Selecting another card option updates active resolution cleanly
        val initialOverlay = ClosureUiOverlay()
        assertEquals(
            AccountClosureResolution.SOLD_PROPERTY,
            AccountClosureController.selectActiveResolution(saleCandidate, initialOverlay)
        )

        val switchedOverlay = ClosureUiOverlay(
            selectedResolutions = mapOf(20L to AccountClosureResolution.REFINANCED_KEEP_PROPERTY)
        )
        assertEquals(
            AccountClosureResolution.REFINANCED_KEEP_PROPERTY,
            AccountClosureController.selectActiveResolution(saleCandidate, switchedOverlay)
        )

        // Local preference dismissal overrides suppress prompts even on pre-038 servers
        assertTrue(
            AccountClosureDetector.detectCandidates(
                accounts = listOf(parkRidge),
                transactions = listOf(saleTxn),
                todayIso = "2026-11-11",
                dismissedOverrides = mapOf("20" to "2026-11-11T23:59:59")
            ).isEmpty()
        )
        assertEquals("newamericanfunding.com", InstitutionLogoDefaults.resolveDomain("New American Funding"))
    }

    @Test
    fun linkedMortgage_suppressesStaleInlineHomeEquityAndContributesDebtOnly() {
        val linkedMortgageWithStaleEst = Account(
            id = 30L,
            name = "Refinanced Mortgage",
            type = AccountType.MORTGAGE,
            currentBalanceCents = -380_000_00L,
            estimatedValueCents = 450_000_00L,
            linkedPropertyAccountId = 2L
        )
        assertEquals(null, linkedMortgageWithStaleEst.homeEquityCents)
        assertEquals(-380_000_00L, linkedMortgageWithStaleEst.netWorthContributionCents)
        assertEquals(
            linkedMortgageWithStaleEst.netWorthContributionCents,
            NetWorthEngine.accountContributionCents(linkedMortgageWithStaleEst, linkedMortgageWithStaleEst.currentBalanceCents)
        )
    }
}
