package com.qoffee.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "archive")
data class ArchiveCoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val typeCode: String,
    val isReadOnly: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int,
)

@Entity(
    tableName = "coffee_product",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId")],
)
data class CoffeeProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val name: String,
    val roaster: String = "",
    val country: String = "",
    val region: String = "",
    val siteName: String = "",
    val processMethodCode: String = "",
    val variety: String = "",
    val altitudeMinM: Double? = null,
    val altitudeMaxM: Double? = null,
    val harvestSeason: String = "",
    val roastLevelTargetCode: String = "",
    val description: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "coffee_batch",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CoffeeProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId"), Index("productId"), Index("isActive")],
)
data class CoffeeBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val productId: Long,
    val batchCode: String = "",
    val displayName: String = "",
    val roastDateEpochDay: Long? = null,
    val purchasedAt: Long? = null,
    val openedAt: Long? = null,
    val expiresAt: Long? = null,
    val initialStockG: Double? = null,
    val packageSizeG: Double? = null,
    val costAmount: Double? = null,
    val currencyCode: String = "CNY",
    val supplierName: String = "",
    val greenBeanMoisturePct: Double? = null,
    val densityGPerMl: Double? = null,
    val awValue: Double? = null,
    val sieveSizeText: String = "",
    val fermentationBatchText: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "equipment_asset_type",
    indices = [Index(value = ["code"], unique = true)],
)
data class EquipmentAssetTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val code: String,
    val displayName: String,
    val description: String = "",
)

@Entity(
    tableName = "equipment_asset",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EquipmentAssetTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["typeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("archiveId"), Index("typeId"), Index("isActive")],
)
data class EquipmentAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val typeId: Long,
    val name: String,
    val brand: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val stepSize: Double? = null,
    val defaultUnitCode: String = "",
    val burrType: String = "",
    val filterType: String = "",
    val capacityValue: Double? = null,
    val capacityUnitCode: String = "",
    val specJson: String = "",
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "water_profile",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId")],
)
data class WaterProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val name: String,
    val tdsPpm: Double? = null,
    val ghPpm: Double? = null,
    val khPpm: Double? = null,
    val ph: Double? = null,
    val calciumPpm: Double? = null,
    val magnesiumPpm: Double? = null,
    val sodiumPpm: Double? = null,
    val bicarbonatePpm: Double? = null,
    val recipeText: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "recipe",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId"), Index("isActive")],
)
data class RecipeCoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val name: String,
    val brewMethodCode: String = "",
    val currentVersionId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "recipe_version",
    foreignKeys = [
        ForeignKey(
            entity = RecipeCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WaterProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["waterProfileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("recipeId"), Index("archiveId"), Index("waterProfileId"), Index(value = ["recipeId", "versionNumber"], unique = true)],
)
data class RecipeVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recipeId: Long,
    val archiveId: Long,
    val versionNumber: Int,
    val brewMethodCode: String = "",
    val waterProfileId: Long? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "recipe_step_template",
    foreignKeys = [
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeVersionId")],
)
data class RecipeStepTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recipeVersionId: Long,
    val stageCode: String,
    val sortOrder: Int,
    val title: String = "",
    val description: String = "",
    val targetSummaryJson: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "metric_definition",
    indices = [Index(value = ["code"], unique = true)],
)
data class MetricDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val code: String,
    val displayName: String,
    val description: String = "",
    val scopeType: String,
    val valueType: String,
    val defaultUnitCode: String = "",
    val isAggregatable: Boolean = false,
    val isSystem: Boolean = true,
    val isRequired: Boolean = false,
    val isFilterable: Boolean = false,
    val isChartable: Boolean = false,
    val normalizationStrategy: String = "",
    val createdAt: Long,
)

@Entity(
    tableName = "metric_enum_option",
    foreignKeys = [
        ForeignKey(
            entity = MetricDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["metricDefinitionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("metricDefinitionId"), Index(value = ["metricDefinitionId", "code"], unique = true)],
)
data class MetricEnumOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val metricDefinitionId: Long,
    val code: String,
    val displayName: String,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
)

@Entity(
    tableName = "event_definition",
    indices = [Index(value = ["code"], unique = true)],
)
data class EventDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val code: String,
    val displayName: String,
    val description: String = "",
    val defaultSeverity: String = "",
)

@Entity(
    tableName = "tag_definition",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("archiveId"),
        Index(value = ["archiveId", "categoryCode", "displayName"], unique = true),
    ],
)
data class TagDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long?,
    val categoryCode: String,
    val code: String = "",
    val displayName: String,
    val description: String = "",
    val colorHex: String = "",
    val isSystem: Boolean = true,
    val createdAt: Long,
)

@Entity(
    tableName = "source_definition",
    indices = [Index(value = ["code"], unique = true)],
)
data class SourceDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val code: String,
    val displayName: String,
    val description: String = "",
    val isMeasured: Boolean = false,
    val isEstimated: Boolean = false,
    val priority: Int = 0,
)

@Entity(
    tableName = "unit_definition",
    indices = [Index(value = ["code"], unique = true)],
)
data class UnitDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val code: String,
    val displayName: String,
    val quantityType: String,
    val symbol: String = "",
)

@Entity(
    tableName = "collection",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId"), Index("typeCode")],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val typeCode: String,
    val title: String,
    val description: String = "",
    val hypothesis: String = "",
    val configJson: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "collection_item_link",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId"), Index("archiveId"), Index(value = ["collectionId", "itemType", "itemId"], unique = true)],
)
data class CollectionItemLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val collectionId: Long,
    val itemType: String,
    val itemId: Long,
    val sortOrder: Int = 0,
    val groupLabel: String = "",
    val roleText: String = "",
)

@Entity(
    tableName = "brew_run",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeVersionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CoffeeBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["coffeeBatchId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = WaterProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["waterProfileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SourceDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("archiveId"),
        Index("recipeVersionId"),
        Index("coffeeBatchId"),
        Index("waterProfileId"),
        Index("sourceId"),
        Index("brewedAt"),
        Index(value = ["archiveId", "brewMethodCode", "brewedAt"]),
        Index(value = ["archiveId", "status"]),
    ],
)
data class BrewRunFrozenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val runKind: String,
    val brewMethodCode: String,
    val status: String,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val brewedAt: Long,
    val timezoneOffsetMinutes: Int? = null,
    val recipeVersionId: Long? = null,
    val coffeeBatchId: Long? = null,
    val waterProfileId: Long? = null,
    val locationText: String = "",
    val operatorText: String = "",
    val sourceId: Long? = null,
    val confidenceScore: Double? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "brew_run_asset_link",
    foreignKeys = [
        ForeignKey(
            entity = BrewRunFrozenEntity::class,
            parentColumns = ["id"],
            childColumns = ["brewRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EquipmentAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId"), Index("brewRunId"), Index("assetId"), Index(value = ["brewRunId", "roleCode"], unique = true)],
)
data class BrewRunAssetLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val brewRunId: Long,
    val assetId: Long,
    val roleCode: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "brew_stage_run",
    foreignKeys = [
        ForeignKey(
            entity = BrewRunFrozenEntity::class,
            parentColumns = ["id"],
            childColumns = ["brewRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId"), Index("brewRunId"), Index(value = ["brewRunId", "sortOrder"], unique = true)],
)
data class BrewStageRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val brewRunId: Long,
    val stageCode: String,
    val stageLabel: String = "",
    val sortOrder: Int,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val targetSummaryJson: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "observation",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MetricDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["metricDefinitionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SourceDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = MetricEnumOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["enumOptionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("archiveId"),
        Index("metricDefinitionId"),
        Index("sourceId"),
        Index("enumOptionId"),
        Index(value = ["subjectType", "subjectId", "metricDefinitionId", "capturedAt"]),
        Index(value = ["archiveId", "metricDefinitionId", "capturedAt"]),
    ],
)
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val subjectType: String,
    val subjectId: Long,
    val metricDefinitionId: Long,
    val capturedAt: Long,
    val sourceId: Long? = null,
    val confidenceScore: Double? = null,
    val valueType: String,
    val numericValue: Double? = null,
    val normalizedNumericValue: Double? = null,
    val unitCode: String? = null,
    val boolValue: Boolean? = null,
    val textValue: String? = null,
    val enumOptionId: Long? = null,
    val timestampValue: Long? = null,
    val jsonValue: String? = null,
    val sequenceNo: Int? = null,
    val isTargetValue: Boolean = false,
    val isActualValue: Boolean = true,
)

@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EventDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventDefinitionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SourceDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("archiveId"),
        Index("eventDefinitionId"),
        Index("sourceId"),
        Index(value = ["subjectType", "subjectId", "eventDefinitionId", "startedAt"]),
    ],
)
data class EventFrozenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val subjectType: String,
    val subjectId: Long,
    val eventDefinitionId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val sourceId: Long? = null,
    val confidenceScore: Double? = null,
    val payloadJson: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "subject_tag_link",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagDefinitionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("archiveId"),
        Index("tagDefinitionId"),
        Index(value = ["subjectType", "subjectId", "tagDefinitionId"], unique = true),
    ],
)
data class SubjectTagLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val subjectType: String,
    val subjectId: Long,
    val tagDefinitionId: Long,
    val taggedAt: Long,
)

@Entity(
    tableName = "inventory_transaction",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CoffeeBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["coffeeBatchId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BrewRunFrozenEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedRunId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SourceDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("archiveId"),
        Index("coffeeBatchId"),
        Index("relatedRunId"),
        Index("sourceId"),
        Index(value = ["coffeeBatchId", "occurredAt"]),
    ],
)
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val coffeeBatchId: Long,
    val transactionTypeCode: String,
    val deltaGrams: Double? = null,
    val costDelta: Double? = null,
    val currencyCode: String = "CNY",
    val occurredAt: Long,
    val sourceId: Long? = null,
    val relatedRunId: Long? = null,
    val notes: String = "",
)

@Entity(
    tableName = "attachment",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SourceDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("archiveId"), Index("sourceId"), Index(value = ["subjectType", "subjectId"])],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val subjectType: String,
    val subjectId: Long,
    val mimeType: String,
    val uriText: String,
    val fileName: String = "",
    val sizeBytes: Long? = null,
    val createdAt: Long,
    val sourceId: Long? = null,
    val notes: String = "",
)

@Entity(
    tableName = "import_log",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveCoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId"), Index(value = ["archiveId", "externalId"])],
)
data class ImportLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val sourceName: String,
    val importTypeCode: String,
    val externalId: String = "",
    val payloadJson: String = "",
    val importedAt: Long,
    val notes: String = "",
)
