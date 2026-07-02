package com.qoffee.backend

import io.ktor.http.HttpStatusCode
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.postgresql.util.PGobject

class SyncService(
    private val dataSource: DataSource,
    private val json: Json,
) {
    fun bootstrap(device: UserDevice): SyncBootstrapResponse {
        return SyncBootstrapResponse(
            serverTime = System.currentTimeMillis(),
            schemaVersion = SyncTables.SCHEMA_VERSION,
            deviceId = device.deviceId.toString(),
            supportedTables = SyncTables.supportedTables,
        )
    }

    fun push(device: UserDevice, request: SyncPushRequest): SyncPushResponse {
        if (request.items.size > 500) {
            throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.TOO_MANY_SYNC_ITEMS, "A sync push can contain at most 500 items.")
        }
        return dataSource.transaction { connection ->
            val accepted = mutableListOf<SyncAcceptedItem>()
            val conflicts = mutableListOf<SyncConflictDto>()
            var cursor = currentCursor(connection, device.userId)

            request.items.forEach { item ->
                val tableName = SyncTables.requireSupported(item.tableName)
                val operation = item.operation.uppercase()
                if (operation != "UPSERT" && operation != "DELETE") {
                    throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.UNSUPPORTED_SYNC_OPERATION, "Unsupported sync operation: ${item.operation}")
                }
                val existing = findExisting(connection, device.userId, tableName, item.remoteId, item.localKey)
                if (existing != null && item.baseVersion != existing.version) {
                    conflicts += createConflict(connection, device, tableName, item, existing)
                    return@forEach
                }

                if (operation == "DELETE") {
                    if (existing != null) {
                        val version = markDeleted(connection, device, tableName, existing.remoteId)
                        cursor = appendChange(connection, device, tableName, existing.remoteId, operation, version, existing.payload)
                        accepted += SyncAcceptedItem(tableName, item.localKey, existing.remoteId.toString(), version)
                    }
                } else {
                    val applied = upsertPayload(connection, device, tableName, item, existing)
                    cursor = appendChange(connection, device, tableName, applied.remoteId, operation, applied.version, item.payload)
                    accepted += SyncAcceptedItem(tableName, item.localKey, applied.remoteId.toString(), applied.version)
                }
            }

            SyncPushResponse(accepted = accepted, conflicts = conflicts, cursor = cursor)
        }
    }

    fun changes(device: UserDevice, since: Long, limit: Int): SyncChangesResponse {
        val safeLimit = limit.coerceIn(1, 500)
        return dataSource.withConnection { connection ->
            val changes = connection.prepareStatement(
                """
                SELECT id, table_name, entity_id::text, operation, version, payload_json::text,
                       EXTRACT(EPOCH FROM changed_at) * 1000 AS changed_at_millis,
                       source_device_id::text
                FROM sync_change_log
                WHERE owner_id = ? AND id > ?
                ORDER BY id ASC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, device.userId)
                statement.setLong(2, since)
                statement.setInt(3, safeLimit)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.toChangeDto())
                        }
                    }
                }
            }
            SyncChangesResponse(
                changes = changes,
                nextCursor = changes.lastOrNull()?.cursor ?: since,
            )
        }
    }

    fun resolveConflict(device: UserDevice, conflictId: UUID, request: ResolveConflictRequest): ResolveConflictResponse {
        val resolution = request.resolution.uppercase()
        if (resolution != "KEEP_REMOTE" && resolution != "KEEP_LOCAL") {
            throw ApiException(HttpStatusCode.BadRequest, ApiErrorCode.BAD_REQUEST, "Unsupported conflict resolution: ${request.resolution}")
        }
        return dataSource.transaction { connection ->
            val conflict = loadConflict(connection, device.userId, conflictId)
                ?: throw ApiException(HttpStatusCode.NotFound, ApiErrorCode.CONFLICT_NOT_FOUND, "Conflict not found or already resolved.")
            if (resolution == "KEEP_REMOTE") {
                closeConflict(connection, conflictId)
                ResolveConflictResponse(
                    conflictId = conflictId.toString(),
                    resolution = resolution,
                    remoteChange = SyncChangeDto(
                        cursor = currentCursor(connection, device.userId),
                        tableName = conflict.tableName,
                        remoteId = conflict.remoteId.toString(),
                        operation = "UPSERT",
                        version = conflict.remoteVersion,
                        payload = conflict.remotePayload,
                        changedAt = System.currentTimeMillis(),
                        sourceDeviceId = null,
                    ),
                )
            } else {
                val item = SyncPushItem(
                    tableName = conflict.tableName,
                    localKey = conflict.localKey,
                    remoteId = conflict.remoteId.toString(),
                    baseVersion = conflict.remoteVersion,
                    operation = "UPSERT",
                    payload = request.payload ?: conflict.localPayload,
                    clientChangedAt = System.currentTimeMillis(),
                )
                val applied = upsertPayload(connection, device, conflict.tableName, item, findExisting(connection, device.userId, conflict.tableName, conflict.remoteId.toString(), conflict.localKey))
                appendChange(connection, device, conflict.tableName, applied.remoteId, "UPSERT", applied.version, item.payload)
                closeConflict(connection, conflictId)
                ResolveConflictResponse(
                    conflictId = conflictId.toString(),
                    resolution = resolution,
                    accepted = SyncAcceptedItem(conflict.tableName, conflict.localKey, applied.remoteId.toString(), applied.version),
                )
            }
        }
    }

    private fun findExisting(
        connection: Connection,
        ownerId: UUID,
        tableName: String,
        remoteId: String?,
        localKey: String,
    ): ExistingRow? {
        val table = SyncTables.quoted(tableName)
        val byRemoteId = remoteId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val sql = if (byRemoteId != null) {
            "SELECT id, version, payload_json::text FROM $table WHERE id = ? AND owner_id = ?"
        } else {
            "SELECT id, version, payload_json::text FROM $table WHERE owner_id = ? AND local_key = ?"
        }
        return connection.prepareStatement(sql).use { statement ->
            if (byRemoteId != null) {
                statement.setObject(1, byRemoteId)
                statement.setObject(2, ownerId)
            } else {
                statement.setObject(1, ownerId)
                statement.setString(2, localKey)
            }
            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    ExistingRow(
                        remoteId = result.getObject("id", UUID::class.java),
                        version = result.getLong("version"),
                        payload = parsePayload(result.getString("payload_json")),
                    )
                }
            }
        }
    }

    private fun upsertPayload(
        connection: Connection,
        device: UserDevice,
        tableName: String,
        item: SyncPushItem,
        existing: ExistingRow?,
    ): AppliedRow {
        val table = SyncTables.quoted(tableName)
        val payload = pgJson(item.payload)
        return if (existing == null) {
            val remoteId = item.remoteId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()
            connection.prepareStatement(
                """
                INSERT INTO $table (
                    id, owner_id, version, created_server_at, updated_server_at, deleted_server_at,
                    source_device_id, local_key, payload_json
                ) VALUES (?, ?, 1, now(), now(), NULL, ?, ?, ?)
                RETURNING version
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, remoteId)
                statement.setObject(2, device.userId)
                statement.setObject(3, device.deviceId)
                statement.setString(4, item.localKey)
                statement.setObject(5, payload)
                statement.executeQuery().use { result ->
                    result.next()
                    AppliedRow(remoteId, result.getLong("version"))
                }
            }
        } else {
            connection.prepareStatement(
                """
                UPDATE $table
                SET version = version + 1,
                    updated_server_at = now(),
                    deleted_server_at = NULL,
                    source_device_id = ?,
                    local_key = ?,
                    payload_json = ?
                WHERE id = ? AND owner_id = ?
                RETURNING version
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, device.deviceId)
                statement.setString(2, item.localKey)
                statement.setObject(3, payload)
                statement.setObject(4, existing.remoteId)
                statement.setObject(5, device.userId)
                statement.executeQuery().use { result ->
                    result.next()
                    AppliedRow(existing.remoteId, result.getLong("version"))
                }
            }
        }
    }

    private fun markDeleted(connection: Connection, device: UserDevice, tableName: String, remoteId: UUID): Long {
        val table = SyncTables.quoted(tableName)
        return connection.prepareStatement(
            """
            UPDATE $table
            SET version = version + 1,
                updated_server_at = now(),
                deleted_server_at = now(),
                source_device_id = ?
            WHERE id = ? AND owner_id = ?
            RETURNING version
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, device.deviceId)
            statement.setObject(2, remoteId)
            statement.setObject(3, device.userId)
            statement.executeQuery().use { result ->
                result.next()
                result.getLong("version")
            }
        }
    }

    private fun appendChange(
        connection: Connection,
        device: UserDevice,
        tableName: String,
        remoteId: UUID,
        operation: String,
        version: Long,
        payload: JsonObject,
    ): Long {
        return connection.prepareStatement(
            """
            INSERT INTO sync_change_log (owner_id, table_name, entity_id, operation, version, payload_json, source_device_id, changed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            RETURNING id
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, device.userId)
            statement.setString(2, tableName)
            statement.setObject(3, remoteId)
            statement.setString(4, operation)
            statement.setLong(5, version)
            statement.setObject(6, pgJson(payload))
            statement.setObject(7, device.deviceId)
            statement.executeQuery().use { result ->
                result.next()
                result.getLong("id")
            }
        }
    }

    private fun createConflict(
        connection: Connection,
        device: UserDevice,
        tableName: String,
        item: SyncPushItem,
        existing: ExistingRow,
    ): SyncConflictDto {
        return connection.prepareStatement(
            """
            INSERT INTO sync_conflicts (
                id, owner_id, table_name, entity_id, local_key, remote_version, local_base_version,
                remote_payload_json, local_payload_json, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            RETURNING id
            """.trimIndent(),
        ).use { statement ->
            val conflictId = UUID.randomUUID()
            statement.setObject(1, conflictId)
            statement.setObject(2, device.userId)
            statement.setString(3, tableName)
            statement.setObject(4, existing.remoteId)
            statement.setString(5, item.localKey)
            statement.setLong(6, existing.version)
            if (item.baseVersion == null) {
                statement.setNull(7, java.sql.Types.BIGINT)
            } else {
                statement.setLong(7, item.baseVersion)
            }
            statement.setObject(8, pgJson(existing.payload))
            statement.setObject(9, pgJson(item.payload))
            statement.executeQuery().use { result ->
                result.next()
            }
            SyncConflictDto(
                id = conflictId.toString(),
                tableName = tableName,
                remoteId = existing.remoteId.toString(),
                localKey = item.localKey,
                remoteVersion = existing.version,
                localBaseVersion = item.baseVersion,
                remotePayload = existing.payload,
                localPayload = item.payload,
                summary = "$tableName/${item.localKey} has a newer remote version.",
            )
        }
    }

    private fun loadConflict(connection: Connection, ownerId: UUID, conflictId: UUID): ConflictRow? {
        return connection.prepareStatement(
            """
            SELECT table_name, entity_id, local_key, remote_version,
                   remote_payload_json::text, local_payload_json::text
            FROM sync_conflicts
            WHERE id = ? AND owner_id = ? AND resolved_at IS NULL
            """.trimIndent(),
        ).use { statement ->
            statement.setObject(1, conflictId)
            statement.setObject(2, ownerId)
            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    ConflictRow(
                        tableName = result.getString("table_name"),
                        remoteId = result.getObject("entity_id", UUID::class.java),
                        localKey = result.getString("local_key"),
                        remoteVersion = result.getLong("remote_version"),
                        remotePayload = parsePayload(result.getString("remote_payload_json")),
                        localPayload = parsePayload(result.getString("local_payload_json")),
                    )
                }
            }
        }
    }

    private fun closeConflict(connection: Connection, conflictId: UUID) {
        connection.prepareStatement("UPDATE sync_conflicts SET resolved_at = now() WHERE id = ?").use { statement ->
            statement.setObject(1, conflictId)
            statement.executeUpdate()
        }
    }

    private fun currentCursor(connection: Connection, ownerId: UUID): Long {
        return connection.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM sync_change_log WHERE owner_id = ?").use { statement ->
            statement.setObject(1, ownerId)
            statement.executeQuery().use { result ->
                result.next()
                result.getLong(1)
            }
        }
    }

    private fun ResultSet.toChangeDto(): SyncChangeDto =
        SyncChangeDto(
            cursor = getLong("id"),
            tableName = getString("table_name"),
            remoteId = getString("entity_id"),
            operation = getString("operation"),
            version = getLong("version"),
            payload = parsePayload(getString("payload_json")),
            changedAt = getDouble("changed_at_millis").toLong(),
            sourceDeviceId = getString("source_device_id"),
        )

    private fun parsePayload(value: String): JsonObject = json.decodeFromString(JsonObject.serializer(), value)

    private fun pgJson(payload: JsonObject): PGobject =
        PGobject().apply {
            type = "jsonb"
            value = json.encodeToString(JsonObject.serializer(), payload)
        }

    private data class ExistingRow(
        val remoteId: UUID,
        val version: Long,
        val payload: JsonObject,
    )

    private data class AppliedRow(
        val remoteId: UUID,
        val version: Long,
    )

    private data class ConflictRow(
        val tableName: String,
        val remoteId: UUID,
        val localKey: String,
        val remoteVersion: Long,
        val remotePayload: JsonObject,
        val localPayload: JsonObject,
    )
}
