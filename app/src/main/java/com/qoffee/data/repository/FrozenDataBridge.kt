package com.qoffee.data.repository

import androidx.room.withTransaction
import com.qoffee.data.local.ArchiveCoreDao
import com.qoffee.data.local.ArchiveCoreEntity
import com.qoffee.data.local.ArchiveDao
import com.qoffee.data.local.CoffeeBatchDao
import com.qoffee.data.local.CoffeeBatchEntity
import com.qoffee.data.local.CoffeeProductDao
import com.qoffee.data.local.CoffeeProductEntity
import com.qoffee.data.local.EquipmentAssetDao
import com.qoffee.data.local.EquipmentAssetEntity
import com.qoffee.data.local.EquipmentAssetTypeDao
import com.qoffee.data.local.EquipmentAssetTypeEntity
import com.qoffee.data.local.EventDefinitionDao
import com.qoffee.data.local.EventDefinitionEntity
import com.qoffee.data.local.FlavorTagDao
import com.qoffee.data.local.GrinderProfileDao
import com.qoffee.data.local.InventoryTransactionDao
import com.qoffee.data.local.InventoryTransactionEntity
import com.qoffee.data.local.MetricDefinitionDao
import com.qoffee.data.local.MetricDefinitionEntity
import com.qoffee.data.local.MetricEnumOptionDao
import com.qoffee.data.local.ObservationDao
import com.qoffee.data.local.ObservationEntity
import com.qoffee.data.local.QoffeeDatabase
import com.qoffee.data.local.RecipeCoreDao
import com.qoffee.data.local.RecipeCoreEntity
import com.qoffee.data.local.RecipeTemplateDao
import com.qoffee.data.local.RecipeTemplateEntity
import com.qoffee.data.local.RecipeVersionDao
import com.qoffee.data.local.RecipeVersionEntity
import com.qoffee.data.local.SourceDefinitionDao
import com.qoffee.data.local.SourceDefinitionEntity
import com.qoffee.data.local.SubjectTagLinkDao
import com.qoffee.data.local.SubjectTagLinkEntity
import com.qoffee.data.local.TagDefinitionDao
import com.qoffee.data.local.TagDefinitionEntity
import com.qoffee.data.local.UnitDefinitionDao
import com.qoffee.data.local.UnitDefinitionEntity
import com.qoffee.data.local.BrewRunAssetLinkDao
import com.qoffee.data.local.BrewRunAssetLinkEntity
import com.qoffee.data.local.BrewRunFrozenDao
import com.qoffee.data.local.BrewRunFrozenEntity
import com.qoffee.data.local.BeanProfileDao
import com.qoffee.data.local.BrewRecordDao
import com.qoffee.data.local.BrewRecordEntity
import com.qoffee.data.mapper.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrozenDataBridge @Inject constructor(
    private val database: QoffeeDatabase,
    private val archiveDao: ArchiveDao,
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val recipeTemplateDao: RecipeTemplateDao,
    private val brewRecordDao: BrewRecordDao,
    private val flavorTagDao: FlavorTagDao,
) {

    private val archiveCoreDao: ArchiveCoreDao get() = database.archiveCoreDao()
    private val coffeeProductDao: CoffeeProductDao get() = database.coffeeProductDao()
    private val coffeeBatchDao: CoffeeBatchDao get() = database.coffeeBatchDao()
    private val equipmentAssetTypeDao: EquipmentAssetTypeDao get() = database.equipmentAssetTypeDao()
    private val equipmentAssetDao: EquipmentAssetDao get() = database.equipmentAssetDao()
    private val recipeCoreDao: RecipeCoreDao get() = database.recipeCoreDao()
    private val recipeVersionDao: RecipeVersionDao get() = database.recipeVersionDao()
    private val metricDefinitionDao: MetricDefinitionDao get() = database.metricDefinitionDao()
    private val metricEnumOptionDao: MetricEnumOptionDao get() = database.metricEnumOptionDao()
    private val eventDefinitionDao: EventDefinitionDao get() = database.eventDefinitionDao()
    private val tagDefinitionDao: TagDefinitionDao get() = database.tagDefinitionDao()
    private val sourceDefinitionDao: SourceDefinitionDao get() = database.sourceDefinitionDao()
    private val unitDefinitionDao: UnitDefinitionDao get() = database.unitDefinitionDao()
    private val brewRunFrozenDao: BrewRunFrozenDao get() = database.brewRunFrozenDao()
    private val brewRunAssetLinkDao: BrewRunAssetLinkDao get() = database.brewRunAssetLinkDao()
    private val observationDao: ObservationDao get() = database.observationDao()
    private val subjectTagLinkDao: SubjectTagLinkDao get() = database.subjectTagLinkDao()
    private val inventoryTransactionDao: InventoryTransactionDao get() = database.inventoryTransactionDao()

    private var sourceIds: Map<String, Long>? = null
    private var metricIds: Map<String, Long>? = null
    private var assetTypeIds: Map<String, Long>? = null

    suspend fun ensureSystemSeeded() = database.withTransaction {
        unitDefinitionDao.insertAll(FrozenSeedCatalog.units)
        sourceDefinitionDao.insertAll(FrozenSeedCatalog.sources)
        equipmentAssetTypeDao.insertAll(FrozenSeedCatalog.assetTypes)
        metricDefinitionDao.insertAll(FrozenSeedCatalog.metrics)
        eventDefinitionDao.insertAll(FrozenSeedCatalog.events)
        refreshCaches()
    }

    suspend fun rebuildAllFromLegacy() = database.withTransaction {
        ensureSystemSeeded()
        archiveDao.getAll().forEach { archive ->
            upsertArchiveInternal(archive.id)
        }
    }

    suspend fun syncArchiveFromLegacy(archiveId: Long) = database.withTransaction {
        ensureSystemSeeded()
        upsertArchiveInternal(archiveId)
    }

    suspend fun deleteArchive(archiveId: Long) = database.withTransaction {
        archiveCoreDao.deleteById(archiveId)
    }

    suspend fun upsertBeanProfileFromLegacy(beanId: Long) = database.withTransaction {
        ensureSystemSeeded()
        upsertBeanInternal(beanId)
    }

    suspend fun softDeleteBeanProfile(beanId: Long) = database.withTransaction {
        ensureSystemSeeded()
        val batch = coffeeBatchDao.getById(beanId) ?: return@withTransaction
        coffeeBatchDao.updateActive(batch.id, isActive = false, updatedAt = System.currentTimeMillis())
    }

    suspend fun upsertGrinderFromLegacy(grinderId: Long) = database.withTransaction {
        ensureSystemSeeded()
        upsertGrinderInternal(grinderId)
    }

    suspend fun reprojectRunsForGrinder(grinderId: Long) = database.withTransaction {
        ensureSystemSeeded()
        val grinder = grinderProfileDao.getById(grinderId) ?: return@withTransaction
        brewRecordDao.getAllByArchive(grinder.archiveId)
            .asSequence()
            .filter { row -> row.record.grinderId == grinderId }
            .forEach { row ->
                upsertRunInternal(row.record.id)
            }
    }

    suspend fun softDeleteGrinder(grinderId: Long) = database.withTransaction {
        ensureSystemSeeded()
        equipmentAssetDao.updateActive(grinderId, isActive = false, updatedAt = System.currentTimeMillis())
    }

    suspend fun upsertRecipeFromLegacy(recipeId: Long) = database.withTransaction {
        ensureSystemSeeded()
        upsertRecipeInternal(recipeId)
    }

    suspend fun softDeleteRecipe(recipeId: Long) = database.withTransaction {
        ensureSystemSeeded()
        val recipe = recipeCoreDao.getById(recipeId) ?: return@withTransaction
        recipeCoreDao.updateActive(recipe.id, isActive = false, updatedAt = System.currentTimeMillis())
    }

    suspend fun upsertRunFromLegacy(recordId: Long) = database.withTransaction {
        ensureSystemSeeded()
        upsertRunInternal(recordId)
    }

    suspend fun deleteRun(recordId: Long) = database.withTransaction {
        inventoryTransactionDao.deleteForRun(recordId)
        brewRunFrozenDao.deleteById(recordId)
    }

    private suspend fun refreshCaches() {
        sourceIds = sourceDefinitionDao.getAll().associate { it.code to it.id }
        metricIds = metricDefinitionDao.getAll().associate { it.code to it.id }
        assetTypeIds = equipmentAssetTypeDao.getAll().associate { it.code to it.id }
    }

    private suspend fun sourceId(code: String): Long {
        return checkNotNull(sourceIds?.get(code)) { "Missing source $code" }
    }

    private suspend fun metricId(code: String): Long {
        return checkNotNull(metricIds?.get(code)) { "Missing metric $code" }
    }

    private suspend fun assetTypeId(code: String): Long {
        return checkNotNull(assetTypeIds?.get(code)) { "Missing asset type $code" }
    }

    private suspend fun upsertArchiveInternal(archiveId: Long) {
        val archive = archiveDao.getById(archiveId) ?: return
        archiveCoreDao.upsert(
            ArchiveCoreEntity(
                id = archive.id,
                name = archive.name,
                typeCode = archive.typeCode,
                isReadOnly = archive.isReadOnly,
                createdAt = archive.createdAt,
                updatedAt = archive.updatedAt,
                sortOrder = archive.sortOrder,
            ),
        )

        beanProfileDao.getAllByArchive(archiveId).forEach { bean ->
            upsertBeanInternal(bean.id)
        }
        grinderProfileDao.getAllByArchive(archiveId).forEach { grinder ->
            upsertGrinderInternal(grinder.id)
        }
        recipeTemplateDao.getAllByArchive(archiveId).forEach { recipe ->
            upsertRecipeInternal(recipe.id)
        }
        flavorTagDao.getAllByArchive(archiveId).forEach { tag ->
            upsertFlavorTagInternal(tag.archiveId, tag.name, tag.isPreset)
        }
        brewRecordDao.getAllByArchive(archiveId).forEach { row ->
            upsertRunInternal(row.record.id)
        }
    }

    private suspend fun upsertBeanInternal(beanId: Long) {
        val bean = beanProfileDao.getById(beanId) ?: return
        val now = System.currentTimeMillis()
        val processCode = when (bean.processValue) {
            0 -> "natural"
            1 -> "washed"
            2 -> "honey"
            else -> ""
        }
        coffeeProductDao.upsert(
            CoffeeProductEntity(
                id = bean.id,
                archiveId = bean.archiveId,
                name = bean.name,
                roaster = bean.roaster,
                region = bean.origin,
                processMethodCode = processCode,
                variety = bean.variety,
                roastLevelTargetCode = bean.roastLevelValue.toString(),
                notes = bean.notes,
                createdAt = bean.createdAt,
                updatedAt = now,
            ),
        )
        coffeeBatchDao.upsert(
            CoffeeBatchEntity(
                id = bean.id,
                archiveId = bean.archiveId,
                productId = bean.id,
                displayName = bean.name,
                roastDateEpochDay = bean.roastDateEpochDay,
                initialStockG = bean.initialStockG,
                packageSizeG = bean.initialStockG,
                notes = bean.notes,
                isActive = true,
                createdAt = bean.createdAt,
                updatedAt = now,
            ),
        )
        upsertInventorySeed(bean.archiveId, bean.id, bean.initialStockG, now)
    }

    private suspend fun upsertGrinderInternal(grinderId: Long) {
        val grinder = grinderProfileDao.getById(grinderId) ?: return
        val now = System.currentTimeMillis()
        equipmentAssetDao.upsert(
            EquipmentAssetEntity(
                id = grinder.id,
                archiveId = grinder.archiveId,
                typeId = assetTypeId("grinder"),
                name = grinder.name,
                minValue = grinder.minSetting,
                maxValue = grinder.maxSetting,
                stepSize = grinder.stepSize,
                defaultUnitCode = grinder.unitLabel,
                specJson = grinder.normalizationJson.orEmpty(),
                notes = grinder.notes,
                isActive = true,
                createdAt = grinder.createdAt,
                updatedAt = now,
            ),
        )
    }

    private suspend fun upsertRecipeInternal(recipeId: Long) {
        val recipe = recipeTemplateDao.getById(recipeId) ?: return
        val now = System.currentTimeMillis()
        val recipeCore = recipeCoreDao.getById(recipe.id)
        if (recipeCore == null) {
            recipeCoreDao.upsert(
                RecipeCoreEntity(
                    id = recipe.id,
                    archiveId = recipe.archiveId,
                    name = recipe.name,
                    brewMethodCode = recipe.brewMethodCode.orEmpty(),
                    currentVersionId = recipe.id,
                    isActive = true,
                    createdAt = recipe.createdAt,
                    updatedAt = recipe.updatedAt,
                ),
            )
            recipeVersionDao.upsert(
                RecipeVersionEntity(
                    id = recipe.id,
                    recipeId = recipe.id,
                    archiveId = recipe.archiveId,
                    versionNumber = 1,
                    brewMethodCode = recipe.brewMethodCode.orEmpty(),
                    notes = recipe.notes,
                    createdAt = recipe.createdAt,
                    updatedAt = recipe.updatedAt,
                ),
            )
            replaceRecipeVersionObservations(recipe.id, recipe)
            return
        }

        val latestVersion = recipeCore.currentVersionId?.let { versionId ->
            recipeVersionDao.getById(versionId)
        }
        val currentVersion = if (latestVersion == null || latestVersion.updatedAt == recipe.updatedAt) {
            latestVersion ?: RecipeVersionEntity(
                id = recipe.id,
                recipeId = recipe.id,
                archiveId = recipe.archiveId,
                versionNumber = 1,
                brewMethodCode = recipe.brewMethodCode.orEmpty(),
                notes = recipe.notes,
                createdAt = recipe.createdAt,
                updatedAt = recipe.updatedAt,
            )
        } else {
            val newVersionId = recipeVersionDao.insert(
                RecipeVersionEntity(
                    recipeId = recipe.id,
                    archiveId = recipe.archiveId,
                    versionNumber = (recipeVersionDao.getMaxVersionNumber(recipe.id) ?: 1) + 1,
                    brewMethodCode = recipe.brewMethodCode.orEmpty(),
                    notes = recipe.notes,
                    createdAt = now,
                    updatedAt = recipe.updatedAt,
                ),
            )
            recipeVersionDao.getById(newVersionId)!!
        }

        recipeCoreDao.upsert(
            RecipeCoreEntity(
                id = recipe.id,
                archiveId = recipe.archiveId,
                name = recipe.name,
                brewMethodCode = recipe.brewMethodCode.orEmpty(),
                currentVersionId = currentVersion.id,
                isActive = true,
                createdAt = recipeCore.createdAt,
                updatedAt = recipe.updatedAt,
            ),
        )
        recipeCoreDao.updateCurrentVersion(recipe.id, currentVersion.id, recipe.updatedAt)
        replaceRecipeVersionObservations(currentVersion.id, recipe)
    }

    private suspend fun upsertRunInternal(recordId: Long) {
        val record = brewRecordDao.getById(recordId) ?: return
        val row = record.record
        val now = System.currentTimeMillis()
        archiveCoreDao.upsert(
            ArchiveCoreEntity(
                id = row.archiveId,
                name = archiveDao.getById(row.archiveId)?.name ?: "",
                typeCode = archiveDao.getById(row.archiveId)?.typeCode ?: "normal",
                isReadOnly = archiveDao.getById(row.archiveId)?.isReadOnly ?: false,
                createdAt = archiveDao.getById(row.archiveId)?.createdAt ?: now,
                updatedAt = archiveDao.getById(row.archiveId)?.updatedAt ?: now,
                sortOrder = archiveDao.getById(row.archiveId)?.sortOrder ?: 0,
            ),
        )
        row.beanId?.let { upsertBeanInternal(it) }
        row.grinderId?.let { upsertGrinderInternal(it) }
        row.recipeTemplateId?.let { upsertRecipeInternal(it) }

        val existingRun = brewRunFrozenDao.getById(row.id)
        val recipeVersionId = when {
            existingRun?.recipeVersionId != null && existingRun.recipeVersionId != 0L -> existingRun.recipeVersionId
            row.recipeTemplateId != null -> recipeCoreDao.getById(row.recipeTemplateId)?.currentVersionId
            else -> null
        }
        brewRunFrozenDao.upsert(
            BrewRunFrozenEntity(
                id = row.id,
                archiveId = row.archiveId,
                runKind = "brew",
                brewMethodCode = row.brewMethodCode.orEmpty(),
                status = row.status,
                brewedAt = row.brewedAt,
                recipeVersionId = recipeVersionId,
                coffeeBatchId = row.beanId,
                locationText = "",
                operatorText = "",
                sourceId = existingRun?.sourceId ?: sourceId(if (existingRun == null) "migrated" else "manual"),
                confidenceScore = 1.0,
                notes = row.notes,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
            ),
        )

        brewRunAssetLinkDao.deleteForRun(row.id)
        row.grinderId?.let { grinderId ->
            brewRunAssetLinkDao.insertAll(
                listOf(
                    BrewRunAssetLinkEntity(
                        archiveId = row.archiveId,
                        brewRunId = row.id,
                        assetId = grinderId,
                        roleCode = "grinder",
                    ),
                ),
            )
        }

        replaceRunObservations(recordId = row.id, row = row, archiveId = row.archiveId)
        replaceRunFlavorTags(recordId = row.id, archiveId = row.archiveId, flavorTagIds = record.subjectiveEvaluation?.flavorTags?.map { it.id }.orEmpty())
        upsertConsumptionTransaction(row = row)
    }

    private suspend fun replaceRecipeVersionObservations(recipeVersionId: Long, recipe: RecipeTemplateEntity) {
        val managedMetricIds = listOf(
            metricId("grind_setting"),
            metricId("coffee_dose_g"),
            metricId("brew_water_ml"),
            metricId("bypass_water_ml"),
            metricId("water_temp_c"),
        )
        observationDao.deleteForSubjectMetrics("recipe_version", recipeVersionId, managedMetricIds)
        val observations = mutableListOf<ObservationEntity>().apply {
            recipe.grindSetting?.let {
                add(recipeObservation(recipe.archiveId, recipeVersionId, "grind_setting", it, "ratio", recipe.updatedAt))
            }
            recipe.coffeeDoseG?.let {
                add(recipeObservation(recipe.archiveId, recipeVersionId, "coffee_dose_g", it, "g", recipe.updatedAt))
            }
            recipe.brewWaterMl?.let {
                add(recipeObservation(recipe.archiveId, recipeVersionId, "brew_water_ml", it, "ml", recipe.updatedAt))
            }
            recipe.bypassWaterMl?.let {
                add(recipeObservation(recipe.archiveId, recipeVersionId, "bypass_water_ml", it, "ml", recipe.updatedAt))
            }
            recipe.waterTempC?.let {
                add(recipeObservation(recipe.archiveId, recipeVersionId, "water_temp_c", it, "celsius", recipe.updatedAt))
            }
        }
        if (observations.isNotEmpty()) {
            observationDao.insertAll(observations)
        }
    }

    private suspend fun replaceRunObservations(recordId: Long, row: BrewRecordEntity, archiveId: Long) {
        val managedMetricIds = listOf(
            metricId("grind_setting"),
            metricId("grind_setting_normalized"),
            metricId("coffee_dose_g"),
            metricId("brew_water_ml"),
            metricId("bypass_water_ml"),
            metricId("total_water_ml"),
            metricId("brew_ratio"),
            metricId("water_temp_c"),
            metricId("brew_duration_seconds"),
            metricId("aroma_score"),
            metricId("acidity_score"),
            metricId("sweetness_score"),
            metricId("bitterness_score"),
            metricId("body_score"),
            metricId("aftertaste_score"),
            metricId("overall_score"),
            metricId("subjective_note"),
        )
        observationDao.deleteForSubjectMetrics("run", recordId, managedMetricIds)
        val sourceCode = if (row.status == com.qoffee.core.model.RecordStatus.DRAFT.code) "manual" else "migrated"
        val runSourceId = sourceId(sourceCode)
        val normalizedGrindSetting = if (row.grindSetting != null) {
            brewRecordDao.getById(recordId)?.toDomain()?.normalizedGrindSetting
        } else {
            null
        }
        val observations = mutableListOf<ObservationEntity>().apply {
            row.grindSetting?.let { add(runNumericObservation(archiveId, recordId, "grind_setting", it, "ratio", row.updatedAt, runSourceId)) }
            normalizedGrindSetting?.let {
                add(runNumericObservation(archiveId, recordId, "grind_setting_normalized", it, "ratio", row.updatedAt, sourceId("derived")))
            }
            row.coffeeDoseG?.let { add(runNumericObservation(archiveId, recordId, "coffee_dose_g", it, "g", row.updatedAt, runSourceId)) }
            row.brewWaterMl?.let { add(runNumericObservation(archiveId, recordId, "brew_water_ml", it, "ml", row.updatedAt, runSourceId)) }
            row.bypassWaterMl?.let { add(runNumericObservation(archiveId, recordId, "bypass_water_ml", it, "ml", row.updatedAt, runSourceId)) }
            row.totalWaterMl?.let { add(runNumericObservation(archiveId, recordId, "total_water_ml", it, "ml", row.updatedAt, sourceId("derived"))) }
            row.brewRatio?.let { add(runNumericObservation(archiveId, recordId, "brew_ratio", it, "ratio", row.updatedAt, sourceId("derived"))) }
            row.waterTempC?.let { add(runNumericObservation(archiveId, recordId, "water_temp_c", it, "celsius", row.updatedAt, runSourceId)) }
            row.brewDurationSeconds?.let { add(runNumericObservation(archiveId, recordId, "brew_duration_seconds", it.toDouble(), "s", row.updatedAt, runSourceId)) }
        }

        val evaluation = brewRecordDao.getById(recordId)?.subjectiveEvaluation?.evaluation
        evaluation?.aroma?.let { observations += runNumericObservation(archiveId, recordId, "aroma_score", it.toDouble(), "score_5", row.updatedAt, runSourceId) }
        evaluation?.acidity?.let { observations += runNumericObservation(archiveId, recordId, "acidity_score", it.toDouble(), "score_5", row.updatedAt, runSourceId) }
        evaluation?.sweetness?.let { observations += runNumericObservation(archiveId, recordId, "sweetness_score", it.toDouble(), "score_5", row.updatedAt, runSourceId) }
        evaluation?.bitterness?.let { observations += runNumericObservation(archiveId, recordId, "bitterness_score", it.toDouble(), "score_5", row.updatedAt, runSourceId) }
        evaluation?.body?.let { observations += runNumericObservation(archiveId, recordId, "body_score", it.toDouble(), "score_5", row.updatedAt, runSourceId) }
        evaluation?.aftertaste?.let { observations += runNumericObservation(archiveId, recordId, "aftertaste_score", it.toDouble(), "score_5", row.updatedAt, runSourceId) }
        evaluation?.overall?.let { observations += runNumericObservation(archiveId, recordId, "overall_score", it.toDouble(), "score_5", row.updatedAt, runSourceId) }
        evaluation?.notes?.takeIf { it.isNotBlank() }?.let {
            observations += ObservationEntity(
                archiveId = archiveId,
                subjectType = "run",
                subjectId = recordId,
                metricDefinitionId = metricId("subjective_note"),
                capturedAt = row.updatedAt,
                sourceId = runSourceId,
                confidenceScore = 1.0,
                valueType = "text",
                textValue = it,
                unitCode = "text",
                isActualValue = true,
            )
        }

        if (observations.isNotEmpty()) {
            observationDao.insertAll(observations)
        }
    }

    private suspend fun replaceRunFlavorTags(recordId: Long, archiveId: Long, flavorTagIds: List<Long>) {
        subjectTagLinkDao.deleteForSubjectCategory("run", recordId, "flavor")
        if (flavorTagIds.isEmpty()) return
        val links = buildList {
            flavorTagIds.forEach { oldTagId ->
                val oldTag = flavorTagDao.getById(oldTagId) ?: return@forEach
                val tagDefinitionId = upsertFlavorTagInternal(archiveId, oldTag.name, oldTag.isPreset)
                add(
                    SubjectTagLinkEntity(
                        archiveId = archiveId,
                        subjectType = "run",
                        subjectId = recordId,
                        tagDefinitionId = tagDefinitionId,
                        taggedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        if (links.isNotEmpty()) {
            subjectTagLinkDao.insertAll(links)
        }
    }

    private suspend fun upsertFlavorTagInternal(archiveId: Long, name: String, isPreset: Boolean): Long {
        val existing = tagDefinitionDao.findByArchiveAndName(archiveId, "flavor", name)
        if (existing != null) return existing.id
        val insertedId = tagDefinitionDao.insert(
            TagDefinitionEntity(
                archiveId = archiveId,
                categoryCode = "flavor",
                displayName = name,
                isSystem = isPreset,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return if (insertedId > 0L) {
            insertedId
        } else {
            checkNotNull(tagDefinitionDao.findByArchiveAndName(archiveId, "flavor", name)).id
        }
    }

    private suspend fun upsertInventorySeed(archiveId: Long, batchId: Long, initialStockG: Double?, now: Long) {
        val existing = inventoryTransactionDao.getBatchTransaction(batchId, "initial_stock")
        if (initialStockG == null) {
            existing?.let {
                inventoryTransactionDao.upsert(it.copy(deltaGrams = null, occurredAt = now))
            }
            return
        }
        inventoryTransactionDao.upsert(
            InventoryTransactionEntity(
                id = existing?.id ?: 0L,
                archiveId = archiveId,
                coffeeBatchId = batchId,
                transactionTypeCode = "initial_stock",
                deltaGrams = initialStockG,
                occurredAt = now,
                sourceId = sourceId("manual"),
                notes = "legacy batch seed",
            ),
        )
    }

    private suspend fun upsertConsumptionTransaction(row: BrewRecordEntity) {
        val existing = inventoryTransactionDao.getRunTransaction(row.id, "consume")
        if (row.status != com.qoffee.core.model.RecordStatus.COMPLETED.code || row.beanId == null || row.coffeeDoseG == null) {
            existing?.let { inventoryTransactionDao.deleteForRun(row.id) }
            return
        }
        inventoryTransactionDao.upsert(
            InventoryTransactionEntity(
                id = existing?.id ?: 0L,
                archiveId = row.archiveId,
                coffeeBatchId = row.beanId,
                transactionTypeCode = "consume",
                deltaGrams = -row.coffeeDoseG,
                occurredAt = row.brewedAt,
                sourceId = sourceId("derived"),
                relatedRunId = row.id,
                notes = "run consumption",
            ),
        )
    }

    private suspend fun recipeObservation(
        archiveId: Long,
        recipeVersionId: Long,
        metricCode: String,
        numericValue: Double,
        unitCode: String,
        capturedAt: Long,
    ): ObservationEntity {
        return ObservationEntity(
            archiveId = archiveId,
            subjectType = "recipe_version",
            subjectId = recipeVersionId,
            metricDefinitionId = metricId(metricCode),
            capturedAt = capturedAt,
            sourceId = sourceId("manual"),
            confidenceScore = 1.0,
            valueType = "numeric",
            numericValue = numericValue,
            normalizedNumericValue = numericValue,
            unitCode = unitCode,
            isTargetValue = true,
            isActualValue = false,
        )
    }

    private suspend fun runNumericObservation(
        archiveId: Long,
        recordId: Long,
        metricCode: String,
        numericValue: Double,
        unitCode: String,
        capturedAt: Long,
        sourceId: Long,
    ): ObservationEntity {
        return ObservationEntity(
            archiveId = archiveId,
            subjectType = "run",
            subjectId = recordId,
            metricDefinitionId = metricId(metricCode),
            capturedAt = capturedAt,
            sourceId = sourceId,
            confidenceScore = 1.0,
            valueType = "numeric",
            numericValue = numericValue,
            normalizedNumericValue = numericValue,
            unitCode = unitCode,
            isTargetValue = false,
            isActualValue = true,
        )
    }
}

private object FrozenSeedCatalog {
    val units = listOf(
        UnitDefinitionEntity(code = "g", displayName = "Gram", quantityType = "mass", symbol = "g"),
        UnitDefinitionEntity(code = "ml", displayName = "Milliliter", quantityType = "volume", symbol = "ml"),
        UnitDefinitionEntity(code = "s", displayName = "Second", quantityType = "time", symbol = "s"),
        UnitDefinitionEntity(code = "celsius", displayName = "Celsius", quantityType = "temperature", symbol = "°C"),
        UnitDefinitionEntity(code = "ratio", displayName = "Ratio", quantityType = "ratio", symbol = ""),
        UnitDefinitionEntity(code = "score_5", displayName = "Score / 5", quantityType = "score", symbol = "/5"),
        UnitDefinitionEntity(code = "text", displayName = "Text", quantityType = "text", symbol = ""),
    )

    val sources = listOf(
        SourceDefinitionEntity(code = "manual", displayName = "Manual", isMeasured = true, priority = 10),
        SourceDefinitionEntity(code = "imported", displayName = "Imported", isMeasured = true, priority = 20),
        SourceDefinitionEntity(code = "device", displayName = "Device", isMeasured = true, priority = 30),
        SourceDefinitionEntity(code = "derived", displayName = "Derived", priority = 40),
        SourceDefinitionEntity(code = "estimated", displayName = "Estimated", isEstimated = true, priority = 50),
        SourceDefinitionEntity(code = "migrated", displayName = "Migrated", isMeasured = true, priority = 60),
    )

    val assetTypes = listOf(
        EquipmentAssetTypeEntity(code = "grinder", displayName = "Grinder"),
        EquipmentAssetTypeEntity(code = "brewer", displayName = "Brewer"),
        EquipmentAssetTypeEntity(code = "dripper", displayName = "Dripper"),
        EquipmentAssetTypeEntity(code = "kettle", displayName = "Kettle"),
        EquipmentAssetTypeEntity(code = "scale", displayName = "Scale"),
        EquipmentAssetTypeEntity(code = "espresso_machine", displayName = "Espresso Machine"),
        EquipmentAssetTypeEntity(code = "filter", displayName = "Filter"),
        EquipmentAssetTypeEntity(code = "basket", displayName = "Basket"),
        EquipmentAssetTypeEntity(code = "server", displayName = "Server"),
        EquipmentAssetTypeEntity(code = "cup", displayName = "Cup"),
    )

    private val createdAt = System.currentTimeMillis()

    val metrics = listOf(
        metric("grind_setting", "Grind Setting", defaultUnitCode = "ratio", isFilterable = true, isChartable = true),
        metric("grind_setting_normalized", "Normalized Grind", defaultUnitCode = "ratio", isFilterable = true, isChartable = true),
        metric("coffee_dose_g", "Coffee Dose", defaultUnitCode = "g", isFilterable = true, isChartable = true),
        metric("brew_water_ml", "Brew Water", defaultUnitCode = "ml", isFilterable = true, isChartable = true),
        metric("bypass_water_ml", "Bypass Water", defaultUnitCode = "ml", isChartable = true),
        metric("total_water_ml", "Total Water", defaultUnitCode = "ml", isChartable = true),
        metric("brew_ratio", "Brew Ratio", defaultUnitCode = "ratio", isFilterable = true, isChartable = true),
        metric("water_temp_c", "Water Temperature", defaultUnitCode = "celsius", isFilterable = true, isChartable = true),
        metric("brew_duration_seconds", "Brew Time", defaultUnitCode = "s", isFilterable = true, isChartable = true),
        metric("aroma_score", "Aroma", defaultUnitCode = "score_5"),
        metric("acidity_score", "Acidity", defaultUnitCode = "score_5"),
        metric("sweetness_score", "Sweetness", defaultUnitCode = "score_5"),
        metric("bitterness_score", "Bitterness", defaultUnitCode = "score_5"),
        metric("body_score", "Body", defaultUnitCode = "score_5"),
        metric("aftertaste_score", "Aftertaste", defaultUnitCode = "score_5"),
        metric("overall_score", "Overall", defaultUnitCode = "score_5", isChartable = true),
        MetricDefinitionEntity(
            code = "subjective_note",
            displayName = "Subjective Note",
            scopeType = "run",
            valueType = "text",
            defaultUnitCode = "text",
            createdAt = createdAt,
        ),
    )

    val events = listOf(
        EventDefinitionEntity(code = "grind_start", displayName = "Grind Start"),
        EventDefinitionEntity(code = "pour_start", displayName = "Pour Start"),
        EventDefinitionEntity(code = "pour_end", displayName = "Pour End"),
        EventDefinitionEntity(code = "bloom_start", displayName = "Bloom Start"),
        EventDefinitionEntity(code = "stir", displayName = "Stir"),
        EventDefinitionEntity(code = "swirl", displayName = "Swirl"),
        EventDefinitionEntity(code = "channeling_detected", displayName = "Channeling Detected"),
        EventDefinitionEntity(code = "stall", displayName = "Stall"),
        EventDefinitionEntity(code = "flush", displayName = "Flush"),
        EventDefinitionEntity(code = "purge", displayName = "Purge"),
        EventDefinitionEntity(code = "taste_start", displayName = "Taste Start"),
    )

    private fun metric(
        code: String,
        displayName: String,
        defaultUnitCode: String,
        isFilterable: Boolean = false,
        isChartable: Boolean = false,
    ) = MetricDefinitionEntity(
        code = code,
        displayName = displayName,
        scopeType = "run",
        valueType = "numeric",
        defaultUnitCode = defaultUnitCode,
        isAggregatable = true,
        isSystem = true,
        isFilterable = isFilterable,
        isChartable = isChartable,
        createdAt = createdAt,
    )
}
