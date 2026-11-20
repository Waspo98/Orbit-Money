package app.overbay.orbit.ui.settings.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.HouseholdUserAccess
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.theme.OrbitTheme
import app.overbay.orbit.ui.components.OrbitAccordionVisibility
import app.overbay.orbit.ui.components.OrbitBadge
import app.overbay.orbit.ui.components.OrbitMiniStatRow
import app.overbay.orbit.ui.components.OrbitSectionCard
import app.overbay.orbit.ui.components.OrbitSegmentedTabRow
import app.overbay.orbit.ui.components.OrbitTextField
import app.overbay.orbit.ui.components.orbitMorphContentTransform
import app.overbay.orbit.ui.settings.SettingsUiState
import app.overbay.orbit.util.LocalOidcAuthLauncher
import app.overbay.orbit.util.PlatformRuntime

fun LazyListScope.syncAndSharingSectionItems(
    uiState: SettingsUiState,
    onBiometricLockChange: (Boolean) -> Unit,
    onBiometricTimeoutChange: (Int) -> Unit,
    onStorageModeChange: (StorageMode) -> Unit,
    onServerUrlDraftChange: (String) -> Unit,
    onCommitServerUrl: (String) -> Unit,
    onToggleReauthOptions: (Boolean) -> Unit,
    onTogglePasswordAuth: (Boolean) -> Unit,
    onUsernameDraftChange: (String) -> Unit,
    onPasswordDraftChange: (String) -> Unit,
    onTriggerSync: () -> Unit,
    onAuthenticateServer: (username: String, password: String) -> Unit,
    onOpenMigrateWizard: () -> Unit,
    onRequestSwitchAccount: () -> Unit,
    onRequestSignOut: () -> Unit,
    onOpenPartnerInvite: () -> Unit,
    onUpdatePartnerAccess: (shareId: Long, accessLevel: String) -> Unit,
    onUpdateMemberAccess: (userId: Long, accessLevel: String) -> Unit,
    onRequestRemoveUser: (HouseholdUserAccess) -> Unit,
    onRequestRevokeShare: (HouseholdShare) -> Unit
) {
    if (!PlatformRuntime.isWeb) {
        item(key = "sync_app_lock") {
            AppLockCard(
                preferences = uiState.preferences,
                onBiometricLockChange = onBiometricLockChange,
                onBiometricTimeoutChange = onBiometricTimeoutChange
            )
        }
    }

    item(key = "sync_storage_mode") {
        val oidcLauncher = LocalOidcAuthLauncher.current
        val serverUrl = uiState.serverUrlDraft
        val showPasswordAuth = uiState.showServerPasswordLogin
        val showReauthOptions = uiState.showReauthOptions
        val username = uiState.serverUsernameDraft
        val password = uiState.serverPasswordDraft

        val btnShape = RoundedCornerShape(14.dp)
        val statusText = when (val result = uiState.syncStatus) {
            is SyncResult.Success -> result.message
            is SyncResult.Error -> "Sync failed: ${result.message}"
            SyncResult.NoOp -> if (uiState.preferences.storageMode == StorageMode.LOCAL) "Offline" else "Idle"
        }
        val statusColor = when (uiState.syncStatus) {
            is SyncResult.Error -> MaterialTheme.colorScheme.error
            is SyncResult.Success -> MaterialTheme.colorScheme.primary
            SyncResult.NoOp -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        OrbitSectionCard(
            title = "Storage & Server Sync",
            subtitle = "Choose between private on-device storage and a self-hosted household server"
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OrbitSegmentedTabRow(
                    items = listOf(StorageMode.LOCAL, StorageMode.SELF_HOSTED),
                    selected = uiState.preferences.storageMode,
                    onSelect = onStorageModeChange,
                    label = { if (it == StorageMode.LOCAL) "On This Device" else "Self-Hosted" }
                )

                AnimatedContent(
                    targetState = uiState.preferences.storageMode,
                    transitionSpec = { orbitMorphContentTransform(clip = false) },
                    label = "syncStorageModeContent"
                ) { mode ->
                    if (mode == StorageMode.LOCAL) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "All accounts, transactions, and budgets remain stored privately on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onOpenMigrateWizard,
                                shape = btnShape,
                                border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text("Migrate Local Workspace to Server", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SessionAuthenticationBanner(
                                isAuthenticated = uiState.isAuthenticated,
                                currentUser = uiState.currentUser,
                                authMethod = uiState.authMethod,
                                serverUrl = serverUrl
                            )

                            OrbitTextField(
                                value = serverUrl,
                                onValueChange = onServerUrlDraftChange,
                                label = { Text("Server Base URL") },
                                placeholder = { Text("https://orbit.yourdomain.com") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = onTriggerSync,
                                enabled = !uiState.isSyncing && serverUrl.isNotBlank(),
                                shape = btnShape,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                if (uiState.isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            val showAuthControls = !uiState.isAuthenticated || showReauthOptions
                            if (uiState.isAuthenticated) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onToggleReauthOptions(!showReauthOptions) }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Re-authenticate or Change Login Method",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (showReauthOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            OrbitAccordionVisibility(visible = showAuthControls) {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            onCommitServerUrl(serverUrl)
                                            oidcLauncher.launchOidcLogin(serverUrl, "/api/auth/oidc/login")
                                        },
                                        enabled = !uiState.isSyncing && serverUrl.isNotBlank(),
                                        shape = btnShape,
                                        border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    ) {
                                        Text("Continue with SSO (OIDC)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onTogglePasswordAuth(!showPasswordAuth) }
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Sign in with Username & Password",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (showPasswordAuth) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    OrbitAccordionVisibility(visible = showPasswordAuth) {
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            OrbitTextField(
                                                value = username,
                                                onValueChange = onUsernameDraftChange,
                                                label = { Text("Username") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OrbitTextField(
                                                value = password,
                                                onValueChange = onPasswordDraftChange,
                                                label = { Text("Password") },
                                                visualTransformation = PasswordVisualTransformation(),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Button(
                                                onClick = { onAuthenticateServer(username.trim(), password) },
                                                enabled = !uiState.isSyncing && username.isNotBlank() && password.isNotBlank(),
                                                shape = btnShape,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                            ) {
                                                Text("Sign In", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onRequestSwitchAccount,
                                    shape = btnShape,
                                    border = BorderStroke(1.dp, OrbitTheme.colors.cardBorder),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text("Switch Account", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    onClick = onRequestSignOut,
                                    shape = btnShape,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Text("Sign Out", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = OrbitTheme.colors.cardBorder)
                OrbitMiniStatRow(label = "Sync Status", value = statusText, valueColor = statusColor)
            }
        }
    }

    item(key = "sync_partner_sharing") {
        PartnerSharingCard(
            storageMode = uiState.preferences.storageMode,
            sharingState = uiState.householdSharing,
            isLoading = uiState.isLoadingSharing,
            onOpenInviteDialog = onOpenPartnerInvite,
            onUpdateAccess = onUpdatePartnerAccess,
            onUpdateUserAccess = onUpdateMemberAccess,
            onRequestRemoveUser = onRequestRemoveUser,
            onRequestRevokeShare = onRequestRevokeShare
        )
    }
}

@Composable
private fun SessionAuthenticationBanner(
    isAuthenticated: Boolean,
    currentUser: String?,
    authMethod: String?,
    serverUrl: String
) {
    val hostLabel = serverUrl.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
        .ifBlank { "Same Origin" }
    val methodKey = authMethod?.lowercase()

    val (headline, badgeText) = when {
        !isAuthenticated -> "Not Signed In" to "Signed Out"
        methodKey == "oidc" -> "Authenticated via OIDC (SSO)" to "OIDC SSO"
        methodKey == "password" || methodKey == "local" -> "Authenticated via Username & Password" to "User / Pass"
        methodKey == "sample" -> "Authenticated in Sample Mode" to "Sample"
        else -> "Authenticated to Server" to "Connected"
    }

    val subtitle = if (!isAuthenticated) {
        "Sign in below using SSO or your username and password for $hostLabel"
    } else {
        val userPart = currentUser?.takeIf { it.isNotBlank() }?.let { "Signed in as $it" } ?: "Active session"
        "$userPart · $hostLabel"
    }

    val containerColor = if (isAuthenticated) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    }
    val borderColor = if (isAuthenticated) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isAuthenticated) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OrbitBadge(
                text = badgeText,
                containerColor = if (isAuthenticated) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                contentColor = if (isAuthenticated) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
