package app.overbay.orbit.ui.household

import app.overbay.orbit.data.model.HouseholdInfo
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdShare
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.domain.Cents

data class HouseholdUiState(
    val householdInfo: HouseholdInfo = HouseholdInfo(),
    val members: List<HouseholdMember> = emptyList(),
    val storageMode: StorageMode = StorageMode.LOCAL,
    val shares: List<HouseholdShare> = emptyList(),
    val inspectingMemberId: Long? = null,
    val showInviteDialog: Boolean = false,
    val showMemberEditor: Boolean = false,
    val editingMember: HouseholdMember? = null,
    val isLoading: Boolean = false,
    val totalHouseholdGrossIncomeCents: Cents = 0L
) {
    val inspectingMember: HouseholdMember?
        get() = members.find { it.id == inspectingMemberId }
}
