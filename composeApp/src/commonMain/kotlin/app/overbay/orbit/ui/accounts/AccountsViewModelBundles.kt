package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction

internal data class AccountsDataBundle(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val workspaceSnapshot: OrbitWorkspaceSnapshot,
    val preferences: OrbitPreferences
)

internal data class AccountsSheetBundle(
    val editingAccount: Account?,
    val showEditorSheet: Boolean,
    val editingCardProfile: CreditCardProfile?,
    val cardProfileAccountId: Long?,
    val showCardProfileSheet: Boolean
)

internal data class AccountsActionBundle(
    val selectedAccountId: Long?,
    val mergingAccount: Account?,
    val showMergeDialog: Boolean,
    val historyAccount: Account?,
    val showHistorySheet: Boolean
)

internal data class AddRecordBundle(
    val showAddRecordDialog: Boolean,
    val addRecordAccount: Account?
)

internal data class SyncReorderBundle(
    val syncing: Boolean,
    val err: String?,
    val showArchived: Boolean,
    val isReorderMode: Boolean,
    val groupOrder: List<AccountTypeCategory> = AccountTypeCategory.entries.toList()
)

internal data class AccountsHubUiControl(
    val mode: AccountsMasterMode,
    val showUsedPerks: Boolean,
    val showWhichCardWhere: Boolean,
    val selectedRewardCategory: String?
)
