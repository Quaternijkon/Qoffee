package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.google.common.truth.Truth.assertThat
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.FileExportPayload
import com.qoffee.core.model.RestoreOutcome
import com.qoffee.core.model.SyncPhase
import com.qoffee.data.remote.AccountDto
import com.qoffee.data.remote.AuthResponseDto
import com.qoffee.data.remote.QoffeeApi
import com.qoffee.data.remote.ResolveConflictRequestDto
import com.qoffee.data.remote.ResolveConflictResponseDto
import com.qoffee.data.remote.SnapshotDownloadResponseDto
import com.qoffee.data.remote.SnapshotRequestDto
import com.qoffee.data.remote.SnapshotResponseDto
import com.qoffee.data.remote.SyncAcceptedItemDto
import com.qoffee.data.remote.SyncBootstrapResponseDto
import com.qoffee.data.remote.SyncChangeDto
import com.qoffee.data.remote.SyncChangesResponseDto
import com.qoffee.data.remote.SyncPushItemDto
import com.qoffee.data.remote.SyncPushRequestDto
import com.qoffee.data.remote.SyncPushResponseDto
import com.qoffee.domain.repository.BackupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class SyncRepositoryImplTest {

    @Test
    fun signInPushPullAndSnapshotUpdateObservableState() = runTest {
        val timeProvider = FakeTimeProvider(now = 1_000L)
        val dataStore = InMemoryPreferencesDataStore()
        val repository = SyncRepositoryImpl(
            dataStore = dataStore,
            backupRepository = FakeBackupRepository(),
            timeProvider = timeProvider,
            apiClient = FakeQoffeeApi(timeProvider),
            roomSyncBridge = FakeSyncDataBridge(),
        )

        assertThat(repository.observeSyncState().first().phase).isEqualTo(SyncPhase.SIGNED_OUT)

        repository.signInWithEmail("USER@example.com", "password123")
        val signedIn = repository.observeSyncState().first()
        assertThat(signedIn.account?.email).isEqualTo("user@example.com")
        assertThat(signedIn.phase).isEqualTo(SyncPhase.IDLE)

        timeProvider.now = 2_000L
        assertThat(repository.pushChanges().success).isTrue()
        timeProvider.now = 3_000L
        assertThat(repository.pullChanges().success).isTrue()
        timeProvider.now = 4_000L
        assertThat(repository.createSnapshot().success).isTrue()

        val synced = repository.observeSyncState().first()
        assertThat(synced.lastPushedAt).isEqualTo(2_000L)
        assertThat(synced.lastPulledAt).isEqualTo(3_000L)
        assertThat(synced.lastSnapshot?.id).isEqualTo("snapshot-remote")
        assertThat(synced.lastSnapshot?.checksum).hasLength(64)
    }

    @Test
    fun pushRequiresSignedInAccount() = runTest {
        val dataStore = InMemoryPreferencesDataStore()
        val repository = SyncRepositoryImpl(
            dataStore = dataStore,
            backupRepository = FakeBackupRepository(),
            timeProvider = FakeTimeProvider(now = 1_000L),
            apiClient = FakeQoffeeApi(FakeTimeProvider(now = 1_000L)),
            roomSyncBridge = FakeSyncDataBridge(),
        )

        val result = repository.pushChanges()

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("登录")
    }

    @Test
    fun pullBlocksChangesFromOtherDevicesInInternalTestBuild() = runTest {
        val timeProvider = FakeTimeProvider(now = 1_000L)
        val repository = SyncRepositoryImpl(
            dataStore = InMemoryPreferencesDataStore(),
            backupRepository = FakeBackupRepository(),
            timeProvider = timeProvider,
            apiClient = FakeQoffeeApi(
                timeProvider = timeProvider,
                remoteChanges = listOf(
                    SyncChangeDto(
                        cursor = 11L,
                        tableName = "archives",
                        remoteId = "remote-archive-2",
                        operation = "UPSERT",
                        version = 2L,
                        payload = JsonObject(emptyMap()),
                        changedAt = 900L,
                        sourceDeviceId = "device-2",
                    ),
                ),
            ),
            roomSyncBridge = FakeSyncDataBridge(),
        )
        repository.signInWithEmail("user@example.com", "password123")

        val result = repository.pullChanges()

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("暂不自动合并")
    }

    @Test
    fun pushFailsFastWhenRemoteTableContractDiffers() = runTest {
        val timeProvider = FakeTimeProvider(now = 1_000L)
        val repository = SyncRepositoryImpl(
            dataStore = InMemoryPreferencesDataStore(),
            backupRepository = FakeBackupRepository(),
            timeProvider = timeProvider,
            apiClient = FakeQoffeeApi(timeProvider, supportedTables = listOf("archives", "brew_records")),
            roomSyncBridge = FakeSyncDataBridge(supportedTables = listOf("archives")),
        )
        repository.signInWithEmail("user@example.com", "password123")

        val result = repository.pushChanges()

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("表定义不匹配")
    }

    private class FakeTimeProvider(
        var now: Long,
    ) : TimeProvider {
        override fun nowMillis(): Long = now
    }

    private class FakeBackupRepository : BackupRepository {
        override suspend fun exportBackup(): FileExportPayload {
            return FileExportPayload(
                fileName = "backup.json",
                mimeType = "application/json",
                content = """{"archives":[{"name":"My Qoffee"}]}""",
            )
        }

        override suspend fun restoreBackup(json: String): RestoreOutcome {
            error("Not used")
        }

        override suspend fun exportRecordsCsv(filter: AnalysisFilter): FileExportPayload {
            error("Not used")
        }
    }

    private class FakeSyncDataBridge(
        private val supportedTables: List<String> = listOf("archives"),
    ) : SyncDataBridge {
        override fun supportedTables(): List<String> = supportedTables

        override suspend fun exportAll(clientChangedAt: Long): List<SyncPushItemDto> {
            return listOf(
                SyncPushItemDto(
                    tableName = "archives",
                    localKey = "1",
                    operation = "UPSERT",
                    payload = JsonObject(emptyMap()),
                    clientChangedAt = clientChangedAt,
                ),
            )
        }

        override suspend fun markAccepted(items: List<SyncAcceptedItemDto>, syncedAt: Long) = Unit

        override suspend fun applyChanges(changes: List<SyncChangeDto>, syncedAt: Long) = Unit
    }

    private class FakeQoffeeApi(
        private val timeProvider: FakeTimeProvider,
        private val schemaVersion: Int = 1,
        private val supportedTables: List<String> = listOf("archives"),
        private val remoteChanges: List<SyncChangeDto> = emptyList(),
    ) : QoffeeApi {
        override suspend fun register(email: String, password: String, deviceName: String): AuthResponseDto =
            session(email)

        override suspend fun login(email: String, password: String, deviceName: String): AuthResponseDto =
            session(email)

        override suspend fun refresh(refreshToken: String): AuthResponseDto =
            session("user@example.com")

        override suspend fun logout(refreshToken: String) = Unit

        override suspend fun bootstrap(accessToken: String): SyncBootstrapResponseDto =
            SyncBootstrapResponseDto(
                serverTime = timeProvider.nowMillis(),
                schemaVersion = schemaVersion,
                deviceId = "device-1",
                supportedTables = supportedTables,
            )

        override suspend fun push(accessToken: String, request: SyncPushRequestDto): SyncPushResponseDto =
            SyncPushResponseDto(
                accepted = request.items.mapIndexed { index, item ->
                    SyncAcceptedItemDto(
                        tableName = item.tableName,
                        localKey = item.localKey,
                        remoteId = "remote-$index",
                        version = 1L,
                    )
                },
                conflicts = emptyList(),
                cursor = 10L,
            )

        override suspend fun changes(accessToken: String, since: Long, limit: Int): SyncChangesResponseDto =
            SyncChangesResponseDto(
                changes = remoteChanges,
                nextCursor = remoteChanges.lastOrNull()?.cursor ?: since,
            )

        override suspend fun resolveConflict(
            accessToken: String,
            conflictId: String,
            request: ResolveConflictRequestDto,
        ): ResolveConflictResponseDto =
            ResolveConflictResponseDto(conflictId = conflictId, resolution = request.resolution)

        override suspend fun createSnapshot(accessToken: String, request: SnapshotRequestDto): SnapshotResponseDto =
            SnapshotResponseDto(
                id = "snapshot-remote",
                createdAt = timeProvider.nowMillis(),
                checksum = "a".repeat(64),
                byteSize = request.content.length.toLong(),
            )

        override suspend fun latestSnapshot(accessToken: String): SnapshotDownloadResponseDto {
            error("Not used")
        }

        private fun session(email: String): AuthResponseDto =
            AuthResponseDto(
                account = AccountDto(id = "user-1", email = email.lowercase(), signedInAt = timeProvider.nowMillis()),
                deviceId = "device-1",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiresAt = timeProvider.nowMillis() + 60_000L,
            )
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
