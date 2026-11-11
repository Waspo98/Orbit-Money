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
    val manualAccountId: Long?,
    val snoozedIds: Set<Long>,
    val isResolving: Boolean
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
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    internal val overlayFlow: Flow<ClosureUiOverlay> = combine(
        _manualAccountId,
        _snoozedAccountIds,
        _isResolving
    ) { manualId, snoozed, resolving ->
        ClosureUiOverlay(manualId, snoozed, resolving)
    }

    fun openForAccount(accountId: Long) {
        _manualAccountId.value = accountId
    }

    fun snoozeCurrent(accountId: Long) {
        _manualAccountId.value = null
        _snoozedAccountIds.value = _snoozedAccountIds.value + accountId
    }

    fun resetSnoozesAfterSync() {
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
            _isResolving.value = true
            val res = repository.resolveAccountClosure(accountId, resolution)
            _isResolving.value = false
            if (res.isSuccess) {
                _manualAccountId.value = null
                _snoozedAccountIds.value = _snoozedAccountIds.value - accountId
                onComplete()
            } else {
                onError(res.exceptionOrNull()?.message ?: "Could not update account status")
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
    }
}
