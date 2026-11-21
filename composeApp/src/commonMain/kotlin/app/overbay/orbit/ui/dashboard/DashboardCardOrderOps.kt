package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.OrbitPreferences

/**
 * Builds the canonical sorted list of all [DashboardCardType] widget configurations,
 * filling in any missing cards with defaults and renumbering [DashboardWidgetConfig.sortOrder].
 */
internal fun normalizeDashboardWidgets(
    raw: List<DashboardWidgetConfig>
): MutableList<DashboardWidgetConfig> {
    val source = raw.ifEmpty { OrbitPreferences.defaultDashboardWidgets() }
    return DashboardCardType.entries.map { type ->
        val existing = source.find {
            it.widgetId.equals(type.widgetId, ignoreCase = true) ||
                DashboardCardType.fromWidgetId(it.widgetId) == type
        }
        DashboardWidgetConfig(
            widgetId = type.widgetId,
            title = existing?.title ?: type.defaultTitle,
            isVisible = existing?.isVisible ?: true,
            sortOrder = existing?.sortOrder ?: type.defaultSortOrder,
            wideSpan = (existing?.wideSpan ?: type.defaultWideSpan).let { if (it == 2) 2 else 1 }
        )
    }.sortedBy { it.sortOrder }
        .mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        .toMutableList()
}

/**
 * Reorders visible dashboard cards according to [orderedVisibleTypes] while preserving the
 * exact slot positions, visibility, and wide-span settings of any hidden/filtered cards
 * (such as [DashboardCardType.MHA_SUMMARY] when MHA tracking is disabled).
 */
internal fun reorderVisibleDashboardWidgets(
    normalizedWidgets: List<DashboardWidgetConfig>,
    orderedVisibleTypes: List<DashboardCardType>
): List<DashboardWidgetConfig> {
    if (orderedVisibleTypes.isEmpty()) return normalizedWidgets
    val visibleSet = orderedVisibleTypes.toSet()
    val byType = normalizedWidgets.associateBy { DashboardCardType.fromWidgetId(it.widgetId) }
    val orderedConfigs = orderedVisibleTypes.mapNotNull { byType[it] }
    if (orderedConfigs.size != orderedVisibleTypes.size) return normalizedWidgets

    val visibleSlots = normalizedWidgets.indices.filter { idx ->
        DashboardCardType.fromWidgetId(normalizedWidgets[idx].widgetId) in visibleSet
    }
    if (visibleSlots.size != orderedConfigs.size) return normalizedWidgets

    val merged = normalizedWidgets.toMutableList()
    visibleSlots.forEachIndexed { pos, fullIdx ->
        merged[fullIdx] = orderedConfigs[pos]
    }
    return merged.mapIndexed { idx, config -> config.copy(sortOrder = idx) }
}

/**
 * Moves a dashboard widget using visible-list indices ([fromVisibleIndex], [toVisibleIndex])
 * while preserving hidden [DashboardCardType.MHA_SUMMARY] when [mhaEnabled] is false.
 */
internal fun moveVisibleDashboardWidget(
    normalizedWidgets: List<DashboardWidgetConfig>,
    mhaEnabled: Boolean,
    fromVisibleIndex: Int,
    toVisibleIndex: Int
): List<DashboardWidgetConfig> {
    val visibleSlots = normalizedWidgets.indices.filter { idx ->
        mhaEnabled || DashboardCardType.fromWidgetId(normalizedWidgets[idx].widgetId) != DashboardCardType.MHA_SUMMARY
    }
    if (fromVisibleIndex !in visibleSlots.indices ||
        toVisibleIndex !in visibleSlots.indices ||
        fromVisibleIndex == toVisibleIndex
    ) {
        return normalizedWidgets
    }
    val reorderedVisible = visibleSlots.map { normalizedWidgets[it] }.toMutableList()
    val moved = reorderedVisible.removeAt(fromVisibleIndex)
    reorderedVisible.add(toVisibleIndex, moved)

    val merged = normalizedWidgets.toMutableList()
    visibleSlots.forEachIndexed { pos, fullIdx ->
        merged[fullIdx] = reorderedVisible[pos]
    }
    return merged.mapIndexed { idx, config -> config.copy(sortOrder = idx) }
}
