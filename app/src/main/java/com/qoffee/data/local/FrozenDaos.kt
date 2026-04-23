package com.qoffee.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveCoreDao {
    @Upsert
    suspend fun upsert(entity: ArchiveCoreEntity)

    @Query("SELECT * FROM `archive` WHERE id = :id")
    suspend fun getById(id: Long): ArchiveCoreEntity?

    @Query("DELETE FROM `archive` WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CoffeeProductDao {
    @Upsert
    suspend fun upsert(entity: CoffeeProductEntity)

    @Query("SELECT * FROM coffee_product WHERE id = :id")
    suspend fun getById(id: Long): CoffeeProductEntity?

    @Query("SELECT COUNT(*) FROM coffee_batch WHERE productId = :productId")
    suspend fun countBatches(productId: Long): Int

    @Query("DELETE FROM coffee_product WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CoffeeBatchDao {
    @Upsert
    suspend fun upsert(entity: CoffeeBatchEntity)

    @Query("SELECT * FROM coffee_batch WHERE id = :id")
    suspend fun getById(id: Long): CoffeeBatchEntity?

    @Query("UPDATE coffee_batch SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateActive(id: Long, isActive: Boolean, updatedAt: Long)
}

@Dao
interface EquipmentAssetTypeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<EquipmentAssetTypeEntity>): List<Long>

    @Query("SELECT * FROM equipment_asset_type")
    suspend fun getAll(): List<EquipmentAssetTypeEntity>

    @Query("SELECT * FROM equipment_asset_type WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): EquipmentAssetTypeEntity?
}

@Dao
interface EquipmentAssetDao {
    @Upsert
    suspend fun upsert(entity: EquipmentAssetEntity)

    @Query("SELECT * FROM equipment_asset WHERE id = :id")
    suspend fun getById(id: Long): EquipmentAssetEntity?

    @Query("UPDATE equipment_asset SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateActive(id: Long, isActive: Boolean, updatedAt: Long)
}

@Dao
interface WaterProfileDao {
    @Upsert
    suspend fun upsert(entity: WaterProfileEntity)
}

@Dao
interface RecipeCoreDao {
    @Upsert
    suspend fun upsert(entity: RecipeCoreEntity)

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun getById(id: Long): RecipeCoreEntity?

    @Query("UPDATE recipe SET currentVersionId = :versionId, updatedAt = :updatedAt WHERE id = :recipeId")
    suspend fun updateCurrentVersion(recipeId: Long, versionId: Long, updatedAt: Long)

    @Query("UPDATE recipe SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateActive(id: Long, isActive: Boolean, updatedAt: Long)
}

@Dao
interface RecipeVersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecipeVersionEntity): Long

    @Upsert
    suspend fun upsert(entity: RecipeVersionEntity)

    @Query("SELECT * FROM recipe_version WHERE id = :id")
    suspend fun getById(id: Long): RecipeVersionEntity?

    @Query("SELECT * FROM recipe_version WHERE recipeId = :recipeId ORDER BY versionNumber DESC LIMIT 1")
    suspend fun getLatestByRecipeId(recipeId: Long): RecipeVersionEntity?

    @Query("SELECT MAX(versionNumber) FROM recipe_version WHERE recipeId = :recipeId")
    suspend fun getMaxVersionNumber(recipeId: Long): Int?
}

@Dao
interface RecipeStepTemplateDao {
    @Query("DELETE FROM recipe_step_template WHERE recipeVersionId = :recipeVersionId")
    suspend fun deleteForRecipeVersion(recipeVersionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RecipeStepTemplateEntity>)
}

@Dao
interface MetricDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MetricDefinitionEntity>): List<Long>

    @Query("SELECT * FROM metric_definition")
    suspend fun getAll(): List<MetricDefinitionEntity>
}

@Dao
interface MetricEnumOptionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MetricEnumOptionEntity>): List<Long>
}

@Dao
interface EventDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<EventDefinitionEntity>): List<Long>
}

@Dao
interface TagDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TagDefinitionEntity): Long

    @Query("SELECT * FROM tag_definition WHERE archiveId = :archiveId AND categoryCode = :categoryCode AND displayName = :displayName LIMIT 1")
    suspend fun findByArchiveAndName(archiveId: Long?, categoryCode: String, displayName: String): TagDefinitionEntity?

    @Query("SELECT t.* FROM tag_definition t INNER JOIN subject_tag_link l ON l.tagDefinitionId = t.id WHERE l.subjectType = :subjectType AND l.subjectId = :subjectId")
    suspend fun getTagsForSubject(subjectType: String, subjectId: Long): List<TagDefinitionEntity>
}

@Dao
interface SourceDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<SourceDefinitionEntity>): List<Long>

    @Query("SELECT * FROM source_definition")
    suspend fun getAll(): List<SourceDefinitionEntity>
}

@Dao
interface UnitDefinitionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<UnitDefinitionEntity>): List<Long>

    @Query("SELECT * FROM unit_definition")
    suspend fun getAll(): List<UnitDefinitionEntity>
}

@Dao
interface CollectionDao {
    @Upsert
    suspend fun upsert(entity: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CollectionEntity): Long

    @Query("SELECT * FROM collection WHERE archiveId = :archiveId AND typeCode = :typeCode ORDER BY updatedAt DESC, createdAt DESC")
    fun observeByArchiveAndType(archiveId: Long, typeCode: String): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection WHERE id = :id")
    fun observeById(id: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collection WHERE id = :id")
    suspend fun getById(id: Long): CollectionEntity?

    @Query("SELECT * FROM collection WHERE archiveId = :archiveId AND typeCode = :typeCode AND title = :title LIMIT 1")
    suspend fun findByArchiveTypeAndTitle(archiveId: Long, typeCode: String, title: String): CollectionEntity?

    @Query("DELETE FROM collection WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CollectionItemLinkDao {
    @Upsert
    suspend fun upsert(entity: CollectionItemLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CollectionItemLinkEntity>)

    @Query("SELECT * FROM collection_item_link WHERE collectionId = :collectionId ORDER BY sortOrder ASC, id ASC")
    fun observeForCollection(collectionId: Long): Flow<List<CollectionItemLinkEntity>>

    @Query("SELECT * FROM collection_item_link WHERE archiveId = :archiveId ORDER BY sortOrder ASC, id ASC")
    fun observeByArchive(archiveId: Long): Flow<List<CollectionItemLinkEntity>>

    @Query("SELECT * FROM collection_item_link WHERE collectionId = :collectionId ORDER BY sortOrder ASC, id ASC")
    suspend fun getForCollection(collectionId: Long): List<CollectionItemLinkEntity>

    @Query("SELECT * FROM collection_item_link WHERE collectionId = :collectionId AND itemType = :itemType ORDER BY sortOrder ASC, id ASC")
    suspend fun getForCollectionItemType(collectionId: Long, itemType: String): List<CollectionItemLinkEntity>

    @Query("SELECT * FROM collection_item_link WHERE collectionId = :collectionId AND itemType = :itemType AND itemId = :itemId LIMIT 1")
    suspend fun findByCollectionAndItem(collectionId: Long, itemType: String, itemId: Long): CollectionItemLinkEntity?

    @Query("DELETE FROM collection_item_link WHERE collectionId = :collectionId")
    suspend fun deleteForCollection(collectionId: Long)

    @Query("DELETE FROM collection_item_link WHERE collectionId = :collectionId AND itemType = :itemType")
    suspend fun deleteForCollectionItemType(collectionId: Long, itemType: String)
}

@Dao
interface BrewRunFrozenDao {
    @Upsert
    suspend fun upsert(entity: BrewRunFrozenEntity)

    @Query("SELECT * FROM brew_run WHERE id = :id")
    suspend fun getById(id: Long): BrewRunFrozenEntity?

    @Query("DELETE FROM brew_run WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BrewRunAssetLinkDao {
    @Query("DELETE FROM brew_run_asset_link WHERE brewRunId = :brewRunId")
    suspend fun deleteForRun(brewRunId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BrewRunAssetLinkEntity>)
}

@Dao
interface BrewStageRunDao {
    @Query("DELETE FROM brew_stage_run WHERE brewRunId = :brewRunId")
    suspend fun deleteForRun(brewRunId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BrewStageRunEntity>)
}

@Dao
interface ObservationDao {
    @Query("DELETE FROM observation WHERE subjectType = :subjectType AND subjectId = :subjectId AND metricDefinitionId IN (:metricIds)")
    suspend fun deleteForSubjectMetrics(subjectType: String, subjectId: Long, metricIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ObservationEntity>)

    @Query("SELECT * FROM observation WHERE subjectType = :subjectType AND subjectId = :subjectId")
    suspend fun getForSubject(subjectType: String, subjectId: Long): List<ObservationEntity>
}

@Dao
interface EventFrozenDao {
    @Query("DELETE FROM event WHERE subjectType = :subjectType AND subjectId = :subjectId")
    suspend fun deleteForSubject(subjectType: String, subjectId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<EventFrozenEntity>)
}

@Dao
interface SubjectTagLinkDao {
    @Query(
        """
        DELETE FROM subject_tag_link
        WHERE subjectType = :subjectType
          AND subjectId = :subjectId
          AND tagDefinitionId IN (
              SELECT id FROM tag_definition WHERE categoryCode = :categoryCode
          )
        """,
    )
    suspend fun deleteForSubjectCategory(subjectType: String, subjectId: Long, categoryCode: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SubjectTagLinkEntity>)
}

@Dao
interface InventoryTransactionDao {
    @Upsert
    suspend fun upsert(entity: InventoryTransactionEntity)

    @Query("SELECT * FROM inventory_transaction WHERE coffeeBatchId = :coffeeBatchId AND transactionTypeCode = :transactionTypeCode AND relatedRunId IS NULL LIMIT 1")
    suspend fun getBatchTransaction(coffeeBatchId: Long, transactionTypeCode: String): InventoryTransactionEntity?

    @Query("SELECT * FROM inventory_transaction WHERE relatedRunId = :runId AND transactionTypeCode = :transactionTypeCode LIMIT 1")
    suspend fun getRunTransaction(runId: Long, transactionTypeCode: String): InventoryTransactionEntity?

    @Query("DELETE FROM inventory_transaction WHERE relatedRunId = :runId")
    suspend fun deleteForRun(runId: Long)
}

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AttachmentEntity): Long
}

@Dao
interface ImportLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImportLogEntity): Long
}
