package app.overbay.orbit.data.sync

import app.overbay.orbit.data.engine.SyncResult
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudSyncManagerTest {

    private class MockDriveClient(
        var email: String? = null,
        var isAvailableFlag: Boolean = true,
        val storage: MutableMap<String, String> = mutableMapOf()
    ) : RemoteDriveClient {
        override suspend fun isAvailable(): Boolean = isAvailableFlag && email != null
        override suspend fun getConnectedEmail(): String? = email
        override suspend fun listFiles(): List<RemoteFileInfo> = storage.map { (name, content) ->
            RemoteFileInfo(id = name, name = name, sizeBytes = content.length.toLong())
        }
        override suspend fun downloadTextFile(fileName: String): String? = storage[fileName]
        override suspend fun uploadTextFile(fileName: String, content: String): Boolean {
            storage[fileName] = content
            return true
        }
        override suspend fun deleteFile(fileName: String): Boolean = storage.remove(fileName) != null
        override suspend fun wipeAllFiles(): Boolean {
            storage.clear()
            return true
        }
        override suspend fun disconnect() {
            email = null
        }
    }

    @Test
    fun testConnectAndDisconnect() = runTest {
        val client = MockDriveClient()
        val manager = CloudSyncManager(client)

        assertFalse(manager.syncStatus.value.isConnected)
        assertNull(manager.connectedEmail.value)

        manager.connect("test@orbitmoney.app")
        assertTrue(manager.syncStatus.value.isConnected)
        assertEquals("test@orbitmoney.app", manager.connectedEmail.value)

        manager.disconnect()
        assertFalse(manager.syncStatus.value.isConnected)
        assertNull(manager.connectedEmail.value)
    }

    @Test
    fun testBackupAndRestoreSnapshot() = runTest {
        val client = MockDriveClient(email = "test@orbitmoney.app")
        val manager = CloudSyncManager(client, initialEmail = "test@orbitmoney.app")

        val testSnapshot = OrbitWorkspaceSnapshot(
            schemaVersion = 1,
            exportedAt = "2026-10-26T00:00:00Z",
            accounts = listOf(
                Account(id = 1L, name = "Primary Checking", type = AccountType.CHECKING, currentBalanceCents = 150000L)
            ),
            transactions = listOf(
                Transaction(
                    id = 101L,
                    accountId = 1L,
                    originalAmountCents = -4500L,
                    originalMerchant = "Trader Joe's",
                    originalDate = "2026-10-25"
                )
            )
        )

        val backupResult = manager.backupSnapshot(testSnapshot)
        assertTrue(backupResult is SyncResult.Success)
        assertNotNull(client.storage[REMOTE_BACKUP_FILENAME])

        val restoredSnapshot = manager.restoreSnapshot()
        assertNotNull(restoredSnapshot)
        assertEquals(1, restoredSnapshot.accounts.size)
        assertEquals("Primary Checking", restoredSnapshot.accounts.first().name)
        assertEquals(1, restoredSnapshot.transactions.size)
        assertEquals("Trader Joe's", restoredSnapshot.transactions.first().effectiveMerchant)
    }

    @Test
    fun testBackupFailsWhenNotConnected() = runTest {
        val client = MockDriveClient(email = null)
        val manager = CloudSyncManager(client, initialEmail = null)

        val result = manager.backupSnapshot(OrbitWorkspaceSnapshot.empty())
        assertTrue(result is SyncResult.Error)
        assertEquals("Not connected to Google Drive", (result as SyncResult.Error).message)
    }

    @Test
    fun testRestoreReturnsNullWhenFileMissing() = runTest {
        val client = MockDriveClient(email = "user@example.com")
        val manager = CloudSyncManager(client, initialEmail = "user@example.com")

        val restored = manager.restoreSnapshot()
        assertNull(restored)
        assertTrue(manager.lastSyncResult.value is SyncResult.Error)
    }

    @Test
    fun testWipeRemoteData() = runTest {
        val client = MockDriveClient(email = "user@example.com")
        client.storage[REMOTE_BACKUP_FILENAME] = "{}"
        val manager = CloudSyncManager(client, initialEmail = "user@example.com")

        val wipeResult = manager.wipeRemoteData()
        assertTrue(wipeResult is SyncResult.Success)
        assertTrue(client.storage.isEmpty())
    }
}
