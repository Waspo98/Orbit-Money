package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.BalanceRecord
import app.overbay.orbit.data.model.BalanceRecordSource
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction

/**
 * Pure domain engine for automatic on-change + monthly-anchor account balance history.
 * Mirrors backend/src/services/accountBalanceRecordService.js so Self-Hosted and Local Mode
 * behave identically while keeping storage compact over multi-year horizons.
 */
object BalanceHistoryEngine {

    fun defaultSourceFor(account: Account): BalanceRecordSource =
        if (!account.simplefinAccountId.isNullOrBlank()) BalanceRecordSource.SIMPLEFIN else BalanceRecordSource.MANUAL

    /**
     * Upserts a dated checkpoint when an account's balance changes or enters a new YYYY-MM month,
     * preserving user-entered MANUAL records over automatic SIMPLEFIN captures on the same date.
     * Returns [records] unchanged (preserving referential identity) when no write is needed.
     */
    fun upsertAutoCheckpoint(
        records: List<BalanceRecord>,
        account: Account,
        recordDate: String,
        balanceCents: Cents = account.currentBalanceCents,
        source: BalanceRecordSource = defaultSourceFor(account)
    ): List<BalanceRecord> {
        if (account.isArchived || account.isProperty || account.id <= 0L || recordDate.length < 10) {
            return records
        }

        val sameDayIndex = records.indexOfFirst { it.accountId == account.id && it.recordDate == recordDate }
        if (sameDayIndex >= 0) {
            val existing = records[sameDayIndex]
            if (existing.source == BalanceRecordSource.MANUAL && source == BalanceRecordSource.SIMPLEFIN) {
                return records
            }
            if (existing.balanceCents == balanceCents && existing.source == source) {
                return records
            }
            val updated = existing.copy(balanceCents = balanceCents, source = source)
            return records.toMutableList().apply { this[sameDayIndex] = updated }
        }

        val prior = records
            .asSequence()
            .filter { it.accountId == account.id && it.recordDate < recordDate }
            .maxWithOrNull(compareBy<BalanceRecord> { it.recordDate }.thenBy { it.id })

        val shouldInsert = if (prior == null) {
            balanceCents != 0L || source == BalanceRecordSource.SIMPLEFIN
        } else {
            prior.balanceCents != balanceCents || prior.recordDate.take(7) != recordDate.take(7)
        }
        if (!shouldInsert) return records

        val nextId = (records.maxOfOrNull { it.id } ?: 0L) + 1L
        val created = BalanceRecord(
            id = nextId,
            householdId = account.householdId,
            accountId = account.id,
            recordDate = recordDate,
            balanceCents = balanceCents,
            source = source
        )
        return records + created
    }

    /**
     * Sweeps all active non-property accounts on Local Mode startup to ensure initial baselines
     * and new-month anchors exist without duplicating unchanged intra-month snapshots.
     */
    fun ensureDailySnapshotCheckpoints(
        snapshot: OrbitWorkspaceSnapshot,
        todayIso: String
    ): OrbitWorkspaceSnapshot {
        var records = snapshot.balanceRecords
        for (acct in snapshot.accounts) {
            records = upsertAutoCheckpoint(records, acct, todayIso)
        }
        return if (records === snapshot.balanceRecords) snapshot else snapshot.copy(balanceRecords = records)
    }

    fun applySavedAccount(
        snapshot: OrbitWorkspaceSnapshot,
        savedAccount: Account,
        updatedAccounts: List<Account>,
        todayIso: String
    ): OrbitWorkspaceSnapshot {
        val updatedRecords = upsertAutoCheckpoint(
            records = snapshot.balanceRecords,
            account = savedAccount,
            recordDate = todayIso,
            balanceCents = savedAccount.currentBalanceCents
        )
        return snapshot.copy(accounts = updatedAccounts, balanceRecords = updatedRecords)
    }

    fun applyTransactionBalanceDeltas(
        snapshot: OrbitWorkspaceSnapshot,
        oldTxns: List<Transaction>,
        newTxns: List<Transaction>,
        todayIso: String
    ): Pair<OrbitWorkspaceSnapshot, List<Account>> {
        val deltas = mutableMapOf<Long, Cents>()
        for (t in oldTxns) {
            if (!t.effectiveIsIgnored && t.accountId > 0L) {
                deltas[t.accountId] = (deltas[t.accountId] ?: 0L) - t.effectiveAmountCents
            }
        }
        for (t in newTxns) {
            if (!t.effectiveIsIgnored && t.accountId > 0L) {
                deltas[t.accountId] = (deltas[t.accountId] ?: 0L) + t.effectiveAmountCents
            }
        }

        val modifiedManualAccounts = mutableListOf<Account>()
        var updatedRecords = snapshot.balanceRecords
        val updatedAccounts = snapshot.accounts.map { acct ->
            val delta = deltas[acct.id] ?: 0L
            if (acct.isManual && delta != 0L) {
                val updatedAcct = acct.copy(currentBalanceCents = acct.currentBalanceCents + delta)
                modifiedManualAccounts.add(updatedAcct)
                updatedRecords = upsertAutoCheckpoint(
                    records = updatedRecords,
                    account = updatedAcct,
                    recordDate = todayIso,
                    balanceCents = updatedAcct.currentBalanceCents,
                    source = BalanceRecordSource.MANUAL
                )
                updatedAcct
            } else {
                acct
            }
        }

        val updatedSnapshot = snapshot.copy(
            transactions = newTxns,
            accounts = updatedAccounts,
            balanceRecords = updatedRecords
        )
        return updatedSnapshot to modifiedManualAccounts
    }

    fun remapAccountId(
        records: List<BalanceRecord>,
        oldAccountId: Long,
        newAccountId: Long
    ): List<BalanceRecord> {
        if (oldAccountId == newAccountId || records.none { it.accountId == oldAccountId }) return records
        val existingDatesOnTarget = records
            .asSequence()
            .filter { it.accountId == newAccountId }
            .map { it.recordDate }
            .toSet()
        return records.mapNotNull { rec ->
            when {
                rec.accountId != oldAccountId -> rec
                rec.recordDate in existingDatesOnTarget -> null
                else -> rec.copy(accountId = newAccountId)
            }
        }
    }

    fun mergeAccountRecords(
        records: List<BalanceRecord>,
        targetAccount: Account,
        sourceAccountId: Long,
        todayIso: String
    ): List<BalanceRecord> {
        val targetByDate = records
            .filter { it.accountId == targetAccount.id }
            .associateBy { it.recordDate }
            .toMutableMap()

        for (srcRec in records.filter { it.accountId == sourceAccountId }) {
            val existing = targetByDate[srcRec.recordDate]
            if (existing == null) {
                targetByDate[srcRec.recordDate] = srcRec.copy(accountId = targetAccount.id)
            } else if (existing.source == BalanceRecordSource.SIMPLEFIN && srcRec.source == BalanceRecordSource.MANUAL) {
                targetByDate[srcRec.recordDate] = existing.copy(
                    balanceCents = srcRec.balanceCents,
                    source = BalanceRecordSource.MANUAL
                )
            }
        }

        val others = records.filter { it.accountId != targetAccount.id && it.accountId != sourceAccountId }
        val merged = others + targetByDate.values
        return upsertAutoCheckpoint(merged, targetAccount, todayIso, targetAccount.currentBalanceCents)
    }
}
