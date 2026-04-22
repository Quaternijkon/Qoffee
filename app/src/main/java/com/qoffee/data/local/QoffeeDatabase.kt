package com.qoffee.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ArchiveEntity::class,
        BeanProfileEntity::class,
        GrinderProfileEntity::class,
        RecipeTemplateEntity::class,
        BrewRecordEntity::class,
        SubjectiveEvaluationEntity::class,
        FlavorTagEntity::class,
        RecordFlavorTagCrossRef::class,
        ArchiveCoreEntity::class,
        CoffeeProductEntity::class,
        CoffeeBatchEntity::class,
        EquipmentAssetTypeEntity::class,
        EquipmentAssetEntity::class,
        WaterProfileEntity::class,
        RecipeCoreEntity::class,
        RecipeVersionEntity::class,
        RecipeStepTemplateEntity::class,
        MetricDefinitionEntity::class,
        MetricEnumOptionEntity::class,
        EventDefinitionEntity::class,
        TagDefinitionEntity::class,
        SourceDefinitionEntity::class,
        UnitDefinitionEntity::class,
        CollectionEntity::class,
        CollectionItemLinkEntity::class,
        BrewRunFrozenEntity::class,
        BrewRunAssetLinkEntity::class,
        BrewStageRunEntity::class,
        ObservationEntity::class,
        EventFrozenEntity::class,
        SubjectTagLinkEntity::class,
        InventoryTransactionEntity::class,
        AttachmentEntity::class,
        ImportLogEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class QoffeeDatabase : RoomDatabase() {
    abstract fun archiveDao(): ArchiveDao
    abstract fun beanProfileDao(): BeanProfileDao
    abstract fun grinderProfileDao(): GrinderProfileDao
    abstract fun recipeTemplateDao(): RecipeTemplateDao
    abstract fun brewRecordDao(): BrewRecordDao
    abstract fun subjectiveEvaluationDao(): SubjectiveEvaluationDao
    abstract fun flavorTagDao(): FlavorTagDao
    abstract fun recordFlavorTagDao(): RecordFlavorTagDao
    abstract fun archiveCoreDao(): ArchiveCoreDao
    abstract fun coffeeProductDao(): CoffeeProductDao
    abstract fun coffeeBatchDao(): CoffeeBatchDao
    abstract fun equipmentAssetTypeDao(): EquipmentAssetTypeDao
    abstract fun equipmentAssetDao(): EquipmentAssetDao
    abstract fun waterProfileDao(): WaterProfileDao
    abstract fun recipeCoreDao(): RecipeCoreDao
    abstract fun recipeVersionDao(): RecipeVersionDao
    abstract fun recipeStepTemplateDao(): RecipeStepTemplateDao
    abstract fun metricDefinitionDao(): MetricDefinitionDao
    abstract fun metricEnumOptionDao(): MetricEnumOptionDao
    abstract fun eventDefinitionDao(): EventDefinitionDao
    abstract fun tagDefinitionDao(): TagDefinitionDao
    abstract fun sourceDefinitionDao(): SourceDefinitionDao
    abstract fun unitDefinitionDao(): UnitDefinitionDao
    abstract fun collectionDao(): CollectionDao
    abstract fun collectionItemLinkDao(): CollectionItemLinkDao
    abstract fun brewRunFrozenDao(): BrewRunFrozenDao
    abstract fun brewRunAssetLinkDao(): BrewRunAssetLinkDao
    abstract fun brewStageRunDao(): BrewStageRunDao
    abstract fun observationDao(): ObservationDao
    abstract fun eventFrozenDao(): EventFrozenDao
    abstract fun subjectTagLinkDao(): SubjectTagLinkDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun importLogDao(): ImportLogDao
}
