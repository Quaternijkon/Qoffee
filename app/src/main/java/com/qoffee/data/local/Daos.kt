package com.qoffee.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BeanProfileDao {
    @Query("SELECT * FROM bean_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BeanProfileEntity>>

    @Query("SELECT * FROM bean_profiles WHERE id = :id")
    suspend fun getById(id: Long): BeanProfileEntity?

    @Insert
    suspend fun insert(entity: BeanProfileEntity): Long

    @Update
    suspend fun update(entity: BeanProfileEntity)
}

@Dao
interface GrinderProfileDao {
    @Query("SELECT * FROM grinder_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GrinderProfileEntity>>

    @Query("SELECT * FROM grinder_profiles WHERE id = :id")
    suspend fun getById(id: Long): GrinderProfileEntity?

    @Insert
    suspend fun insert(entity: GrinderProfileEntity): Long

    @Update
    suspend fun update(entity: GrinderProfileEntity)
}

@Dao
interface BrewRecordDao {
    @Transaction
    @Query(
        """
        SELECT * FROM brew_records
        ORDER BY CASE WHEN status = 'draft' THEN 0 ELSE 1 END ASC,
                 brewedAt DESC,
                 updatedAt DESC
        """,
    )
    fun observeAll(): Flow<List<BrewRecordWithRelations>>

    @Transaction
    @Query("SELECT * FROM brew_records WHERE id = :id")
    fun observeById(id: Long): Flow<BrewRecordWithRelations?>

    @Transaction
    @Query("SELECT * FROM brew_records WHERE id = :id")
    suspend fun getById(id: Long): BrewRecordWithRelations?

    @Query("SELECT * FROM brew_records WHERE id = :id")
    suspend fun getEntityById(id: Long): BrewRecordEntity?

    @Query("SELECT * FROM brew_records WHERE status = 'draft' LIMIT 1")
    suspend fun getActiveDraft(): BrewRecordEntity?

    @Insert
    suspend fun insert(entity: BrewRecordEntity): Long

    @Update
    suspend fun update(entity: BrewRecordEntity)

    @Query("DELETE FROM brew_records WHERE status = 'draft'")
    suspend fun deleteDrafts()
}

@Dao
interface SubjectiveEvaluationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubjectiveEvaluationEntity)

    @Query("DELETE FROM subjective_evaluations WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: Long)
}

@Dao
interface FlavorTagDao {
    @Query("SELECT * FROM flavor_tags ORDER BY isPreset DESC, name ASC")
    fun observeAll(): Flow<List<FlavorTagEntity>>

    @Query("SELECT * FROM flavor_tags WHERE id = :id")
    suspend fun getById(id: Long): FlavorTagEntity?

    @Query("SELECT * FROM flavor_tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): FlavorTagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FlavorTagEntity): Long

    @Query("SELECT * FROM flavor_tags WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FlavorTagEntity>
}

@Dao
interface RecordFlavorTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<RecordFlavorTagCrossRef>)

    @Query("DELETE FROM record_flavor_tags WHERE recordId = :recordId")
    suspend fun deleteForRecord(recordId: Long)
}
