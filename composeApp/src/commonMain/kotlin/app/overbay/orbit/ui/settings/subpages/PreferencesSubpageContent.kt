package app.overbay.orbit.ui.settings.subpages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.TransactionQuickAction
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitIcons
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.settings.SettingsUiState
import app.overbay.orbit.ui.settings.SettingsViewModel
import app.overbay.orbit.ui.settings.blocks.NavigationCustomizeBlock
import app.overbay.orbit.ui.settings.blocks.PushNotificationsBlock
import app.overbay.orbit.ui.settings.blocks.SecurityBiometricsBlock
import app.overbay.orbit.ui.settings.blocks.ThemeAppearanceBlock
import app.overbay.orbit.ui.transactions.sheets.TransactionActionsCustomizeSheet
import app.overbay.orbit.util.LocalOrbitHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesSubpageContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val haptics = LocalOrbitHaptics.current
    val prefs = uiState.preferences
    val (pinnedActions, _) = remember(prefs.transactionPinnedActions, prefs.transactionActionOrder, prefs.mhaTrackerEnabled) {
        TransactionQuickAction.resolveActions(
            pinnedIds = prefs.transactionPinnedActions,
            orderIds = prefs.transactionActionOrder,
            mhaEnabled = prefs.mhaTrackerEnabled
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ThemeAppearanceBlock(
            preferences = prefs,
            onPresetChange = viewModel::setThemePreset,
            onThemeModeChange = viewModel::setThemeMode,
            onDynamicColorChange = viewModel::setDynamicColor,
            onAmoledDarkChange = viewModel::setAmoledDark
        )

        PushNotificationsBlock(
            preferences = prefs,
            pushConfig = uiState.pushConfig,
            isTesting = uiState.isPushTesting,
            onSendTestNotification = viewModel::sendTestPush,
            onUpdateNotificationPreferences = viewModel::updateNotificationPreferences,
            accounts = uiState.accounts
        )

        OrbitSectionCard(
            title = "Transaction Quick Actions",
            subtitle = "Choose and reorder the buttons shown when expanding a transaction"
        ) {
            if (pinnedActions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pinnedActions.forEach { action ->
                        OrbitBadge(
                            text = action.label,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    viewModel.openTransactionActionsSheet()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = OrbitIcons.ViewSliders,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(16.dp)
                )
                Text("Customize Buttons")
            }
        }

        NavigationCustomizeBlock(
            preferences = prefs,
            onToggleRouteHidden = viewModel::toggleRouteHidden,
            onMoveRoute = viewModel::moveRouteOrder,
            onMhaTrackerChange = viewModel::setMhaTrackerEnabled,
            onCardRewardsToolsChange = viewModel::setCardRewardsToolsEnabled
        )

        SecurityBiometricsBlock(
            preferences = prefs,
            onBiometricLockChange = viewModel::setBiometricLock,
            onHapticsChange = viewModel::setHapticsEnabled
        )
    }

    if (uiState.showTransactionActionsSheet) {
        TransactionActionsCustomizeSheet(
            pinnedIds = prefs.transactionPinnedActions,
            orderIds = prefs.transactionActionOrder,
            mhaEnabled = prefs.mhaTrackerEnabled,
            onSave = { pinned, ordered -> viewModel.saveTransactionQuickActions(pinned, ordered) },
            onDismiss = viewModel::closeTransactionActionsSheet
        )
    }
}
