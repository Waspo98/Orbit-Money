package app.overbay.orbit.ui.household

import androidx.lifecycle.ViewModel
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.HouseholdInfo
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdRole
import app.overbay.orbit.data.model.HouseholdShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HouseholdViewModel(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : ViewModel() {

    private val _uiState = MutableStateFlow(HouseholdUiState())
    val uiState: StateFlow<HouseholdUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            repository.snapshot.collect { snapshot ->
                val totalIncome = snapshot.members.sumOf { it.grossIncomeAnnualCents }
                _uiState.update { current ->
                    current.copy(
                        householdInfo = snapshot.household,
                        members = snapshot.members,
                        totalHouseholdGrossIncomeCents = totalIncome,
                        isLoading = false
                    )
                }
            }
        }
        scope.launch {
            repository.storageMode.collect { mode ->
                _uiState.update { it.copy(storageMode = mode) }
            }
        }
    }

    fun updateHouseholdName(name: String) {
        val updated = _uiState.value.householdInfo.copy(name = name.trim())
        _uiState.update { it.copy(householdInfo = updated) }
        scope.launch {
            repository.updateHouseholdInfo(updated)
        }
    }

    fun saveMember(member: HouseholdMember) {
        scope.launch {
            repository.saveHouseholdMember(member)
        }
        closeMemberEditor()
    }

    fun deleteMember(id: Long) {
        scope.launch {
            repository.deleteHouseholdMember(id)
        }
    }

    fun inviteMember(email: String, role: HouseholdRole) {
        val currentShares = _uiState.value.shares
        val newShare = HouseholdShare(
            id = (currentShares.maxOfOrNull { it.id } ?: 0L) + 1L,
            householdId = _uiState.value.householdInfo.id,
            invitedEmail = email.trim(),
            role = role,
            createdAt = "2026-10-26T00:00:00Z"
        )
        _uiState.update {
            it.copy(
                shares = it.shares + newShare,
                showInviteDialog = false
            )
        }
    }

    fun revokeInvite(shareId: Long) {
        _uiState.update { current ->
            current.copy(shares = current.shares.filterNot { it.id == shareId })
        }
    }

    fun openInviteDialog() {
        _uiState.update { it.copy(showInviteDialog = true) }
    }

    fun closeInviteDialog() {
        _uiState.update { it.copy(showInviteDialog = false) }
    }

    fun openMemberEditor(member: HouseholdMember? = null) {
        _uiState.update { it.copy(showMemberEditor = true, editingMember = member) }
    }

    fun closeMemberEditor() {
        _uiState.update { it.copy(showMemberEditor = false, editingMember = null) }
    }
}
