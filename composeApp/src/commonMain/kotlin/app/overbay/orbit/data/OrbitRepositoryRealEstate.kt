package app.overbay.orbit.data

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.StorageMode
import app.overbay.orbit.data.network.AccountDto
import app.overbay.orbit.data.network.OrbitApiClient
import app.overbay.orbit.data.network.archiveAccount
import app.overbay.orbit.data.network.resolveAccountClosure
import app.overbay.orbit.domain.AccountClosureResolution
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils

/**
 * Resolves a detected paid-off or closed account prompt across both Self-Hosted
 * backend storage (with automatic fallback for pre-038 servers) and local-first snapshots.
 */
suspend fun OrbitRepository.resolveAccountClosure(
    accountId: Long,
    resolution: AccountClosureResolution
): Result<Unit> = runCatching {
    val currentAccounts = accounts.value
    val existing = currentAccounts.find { it.id == accountId }
        ?: throw NoSuchElementException("Account $accountId not found")
    val nowStamp = "${LocalDateUtils.todayIsoDate()}T23:59:59"

    if (storageMode.value == StorageMode.SELF_HOSTED) {
        val api = createApiClient()
        val remoteRes = api.resolveAccountClosure(accountId, resolution.wireKey)
        if (remoteRes.isSuccess) {
            val remote = remoteRes.getOrThrow()
            recordClosureDismissalOverride(accountId, nowStamp)
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

        val errMessage = remoteRes.exceptionOrNull()?.message.orEmpty()
        if ("HTTP 404" in errMessage) {
            resolveClosureOnLegacyServer(api, existing, currentAccounts, resolution, nowStamp)
            return@runCatching
        }

        remoteRes.getOrThrow()
        return@runCatching
    }

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
    recordClosureDismissalOverride(accountId, nowStamp)
    commitSnapshot(snapshot.value.copy(accounts = updatedList))
}

private suspend fun OrbitRepository.recordClosureDismissalOverride(accountId: Long, stamp: String) {
    val currentPrefs = preferences.value
    updatePreferences(
        currentPrefs.copy(
            dismissedClosurePrompts = currentPrefs.dismissedClosurePrompts + (accountId.toString() to stamp)
        )
    )
}

private suspend fun OrbitRepository.resolveClosureOnLegacyServer(
    api: OrbitApiClient,
    existing: Account,
    currentAccounts: List<Account>,
    resolution: AccountClosureResolution,
    nowStamp: String
) {
    when (resolution) {
        AccountClosureResolution.DISMISS -> {
            recordClosureDismissalOverride(existing.id, nowStamp)
        }
        AccountClosureResolution.SOLD_PROPERTY -> {
            val anchorId = existing.linkedPropertyAccountId ?: existing.id
            val toArchive = currentAccounts.filter {
                it.id == existing.id || it.id == anchorId || it.linkedPropertyAccountId == anchorId
            }
            for (acct in toArchive) {
                api.archiveAccount(acct.id).getOrThrow()
            }
            recordClosureDismissalOverride(existing.id, nowStamp)
            sync(force = false)
        }
        AccountClosureResolution.SOLD_OR_CLOSED -> {
            val isAnchor = existing.isProperty || (existing.type == AccountType.MORTGAGE && existing.linkedPropertyAccountId == null)
            api.archiveAccount(existing.id).getOrThrow()
            if (isAnchor) {
                currentAccounts.filter { it.linkedPropertyAccountId == existing.id }.forEach { child ->
                    api.archiveAccount(child.id).getOrThrow()
                }
            }
            recordClosureDismissalOverride(existing.id, nowStamp)
            sync(force = false)
        }
        AccountClosureResolution.PAID_OFF_KEEP_PROPERTY,
        AccountClosureResolution.REFINANCED_KEEP_PROPERTY -> {
            if (existing.linkedPropertyAccountId != null) {
                api.archiveAccount(existing.id).getOrThrow()
            } else if (existing.type == AccountType.MORTGAGE && (existing.estimatedValueCents ?: 0L) > 0L) {
                val propTitle = existing.propertyName?.takeIf { it.isNotBlank() } ?: existing.name
                val estDollars = MoneyUtils.centsToDollars(existing.estimatedValueCents ?: 0L)
                val created = api.createAccount(
                    AccountDto(
                        name = propTitle,
                        type = "mortgage",
                        balance = 0.0,
                        currentBalance = 0.0,
                        estimatedValue = estDollars,
                        isManual = true,
                        sortOrder = existing.sortOrder
                    )
                ).getOrThrow()
                if (created.id > 0L) {
                    api.updateAccount(
                        created.id,
                        created.copy(
                            name = propTitle,
                            type = "mortgage",
                            currentBalance = 0.0,
                            estimatedValue = estDollars
                        )
                    ).getOrThrow()
                }
                api.updateAccount(
                    existing.id,
                    AccountDto(
                        id = existing.id,
                        name = existing.name,
                        type = existing.type.name.lowercase(),
                        institution = existing.institution,
                        accountNumberLast4 = existing.accountNumberLast4,
                        currentBalance = MoneyUtils.centsToDollars(existing.currentBalanceCents),
                        estimatedValue = null,
                        isManual = existing.isManual,
                        isArchived = true,
                        sortOrder = existing.sortOrder,
                        mhaDefaultEligible = existing.mhaDefaultEligible,
                        simplefinAccountId = existing.simplefinAccountId
                    )
                ).getOrThrow()
                api.archiveAccount(existing.id).getOrThrow()
            }
            recordClosureDismissalOverride(existing.id, nowStamp)
            sync(force = false)
        }
    }
}
