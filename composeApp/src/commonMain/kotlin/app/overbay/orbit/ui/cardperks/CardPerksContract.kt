package app.overbay.orbit.ui.cardperks

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.ui.dashboard.dialogs.OrbitBentoCustomizeItem

/**
 * Enumeration of the 7 customizable Bento cards on the Card Perks & Rewards page.
 * Persists via [OrbitPreferences.cardPerksWidgets].
 */
enum class CardPerksCardType(
    val widgetId: String,
    val defaultTitle: String,
    val defaultDescription: String,
    val defaultSortOrder: Int,
    val defaultWideSpan: Int = 1,
    val isStrategyTool: Boolean = false
) {
    CARD_WALLET_ROI(
        widgetId = "card_wallet_roi",
        defaultTitle = "Card Wallet",
        defaultDescription = "Your credit cards with annual fee vs. credits captured value ledger.",
        defaultSortOrder = 0,
        defaultWideSpan = 2
    ),
    PERKS_THIS_PERIOD(
        widgetId = "perks_this_period",
        defaultTitle = "Perks This Period",
        defaultDescription = "Monthly, quarterly, and annual statement credits left to use.",
        defaultSortOrder = 1,
        defaultWideSpan = 2
    ),
    WHICH_CARD_WHERE(
        widgetId = "which_card_where",
        defaultTitle = "Which Card Where",
        defaultDescription = "Best multiplier card for each spending category at checkout.",
        defaultSortOrder = 2,
        defaultWideSpan = 1,
        isStrategyTool = true
    ),
    WELCOME_BONUSES(
        widgetId = "welcome_bonuses",
        defaultTitle = "Welcome Bonuses",
        defaultDescription = "Active sign-up bonus minimum spend progress and daily pace.",
        defaultSortOrder = 3,
        defaultWideSpan = 1,
        isStrategyTool = true
    ),
    CHASE_524(
        widgetId = "chase_524",
        defaultTitle = "Chase 5/24 Status",
        defaultDescription = "Personal card slots opened in the last 24 months and drop-off dates.",
        defaultSortOrder = 4,
        defaultWideSpan = 1,
        isStrategyTool = true
    ),
    ANNUAL_FEES_AHEAD(
        widgetId = "annual_fees_ahead",
        defaultTitle = "Annual Fees Ahead",
        defaultDescription = "Upcoming annual fee renewals in the next 90 days.",
        defaultSortOrder = 5,
        defaultWideSpan = 1
    ),
    PAYMENTS_DUE(
        widgetId = "payments_due",
        defaultTitle = "Billing & Payments Due",
        defaultDescription = "Statement closing dates, payment due dates, and 0% promo APR alerts.",
        defaultSortOrder = 6,
        defaultWideSpan = 1
    );

    companion object {
        fun fromWidgetId(id: String): CardPerksCardType? =
            entries.firstOrNull { it.widgetId.equals(id, ignoreCase = true) }
    }
}

data class CardPerksGridRow(
    val leftCard: CardPerksCardType,
    val rightCard: CardPerksCardType? = null,
    val isFullSpan: Boolean = false
)

data class CardPerksBentoActions(
    val onInspectAccount: (Long) -> Unit,
    val onOpenCardEditor: (Account) -> Unit,
    val onToggleBenefit: (accountId: Long, benefitId: String) -> Unit,
    val onOpenPerkUsage: (accountId: Long, benefitId: String) -> Unit,
    val onToggleShowUsedPerks: () -> Unit
)

fun resolveCardPerksItems(
    savedWidgets: List<DashboardWidgetConfig>,
    rewardsToolsEnabled: Boolean
): List<OrbitBentoCustomizeItem<CardPerksCardType>> {
    val byType = savedWidgets.mapNotNull { cfg ->
        CardPerksCardType.fromWidgetId(cfg.widgetId)?.let { it to cfg }
    }.toMap()

    return CardPerksCardType.entries
        .filter { !it.isStrategyTool || rewardsToolsEnabled }
        .map { type ->
            val saved = byType[type]
            OrbitBentoCustomizeItem(
                key = type,
                title = type.defaultTitle,
                description = type.defaultDescription,
                isVisible = saved?.isVisible ?: true,
                wideSpan = (saved?.wideSpan ?: type.defaultWideSpan).coerceIn(1, 2)
            ) to (saved?.sortOrder ?: type.defaultSortOrder)
        }
        .sortedBy { it.second }
        .map { it.first }
}

fun buildCardPerksGridRows(
    visibleConfigs: List<OrbitBentoCustomizeItem<CardPerksCardType>>
): List<CardPerksGridRow> {
    val rows = mutableListOf<CardPerksGridRow>()
    var pendingLeft: CardPerksCardType? = null

    for (config in visibleConfigs) {
        if (config.wideSpan == 2) {
            if (pendingLeft != null) {
                rows.add(CardPerksGridRow(leftCard = pendingLeft, rightCard = null, isFullSpan = false))
                pendingLeft = null
            }
            rows.add(CardPerksGridRow(leftCard = config.key, rightCard = null, isFullSpan = true))
        } else {
            if (pendingLeft == null) {
                pendingLeft = config.key
            } else {
                rows.add(CardPerksGridRow(leftCard = pendingLeft, rightCard = config.key, isFullSpan = false))
                pendingLeft = null
            }
        }
    }

    if (pendingLeft != null) {
        rows.add(CardPerksGridRow(leftCard = pendingLeft, rightCard = null, isFullSpan = false))
    }

    return rows
}

fun List<OrbitBentoCustomizeItem<CardPerksCardType>>.toWidgetConfigs(): List<DashboardWidgetConfig> =
    mapIndexed { idx, item ->
        DashboardWidgetConfig(
            widgetId = item.key.widgetId,
            title = item.title,
            isVisible = item.isVisible,
            sortOrder = idx,
            wideSpan = item.wideSpan
        )
    }
