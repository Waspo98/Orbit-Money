package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingHeroHeader
import app.overbay.orbit.util.LocalOrbitHaptics

@Composable
fun OnboardingStorageModeStep(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OnboardingHeroHeader(
            title = "Where Should Your Data Live?",
            subtitle = "You can start locally today and migrate to a self-hosted server later anytime.",
            icon = OrbitIcons.CloudSync
        )

        Spacer(modifier = Modifier.height(22.dp))

        StorageChoiceCard(
            icon = OrbitIcons.Dashboard,
            title = "On This Device (Local)",
            badgeText = "Simple",
            description = "Everything stays privately on this device with zero server setup. No syncing across devices or partner sharing.",
            bullets = listOf(
                "Instant start — no accounts or passwords required",
                "JSON backup export & restore whenever you want",
                "Direct bank sync via SimpleFIN on Android, or CSV / manual entry"
            ),
            highlighted = state.selectedStorageMode == StorageMode.LOCAL,
            onClick = {
                haptics.tick()
                onIntent(OnboardingIntent.SelectStorageMode(StorageMode.LOCAL))
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        StorageChoiceCard(
            icon = OrbitIcons.BackupArchive,
            title = "Self-Hosted with Docker",
            badgeText = "Advanced",
            description = "Connect to your own Orbit Money container for the full multi-device household setup.",
            bullets = listOf(
                "Sync seamlessly across phone, tablet, and desktop web",
                "Share your household with a partner (read or write access)",
                "Automatic background SimpleFIN bank syncing on a schedule",
                "Push notifications and optional SSO / OIDC login"
            ),
            highlighted = state.selectedStorageMode == StorageMode.SELF_HOSTED,
            onClick = {
                haptics.tick()
                onIntent(OnboardingIntent.SelectStorageMode(StorageMode.SELF_HOSTED))
            }
        )
    }
}

@Composable
private fun StorageChoiceCard(
    icon: ImageVector,
    title: String,
    badgeText: String,
    description: String,
    bullets: List<String>,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (highlighted) 1.75.dp else 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OrbitBadge(
                        text = badgeText,
                        containerColor = if (badgeText == "Advanced") {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        contentColor = if (badgeText == "Advanced") {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
                RadioButton(
                    selected = highlighted,
                    onClick = null
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bullets.forEach { bullet ->
                    Text(
                        text = "• $bullet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
