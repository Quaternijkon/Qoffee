package com.qoffee.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {
    @Query(
        """
        SELECT
               a.id AS id,
               a.name AS name,
               a.typeCode AS typeCode,
               a.isReadOnly AS isReadOnly,
               a.createdAt AS createdAt,
               a.updatedAt AS updatedAt,
               a.sortOrder AS sortOrder,
               (SELECT COUNT(*) FROM bean_profiles b WHERE b.archiveId = a.id) AS beanCount,
               (SELECT COUNT(*) FROM grinder_profiles g WHERE g.archiveId = a.id) AS grinderCount,
               (SELECT COUNT(*) FROM brew_records r WHERE r.archiveId = a.id AND r.status = 'completed') AS recordCount,
               (SELECT MAX(r.brewedAt) FROM brew_records r WHERE r.archiveId = a.id) AS lastRecordAt
        FROM archives a
        ORDER BY a.sortOrder ASC, a.updatedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<ArchiveSummaryRow>>

    @Query("SELECT * FROM archives WHERE id = :id")
    fun observeById(id: Long): Flow<ArchiveEntity?>

    @Query("SELECT * FROM archives WHERE id = :id")
    suspend fun getById(id: Long): ArchiveEntity?

    @Query("SELECT * FROM archives WHERE typeCode = :typeCode LIMIT 1")
    suspend fun findByType(typeCode: String): ArchiveEntity?

    @Query("SELECT * FROM archives ORDER BY sortOrder ASC, updatedAt DESC")
    suspend fun getAll(): List<ArchiveEntity>

    @Insert
    suspend fun insert(entity: ArchiveEntity): Long

    @Update
    suspend fun update(entity: ArchiveEntity)

    @Query("DELETE FROM archives WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BeanProfileDao {
    @Query("SELECT * FROM bean_profiles WHERE archiveId = :archiveId ORDER BY createdAt DESC")
    fun observeByArchive(archiveId: Long): Flow<List<BeanProfileEntity>>

    @Query("SELECT * FROM bean_profiles WHERE archiveId = :archiveId")
    suspend fun getAllByArchive(archiveId: Long): List<BeanProfileEntity>

    @Query("SELECT * FROM bean_profiles WHERE id = :id")
    suspend fun getById(id: Long): BeanProfileEntity?

    @Insert
    suspend fun insert(entity: BeanProfileEntity): Long

    @Update
    suspend fun update(entity: BeanProfileEntity)

    @Query("DELETE FROM bean_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GrinderProfileDao {
    @Query("SELECT * FROM grinder_profiles WHERE archiveId = :archiveId ORDER BY createdAt DESC")
    fun observeByArchive(archiveId: Long): Flow<List<GrinderProfileEntity>>

    @Query("SELECT * FROM grinder_profiles WHERE archiveId = :archiveId")
    suspend fun getAllByArchive(archiveId: Long): List<GrinderProfileEntity>

    @Query("SELECT id FROM grinder_profiles WHERE archiveId = :archiveId")
    suspend fun getIdsByArchive(archiveId: Long): List<Long>

    @Query("SELECT * FROM grinder_profiles WHERE id = :id")
    suspend fun getById(id: Long): GrinderProfileEntity?

    @Insert
    suspend fun insert(entity: GrinderProfileEntity): Long

    @Update
    suspend fun update(entity: GrinderProfileEntity)

    @Query("DELETE FROM grinder_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RecipeTemplateDao {
    @Query("SELECT * FROM recipe_templates WHERE archiveId = :archiveId ORDER BY updatedAt DESC, createdAt DESC")
    fun observeByArchive(archiveId: Long): Flow<List<RecipeTemplateEntity>>

    @Query("SELECT * FROM recipe_templates WHERE archiveId = :archiveId ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getAllByArchive(archiveId: Long): List<RecipeTemplateEntity>

    @Query("SELECT * FROM recipe_templates WHERE id = :id")
    suspend fun getById(id: Long): RecipeTemplateEntity?

    @Insert
    suspend fun insert(entity: RecipeTemplateEntity): Long

    @Update
    suspend fun update(entity: RecipeTemplateEntity)

    @Query("DELETE FROM recipe_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BrewRecordDao {
    @Transaction
    @Query(
        """
        SELECT * FROM brew_records
        WHERE archiveId = :archiveId
        ORDER BY CASE WHEN status = 'draft' THEN 0 ELSE 1 END ASC,
                 brewedAt DESC,
                 updatedAt DESC
        """,
    )
    fun observeAll(archiveId: Long): Flow<List<BrewRecordWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM brew_records
        WHERE archiveId = :archiveId AND status = 'completed'
        ORDER BY brewedAt DESC, updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecentCompleted(archiveId: Long, limit: Int): Flow<List<BrewRecordWithRelations>>

    @Transaction
    @Query(
        """
        SELECT * FROM brew_records
        WHERE archiveId = :archiveId
        ORDER BY brewedAt DESC, updatedAt DESC
        """,
    )
    suspend fun getAllByArchive(archiveId: Long): List<BrewRecordWithRelations>

    @Transaction
    @Query("SELECT * FROM brew_records WHERE id = :id")
    fun observeById(id: Long): Flow<BrewRecordWithRelations?>

    @Transaction
    @Query("SELECT * FROM brew_records WHERE id = :id")
    suspend fun getById(id: Long): BrewRecordWithRelations?

    @Transaction
    @Query(
        """
        SELECT * FROM brew_records
        WHERE archiveId = :archiveId
          AND status = 'completed'
          AND (:beanId IS NULL OR beanId = :beanId)
          AND (:brewMethodCode IS NULL OR brewMethodCode = :brewMethodCode)
          AND (:excludingRecordId IS NULL OR id != :excludingRecordId)
        ORDER BY brewedAt DESC, updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestComparable(
        archiveId: Long,
        beanId: Long?,
        brewMethodCode: String?,
        excludingRecordId: Long?,
    ): BrewRecordWithRelations?

    @Query("SELECT * FROM brew_records WHERE id = :id")
    suspend fun getEntityById(id: Long): BrewRecordEntity?

    @Query("SELECT * FROM brew_records WHERE archiveId = :archiveId AND status = 'draft' LIMIT 1")
    suspend fun getActiveDraft(archiveId: Long): BrewRecordEntity?

    @Insert
    suspend fun insert(entity: BrewRecordEntity): Long

    @Update
    suspend fun update(entity: BrewRecordEntity)

    @Query("DELETE FROM brew_records WHERE archiveId = :archiveId AND status = 'draft'")
    suspend fun deleteDrafts(archiveId: Long)

    @Query("DELETE FROM brew_records WHERE id = :id")
    suspend fun deleteById(id: Long)
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
    @Query("SELECT * FROM flavor_tags WHERE archiveId = :archiveId ORDER BY isPreset DESC, name ASC")
    fun observeAll(archiveId: Long): Flow<List<FlavorTagEntity>>

    @Query("SELECT * FROM flavor_tags WHERE archiveId = :archiveId")
    suspend fun getAllByArchive(archiveId: Long): List<FlavorTagEntity>

    @Query("SELECT * FROM flavor_tags WHERE id = :id")
    suspend fun getById(id: Long): FlavorTagEntity?

    @Query("SELECT * FROM flavor_tags WHERE archiveId = :archiveId AND name = :name LIMIT 1")
    suspend fun findByName(archiveId: Long, name: String): FlavorTagEntity?

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
