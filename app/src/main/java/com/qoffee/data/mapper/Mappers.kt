package com.qoffee.data.mapper

import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RoastLevel
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.data.local.BeanProfileEntity
import com.qoffee.data.local.BrewRecordEntity
import com.qoffee.data.local.BrewRecordWithRelations
import com.qoffee.data.local.FlavorTagEntity
import com.qoffee.data.local.GrinderProfileEntity
import com.qoffee.data.local.SubjectiveEvaluationEntity

fun BeanProfileEntity.toDomain() = BeanProfile(
    id = id,
    name = name,
    roaster = roaster,
    origin = origin,
    process = process,
    variety = variety,
    roastLevel = RoastLevel.fromCode(roastLevelCode) ?: RoastLevel.MEDIUM,
    roastDateEpochDay = roastDateEpochDay,
    notes = notes,
    createdAt = createdAt,
)

fun BeanProfile.toEntity() = BeanProfileEntity(
    id = id,
    name = name,
    roaster = roaster,
    origin = origin,
    process = process,
    variety = variety,
    roastLevelCode = roastLevel.code,
    roastDateEpochDay = roastDateEpochDay,
    notes = notes,
    createdAt = createdAt,
)

fun GrinderProfileEntity.toDomain() = GrinderProfile(
    id = id,
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
    name = name,
    minSetting = minSetting,
    maxSetting = maxSetting,
    stepSize = stepSize,
    unitLabel = unitLabel,
    notes = notes,
    createdAt = createdAt,
)

fun FlavorTagEntity.toDomain() = FlavorTag(
    id = id,
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
    status = RecordStatus.fromCode(status),
    brewMethod = BrewMethod.fromCode(brewMethodCode),
    beanProfileId = beanId,
    beanNameSnapshot = beanNameSnapshot,
    beanRoastLevelSnapshot = RoastLevel.fromCode(beanRoastLevelSnapshotCode),
    grinderProfileId = grinderId,
    grinderNameSnapshot = grinderNameSnapshot,
    grindSetting = grindSetting,
    coffeeDoseG = coffeeDoseG,
    brewWaterMl = brewWaterMl,
    bypassWaterMl = bypassWaterMl,
    waterTempC = waterTempC,
    notes = notes,
    brewedAt = brewedAt,
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
