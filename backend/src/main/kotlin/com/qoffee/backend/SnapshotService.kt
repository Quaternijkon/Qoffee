package com.qoffee.backend

import io.ktor.http.HttpStatusCode
import java.util.UUID
import javax.sql.DataSource

class SnapshotService(
    private val dataSource: DataSource,
) {
    fun create(device: UserDevice, request: SnapshotRequest): SnapshotResponse {
        val now = System.currentTimeMillis()
        val checksum = sha256(request.content)
        val bytes = request.content.toByteArray(Charsets.UTF_8).size.toLong()
        val snapshotId = UUID.randomUUID()
        return dataSource.withConnection { connection ->
            connection.prepareStatement(
                """
                INSERT INTO sync_snapshots (
                    id, owner_id, device_id, file_name, mime_type, content, checksum, byte_size, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, snapshotId)
                statement.setObject(2, device.userId)
                statement.setObject(3, device.deviceId)
                statement.setString(4, request.fileName)
                statement.setString(5, request.mimeType)
                statement.setString(6, request.content)
                statement.setString(7, checksum)
                statement.setLong(8, bytes)
                statement.executeUpdate()
            }
            SnapshotResponse(
                id = snapshotId.toString(),
                createdAt = now,
                checksum = checksum,
                byteSize = bytes,
            )
        }
    }

    fun latest(device: UserDevice): SnapshotDownloadResponse {
        return dataSource.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id::text, file_name, mime_type, content, checksum, byte_size,
                       EXTRACT(EPOCH FROM created_at) * 1000 AS created_at_millis
                FROM sync_snapshots
                WHERE owner_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, device.userId)
                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        throw ApiException(HttpStatusCode.NotFound, ApiErrorCode.SNAPSHOT_NOT_FOUND, "No cloud snapshot exists.")
                    }
                    val summary = SnapshotResponse(
                        id = result.getString("id"),
                        createdAt = result.getDouble("created_at_millis").toLong(),
                        checksum = result.getString("checksum"),
                        byteSize = result.getLong("byte_size"),
                    )
                    SnapshotDownloadResponse(
                        summary = summary,
                        fileName = result.getString("file_name"),
                        mimeType = result.getString("mime_type"),
                        content = result.getString("content"),
                    )
                }
            }
        }
    }
}
