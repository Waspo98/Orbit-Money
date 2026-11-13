package app.overbay.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Shared header row for OrbitModalSheet editors (optional back button, title, subtitle,
 * trailing action chips, and circular close action).
 */
@Composable
fun OrbitSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onDismiss: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    closeEnabled: Boolean = true,
    trailingActions: (@Composable RowScope.() -> Unit)? = null
) {
    val haptics = LocalOrbitHaptics.current
    val sheetDismiss = LocalOrbitSheetDismiss.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onNavigateBack != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            onNavigateBack()
                        },
                        enabled = closeEnabled,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (trailingActions != null || onDismiss != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trailingActions != null) {
                    trailingActions()
                }
                if (onDismiss != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                sheetDismiss?.invoke(onDismiss) ?: onDismiss()
                            },
                            enabled = closeEnabled,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared surface + 1dp top border + optional error banner container for pinned modal footers.
 */
@Composable
fun OrbitModalFooterFrame(
    modifier: Modifier = Modifier,
    errorBannerText: String? = null,
    onErrorClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptics = LocalOrbitHaptics.current

    Surface(
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

            if (!errorBannerText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .let { base ->
                            if (onErrorClick != null) {
                                base.clickable {
                                    haptics.tick()
                                    onErrorClick()
                                }
                            } else base
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = errorBannerText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (onErrorClick != null) {
                            Text(
                                text = "View",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            content()
        }
    }
}

/**
 * Pinned bottom action bar for Orbit modals and two-pane inspector editors.
 * Always remains anchored beneath scrollable body content.
 */
@Composable
fun OrbitModalFooter(
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = "Cancel",
    onSecondaryAction: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    isSaving: Boolean = false,
    errorBannerText: String? = null,
    onErrorClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    val haptics = LocalOrbitHaptics.current
    val sheetDismiss = LocalOrbitSheetDismiss.current

    OrbitModalFooterFrame(
        modifier = modifier,
        errorBannerText = errorBannerText,
        onErrorClick = onErrorClick,
        containerColor = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!dismissText.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        haptics.tick()
                        if (onSecondaryAction != null) {
                            onSecondaryAction()
                        } else {
                            sheetDismiss?.invoke(onDismiss) ?: onDismiss()
                        }
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(dismissText, fontWeight = FontWeight.SemiBold)
                }
            }

            Button(
                onClick = {
                    haptics.confirm()
                    onConfirm()
                },
                enabled = confirmEnabled && !isSaving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(if (dismissText.isNullOrBlank()) 1f else 1.25f)
                    .height(48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Shared modal editor scaffold for both [OrbitModalSheet] and two-pane inspector panes.
 * Keeps header and [OrbitModalFooter] pinned while only the middle body scrolls.
 */
@Composable
fun OrbitModalScaffold(
    onDismiss: () -> Unit,
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
    footerContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    bodyPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    bodySpacing: Dp = 14.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            OrbitSheetHeader(
                title = title,
                subtitle = subtitle,
                onDismiss = onDismiss,
                onNavigateBack = onNavigateBack,
                closeEnabled = !isSaving,
                trailingActions = headerTrailingActions,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            if (showHeaderDivider) {
                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .let { base ->
                    if (scrollable) base.verticalScroll(rememberScrollState()) else base
                }
                .padding(bodyPadding),
            verticalArrangement = Arrangement.spacedBy(bodySpacing),
            content = content
        )

        OrbitModalFooter(
            confirmText = confirmText,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            dismissText = dismissText,
            onSecondaryAction = onSecondaryAction,
            confirmEnabled = confirmEnabled,
            isSaving = isSaving,
            errorBannerText = errorBannerText,
            onErrorClick = onErrorClick,
            containerColor = footerContainerColor
        )
    }
}
