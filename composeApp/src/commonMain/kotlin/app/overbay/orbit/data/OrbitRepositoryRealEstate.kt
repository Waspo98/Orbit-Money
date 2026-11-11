package app.overbay.orbit.data

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.resolveAccountClosure
import app.overbay.orbit.domain.AccountClosureResolution
import app.overbay.orbit.domain.LocalDateUtils

/**
 * Resolves a detected paid-off or closed account prompt across both Self-Hosted
 * backend storage and local-first offline/Wasm snapshots.
 */
suspend fun OrbitRepository.resolveAccountClosure(
    accountId: Long,
    resolution: AccountClosureResolution
): Result<Unit> = runCatching {
    val currentAccounts = accounts.value
    val existing = currentAccounts.find { it.id == accountId }
        ?: throw NoSuchElementException("Account $accountId not found")

    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val remote = createApiClient()
            .resolveAccountClosure(accountId, resolution.wireKey)
            .getOrThrow()
        // Refresh full snapshot so any sibling/child account archive updates stay synchronized
        sync(force = false)
        if (remote.updatedAccount != null) {
            val latest = accounts.value.toMutableList()
            val idx = latest.indexOfFirst { it.id == remote.updatedAccount.id }
            if (idx >= 0) latest[idx] = remote.updatedAccount else latest.add(remote.updatedAccount)
            if (remote.createdProperty != null && latest.none { it.id == remote.createdProperty.id }) {
                latest.add(remote.createdProperty)
            }
            commitSnapshot(snapshot.value.copy(accounts = latest))
        }
        return@runCatching
    }

    val nowStamp = "${LocalDateUtils.todayIsoDate()}T23:59:59"
    val updatedList = when (resolution) {
        AccountClosureResolution.DISMISS -> {
            currentAccounts.map {
                if (it.id == accountId) it.copy(closurePromptDismissedAt = nowStamp) else it
            }
        }
        AccountClosureResolution.SOLD_PROPERTY -> {
            val anchorId = existing.linkedPropertyAccountId ?: existing.id
            currentAccounts.map { acct ->
                if (acct.id == accountId || acct.id == anchorId || acct.linkedPropertyAccountId == anchorId) {
                    acct.copy(isArchived = true, simplefinMissingSince = null, closurePromptDismissedAt = nowStamp)
                } else acct
            }
        }
        AccountClosureResolution.SOLD_OR_CLOSED -> {
            val isAnchor = existing.isProperty || (existing.type == AccountType.MORTGAGE && existing.linkedPropertyAccountId == null)
            currentAccounts.map { acct ->
                when {
                    acct.id == accountId -> acct.copy(isArchived = true, simplefinMissingSince = null, closurePromptDismissedAt = nowStamp)
                    isAnchor && acct.linkedPropertyAccountId == accountId ->
                        acct.copy(isArchived = true, simplefinMissingSince = null, closurePromptDismissedAt = nowStamp)
                    else -> acct
                }
            }
        }
        AccountClosureResolution.PAID_OFF_KEEP_PROPERTY,
        AccountClosureResolution.REFINANCED_KEEP_PROPERTY -> {
            if (existing.linkedPropertyAccountId != null) {
                currentAccounts.map { acct ->
                    if (acct.id == accountId) {
                        acct.copy(isArchived = true, simplefinMissingSince = null, closurePromptDismissedAt = nowStamp)
                    } else acct
                }
            } else if (existing.type == AccountType.MORTGAGE && (existing.estimatedValueCents ?: 0L) > 0L) {
                val nextId = (currentAccounts.maxOfOrNull { it.id } ?: 0L) + 1L
                val newProp = Account(
                    id = nextId,
                    householdId = existing.householdId,
                    name = existing.propertyName?.takeIf { it.isNotBlank() } ?: existing.name,
                    type = AccountType.OTHER,
                    isProperty = true,
                    isManual = true,
                    currentBalanceCents = 0L,
                    estimatedValueCents = existing.estimatedValueCents,
                    sortOrder = existing.sortOrder
                )
                currentAccounts.map { acct ->
                    when {
                        acct.id == accountId -> acct.copy(
                            isArchived = true,
                            estimatedValueCents = null,
                            linkedPropertyAccountId = nextId,
                            simplefinMissingSince = null,
                            closurePromptDismissedAt = nowStamp
                        )
                        acct.linkedPropertyAccountId == accountId -> acct.copy(linkedPropertyAccountId = nextId)
                        else -> acct
                    }
                } + newProp
            } else {
                currentAccounts.map { acct ->
                    if (acct.id == accountId) acct.copy(closurePromptDismissedAt = nowStamp, simplefinMissingSince = null) else acct
                }
            }
        }
    }
    commitSnapshot(snapshot.value.copy(accounts = updatedList))
}
