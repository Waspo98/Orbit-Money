package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Transaction
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
}
