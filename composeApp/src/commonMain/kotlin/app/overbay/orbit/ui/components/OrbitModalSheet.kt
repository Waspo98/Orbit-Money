package app.overbay.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Provided inside [OrbitModalSheet] so header close buttons, footer cancel buttons, and custom
 * sheet action buttons can play `sheetState.hide()` smoothly before mutating ViewModel state.
 * Remains `null` when components such as [OrbitModalFooter] render inside two-pane inspectors.
 */
val LocalOrbitSheetDismiss = compositionLocalOf<((afterHide: () -> Unit) -> Unit)?> { null }

/**
 * Standard Orbit bottom-up modal sheet that slides in from the bottom of the screen,
 * wraps content height for shorter forms, expands up to the status bar for tall views,
 * and pins an optional [footer] beneath the scrollable body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val animateAndDismiss: ((() -> Unit) -> Unit) = remember(sheetState, scope) {
        { afterHide ->
            scope.launch {
                try {
                    sheetState.hide()
                } finally {
                    afterHide()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = containerColor,
        dragHandle = { OrbitSheetDragHandle() },
        modifier = modifier.statusBarsPadding()
    ) {
        CompositionLocalProvider(LocalOrbitSheetDismiss provides animateAndDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    content = content
                )
                footer?.invoke()
            }
        }
    }
}

/**
 * Confirm/Cancel bottom-up modal sheet primitive with a pinned [OrbitSheetHeader],
 * scrollable body, and always-visible [OrbitModalFooter] anchored beneath the page.
 */
@Composable
fun OrbitModalSheet(
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    headerTrailingActions: (@Composable RowScope.() -> Unit)? = null,
    showHeaderDivider: Boolean = false,
    dismissText: String? = "Cancel",
    onSecondaryAction: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    isSaving: Boolean = false,
    errorBannerText: String? = null,
    onErrorClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    bodyPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    bodySpacing: Dp = 14.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    OrbitModalSheet(
        onDismissRequest = { if (!isSaving) onDismissRequest() },
        modifier = modifier,
        containerColor = containerColor
    ) {
        OrbitModalScaffold(
            onDismiss = onDismissRequest,
            confirmText = confirmText,
            onConfirm = onConfirm,
            title = title,
            subtitle = subtitle,
            onNavigateBack = onNavigateBack,
            headerTrailingActions = headerTrailingActions,
            showHeaderDivider = showHeaderDivider,
            dismissText = dismissText,
            onSecondaryAction = onSecondaryAction,
            confirmEnabled = confirmEnabled,
            isSaving = isSaving,
            errorBannerText = errorBannerText,
            onErrorClick = onErrorClick,
            footerContainerColor = containerColor,
            bodyPadding = bodyPadding,
            bodySpacing = bodySpacing,
            scrollable = scrollable,
            content = content
        )
    }
}

@Composable
private fun OrbitSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        )
    }
}
