package com.qoffee.data.mapper

import com.qoffee.core.model.Archive
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.core.model.ArchiveType
import com.qoffee.core.model.BeanProcessMethod
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RoastLevel
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.core.model.WaterCurveJsonCodec
import com.qoffee.data.local.ArchiveEntity
import com.qoffee.data.local.ArchiveSummaryRow
import com.qoffee.data.local.BeanProfileEntity
import com.qoffee.data.local.BrewRecordEntity
import com.qoffee.data.local.BrewRecordWithRelations
import com.qoffee.data.local.FlavorTagEntity
import com.qoffee.data.local.GrinderProfileEntity
import com.qoffee.data.local.RecipeTemplateEntity
import com.qoffee.data.local.SubjectiveEvaluationEntity

fun ArchiveEntity.toDomain() = Archive(
    id = id,
    name = name,
    type = ArchiveType.fromCode(typeCode),
    isReadOnly = isReadOnly,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
)

fun ArchiveSummaryRow.toDomain() = ArchiveSummary(
    archive = archive.toDomain(),
    beanCount = beanCount,
    grinderCount = grinderCount,
    recordCount = recordCount,
    lastRecordAt = lastRecordAt,
)

fun BeanProfileEntity.toDomain() = BeanProfile(
    id = id,
    archiveId = archiveId,
    name = name,
    roaster = roaster,
    origin = origin,
    processMethod = BeanProcessMethod.fromStorageValue(processValue),
    variety = variety,
    roastLevel = RoastLevel.fromStorageValue(roastLevelValue),
    roastDateEpochDay = roastDateEpochDay,
    initialStockG = initialStockG,
    notes = notes,
    createdAt = createdAt,
)

fun BeanProfile.toEntity() = BeanProfileEntity(
    id = id,
    archiveId = archiveId,
    name = name,
    roaster = roaster,
    origin = origin,
    processValue = processMethod.storageValue,
    variety = variety,
    roastLevelValue = roastLevel.storageValue,
    roastDateEpochDay = roastDateEpochDay,
    initialStockG = initialStockG,
    notes = notes,
    createdAt = createdAt,
)

fun GrinderProfileEntity.toDomain() = GrinderProfile(
    id = id,
    archiveId = archiveId,
    name = name,
    minSetting = minSetting,
    maxSetting = maxSetting,
    stepSize = stepSize,
    unitLabel = unitLabel,
    notes = notes,
    createdAt = createdAt,
)

fun GrinderProfile.toEntity() = GrinderProfileEntity(
    id = id,
    archiveId = archiveId,
    name = name,
    minSetting = minSetting,
    maxSetting = maxSetting,
    stepSize = stepSize,
    unitLabel = unitLabel,
    notes = notes,
    createdAt = createdAt,
)

fun RecipeTemplateEntity.toDomain() = RecipeTemplate(
    id = id,
    archiveId = archiveId,
    name = name,
    brewMethod = BrewMethod.fromCode(brewMethodCode),
    beanProfileId = beanId,
    beanNameSnapshot = beanNameSnapshot,
    grinderProfileId = grinderId,
    grinderNameSnapshot = grinderNameSnapshot,
    grindSetting = grindSetting,
    coffeeDoseG = coffeeDoseG,
    brewWaterMl = brewWaterMl,
    bypassWaterMl = bypassWaterMl,
    waterTempC = waterTempC,
    waterCurve = WaterCurveJsonCodec.decode(waterCurveJson),
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecipeTemplate.toEntity() = RecipeTemplateEntity(
    id = id,
    archiveId = archiveId,
    name = name,
    brewMethodCode = brewMethod?.code,
    beanId = beanProfileId,
    beanNameSnapshot = beanNameSnapshot,
    grinderId = grinderProfileId,
    grinderNameSnapshot = grinderNameSnapshot,
    grindSetting = grindSetting,
    coffeeDoseG = coffeeDoseG,
    brewWaterMl = brewWaterMl,
    bypassWaterMl = bypassWaterMl,
    waterTempC = waterTempC,
    waterCurveJson = WaterCurveJsonCodec.encode(waterCurve),
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun FlavorTagEntity.toDomain() = FlavorTag(
    id = id,
    archiveId = archiveId,
    name = name,
    isPreset = isPreset,
)

fun SubjectiveEvaluationEntity.toDomain(flavorTags: List<FlavorTag>) = SubjectiveEvaluation(
    recordId = recordId,
    aroma = aroma,
    acidity = acidity,
    sweetness = sweetness,
    bitterness = bitterness,
    body = body,
    aftertaste = aftertaste,
    overall = overall,
    notes = notes,
    flavorTags = flavorTags,
)

fun SubjectiveEvaluation.toEntity() = SubjectiveEvaluationEntity(
    recordId = recordId,
    aroma = aroma,
    acidity = acidity,
    sweetness = sweetness,
    bitterness = bitterness,
    body = body,
    aftertaste = aftertaste,
    overall = overall,
    notes = notes,
)

fun BrewRecordEntity.toDomain(
    beanProfile: BeanProfile? = null,
    grinderProfile: GrinderProfile? = null,
    subjectiveEvaluation: SubjectiveEvaluation? = null,
) = CoffeeRecord(
    id = id,
    archiveId = archiveId,
    status = RecordStatus.fromCode(status),
    brewMethod = BrewMethod.fromCode(brewMethodCode),
    beanProfileId = beanId,
    beanNameSnapshot = beanNameSnapshot,
    beanRoastLevelSnapshot = beanRoastLevelSnapshotValue?.let(RoastLevel::fromStorageValue),
    beanProcessMethodSnapshot = beanProcessMethodSnapshotValue?.let(BeanProcessMethod::fromStorageValue),
    recipeTemplateId = recipeTemplateId,
    recipeNameSnapshot = recipeNameSnapshot,
    grinderProfileId = grinderId,
    grinderNameSnapshot = grinderNameSnapshot,
    grindSetting = grindSetting,
    coffeeDoseG = coffeeDoseG,
    brewWaterMl = brewWaterMl,
    bypassWaterMl = bypassWaterMl,
    waterTempC = waterTempC,
    waterCurve = WaterCurveJsonCodec.decode(waterCurveJson),
    notes = notes,
    brewedAt = brewedAt,
    brewDurationSeconds = brewDurationSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    totalWaterMl = totalWaterMl,
    brewRatio = brewRatio,
    beanProfile = beanProfile,
    grinderProfile = grinderProfile,
    subjectiveEvaluation = subjectiveEvaluation,
)

fun BrewRecordWithRelations.toDomain() = record.toDomain(
    beanProfile = beanProfile?.toDomain(),
    grinderProfile = grinderProfile?.toDomain(),
    subjectiveEvaluation = subjectiveEvaluation?.evaluation?.toDomain(
        subjectiveEvaluation.flavorTags.map { it.toDomain() },
    ),
)
