package app.overbay.orbit.ui.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.DateFieldButton
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.onboarding.OnboardingIntent
import app.overbay.orbit.ui.onboarding.OnboardingUiState
import app.overbay.orbit.ui.onboarding.components.OnboardingCardContainer
import app.overbay.orbit.ui.onboarding.components.OnboardingErrorBanner
import app.overbay.orbit.util.LocalOrbitHaptics
import app.overbay.orbit.util.PlatformRuntime
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
internal fun OnboardingSimpleFinCard(
    state: OnboardingUiState,
    onIntent: (OnboardingIntent) -> Unit
) {
    val haptics = LocalOrbitHaptics.current
    val uriHandler = LocalUriHandler.current
    val isWebLocal = PlatformRuntime.isWeb && state.preferences.storageMode == StorageMode.LOCAL

    OnboardingCardContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Automatic Bank Sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OrbitBadge(
                text = "SimpleFIN",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Text(
            text = "SimpleFIN connects your bank accounts to Orbit so your balances and transactions stay up to date on their own. It's a separate service (\$1.50/mo) made for read-only bank syncing—Orbit never sees your bank logins.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        if (state.simpleFinConnected) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = OrbitTheme.colors.incomeContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = OrbitTheme.colors.income, modifier = Modifier.size(20.dp))
                    Text(
                        text = state.simpleFinSummaryText ?: "Connected to SimpleFIN Bridge",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OrbitTheme.colors.onIncomeContainer
                    )
                }
            }
        } else if (isWebLocal) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Browsers block direct bank-bridge connections in offline mode. For automatic SimpleFIN sync, use the Android app or connect a Self-Hosted Docker server. CSV import and manual tracking work right here in the browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    uriHandler.openUri("https://beta-bridge.simplefin.org/")
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open SimpleFIN Bridge")
            }

            OrbitTextField(
                value = state.simpleFinToken,
                onValueChange = { onIntent(OnboardingIntent.UpdateSimpleFinToken(it)) },
                label = { Text("SimpleFIN Setup Token") },
                placeholder = { Text("Paste your one-time setup token here...") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.csvSuggestedCutoverDate != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Starting bank sync on ${state.simpleFinCutoverDate ?: state.csvSuggestedCutoverDate} (right after your CSV import) to avoid duplicates.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.tick()
                        onIntent(OnboardingIntent.ToggleSimpleFinAdvancedDate(!state.showSimpleFinAdvancedDate))
                    }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced: choose start date",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (state.showSimpleFinAdvancedDate) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            OrbitAccordionVisibility(visible = state.showSimpleFinAdvancedDate) {
                val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
                val parsedDate = remember(state.simpleFinCutoverDate) {
                    state.simpleFinCutoverDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
                }
                DateFieldButton(
                    date = parsedDate,
                    onDateSelected = { onIntent(OnboardingIntent.UpdateSimpleFinCutoverDate(it.toString())) },
                    label = "Import transactions on or after"
                )
            }

            OnboardingErrorBanner(message = state.simpleFinError)

            Button(
                onClick = {
                    haptics.confirm()
                    onIntent(OnboardingIntent.ConnectSimpleFin)
                },
                enabled = state.simpleFinToken.isNotBlank() && !state.isConnectingSimpleFin,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (state.isConnectingSimpleFin) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (state.isConnectingSimpleFin) "Connecting & Syncing..." else "Connect & Sync SimpleFIN",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
