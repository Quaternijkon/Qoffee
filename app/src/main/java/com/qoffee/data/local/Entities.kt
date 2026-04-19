package com.qoffee.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "archives")
data class ArchiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val typeCode: String,
    val isReadOnly: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int,
)

data class ArchiveSummaryRow(
    @Embedded val archive: ArchiveEntity,
    val beanCount: Int,
    val grinderCount: Int,
    val recordCount: Int,
    val lastRecordAt: Long?,
)

@Entity(
    tableName = "bean_profiles",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId")],
)
data class BeanProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val name: String,
    val roaster: String,
    val origin: String,
    val processValue: Int,
    val variety: String,
    val roastLevelValue: Int,
    val roastDateEpochDay: Long?,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "grinder_profiles",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("archiveId")],
)
data class GrinderProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val name: String,
    val minSetting: Double,
    val maxSetting: Double,
    val stepSize: Double,
    val unitLabel: String,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "recipe_templates",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BeanProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["beanId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = GrinderProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["grinderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("archiveId"),
        Index("beanId"),
        Index("grinderId"),
    ],
)
data class RecipeTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val name: String,
    val brewMethodCode: String?,
    val beanId: Long?,
    val beanNameSnapshot: String?,
    val grinderId: Long?,
    val grinderNameSnapshot: String?,
    val grindSetting: Double?,
    val coffeeDoseG: Double?,
    val brewWaterMl: Double?,
    val bypassWaterMl: Double?,
    val waterTempC: Double?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "brew_records",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BeanProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["beanId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = GrinderProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["grinderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("archiveId"),
        Index("status"),
        Index("brewedAt"),
        Index("beanId"),
        Index("grinderId"),
        Index("recipeTemplateId"),
    ],
)
data class BrewRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val status: String,
    val brewMethodCode: String?,
    val beanId: Long?,
    val beanNameSnapshot: String?,
    val beanRoastLevelSnapshotValue: Int?,
    val beanProcessMethodSnapshotValue: Int?,
    val recipeTemplateId: Long? = null,
    val recipeNameSnapshot: String? = null,
    val grinderId: Long?,
    val grinderNameSnapshot: String?,
    val grindSetting: Double?,
    val coffeeDoseG: Double?,
    val brewWaterMl: Double?,
    val bypassWaterMl: Double?,
    val waterTempC: Double?,
    val notes: String,
    val brewedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val totalWaterMl: Double?,
    val brewRatio: Double?,
)

@Entity(
    tableName = "subjective_evaluations",
    foreignKeys = [
        ForeignKey(
            entity = BrewRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordId")],
)
data class SubjectiveEvaluationEntity(
    @PrimaryKey val recordId: Long,
    val aroma: Int?,
    val acidity: Int?,
    val sweetness: Int?,
    val bitterness: Int?,
    val body: Int?,
    val aftertaste: Int?,
    val overall: Int?,
    val notes: String,
)

@Entity(
    tableName = "flavor_tags",
    foreignKeys = [
        ForeignKey(
            entity = ArchiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["archiveId", "name"], unique = true), Index("archiveId")],
)
data class FlavorTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val archiveId: Long,
    val name: String,
    val isPreset: Boolean,
)

@Entity(
    tableName = "record_flavor_tags",
    primaryKeys = ["recordId", "flavorTagId"],
    foreignKeys = [
        ForeignKey(
            entity = BrewRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FlavorTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["flavorTagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("flavorTagId")],
)
data class RecordFlavorTagCrossRef(
    val recordId: Long,
    val flavorTagId: Long,
)

data class SubjectiveEvaluationWithTags(
    @Embedded val evaluation: SubjectiveEvaluationEntity,
    @Relation(
        parentColumn = "recordId",
        entityColumn = "id",
        associateBy = Junction(
            value = RecordFlavorTagCrossRef::class,
            parentColumn = "recordId",
            entityColumn = "flavorTagId",
        ),
    )
    val flavorTags: List<FlavorTagEntity>,
)

data class BrewRecordWithRelations(
    @Embedded val record: BrewRecordEntity,
    @Relation(parentColumn = "beanId", entityColumn = "id")
    val beanProfile: BeanProfileEntity?,
    @Relation(parentColumn = "grinderId", entityColumn = "id")
    val grinderProfile: GrinderProfileEntity?,
    @Relation(parentColumn = "id", entityColumn = "recordId", entity = SubjectiveEvaluationEntity::class)
    val subjectiveEvaluation: SubjectiveEvaluationWithTags?,
)
