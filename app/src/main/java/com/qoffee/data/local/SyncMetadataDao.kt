package com.qoffee.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncMetadataDao {
    @Upsert
    suspend fun upsertEntityMap(entity: SyncEntityMapEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(entity: SyncOutboxEntity): Long

    @Query("DELETE FROM sync_outbox WHERE id IN (:ids)")
    suspend fun deleteOutbox(ids: List<Long>)

    @Query("SELECT * FROM sync_outbox ORDER BY clientChangedAt ASC, id ASC LIMIT :limit")
    suspend fun getOutbox(limit: Int): List<SyncOutboxEntity>

    @Upsert
    suspend fun upsertCursor(entity: SyncCursorEntity)

    @Query("SELECT cursor FROM sync_cursors WHERE scope = :scope")
    suspend fun getCursor(scope: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(entity: SyncConflictCacheEntity)

    @Query("SELECT * FROM sync_conflict_cache ORDER BY createdAt DESC")
    suspend fun getConflicts(): List<SyncConflictCacheEntity>

    @Query("DELETE FROM sync_conflict_cache WHERE id = :id")
    suspend fun deleteConflict(id: String)

    @Query("DELETE FROM sync_conflict_cache")
    suspend fun clearConflicts()
}
