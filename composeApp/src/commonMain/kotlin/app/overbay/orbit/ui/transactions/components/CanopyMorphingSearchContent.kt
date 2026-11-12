package app.overbay.orbit.ui.transactions.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.overbay.orbit.theme.OrbitSprings
import kotlin.math.roundToInt

/**
 * Shared 2-axis morphing canopy search bar and trailing controls layout used inside [MorphingPageHero].
 * Defers [collapseFractionProvider] reads strictly to Compose's Layout phase to eliminate scroll recompositions.
 */
@Composable
fun BoxWithConstraintsScope.CanopyMorphingSearchContent(
    collapseFractionProvider: () -> Float,
    collapsedTitleRightDp: Dp,
    expandedTextBottomPxProvider: () -> Float,
    searchActiveProgress: Float,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    expandedPlaceholder: String,
    compactPlaceholder: String = "Search...",
    trailingControls: @Composable (buttonSize: Dp) -> Unit
) {
    val isCompact by remember(isSearchActive) {
        derivedStateOf { !isSearchActive && collapseFractionProvider() > 0.45f }
    }
    val placeholder = if (isSearchActive || !isCompact) expandedPlaceholder else compactPlaceholder
    val controlsButtonSize by animateDpAsState(
        targetValue = if (isCompact) 38.dp else 46.dp,
        animationSpec = OrbitSprings.DpSpatialFast,
        label = "searchTrailingControlSize"
    )
    val controlsAlpha = (1f - searchActiveProgress * 2.2f).coerceIn(0f, 1f)

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box(
                modifier = Modifier.graphicsLayer { alpha = controlsAlpha },
                contentAlignment = Alignment.CenterEnd
            ) {
                if (controlsAlpha > 0.01f) {
                    trailingControls(controlsButtonSize)
                }
            }
            Box {
                TransactionsSearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = onSearchActiveChange,
                    isCompact = isCompact,
                    placeholderText = placeholder,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) { measurables, constraints ->
        val f = collapseFractionProvider()
        val maxW = constraints.maxWidth.toFloat()
        val restingHeightPx = lerp(46.dp.toPx(), 38.dp.toPx(), f)
        val gapPx = 8.dp.toPx()
        val controlsWidthPx = restingHeightPx * 2f + gapPx
        val totalTrailingReservePx = controlsWidthPx + gapPx

        val collapsedRowLeftPx = (collapsedTitleRightDp + 10.dp).toPx()
        val restingLeftPx = lerp(20.dp.toPx(), collapsedRowLeftPx, f)
        val restingTopPx = lerp(expandedTextBottomPxProvider(), 9.dp.toPx(), f)
        val restingRightMarginPx = lerp(20.dp.toPx(), 16.dp.toPx(), f)
        val restingTotalRowWidthPx = (maxW - restingRightMarginPx - restingLeftPx).coerceAtLeast(160.dp.toPx())
        val restingSearchWidthPx = (restingTotalRowWidthPx - totalTrailingReservePx).coerceAtLeast(72.dp.toPx())

        val currentLeftPx = lerp(restingLeftPx, 12.dp.toPx(), searchActiveProgress)
        val currentTopPx = lerp(restingTopPx, 6.dp.toPx(), searchActiveProgress)
        val currentSearchWidthPx = lerp(restingSearchWidthPx, (maxW - 24.dp.toPx()).coerceAtLeast(100.dp.toPx()), searchActiveProgress)
        val currentHeightPx = lerp(restingHeightPx, 48.dp.toPx(), searchActiveProgress)
        val controlsLeftPx = restingLeftPx + restingSearchWidthPx + gapPx

        val controlsPlaceable = measurables[0].measure(
            Constraints(
                minWidth = 0,
                maxWidth = controlsWidthPx.roundToInt().coerceAtLeast(0),
                minHeight = restingHeightPx.roundToInt().coerceAtLeast(0),
                maxHeight = restingHeightPx.roundToInt().coerceAtLeast(0)
            )
        )
        val searchPlaceable = measurables[1].measure(
            Constraints.fixed(
                currentSearchWidthPx.roundToInt().coerceAtLeast(1),
                currentHeightPx.roundToInt().coerceAtLeast(1)
            )
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            if (controlsAlpha > 0.01f) {
                controlsPlaceable.placeRelative(controlsLeftPx.roundToInt(), restingTopPx.roundToInt())
            }
            searchPlaceable.placeRelative(currentLeftPx.roundToInt(), currentTopPx.roundToInt())
        }
    }
}
