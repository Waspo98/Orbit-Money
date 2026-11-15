package app.overbay.orbit.ui.cardperks

import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.OrbitPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardPerksContractTest {

    @Test
    fun resolveCardPerksItems_hidesStrategyToolsWhenDisabled() {
        val defaults = OrbitPreferences.defaultCardPerksWidgets()

        val withTools = resolveCardPerksItems(defaults, rewardsToolsEnabled = true)
        assertEquals(CardPerksCardType.entries.size, withTools.size)
        assertTrue(withTools.any { it.key == CardPerksCardType.WHICH_CARD_WHERE })
        assertTrue(withTools.any { it.key == CardPerksCardType.WELCOME_BONUSES })
        assertTrue(withTools.any { it.key == CardPerksCardType.CHASE_524 })

        val withoutTools = resolveCardPerksItems(defaults, rewardsToolsEnabled = false)
        assertEquals(4, withoutTools.size)
        assertFalse(withoutTools.any { it.key.isStrategyTool })
    }

    @Test
    fun customizingWhileStrategyToolsDisabledPreservesHiddenStrategyConfigs() {
        val customInitial = listOf(
            DashboardWidgetConfig("card_wallet_roi", "Card Wallet", true, 0, 2),
            DashboardWidgetConfig("which_card_where", "Which Card Where", false, 1, 2),
            DashboardWidgetConfig("perks_this_period", "Perks This Period", true, 2, 2),
            DashboardWidgetConfig("welcome_bonuses", "Welcome Bonuses", true, 3, 2),
            DashboardWidgetConfig("chase_524", "Chase 5/24 Status", false, 4, 1),
            DashboardWidgetConfig("annual_fees_ahead", "Annual Fees Ahead", true, 5, 1),
            DashboardWidgetConfig("payments_due", "Billing & Payments Due", true, 6, 1)
        )

        // Toggle Annual Fees Ahead visibility while rewards tools are disabled
        val afterVisibility = toggleCardPerksWidgetVisibility(customInitial, CardPerksCardType.ANNUAL_FEES_AHEAD)
        // Change Payments Due wide span while rewards tools are disabled
        val afterSpan = setCardPerksWidgetWideSpan(afterVisibility, CardPerksCardType.PAYMENTS_DUE, 2)
        // Reorder visible non-strategy item Payments Due (visible idx 3) to top (visible idx 0)
        val afterMove = moveVisibleCardPerksWidget(
            savedWidgets = afterSpan,
            rewardsToolsEnabled = false,
            fromVisibleIndex = 3,
            toVisibleIndex = 0
        )

        // Turning Strategy Tools back on must still find all 3 strategy widgets with original config intact
        val restored = resolveCardPerksItems(afterMove, rewardsToolsEnabled = true)
        assertEquals(7, restored.size)
        val whichCard = restored.first { it.key == CardPerksCardType.WHICH_CARD_WHERE }
        val welcomeBonuses = restored.first { it.key == CardPerksCardType.WELCOME_BONUSES }
        val chase524 = restored.first { it.key == CardPerksCardType.CHASE_524 }
        assertFalse(whichCard.isVisible)
        assertEquals(2, whichCard.wideSpan)
        assertTrue(welcomeBonuses.isVisible)
        assertEquals(2, welcomeBonuses.wideSpan)
        assertFalse(chase524.isVisible)
        assertEquals(1, chase524.wideSpan)

        // Visible non-strategy order updated cleanly (PAYMENTS_DUE moved to first visible position)
        val restoredVisibleOrder = restored.filter { !it.key.isStrategyTool }.map { it.key }
        assertEquals(
            listOf(
                CardPerksCardType.PAYMENTS_DUE,
                CardPerksCardType.CARD_WALLET_ROI,
                CardPerksCardType.PERKS_THIS_PERIOD,
                CardPerksCardType.ANNUAL_FEES_AHEAD
            ),
            restoredVisibleOrder
        )
        assertFalse(restored.first { it.key == CardPerksCardType.ANNUAL_FEES_AHEAD }.isVisible)
        assertEquals(2, restored.first { it.key == CardPerksCardType.PAYMENTS_DUE }.wideSpan)
    }

    @Test
    fun buildCardPerksGridRows_packsWideAndHalfSpanCardsCorrectly() {
        val visible = resolveCardPerksItems(OrbitPreferences.defaultCardPerksWidgets(), rewardsToolsEnabled = true)
            .map {
                when (it.key) {
                    CardPerksCardType.CARD_WALLET_ROI -> it.copy(wideSpan = 2)
                    CardPerksCardType.PERKS_THIS_PERIOD -> it.copy(wideSpan = 1)
                    CardPerksCardType.WHICH_CARD_WHERE -> it.copy(wideSpan = 1)
                    CardPerksCardType.WELCOME_BONUSES -> it.copy(wideSpan = 1)
                    CardPerksCardType.CHASE_524 -> it.copy(wideSpan = 2)
                    else -> it
                }
            }
        val rows = buildCardPerksGridRows(visible)
        assertTrue(rows[0].isFullSpan)
        assertEquals(CardPerksCardType.CARD_WALLET_ROI, rows[0].leftCard)
        assertFalse(rows[1].isFullSpan)
        assertEquals(CardPerksCardType.PERKS_THIS_PERIOD, rows[1].leftCard)
        assertEquals(CardPerksCardType.WHICH_CARD_WHERE, rows[1].rightCard)
        assertFalse(rows[2].isFullSpan)
        assertEquals(CardPerksCardType.WELCOME_BONUSES, rows[2].leftCard)
        assertEquals(null, rows[2].rightCard)
        assertTrue(rows[3].isFullSpan)
        assertEquals(CardPerksCardType.CHASE_524, rows[3].leftCard)
    }
}
