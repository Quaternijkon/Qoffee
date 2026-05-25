package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.CloudSnapshotSummary
import com.qoffee.core.model.ServerEnvironment
import com.qoffee.core.model.SyncAccount
import com.qoffee.core.model.SyncConflict
import com.qoffee.core.model.SyncConflictResolution
import com.qoffee.core.model.SyncOperationResult
import com.qoffee.core.model.SyncPhase
import com.qoffee.core.model.SyncState
import com.qoffee.data.remote.AuthResponseDto
import com.qoffee.data.remote.QoffeeApi
import com.qoffee.data.remote.QoffeeApiException
import com.qoffee.data.remote.ResolveConflictRequestDto
import com.qoffee.data.remote.SnapshotRequestDto
import com.qoffee.data.remote.SyncBootstrapResponseDto
import com.qoffee.data.remote.SyncConflictDto
import com.qoffee.data.remote.SyncPushRequestDto
import com.qoffee.domain.repository.BackupRepository
import com.qoffee.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val backupRepository: BackupRepository,
    private val timeProvider: TimeProvider,
    private val apiClient: QoffeeApi,
    private val roomSyncBridge: SyncDataBridge,
) : SyncRepository {

    private val transientPhase = MutableStateFlow<SyncPhase?>(null)
    private val pendingConflicts = MutableStateFlow<List<SyncConflict>>(emptyList())

    override fun observeSyncState(): Flow<SyncState> {
        return combine(dataStore.data, transientPhase, pendingConflicts) { prefs, phase, conflicts ->
            prefs.toSyncState(phase, conflicts)
        }
    }

    override suspend fun registerWithEmail(email: String, password: String): SyncOperationResult {
        return runAuthOperation {
            val normalizedEmail = normalizeEmail(email)
            val session = apiClient.register(normalizedEmail, password, deviceName = DEVICE_NAME)
            storeSession(session)
            SyncOperationResult(success = true, message = "已注册并登录 $normalizedEmail。")
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): SyncOperationResult {
        return runAuthOperation {
            val normalizedEmail = normalizeEmail(email)
            val session = apiClient.login(normalizedEmail, password, deviceName = DEVICE_NAME)
            storeSession(session)
            SyncOperationResult(success = true, message = "已登录 $normalizedEmail。")
        }
    }

    override suspend fun signInWithEmail(email: String): SyncOperationResult {
        return SyncOperationResult(success = false, message = "请输入邮箱和密码登录云同步。")
    }

    override suspend fun signOut(): SyncOperationResult {
        val refreshToken = dataStore.data.first()[PreferenceKeys.SYNC_REFRESH_TOKEN]
        runCatching {
            if (!refreshToken.isNullOrBlank()) {
                apiClient.logout(refreshToken)
            }
        }
        clearSession()
        pendingConflicts.value = emptyList()
        transientPhase.value = null
        return SyncOperationResult(success = true, message = "已退出云同步账号。")
    }

    override suspend fun pushChanges(): SyncOperationResult {
        return runSignedInOperation(SyncPhase.PUSHING) {
            val accessToken = ensureAccessToken()
            validateBootstrap(apiClient.bootstrap(accessToken))
            val now = timeProvider.nowMillis()
            val items = roomSyncBridge.exportAll(clientChangedAt = now)
            var acceptedCount = 0
            var conflictCount = 0
            var latestCursor = dataStore.data.first()[PreferenceKeys.SYNC_CHANGE_CURSOR] ?: 0L
            val conflicts = mutableListOf<SyncConflictDto>()
            items.chunked(PUSH_CHUNK_SIZE).forEach { chunk ->
                val response = apiClient.push(accessToken, SyncPushRequestDto(chunk))
                roomSyncBridge.markAccepted(response.accepted, syncedAt = now)
                acceptedCount += response.accepted.size
                conflictCount += response.conflicts.size
                latestCursor = maxOf(latestCursor, response.cursor)
                conflicts += response.conflicts
            }
            pendingConflicts.value = conflicts.map { it.toDomainConflict() }
            val message = if (conflictCount == 0) {
                "已上传 $acceptedCount 条本地同步项。"
            } else {
                "已上传 $acceptedCount 条，发现 $conflictCount 个冲突。"
            }
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.SYNC_LAST_PUSHED_AT] = now
                prefs[PreferenceKeys.SYNC_CHANGE_CURSOR] = latestCursor
                prefs[PreferenceKeys.SYNC_LAST_MESSAGE] = message
            }
            SyncOperationResult(success = conflictCount == 0, message = message)
        }
    }

    override suspend fun pullChanges(): SyncOperationResult {
        return runSignedInOperation(SyncPhase.PULLING) {
            val accessToken = ensureAccessToken()
            validateBootstrap(apiClient.bootstrap(accessToken))
            val now = timeProvider.nowMillis()
            val deviceId = dataStore.data.first()[PreferenceKeys.SYNC_DEVICE_ID]
            var cursor = dataStore.data.first()[PreferenceKeys.SYNC_CHANGE_CURSOR] ?: 0L
            var appliedCount = 0
            do {
                val response = apiClient.changes(accessToken, since = cursor, limit = PULL_PAGE_SIZE)
                val incoming = response.changes.filterNot { it.sourceDeviceId == deviceId }
                check(incoming.isEmpty()) {
                    "当前内测版暂不自动合并其他设备的云端变更，请在源设备创建云端快照，或使用本地备份恢复。"
                }
                roomSyncBridge.applyChanges(incoming, syncedAt = now)
                appliedCount += incoming.size
                cursor = response.nextCursor
            } while (response.changes.size == PULL_PAGE_SIZE)
            val message = "已拉取并应用 $appliedCount 条远端变更。"
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.SYNC_LAST_PULLED_AT] = now
                prefs[PreferenceKeys.SYNC_CHANGE_CURSOR] = cursor
                prefs[PreferenceKeys.SYNC_LAST_MESSAGE] = message
            }
            SyncOperationResult(success = true, message = message)
        }
    }

    override suspend fun createSnapshot(): SyncOperationResult {
        return runSignedInOperation(SyncPhase.SNAPSHOTTING) {
            val accessToken = ensureAccessToken()
            val payload = backupRepository.exportBackup()
            val snapshot = apiClient.createSnapshot(
                accessToken,
                SnapshotRequestDto(
                    fileName = payload.fileName,
                    mimeType = payload.mimeType,
                    content = payload.content,
                ),
            )
            val message = "已创建云端快照。"
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.SYNC_LAST_SNAPSHOT_ID] = snapshot.id
                prefs[PreferenceKeys.SYNC_LAST_SNAPSHOT_AT] = snapshot.createdAt
                prefs[PreferenceKeys.SYNC_LAST_SNAPSHOT_CHECKSUM] = snapshot.checksum
                prefs[PreferenceKeys.SYNC_LAST_SNAPSHOT_BYTES] = snapshot.byteSize
                prefs[PreferenceKeys.SYNC_LAST_MESSAGE] = message
            }
            SyncOperationResult(success = true, message = message)
        }
    }

    override suspend fun resolveConflict(
        conflictId: String,
        resolution: SyncConflictResolution,
    ): SyncOperationResult {
        return runSignedInOperation(SyncPhase.PULLING) {
            val accessToken = ensureAccessToken()
            val response = apiClient.resolveConflict(
                accessToken,
                conflictId,
                ResolveConflictRequestDto(resolution = resolution.name),
            )
            val now = timeProvider.nowMillis()
            response.remoteChange?.let { change ->
                roomSyncBridge.applyChanges(listOf(change), syncedAt = now)
            }
            response.accepted?.let { accepted ->
                roomSyncBridge.markAccepted(listOf(accepted), syncedAt = now)
            }
            pendingConflicts.value = pendingConflicts.value.filterNot { it.id == conflictId }
            val message = when (resolution) {
                SyncConflictResolution.KEEP_REMOTE -> "已保留远端版本。"
                SyncConflictResolution.KEEP_LOCAL -> "已保留本机版本。"
            }
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.SYNC_LAST_MESSAGE] = message
            }
            SyncOperationResult(success = true, message = message)
        }
    }

    private suspend fun runAuthOperation(block: suspend () -> SyncOperationResult): SyncOperationResult {
        return try {
            block()
        } catch (error: Throwable) {
            SyncOperationResult(success = false, message = error.readableMessage("登录失败，请稍后重试。"))
        }
    }

    private suspend fun runSignedInOperation(
        phase: SyncPhase,
        block: suspend () -> SyncOperationResult,
    ): SyncOperationResult {
        return try {
            transientPhase.value = phase
            block()
        } catch (error: Throwable) {
            val message = error.readableMessage("同步操作失败，请稍后重试。")
            if (error is QoffeeApiException && error.isAuthExpired) {
                clearSession()
                pendingConflicts.value = emptyList()
            }
            dataStore.edit { prefs ->
                prefs[PreferenceKeys.SYNC_LAST_MESSAGE] = message
            }
            SyncOperationResult(success = false, message = message)
        } finally {
            transientPhase.value = null
        }
    }

    private suspend fun ensureAccessToken(): String {
        val prefs = dataStore.data.first()
        val accessToken = prefs[PreferenceKeys.SYNC_ACCESS_TOKEN]
        val refreshToken = prefs[PreferenceKeys.SYNC_REFRESH_TOKEN]
        val expiresAt = prefs[PreferenceKeys.SYNC_ACCESS_EXPIRES_AT] ?: 0L
        check(!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) { "请先登录云同步账号。" }
        if (expiresAt > timeProvider.nowMillis() + TOKEN_REFRESH_SKEW_MILLIS) {
            return accessToken
        }
        val refreshed = apiClient.refresh(refreshToken)
        storeSession(refreshed)
        return refreshed.accessToken
    }

    private suspend fun validateBootstrap(bootstrap: SyncBootstrapResponseDto) {
        check(bootstrap.schemaVersion == REMOTE_SCHEMA_VERSION) {
            "云端同步协议版本不匹配，请更新应用后再同步。"
        }
        val localTables = roomSyncBridge.supportedTables()
        val remoteTables = bootstrap.supportedTables
        check(localTables == remoteTables) {
            val missingRemote = localTables.filterNot(remoteTables.toSet()::contains)
            val unknownRemote = remoteTables.filterNot(localTables.toSet()::contains)
            buildString {
                append("云端同步表定义不匹配，请更新应用后再同步。")
                if (missingRemote.isNotEmpty()) append(" 缺少远端表：${missingRemote.joinToString()}.")
                if (unknownRemote.isNotEmpty()) append(" 未知远端表：${unknownRemote.joinToString()}.")
            }
        }
        val expectedDeviceId = dataStore.data.first()[PreferenceKeys.SYNC_DEVICE_ID]
        check(expectedDeviceId.isNullOrBlank() || expectedDeviceId == bootstrap.deviceId) {
            "云端设备状态不匹配，请重新登录。"
        }
    }

    private suspend fun storeSession(session: AuthResponseDto) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.SYNC_EMAIL] = session.account.email
            prefs[PreferenceKeys.SYNC_SIGNED_IN_AT] = session.account.signedInAt
            prefs[PreferenceKeys.SYNC_DEVICE_ID] = session.deviceId
            prefs[PreferenceKeys.SYNC_ACCESS_TOKEN] = session.accessToken
            prefs[PreferenceKeys.SYNC_REFRESH_TOKEN] = session.refreshToken
            prefs[PreferenceKeys.SYNC_ACCESS_EXPIRES_AT] = session.expiresAt
            prefs[PreferenceKeys.SYNC_LAST_MESSAGE] = "云同步已登录。"
        }
    }

    private suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.SYNC_EMAIL)
            prefs.remove(PreferenceKeys.SYNC_SIGNED_IN_AT)
            prefs.remove(PreferenceKeys.SYNC_DEVICE_ID)
            prefs.remove(PreferenceKeys.SYNC_ACCESS_TOKEN)
            prefs.remove(PreferenceKeys.SYNC_REFRESH_TOKEN)
            prefs.remove(PreferenceKeys.SYNC_ACCESS_EXPIRES_AT)
            prefs.remove(PreferenceKeys.SYNC_CHANGE_CURSOR)
            prefs.remove(PreferenceKeys.SYNC_LAST_PUSHED_AT)
            prefs.remove(PreferenceKeys.SYNC_LAST_PULLED_AT)
            prefs.remove(PreferenceKeys.SYNC_LAST_MESSAGE)
            prefs.remove(PreferenceKeys.SYNC_LAST_SNAPSHOT_ID)
            prefs.remove(PreferenceKeys.SYNC_LAST_SNAPSHOT_AT)
            prefs.remove(PreferenceKeys.SYNC_LAST_SNAPSHOT_CHECKSUM)
            prefs.remove(PreferenceKeys.SYNC_LAST_SNAPSHOT_BYTES)
        }
    }

    private fun Preferences.toSyncState(transient: SyncPhase?, conflicts: List<SyncConflict>): SyncState {
        val environment = ServerEnvironment.entries.firstOrNull {
            it.name == this[PreferenceKeys.SERVER_ENVIRONMENT]
        } ?: ServerEnvironment.TEST
        val email = this[PreferenceKeys.SYNC_EMAIL]
        val account = email?.let {
            SyncAccount(
                email = it,
                signedInAt = this[PreferenceKeys.SYNC_SIGNED_IN_AT] ?: 0L,
            )
        }
        val snapshot = this[PreferenceKeys.SYNC_LAST_SNAPSHOT_ID]?.let { snapshotId ->
            CloudSnapshotSummary(
                id = snapshotId,
                createdAt = this[PreferenceKeys.SYNC_LAST_SNAPSHOT_AT] ?: 0L,
                checksum = this[PreferenceKeys.SYNC_LAST_SNAPSHOT_CHECKSUM].orEmpty(),
                byteSize = this[PreferenceKeys.SYNC_LAST_SNAPSHOT_BYTES] ?: 0L,
            )
        }
        return SyncState(
            account = account,
            phase = transient ?: if (account == null) SyncPhase.SIGNED_OUT else SyncPhase.IDLE,
            backendLabel = environment.backendLabel,
            lastPushedAt = this[PreferenceKeys.SYNC_LAST_PUSHED_AT],
            lastPulledAt = this[PreferenceKeys.SYNC_LAST_PULLED_AT],
            lastSnapshot = snapshot,
            pendingConflicts = if (account == null) emptyList() else conflicts,
            lastMessage = this[PreferenceKeys.SYNC_LAST_MESSAGE],
        )
    }

    private fun SyncConflictDto.toDomainConflict(): SyncConflict {
        return SyncConflict(
            id = id,
            entityType = tableName,
            entityId = remoteId,
            localUpdatedAt = 0L,
            remoteUpdatedAt = 0L,
            summary = summary.ifBlank { "$tableName/$localKey 需要选择保留版本。" },
        )
    }

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()
        check(normalized.contains("@") && normalized.substringAfter("@").contains(".")) { "请输入有效邮箱。" }
        return normalized
    }

    private fun Throwable.readableMessage(fallback: String): String {
        return when (this) {
            is QoffeeApiException -> toUserMessage(fallback)
            is IllegalStateException -> message?.takeIf { it.isNotBlank() } ?: fallback
            else -> fallback
        }
    }

    private fun QoffeeApiException.toUserMessage(fallback: String): String {
        return when (code) {
            "INVALID_EMAIL" -> "请输入有效邮箱。"
            "WEAK_PASSWORD" -> "密码至少需要 8 位。"
            "EMAIL_ALREADY_REGISTERED" -> "邮箱已注册，请直接登录。"
            "INVALID_CREDENTIALS" -> "邮箱或密码不正确。"
            "AUTH_EXPIRED", "INVALID_AUTH_STATE", "INVALID_DEVICE" -> "登录已过期，请重新登录。"
            "TOO_MANY_SYNC_ITEMS" -> "单次同步内容过多，请稍后重试。"
            "UNSUPPORTED_SYNC_OPERATION", "UNSUPPORTED_SYNC_TABLE" -> "当前应用与云端同步协议不匹配，请更新应用后再试。"
            "CONFLICT_NOT_FOUND" -> "冲突已处理或不存在。"
            "SNAPSHOT_NOT_FOUND" -> "还没有云端快照。"
            "BAD_REQUEST" -> "请求格式有误，请更新应用后再试。"
            "INTERNAL_ERROR" -> "服务暂时不可用，请稍后重试。"
            "INVALID_RESPONSE" -> userMessage
            else -> userMessage.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    private companion object {
        const val DEVICE_NAME = "Qoffee Android"
        const val PUSH_CHUNK_SIZE = 250
        const val PULL_PAGE_SIZE = 200
        const val REMOTE_SCHEMA_VERSION = 1
        const val TOKEN_REFRESH_SKEW_MILLIS = 60_000L
    }
}
