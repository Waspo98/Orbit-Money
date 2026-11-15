package app.overbay.orbit.ui.accounts.cards

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import app.overbay.orbit.ui.accounts.PerkToggleOrigin
import app.overbay.orbit.ui.accounts.PerkToggleUndoEvent
import app.overbay.orbit.ui.accounts.formatPerkToggleUndoMessage

/**
 * Shared snackbar effect displaying an actionable Undo toast for perk toggles belonging to [origin],
 * dismissing immediately on consecutive toggles or when the hosting screen leaves composition.
 */
@Composable
fun PerkUndoSnackbarEffect(
    event: PerkToggleUndoEvent?,
    origin: PerkToggleOrigin,
    snackbarHostState: SnackbarHostState,
    onUndo: (PerkToggleUndoEvent) -> Unit,
    onDismissOrigin: (PerkToggleOrigin) -> Unit
) {
    val currentOnUndo by rememberUpdatedState(onUndo)
    val currentOnDismiss by rememberUpdatedState(onDismissOrigin)
    val scopedEvent = event?.takeIf { it.origin == origin }

    DisposableEffect(origin) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
            currentOnDismiss(origin)
        }
    }

    LaunchedEffect(scopedEvent?.eventId) {
        if (scopedEvent == null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = formatPerkToggleUndoMessage(scopedEvent),
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            currentOnUndo(scopedEvent)
        } else {
            currentOnDismiss(origin)
        }
    }
}
