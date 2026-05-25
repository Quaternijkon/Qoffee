package com.qoffee.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiErrorResponseDto(
    val code: String = "UNKNOWN",
    val message: String = "",
)

@Serializable
data class AuthRequestDto(
    val email: String,
    val password: String,
    val deviceName: String = "Android",
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
)

@Serializable
data class LogoutRequestDto(
    val refreshToken: String,
)

@Serializable
data class LogoutResponseDto(
    val success: Boolean,
)

@Serializable
data class AccountDto(
    val id: String,
    val email: String,
    val signedInAt: Long,
)

@Serializable
data class AuthResponseDto(
    val account: AccountDto,
    val deviceId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

@Serializable
data class SyncBootstrapResponseDto(
    val serverTime: Long,
    val schemaVersion: Int,
    val deviceId: String,
    val supportedTables: List<String>,
)

@Serializable
data class SyncPushRequestDto(
    val items: List<SyncPushItemDto>,
)

@Serializable
data class SyncPushItemDto(
    val tableName: String,
    val localKey: String,
    val remoteId: String? = null,
    val baseVersion: Long? = null,
    val operation: String,
    val payload: JsonObject,
    val clientChangedAt: Long,
)

@Serializable
data class SyncPushResponseDto(
    val accepted: List<SyncAcceptedItemDto>,
    val conflicts: List<SyncConflictDto>,
    val cursor: Long,
)

@Serializable
data class SyncAcceptedItemDto(
    val tableName: String,
    val localKey: String,
    val remoteId: String,
    val version: Long,
)

@Serializable
data class SyncChangesResponseDto(
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
    val sourceDeviceId: String? = null,
)

@Serializable
data class SyncConflictDto(
    val id: String,
    val tableName: String,
    val remoteId: String,
    val localKey: String,
    val remoteVersion: Long,
    val localBaseVersion: Long? = null,
    val remotePayload: JsonObject,
    val localPayload: JsonObject,
    val summary: String,
)

@Serializable
data class ResolveConflictRequestDto(
    val resolution: String,
    val payload: JsonObject? = null,
)

@Serializable
data class ResolveConflictResponseDto(
    val conflictId: String,
    val resolution: String,
    val accepted: SyncAcceptedItemDto? = null,
    val remoteChange: SyncChangeDto? = null,
)

@Serializable
data class SnapshotRequestDto(
    val fileName: String,
    val mimeType: String,
    val content: String,
)

@Serializable
data class SnapshotResponseDto(
    val id: String,
    val createdAt: Long,
    val checksum: String,
    val byteSize: Long,
)

@Serializable
data class SnapshotDownloadResponseDto(
    val summary: SnapshotResponseDto,
    val fileName: String,
    val mimeType: String,
    val content: String,
)
