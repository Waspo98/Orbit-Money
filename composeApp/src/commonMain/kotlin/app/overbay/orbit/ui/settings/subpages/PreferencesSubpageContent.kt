package app.overbay.orbit.ui.settings.subpages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.ui.settings.SettingsUiState
import app.overbay.orbit.ui.settings.SettingsViewModel
import app.overbay.orbit.ui.settings.blocks.NavigationCustomizeBlock
import app.overbay.orbit.ui.settings.blocks.PushNotificationsBlock
import app.overbay.orbit.ui.settings.blocks.SecurityBiometricsBlock
import app.overbay.orbit.ui.settings.blocks.ThemeAppearanceBlock

@Composable
fun PreferencesSubpageContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Theme & Appearance (Presets, Mode, Soft Dark vs AMOLED Black, Dynamic Color)
        ThemeAppearanceBlock(
            preferences = uiState.preferences,
            onPresetChange = viewModel::setThemePreset,
            onThemeModeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
            onAmoledDarkChange = viewModel::setAmoledDark
        )

        // 2. Push Notifications & Pulse Alerts
        PushNotificationsBlock(
            preferences = uiState.preferences,
            pushConfig = uiState.pushConfig,
            isTesting = uiState.isPushTesting,
            onSendTestNotification = viewModel::sendTestPush,
            onUpdateNotificationPreferences = viewModel::updateNotificationPreferences
        )

        // 3. Navigation & Feature Visibility
        NavigationCustomizeBlock(
            preferences = uiState.preferences,
            onToggleRouteHidden = viewModel::toggleRouteHidden,
            onMoveRoute = viewModel::moveRouteOrder
        )

        // 4. Security & Biometrics
        SecurityBiometricsBlock(
            preferences = uiState.preferences,
            onBiometricLockChange = viewModel::setBiometricLock,
            onHapticsChange = viewModel::setHapticsEnabled
        )
    }
}
