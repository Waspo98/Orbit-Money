package app.overbay.orbit.data

import app.overbay.orbit.data.engine.LocalSnapshotEngine
import app.overbay.orbit.data.engine.OrbitStorageEngine
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.sync.CloudSyncManager
import app.overbay.orbit.util.PlatformStorageEpoch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coalesces rapid in-memory workspace mutations into a single debounced background disk write
 * off the Main UI thread so full-database JSON encoding never blocks 60/120fps animations.
 */
internal class SnapshotFlushCoordinator(
    private val scope: CoroutineScope,
    private val localEngine: LocalSnapshotEngine,
    private val cloudSyncManager: CloudSyncManager,
    private val debounceMs: Long = 250L
) {
    private val diskMutex = Mutex()
    private var pendingJob: Job? = null
    private var lastSyncedLocalPreferences: OrbitPreferences? = null

    fun scheduleFlush(
        snapshotProvider: () -> OrbitWorkspaceSnapshot,
        activeEngineProvider: () -> OrbitStorageEngine
    ) {
        val scheduledEpoch = PlatformStorageEpoch.epoch
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(debounceMs)
            withContext(NonCancellable) {
                diskMutex.withLock {
                    if (PlatformStorageEpoch.epoch != scheduledEpoch) return@withLock
                    persistLocked(snapshotProvider(), activeEngineProvider())
                }
            }
        }
    }

    suspend fun flushImmediate(
        snapshot: OrbitWorkspaceSnapshot,
        activeEngine: OrbitStorageEngine
    ) {
        pendingJob?.cancel()
        pendingJob = null
        val scheduledEpoch = PlatformStorageEpoch.epoch
        withContext(NonCancellable) {
            diskMutex.withLock {
                if (PlatformStorageEpoch.epoch != scheduledEpoch) return@withLock
                persistLocked(snapshot, activeEngine)
            }
        }
    }

    private suspend fun persistLocked(
        snapshot: OrbitWorkspaceSnapshot,
        activeEngine: OrbitStorageEngine
    ) {
        activeEngine.saveSnapshot(snapshot)
        if (activeEngine !== localEngine) {
            // In self-hosted mode, avoid duplicating multi-MB transaction snapshots into localEngine;
            // only keep startup preferences in sync when preferences change.
            if (lastSyncedLocalPreferences != snapshot.preferences) {
                val baseLocal = localEngine.loadSnapshot()
                localEngine.saveSnapshot(baseLocal.copy(preferences = snapshot.preferences))
                lastSyncedLocalPreferences = snapshot.preferences
            }
        } else {
            lastSyncedLocalPreferences = snapshot.preferences
        }
        if (snapshot.preferences.googleDriveSyncEnabled) {
            cloudSyncManager.backupSnapshot(snapshot)
        }
    }
}
