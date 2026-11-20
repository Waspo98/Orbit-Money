package app.overbay.orbit.ui.settings.controllers

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.disconnectSimpleFinBridge
import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.fetchHouseholdSharing
import app.overbay.orbit.data.fetchSimpleFinSyncLogs
import app.overbay.orbit.data.inviteHouseholdPartner
import app.overbay.orbit.data.model.HouseholdSharingState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsSyncSharingController(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>
) {
    private var serverUrlSaveJob: Job? = null

    fun setStorageMode(mode: StorageMode) {
        serverUrlSaveJob?.cancel()
        val updated = uiState.value.preferences.copy(storageMode = mode)
        uiState.update {
            it.copy(
                preferences = updated,
                storageMode = mode,
                householdSharing = if (mode == StorageMode.LOCAL) null else it.householdSharing
            )
        }
        scope.launch {
            repository.setStorageMode(mode)
            if (mode == StorageMode.SELF_HOSTED) {
                loadHouseholdSharing()
            }
        }
    }

    fun setServerUrl(url: String) {
        serverUrlSaveJob?.cancel()
        val trimmed = url.trim()
        val updated = uiState.value.preferences.copy(selfHostedServerUrl = trimmed)
        uiState.update { it.copy(preferences = updated, serverUrlDraft = url) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun updateServerUrlDraft(url: String) {
        uiState.update { it.copy(serverUrlDraft = url) }
        serverUrlSaveJob?.cancel()
        serverUrlSaveJob = scope.launch {
            delay(350)
            val trimmed = url.trim()
            if (trimmed != uiState.value.preferences.selfHostedServerUrl) {
                val updated = uiState.value.preferences.copy(selfHostedServerUrl = trimmed)
                uiState.update { it.copy(preferences = updated) }
                repository.updatePreferences(updated)
            }
        }
    }

    fun setShowReauthOptions(show: Boolean) {
        uiState.update { it.copy(showReauthOptions = show) }
    }

    fun setShowServerPasswordLogin(show: Boolean) {
        uiState.update { it.copy(showServerPasswordLogin = show) }
    }

    fun updateServerUsernameDraft(username: String) {
        uiState.update { it.copy(serverUsernameDraft = username) }
    }

    fun updateServerPasswordDraft(password: String) {
        uiState.update { it.copy(serverPasswordDraft = password) }
    }

    fun updateSimpleFinTokenDraft(token: String) {
        uiState.update { it.copy(simpleFinTokenDraft = token) }
    }

    fun updateSimpleFinCutoverDateDraft(date: String?) {
        uiState.update { it.copy(simpleFinCutoverDateDraft = date) }
    }

    fun setShowSimpleFinReconnectEditor(show: Boolean) {
        uiState.update { it.copy(showSimpleFinReconnectEditor = show) }
    }

    private suspend fun flushPendingServerUrl(): String {
        serverUrlSaveJob?.cancel()
        val draftTrimmed = uiState.value.serverUrlDraft.trim()
        val targetRaw = draftTrimmed.ifBlank { uiState.value.preferences.selfHostedServerUrl }
        if (draftTrimmed.isNotEmpty() && draftTrimmed != uiState.value.preferences.selfHostedServerUrl) {
            val updated = uiState.value.preferences.copy(selfHostedServerUrl = draftTrimmed)
            uiState.update { it.copy(preferences = updated) }
            repository.updatePreferences(updated)
        }
        return ServerUrlNormalizer.normalize(targetRaw)
    }

    fun loginToSelfHostedServer(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            uiState.update { it.copy(statusMessage = "Username and password required") }
            return
        }
        uiState.update { it.copy(isSyncing = true, statusMessage = "Authenticating...") }
        scope.launch {
            val normalizedUrl = flushPendingServerUrl()
            val client = repository.createApiClient(normalizedUrl)
            val loginRes = client.login(username.trim(), password)
            if (loginRes.isSuccess) {
                val me = loginRes.getOrNull()
                val userLabel = me?.displayName ?: me?.username ?: username.trim()
                val authMethod = me?.authMethod ?: "password"
                repository.markAuthenticated(normalizedUrl, userLabel, authMethod)
                val txCount = repository.transactions.value.size
                uiState.update {
                    it.copy(
                        isSyncing = false,
                        serverPasswordDraft = "",
                        showReauthOptions = false,
                        showServerPasswordLogin = false,
                        syncStatus = SyncResult.Success("Signed in as $userLabel ($txCount transactions)"),
                        statusMessage = "Logged in as $userLabel"
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
            flushPendingServerUrl()
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
        serverUrlSaveJob?.cancel()
        uiState.update {
            it.copy(
                activeDialog = SettingsDialogState.None,
                serverPasswordDraft = "",
                showReauthOptions = false,
                showServerPasswordLogin = false
            )
        }
        scope.launch {
            repository.logout()
            uiState.update {
                it.copy(
                    isSyncing = false,
                    syncStatus = SyncResult.NoOp,
                    householdSharing = null,
                    simpleFinLogs = emptyList(),
                    isSimpleFinLogsExpanded = false,
                    statusMessage = "Signed out of server"
                )
            }
        }
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
                        simpleFinTokenDraft = trimmedToken,
                        simpleFinCutoverDateDraft = cutoverDate,
                        showSimpleFinReconnectEditor = false,
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
                    simpleFinLogs = emptyList(),
                    isSimpleFinLogsExpanded = false,
                    simpleFinTokenDraft = "",
                    simpleFinCutoverDateDraft = null,
                    showSimpleFinReconnectEditor = false
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

    fun loadHouseholdSharing(showLoading: Boolean = uiState.value.householdSharing == null) {
        if (showLoading) {
            uiState.update { it.copy(isLoadingSharing = true) }
        }
        scope.launch {
            val res = repository.fetchHouseholdSharing()
            uiState.update {
                it.copy(
                    householdSharing = res.getOrNull() ?: it.householdSharing,
                    isLoadingSharing = false
                )
            }
        }
    }

    fun inviteHouseholdPartner(email: String, role: String, accessLevel: String) {
        val currentDialog = uiState.value.activeDialog
        if (currentDialog is SettingsDialogState.PartnerInvite && currentDialog.isSending) return
        val trimmedEmail = email.trim()
        uiState.update {
            if (it.activeDialog is SettingsDialogState.PartnerInvite) {
                it.copy(activeDialog = SettingsDialogState.PartnerInvite(isSending = true, errorMessage = null))
            } else {
                it
            }
        }
        scope.launch {
            val res = repository.inviteHouseholdPartner(trimmedEmail, role, accessLevel)
            if (res.isSuccess) {
                uiState.update {
                    it.copy(
                        activeDialog = SettingsDialogState.None,
                        statusMessage = "Invitation sent to $trimmedEmail"
                    )
                }
                loadHouseholdSharing(showLoading = false)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Unknown error"
                val message = "Invite failed: $err"
                uiState.update { state ->
                    if (state.activeDialog is SettingsDialogState.PartnerInvite) {
                        state.copy(activeDialog = SettingsDialogState.PartnerInvite(isSending = false, errorMessage = message))
                    } else {
                        state.copy(statusMessage = message)
                    }
                }
            }
        }
    }

    private fun runSharingMutation(
        errorMessage: String,
        dismissDialog: Boolean = false,
        optimisticTransform: (HouseholdSharingState) -> HouseholdSharingState,
        action: suspend () -> Result<Boolean>
    ) {
        val previous = uiState.value.householdSharing
        uiState.update { current ->
            current.copy(
                activeDialog = if (dismissDialog) SettingsDialogState.None else current.activeDialog,
                householdSharing = current.householdSharing?.let(optimisticTransform)
            )
        }
        scope.launch {
            val res = action()
            if (res.isSuccess) {
                loadHouseholdSharing(showLoading = false)
            } else {
                uiState.update {
                    it.copy(
                        householdSharing = previous,
                        statusMessage = errorMessage
                    )
                }
            }
        }
    }

    fun updatePartnerAccess(shareId: Long, accessLevel: String) {
        runSharingMutation(
            errorMessage = "Could not update share access",
            optimisticTransform = { state ->
                state.copy(shares = state.shares.map { if (it.id == shareId) it.copy(accessLevel = accessLevel) else it })
            },
            action = { repository.updatePartnerShareAccess(shareId, accessLevel) }
        )
    }

    fun updateHouseholdMemberAccess(userId: Long, accessLevel: String) {
        runSharingMutation(
            errorMessage = "Could not update member access",
            optimisticTransform = { state ->
                state.copy(users = state.users.map { if (it.id == userId) it.copy(accessLevel = accessLevel) else it })
            },
            action = { repository.updateHouseholdMemberUserAccess(userId, accessLevel) }
        )
    }

    fun revokePartnerShare(shareId: Long) {
        runSharingMutation(
            errorMessage = "Could not revoke share",
            dismissDialog = true,
            optimisticTransform = { state ->
                state.copy(shares = state.shares.filterNot { it.id == shareId })
            },
            action = { repository.revokePartnerShare(shareId) }
        )
    }

    fun removeHouseholdUser(userId: Long) {
        runSharingMutation(
            errorMessage = "Could not remove member",
            dismissDialog = true,
            optimisticTransform = { state ->
                state.copy(users = state.users.filterNot { it.id == userId })
            },
            action = { repository.removeHouseholdMemberUser(userId) }
        )
    }
}
