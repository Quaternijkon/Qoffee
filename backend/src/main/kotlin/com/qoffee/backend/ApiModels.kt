package com.qoffee.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiErrorResponse(
    val code: String,
    val message: String,
)

@Serializable
data class HealthResponse(
    val status: String,
    val schemaVersion: Int,
    val serverTime: Long,
)

@Serializable
data class AccountDto(
    val id: String,
    val email: String,
    val signedInAt: Long,
)

@Serializable
data class AuthRequest(
    val email: String,
    val password: String,
    val deviceName: String = "Android",
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
)

@Serializable
data class LogoutResponse(
    val success: Boolean,
)

@Serializable
data class AuthResponse(
    val account: AccountDto,
    val deviceId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

@Serializable
data class SyncBootstrapResponse(
    val serverTime: Long,
    val schemaVersion: Int,
    val deviceId: String,
    val supportedTables: List<String>,
)

@Serializable
data class SyncPushRequest(
    val items: List<SyncPushItem>,
)

@Serializable
data class SyncPushItem(
    val tableName: String,
    val localKey: String,
    val remoteId: String? = null,
    val baseVersion: Long? = null,
    val operation: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    val clientChangedAt: Long,
)

@Serializable
data class SyncPushResponse(
    val accepted: List<SyncAcceptedItem>,
    val conflicts: List<SyncConflictDto>,
    val cursor: Long,
)

@Serializable
data class SyncAcceptedItem(
    val tableName: String,
    val localKey: String,
    val remoteId: String,
    val version: Long,
)

@Serializable
data class SyncChangesResponse(
    val changes: List<SyncChangeDto>,
    val nextCursor: Long,
)

@Serializable
data class SyncChangeDto(
    val cursor: Long,
    val tableName: String,
    val remoteId: String,
    val operation: String,
    val version: Long,
    val payload: JsonObject,
    val changedAt: Long,
    val sourceDeviceId: String?,
)

@Serializable
data class SyncConflictDto(
    val id: String,
    val tableName: String,
    val remoteId: String,
    val localKey: String,
    val remoteVersion: Long,
    val localBaseVersion: Long?,
    val remotePayload: JsonObject,
    val localPayload: JsonObject,
    val summary: String,
)

@Serializable
data class ResolveConflictRequest(
    val resolution: String,
    val payload: JsonObject? = null,
)

@Serializable
data class ResolveConflictResponse(
    val conflictId: String,
    val resolution: String,
    val accepted: SyncAcceptedItem? = null,
    val remoteChange: SyncChangeDto? = null,
)

@Serializable
data class SnapshotRequest(
    val fileName: String,
    val mimeType: String,
    val content: String,
)

@Serializable
data class SnapshotResponse(
    val id: String,
    val createdAt: Long,
    val checksum: String,
    val byteSize: Long,
)

@Serializable
data class SnapshotDownloadResponse(
    val summary: SnapshotResponse,
    val fileName: String,
    val mimeType: String,
    val content: String,
)
