package app.overbay.orbit.ui.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.util.PlatformRuntime

fun LazyListScope.aboutSectionItems(
    preferences: OrbitPreferences,
    onReplayWalkthrough: () -> Unit,
    onSupportClick: () -> Unit,
    onRequestSignOut: () -> Unit
) {
    item(key = "about_orbit") {
        val btnShape = RoundedCornerShape(14.dp)

        OrbitSectionCard(
            title = "About Orbit Money",
            subtitle = "Open source, privacy-focused household finance"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OrbitMiniStatRow(
                        label = "Release Version",
                        value = "v${PlatformRuntime.appVersionName}",
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                    OrbitMiniStatRow(
                        label = "Developer",
                        value = "Neal Overbay",
                        showTopDivider = true
                    )
                    OrbitMiniStatRow(
                        label = "Storage Mode",
                        value = if (preferences.storageMode == StorageMode.LOCAL) {
                            "On This Device"
                        } else {
                            "Self-Hosted Server"
                        },
                        showTopDivider = true
                    )
                    if (preferences.storageMode == StorageMode.SELF_HOSTED &&
                        preferences.selfHostedServerUrl.isNotBlank()
                    ) {
                        OrbitMiniStatRow(
                            label = "Server URL",
                            value = preferences.selfHostedServerUrl,
                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            showTopDivider = true
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

                Text(
                    text = "Designed for simple, private household budgeting across phone, tablet, and web.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReplayWalkthrough,
                        shape = btnShape,
                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Run Setup Guide", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = onSupportClick,
                        shape = btnShape,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Support Development", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (preferences.storageMode == StorageMode.SELF_HOSTED) {
                    OutlinedButton(
                        onClick = onRequestSignOut,
                        shape = btnShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Sign Out", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Text(
                    text = "Copyright 2026 Neal Overbay. All rights reserved.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
