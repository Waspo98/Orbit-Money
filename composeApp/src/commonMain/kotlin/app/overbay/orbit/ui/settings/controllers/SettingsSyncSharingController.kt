package app.overbay.orbit.ui.settings.controllers

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.disconnectSimpleFinBridge
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.fetchHouseholdSharing
import app.overbay.orbit.data.fetchSimpleFinSyncLogs
import app.overbay.orbit.data.inviteHouseholdPartner
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.ServerUrlNormalizer
import app.overbay.orbit.data.removeHouseholdMemberUser
import app.overbay.orbit.data.revokePartnerShare
import app.overbay.orbit.data.setupSimpleFinBridge
import app.overbay.orbit.data.unmuteSimpleFinAccountWarning
import app.overbay.orbit.data.updateHouseholdMemberUserAccess
import app.overbay.orbit.data.updatePartnerShareAccess
import app.overbay.orbit.ui.settings.SettingsDialogState
import app.overbay.orbit.ui.settings.SettingsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsSyncSharingController(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>
) {
    fun setStorageMode(mode: StorageMode) {
        val updated = uiState.value.preferences.copy(storageMode = mode)
        uiState.update { it.copy(preferences = updated, storageMode = mode) }
        scope.launch {
            repository.setStorageMode(mode)
        }
    }

    fun setServerUrl(url: String) {
        val trimmed = url.trim()
        val updated = uiState.value.preferences.copy(selfHostedServerUrl = trimmed)
        uiState.update { it.copy(preferences = updated) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun loginToSelfHostedServer(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            uiState.update { it.copy(statusMessage = "Username and password required") }
            return
        }
        val normalizedUrl = ServerUrlNormalizer.normalize(uiState.value.preferences.selfHostedServerUrl)
        uiState.update { it.copy(isSyncing = true, statusMessage = "Authenticating...") }
        scope.launch {
            val client = repository.createApiClient(normalizedUrl)
            val loginRes = client.login(username.trim(), password)
            if (loginRes.isSuccess) {
                repository.setStorageMode(StorageMode.SELF_HOSTED, normalizedUrl)
                val txCount = repository.transactions.value.size
                uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = SyncResult.Success("Signed in as ${username.trim()} ($txCount transactions)"),
                        statusMessage = "Logged in as ${username.trim()}"
                    )
                }
                loadHouseholdSharing()
            } else {
                val errMsg = loginRes.exceptionOrNull()?.message ?: "Authentication failed"
                uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = SyncResult.Error(errMsg),
                        statusMessage = "Login failed: $errMsg"
                    )
                }
            }
        }
    }

    fun triggerSync() {
        uiState.update { it.copy(isSyncing = true, statusMessage = "Syncing workspace...") }
        scope.launch {
            val res = repository.sync(force = true)
            uiState.update {
                it.copy(
                    isSyncing = false,
                    statusMessage = when (res) {
                        is SyncResult.Success -> "Sync completed successfully"
                        is SyncResult.Error -> "Sync failed: ${res.message}"
                        SyncResult.NoOp -> "Workspace is up to date"
                    }
                )
            }
        }
    }

    fun logout() {
        uiState.update { it.copy(activeDialog = SettingsDialogState.None) }
        scope.launch {
            repository.logout()
            uiState.update {
                it.copy(
                    isSyncing = false,
                    syncStatus = SyncResult.NoOp,
                    householdSharing = null,
                    statusMessage = "Signed out of server"
                )
            }
        }
    }

    fun setSimpleFinToken(token: String?) {
        val updated = uiState.value.preferences.copy(simpleFinToken = token?.trim())
        uiState.update { it.copy(preferences = updated) }
        scope.launch { repository.updatePreferences(updated) }
    }

    fun setupSimpleFinBridge(token: String, cutoverDate: String?) {
        val trimmedToken = token.trim()
        if (trimmedToken.isEmpty()) {
            uiState.update { it.copy(statusMessage = "SimpleFIN setup failed: Token is required") }
            return
        }
        uiState.update { it.copy(isConnectingSimpleFin = true) }
        scope.launch {
            val res = repository.setupSimpleFinBridge(trimmedToken, cutoverDate)
            if (res.isSuccess) {
                val updated = uiState.value.preferences.copy(
                    simpleFinToken = trimmedToken,
                    simpleFinCutoverDate = cutoverDate
                )
                repository.updatePreferences(updated)
                uiState.update {
                    it.copy(
                        preferences = updated,
                        isConnectingSimpleFin = false,
                        statusMessage = "SimpleFIN connected successfully"
                    )
                }
                loadSimpleFinLogs()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Unknown error"
                uiState.update {
                    it.copy(
                        isConnectingSimpleFin = false,
                        statusMessage = "SimpleFIN setup failed: $err"
                    )
                }
            }
        }
    }

    fun disconnectSimpleFin() {
        uiState.update { it.copy(activeDialog = SettingsDialogState.None) }
        scope.launch {
            repository.disconnectSimpleFinBridge()
            uiState.update {
                it.copy(
                    statusMessage = "SimpleFIN disconnected",
                    simpleFinLogs = emptyList()
                )
            }
        }
    }

    fun loadSimpleFinLogs() {
        uiState.update { it.copy(isLoadingSimpleFinLogs = true) }
        scope.launch {
            val res = repository.fetchSimpleFinSyncLogs()
            uiState.update {
                it.copy(
                    simpleFinLogs = res.getOrDefault(emptyList()),
                    isLoadingSimpleFinLogs = false
                )
            }
        }
    }

    fun toggleSimpleFinLogsExpanded() {
        uiState.update { it.copy(isSimpleFinLogsExpanded = !it.isSimpleFinLogsExpanded) }
    }

    fun unmuteSimpleFinAccount(label: String) {
        scope.launch {
            repository.unmuteSimpleFinAccountWarning(label)
        }
    }

    fun loadHouseholdSharing() {
        uiState.update { it.copy(isLoadingSharing = true) }
        scope.launch {
            val res = repository.fetchHouseholdSharing()
            uiState.update {
                it.copy(
                    householdSharing = res.getOrNull(),
                    isLoadingSharing = false
                )
            }
        }
    }

    fun inviteHouseholdPartner(email: String, role: String, accessLevel: String) {
        val trimmedEmail = email.trim()
        scope.launch {
            val res = repository.inviteHouseholdPartner(trimmedEmail, role, accessLevel)
            if (res.isSuccess) {
                uiState.update {
                    it.copy(
                        activeDialog = SettingsDialogState.None,
                        statusMessage = "Invitation sent to $trimmedEmail"
                    )
                }
                loadHouseholdSharing()
            } else {
                val err = res.exceptionOrNull()?.message ?: "Unknown error"
                uiState.update { it.copy(statusMessage = "Invite failed: $err") }
            }
        }
    }

    fun updatePartnerAccess(shareId: Long, accessLevel: String) {
        scope.launch {
            repository.updatePartnerShareAccess(shareId, accessLevel)
            loadHouseholdSharing()
        }
    }

    fun updateHouseholdMemberAccess(userId: Long, accessLevel: String) {
        scope.launch {
            repository.updateHouseholdMemberUserAccess(userId, accessLevel)
            loadHouseholdSharing()
        }
    }

    fun revokePartnerShare(shareId: Long) {
        uiState.update { it.copy(activeDialog = SettingsDialogState.None) }
        scope.launch {
            repository.revokePartnerShare(shareId)
            loadHouseholdSharing()
        }
    }

    fun removeHouseholdUser(userId: Long) {
        uiState.update { it.copy(activeDialog = SettingsDialogState.None) }
        scope.launch {
            repository.removeHouseholdMemberUser(userId)
            loadHouseholdSharing()
        }
    }
}
