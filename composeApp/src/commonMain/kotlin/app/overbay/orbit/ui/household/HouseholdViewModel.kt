package app.overbay.orbit.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.overbay.orbit.data.*
import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.HouseholdIncomeRecord
import app.overbay.orbit.data.model.HouseholdInfo
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdRole
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.domain.LocalDateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class HouseholdViewModel(
    private val repository: OrbitRepository,
    customScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = customScope ?: (viewModelScope + Dispatchers.Default)

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
                        shares = snapshot.shares,
                        incomeRecords = snapshot.incomeRecords,
                        includeHsaInRetirementRate = snapshot.preferences.includeHsaInRetirementRate,
                        totalHouseholdGrossIncomeCents = totalIncome,
                        isLoading = false
                    )
                }
            }
        }
        scope.launch {
            repository.accounts.collect { accounts ->
                _uiState.update { it.copy(availableAccounts = accounts) }
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

    fun saveMember(member: HouseholdMember, effectiveDate: String? = null) {
        scope.launch {
            repository.saveHouseholdMember(member, effectiveDate)
        }
        closeMemberEditor()
    }

    fun addIncomeRecord(record: HouseholdIncomeRecord) {
        scope.launch {
            repository.addHouseholdIncomeRecord(record)
        }
    }

    fun setIncludeHsaInRetirementRate(include: Boolean) {
        val updated = repository.preferences.value.copy(includeHsaInRetirementRate = include)
        _uiState.update { it.copy(includeHsaInRetirementRate = include) }
        scope.launch {
            repository.updatePreferences(updated)
        }
    }

    fun requestDeleteMember(member: HouseholdMember) {
        _uiState.update { it.copy(memberToDelete = member) }
    }

    fun cancelDeleteMember() {
        _uiState.update { it.copy(memberToDelete = null) }
    }

    fun confirmDeleteMember() {
        val member = _uiState.value.memberToDelete ?: return
        _uiState.update { it.copy(memberToDelete = null) }
        deleteMember(member.id)
    }

    fun deleteMember(id: Long) {
        scope.launch {
            repository.deleteHouseholdMember(id)
        }
        _uiState.update { current ->
            if (current.inspectingMemberId == id) {
                current.copy(inspectingMemberId = null, showMemberEditor = false, editingMember = null)
            } else {
                current
            }
        }
    }

    fun selectMemberForInspect(id: Long?) {
        _uiState.update { current ->
            val nextId = if (current.inspectingMemberId == id) null else id
            current.copy(
                inspectingMemberId = nextId,
                showMemberEditor = if (nextId != null && current.editingMember?.id != nextId) false else current.showMemberEditor,
                showInviteDialog = false
            )
        }
    }

    fun dismissInspectorPane() {
        _uiState.update {
            it.copy(
                inspectingMemberId = null,
                showMemberEditor = false,
                editingMember = null,
                showInviteDialog = false
            )
        }
    }

    fun inviteMember(email: String, role: HouseholdRole) {
        _uiState.update { it.copy(showInviteDialog = false) }
        scope.launch {
            repository.inviteHouseholdPartner(email = email.trim(), role = role.name.lowercase())
        }
    }

    fun revokeInvite(shareId: Long) {
        _uiState.update { current ->
            current.copy(shares = current.shares.filterNot { it.id == shareId })
        }
        scope.launch {
            repository.revokePartnerShare(shareId)
        }
    }

    fun openInviteDialog() {
        _uiState.update {
            it.copy(
                showInviteDialog = true,
                showMemberEditor = false
            )
        }
    }

    fun closeInviteDialog() {
        _uiState.update { it.copy(showInviteDialog = false) }
    }

    fun openMemberEditor(member: HouseholdMember? = null) {
        _uiState.update {
            it.copy(
                inspectingMemberId = member?.id ?: it.inspectingMemberId,
                showMemberEditor = true,
                editingMember = member,
                showInviteDialog = false
            )
        }
    }

    fun closeMemberEditor() {
        _uiState.update { it.copy(showMemberEditor = false, editingMember = null) }
    }
}
