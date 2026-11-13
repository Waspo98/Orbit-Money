package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings
import kotlinx.coroutines.delay

/**
 * Stable-keyed wrapper for an editable or dismissible list item so rows preserve focus and can
 * complete their entrance and exit transitions before being removed from state.
 */
data class OrbitAnimatedEntry<T>(
    val key: Long,
    val value: T,
    val animateEnter: Boolean = false,
    val isRemoving: Boolean = false
)

/**
 * Non-snapshot monotonic key generator scoped cleanly inside `remember(...)` blocks.
 */
class OrbitEntryKeyGenerator(initialNextKey: Long = 1L) {
    private var next = initialNextKey
    fun nextKey(): Long = next++
}

fun <T> MutableList<OrbitAnimatedEntry<T>>.updateActiveEntry(
    key: Long,
    transform: (T) -> T
) {
    val index = indexOfFirst { it.key == key && !it.isRemoving }
    if (index >= 0) {
        val current = this[index]
        this[index] = current.copy(value = transform(current.value))
    }
}

fun <T> MutableList<OrbitAnimatedEntry<T>>.markEntryRemoving(
    key: Long,
    minActiveCount: Int = 1
) {
    if (count { !it.isRemoving } <= minActiveCount) return
    val index = indexOfFirst { it.key == key && !it.isRemoving }
    if (index >= 0) {
        this[index] = this[index].copy(isRemoving = true)
    }
}

/**
 * Column of stable-keyed rows that smoothly unfurl on insertion, exclude soft-removed rows from
 * active counts immediately, and defer list removal until the exit spring settles.
 */
@Composable
fun <T> OrbitAnimatedEntryColumn(
    entries: List<OrbitAnimatedEntry<T>>,
    onRemoveFinished: (Long) -> Unit,
    modifier: Modifier = Modifier,
    minActiveCount: Int = 1,
    itemSpacing: Dp = 0.dp,
    bringIntoViewOnAdd: Boolean = true,
    connectorContent: (@Composable (showConnector: Boolean) -> Unit)? = null,
    rowContent: @Composable (entry: OrbitAnimatedEntry<T>, displayIndex: Int, canDelete: Boolean) -> Unit
) {
    val activeIndicesByKey = remember(entries) {
        var activeIndex = 0
        buildMap {
            entries.forEach { entry ->
                if (!entry.isRemoving) {
                    put(entry.key, activeIndex++)
                }
            }
        }
    }
    val activeCount = activeIndicesByKey.size

    Column(modifier = modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            val activeIndex = activeIndicesByKey[entry.key]
            val canDelete = activeCount > minActiveCount && !entry.isRemoving
            key(entry.key) {
                OrbitAnimatedEntryContainer(
                    entry = entry,
                    activeIndex = activeIndex,
                    itemSpacing = itemSpacing,
                    bringIntoViewOnAdd = bringIntoViewOnAdd,
                    onRemoveFinished = onRemoveFinished,
                    connectorContent = connectorContent
                ) { displayIndex ->
                    rowContent(entry, displayIndex, canDelete)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> OrbitAnimatedEntryContainer(
    entry: OrbitAnimatedEntry<T>,
    activeIndex: Int?,
    itemSpacing: Dp,
    bringIntoViewOnAdd: Boolean,
    onRemoveFinished: (Long) -> Unit,
    connectorContent: (@Composable (showConnector: Boolean) -> Unit)?,
    content: @Composable (displayIndex: Int) -> Unit
) {
    val visibleState = remember { MutableTransitionState(!entry.animateEnter) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var hasBroughtIntoView by remember { mutableStateOf(false) }

    // Latch index/connector state while exiting so labels and dividers don't jump mid-collapse.
    var lastActiveIndex by remember { mutableIntStateOf(activeIndex ?: 0) }
    var showConnector by remember { mutableStateOf((activeIndex ?: 0) > 0) }
    if (!entry.isRemoving && activeIndex != null) {
        lastActiveIndex = activeIndex
        showConnector = activeIndex > 0
    }

    LaunchedEffect(entry.isRemoving) {
        visibleState.targetState = !entry.isRemoving
    }

    LaunchedEffect(
        entry.isRemoving,
        visibleState.isIdle,
        visibleState.currentState,
        visibleState.targetState
    ) {
        if (entry.isRemoving && visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
            onRemoveFinished(entry.key)
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = expandVertically(
            animationSpec = OrbitSprings.IntSizeSpatialDefault,
            expandFrom = Alignment.Top
        ) + scaleIn(
            animationSpec = OrbitSprings.FloatSpatialDefault,
            initialScale = 0.96f,
            transformOrigin = TransformOrigin(0.5f, 0f)
        ) + fadeIn(animationSpec = OrbitSprings.FloatEffectsFast),
        exit = shrinkVertically(
            animationSpec = OrbitSprings.IntSizeSpatialDefault,
            shrinkTowards = Alignment.Top
        ) + scaleOut(
            animationSpec = OrbitSprings.FloatSpatialDefault,
            targetScale = 0.96f,
            transformOrigin = TransformOrigin(0.5f, 0f)
        ) + fadeOut(animationSpec = OrbitSprings.FloatEffectsFast),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (bringIntoViewOnAdd && entry.animateEnter && !entry.isRemoving && !hasBroughtIntoView) {
            LaunchedEffect(entry.key) {
                delay(180)
                bringIntoViewRequester.bringIntoView()
                hasBroughtIntoView = true
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
        ) {
            if (connectorContent != null) {
                connectorContent(showConnector)
            } else if (itemSpacing > 0.dp) {
                OrbitAccordionVisibility(visible = showConnector) {
                    Spacer(modifier = Modifier.height(itemSpacing))
                }
            }
            content(lastActiveIndex)
        }
    }
}
