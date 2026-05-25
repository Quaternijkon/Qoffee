package com.qoffee.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_entity_map",
    primaryKeys = ["tableName", "localKey"],
    indices = [Index(value = ["remoteId"], unique = true)],
)
data class SyncEntityMapEntity(
    val tableName: String,
    val localKey: String,
    val remoteId: String,
    val serverVersion: Long,
    val lastSyncedAt: Long,
)

@Entity(
    tableName = "sync_outbox",
    indices = [Index("tableName"), Index("localKey"), Index("clientChangedAt")],
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tableName: String,
    val localKey: String,
    val remoteId: String? = null,
    val baseVersion: Long? = null,
    val operation: String,
    val payloadJson: String,
    val clientChangedAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Entity(
    tableName = "sync_tombstones",
    indices = [Index("tableName"), Index("localKey"), Index("deletedAt")],
)
data class SyncTombstoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tableName: String,
    val localKey: String,
    val remoteId: String? = null,
    val baseVersion: Long? = null,
    val payloadJson: String,
    val deletedAt: Long,
)

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val scope: String,
    val cursor: Long,
    val updatedAt: Long,
)

@Entity(tableName = "sync_conflict_cache")
data class SyncConflictCacheEntity(
    @PrimaryKey val id: String,
    val tableName: String,
    val remoteId: String,
    val localKey: String,
    val remoteVersion: Long,
    val localBaseVersion: Long?,
    val remotePayloadJson: String,
    val localPayloadJson: String,
    val summary: String,
    val createdAt: Long,
)
