package app.overbay.orbit.ui.accounts

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.resolveAccountClosure
import app.overbay.orbit.domain.AccountClosureCandidate
import app.overbay.orbit.domain.AccountClosureResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal data class ClosureUiOverlay(
    val manualAccountId: Long? = null,
    val snoozedIds: Set<Long> = emptySet(),
    val isResolving: Boolean = false,
    val selectedResolutions: Map<Long, AccountClosureResolution> = emptyMap(),
    val errorMessage: String? = null
)

/**
 * Coordinates the "Did you recently pay off or close this account?" popup flow,
 * keeping AccountsViewModel lean and configuration-proof.
 */
class AccountClosureController(
    private val repository: OrbitRepository,
    private val scope: CoroutineScope
) {
    private val _manualAccountId = MutableStateFlow<Long?>(null)
    private val _snoozedAccountIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isResolving = MutableStateFlow(false)
    private val _selectedResolutions = MutableStateFlow<Map<Long, AccountClosureResolution>>(emptyMap())
    private val _errorMessage = MutableStateFlow<String?>(null)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    internal val overlayFlow: Flow<ClosureUiOverlay> = combine(
        combine(_manualAccountId, _snoozedAccountIds, _isResolving) { m, s, r -> Triple(m, s, r) },
        _selectedResolutions,
        _errorMessage
    ) { (manualId, snoozed, resolving), selections, error ->
        ClosureUiOverlay(
            manualAccountId = manualId,
            snoozedIds = snoozed,
            isResolving = resolving,
            selectedResolutions = selections,
            errorMessage = error
        )
    }

    fun openForAccount(accountId: Long) {
        _errorMessage.value = null
        _manualAccountId.value = accountId
    }

    fun selectResolution(accountId: Long, resolution: AccountClosureResolution) {
        _errorMessage.value = null
        _selectedResolutions.value = _selectedResolutions.value + (accountId to resolution)
    }

    fun snoozeCurrent(accountId: Long) {
        _errorMessage.value = null
        _manualAccountId.value = null
        _snoozedAccountIds.value = _snoozedAccountIds.value + accountId
    }

    fun resetSnoozesAfterSync() {
        _errorMessage.value = null
        _snoozedAccountIds.value = emptySet()
    }

    fun resolve(
        accountId: Long,
        resolution: AccountClosureResolution,
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        if (_isResolving.value) return
        scope.launch {
            _errorMessage.value = null
            _isResolving.value = true
            val res = repository.resolveAccountClosure(accountId, resolution)
            _isResolving.value = false
            if (res.isSuccess) {
                _manualAccountId.value = null
                _snoozedAccountIds.value = _snoozedAccountIds.value - accountId
                _selectedResolutions.value = _selectedResolutions.value - accountId
                onComplete()
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Could not update account status"
                _errorMessage.value = msg
                onError(msg)
            }
        }
    }

    companion object {
        internal fun selectActivePrompt(
            candidates: List<AccountClosureCandidate>,
            overlay: ClosureUiOverlay
        ): AccountClosureCandidate? {
            if (overlay.manualAccountId != null) {
                return candidates.find { it.account.id == overlay.manualAccountId }
            }
            return candidates.firstOrNull { it.account.id !in overlay.snoozedIds }
        }

        internal fun selectActiveResolution(
            candidate: AccountClosureCandidate?,
            overlay: ClosureUiOverlay
        ): AccountClosureResolution? {
            val active = candidate ?: return null
            return overlay.selectedResolutions[active.account.id] ?: active.defaultResolution()
        }
    }
}
