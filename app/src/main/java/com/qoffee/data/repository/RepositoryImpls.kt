package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.qoffee.core.analytics.AnalyticsEngine
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalyticsDashboard
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.DraftReplacePolicy
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.ObjectiveDraftUpdate
import com.qoffee.core.model.RecordPrefillSource
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecordValidationResult
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.core.model.UserSettings
import com.qoffee.core.model.WaterCurve
import com.qoffee.core.model.WaterCurveDerivedValues
import com.qoffee.core.model.WaterCurveJsonCodec
import com.qoffee.core.model.deriveValues
import com.qoffee.core.records.RecordValidator
import com.qoffee.data.local.ArchiveDao
import com.qoffee.data.local.BeanProfileDao
import com.qoffee.data.local.BeanProfileEntity
import com.qoffee.data.local.BrewRecordDao
import com.qoffee.data.local.BrewRecordEntity
import com.qoffee.data.local.FlavorTagDao
import com.qoffee.data.local.FlavorTagEntity
import com.qoffee.data.local.GrinderProfileDao
import com.qoffee.data.local.GrinderProfileEntity
import com.qoffee.data.local.QoffeeDatabase
import com.qoffee.data.local.RecipeTemplateDao
import com.qoffee.data.local.RecipeTemplateEntity
import com.qoffee.data.local.RecordFlavorTagCrossRef
import com.qoffee.data.local.RecordFlavorTagDao
import com.qoffee.data.local.SubjectiveEvaluationDao
import com.qoffee.data.mapper.toDomain
import com.qoffee.data.mapper.toEntity
import com.qoffee.domain.repository.AnalyticsRepository
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val archiveDao: ArchiveDao,
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val flavorTagDao: FlavorTagDao,
    private val dataStore: DataStore<Preferences>,
    private val timeProvider: TimeProvider,
    private val frozenDataBridge: FrozenDataBridge,
) : CatalogRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeBeanProfiles(): Flow<List<BeanProfile>> = currentArchiveIdFlow().flatMapLatest { archiveId ->
        archiveId?.let { beanProfileDao.observeByArchive(it).map { entities -> entities.map { entity -> entity.toDomain() } } } ?: flowOf(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeGrinderProfiles(): Flow<List<GrinderProfile>> = currentArchiveIdFlow().flatMapLatest { archiveId ->
        archiveId?.let { grinderProfileDao.observeByArchive(it).map { entities -> entities.map { entity -> entity.toDomain() } } } ?: flowOf(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFlavorTags(): Flow<List<FlavorTag>> = currentArchiveIdFlow().flatMapLatest { archiveId ->
        archiveId?.let { flavorTagDao.observeAll(it).map { entities -> entities.map(FlavorTagEntity::toDomain) } } ?: flowOf(emptyList())
    }

    override suspend fun getBeanProfile(id: Long): BeanProfile? = beanProfileDao.getById(id)?.toDomain()

    override suspend fun getGrinderProfile(id: Long): GrinderProfile? = grinderProfileDao.getById(id)?.toDomain()

    override suspend fun saveBeanProfile(profile: BeanProfile): Long {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val entity = profile.copy(
            archiveId = profile.archiveId.takeIf { it > 0 } ?: archiveId,
            createdAt = profile.createdAt.takeIf { it > 0 } ?: timeProvider.nowMillis(),
        ).toEntity()
        return if (profile.id == 0L) {
            beanProfileDao.insert(entity)
        } else {
            beanProfileDao.update(entity)
            profile.id
        }.also { beanId ->
            frozenDataBridge.upsertBeanProfileFromLegacy(beanId)
        }
    }

    override suspend fun saveGrinderProfile(profile: GrinderProfile): Long {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val entity = profile.copy(
            archiveId = profile.archiveId.takeIf { it > 0 } ?: archiveId,
            createdAt = profile.createdAt.takeIf { it > 0 } ?: timeProvider.nowMillis(),
        ).toEntity()
        return if (profile.id == 0L) {
            grinderProfileDao.insert(entity)
        } else {
            grinderProfileDao.update(entity)
            profile.id
        }.also { grinderId ->
            frozenDataBridge.upsertGrinderFromLegacy(grinderId)
        }
    }

    override suspend fun deleteBeanProfile(id: Long) {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        beanProfileDao.deleteById(id)
        clearDeletedDefaults(beanId = id)
        frozenDataBridge.softDeleteBeanProfile(id)
    }

    override suspend fun deleteGrinderProfile(id: Long) {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        grinderProfileDao.deleteById(id)
        clearDeletedDefaults(grinderId = id)
        frozenDataBridge.softDeleteGrinder(id)
    }

    override suspend fun ensureFlavorTag(name: String): FlavorTag {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "风味标签名称不能为空。" }
        flavorTagDao.findByName(archiveId, normalized)?.let { return it.toDomain() }
        val id = flavorTagDao.insert(
            FlavorTagEntity(
                archiveId = archiveId,
                name = normalized,
                isPreset = false,
            ),
        )
        return if (id > 0L) {
            FlavorTag(id = id, archiveId = archiveId, name = normalized, isPreset = false)
        } else {
            checkNotNull(flavorTagDao.findByName(archiveId, normalized)).toDomain()
        }
    }

    override suspend fun seedPresetFlavorTags() {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val presets = listOf("花香", "柑橘", "莓果", "热带水果", "茶感", "焦糖", "可可", "巧克力", "坚果", "香料")
        presets.forEach { name ->
            if (flavorTagDao.findByName(archiveId, name) == null) {
                flavorTagDao.insert(
                    FlavorTagEntity(
                        archiveId = archiveId,
                        name = name,
                        isPreset = true,
                    ),
                )
            }
        }
    }

    private fun currentArchiveIdFlow(): Flow<Long?> =
        dataStore.data.map { prefs -> prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] }

    private suspend fun requireCurrentArchiveId(): Long =
        checkNotNull(dataStore.data.first()[PreferenceKeys.CURRENT_ARCHIVE_ID]) { "当前没有活动存档。" }

    private suspend fun ensureArchiveWritable(archiveId: Long) {
        check(!(archiveDao.getById(archiveId)?.isReadOnly ?: false)) { "当前存档为只读，不能修改内容。" }
    }
    private suspend fun clearDeletedDefaults(beanId: Long? = null, grinderId: Long? = null) {
        dataStore.edit { prefs ->
            if (beanId != null && prefs[PreferenceKeys.DEFAULT_BEAN_ID] == beanId) {
                prefs.remove(PreferenceKeys.DEFAULT_BEAN_ID)
            }
            if (grinderId != null && prefs[PreferenceKeys.DEFAULT_GRINDER_ID] == grinderId) {
                prefs.remove(PreferenceKeys.DEFAULT_GRINDER_ID)
            }
        }
    }
}

@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val archiveDao: ArchiveDao,
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val recipeTemplateDao: RecipeTemplateDao,
    private val dataStore: DataStore<Preferences>,
    private val timeProvider: TimeProvider,
    private val frozenDataBridge: FrozenDataBridge,
) : RecipeRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecipes(): Flow<List<RecipeTemplate>> = currentArchiveIdFlow().flatMapLatest { archiveId ->
        archiveId?.let {
            recipeTemplateDao.observeByArchive(it).map { entities -> entities.map { entity -> entity.toDomain() } }
        } ?: flowOf(emptyList())
    }

    override suspend fun getRecipe(id: Long): RecipeTemplate? = recipeTemplateDao.getById(id)?.toDomain()

    override suspend fun saveRecipe(template: RecipeTemplate): Long {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val bean = template.beanProfileId?.let { beanProfileDao.getById(it) }
        val grinder = template.grinderProfileId?.let { grinderProfileDao.getById(it) }
        val now = timeProvider.nowMillis()
        val derived = resolveWaterCurveDerivedValues(
            waterCurve = template.waterCurve,
            brewWaterMl = template.brewWaterMl,
            bypassWaterMl = template.bypassWaterMl,
            waterTempC = template.waterTempC,
            brewDurationSeconds = null,
            coffeeDoseG = template.coffeeDoseG,
        )
        val entity = template.copy(
            archiveId = template.archiveId.takeIf { it > 0 } ?: archiveId,
            beanNameSnapshot = bean?.name ?: template.beanNameSnapshot,
            grinderNameSnapshot = grinder?.name ?: template.grinderNameSnapshot,
            brewWaterMl = derived.brewWaterMl,
            bypassWaterMl = derived.bypassWaterMl,
            waterTempC = derived.waterTempC,
            createdAt = template.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        ).toEntity()
        return if (template.id == 0L) {
            recipeTemplateDao.insert(entity)
        } else {
            recipeTemplateDao.update(entity)
            template.id
        }.also { recipeId ->
            frozenDataBridge.upsertRecipeFromLegacy(recipeId)
        }
    }

    override suspend fun deleteRecipe(id: Long) {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        recipeTemplateDao.deleteById(id)
        frozenDataBridge.softDeleteRecipe(id)
    }

    private fun currentArchiveIdFlow(): Flow<Long?> =
        dataStore.data.map { prefs -> prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] }

    private suspend fun requireCurrentArchiveId(): Long =
        checkNotNull(dataStore.data.first()[PreferenceKeys.CURRENT_ARCHIVE_ID]) { "当前没有活动存档。" }

    private suspend fun ensureArchiveWritable(archiveId: Long) {
        check(!(archiveDao.getById(archiveId)?.isReadOnly ?: false)) { "当前存档为只读，不能修改内容。" }
    }
}

@Singleton
class RecordRepositoryImpl @Inject constructor(
    private val database: QoffeeDatabase,
    private val archiveDao: ArchiveDao,
    private val brewRecordDao: BrewRecordDao,
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val recipeTemplateDao: RecipeTemplateDao,
    private val subjectiveEvaluationDao: SubjectiveEvaluationDao,
    private val flavorTagDao: FlavorTagDao,
    private val recordFlavorTagDao: RecordFlavorTagDao,
    private val dataStore: DataStore<Preferences>,
    private val validator: RecordValidator,
    private val timeProvider: TimeProvider,
    private val frozenDataBridge: FrozenDataBridge,
) : RecordRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecords(filter: AnalysisFilter): Flow<List<CoffeeRecord>> {
        return currentArchiveIdFlow().flatMapLatest { archiveId ->
            val effectiveArchiveId = filter.archiveId ?: archiveId
            effectiveArchiveId?.let {
                brewRecordDao.observeAll(it).map { rows ->
                    rows.map { row -> row.toDomain() }.filter { record ->
                        record.status == RecordStatus.DRAFT || filter.matches(record, timeProvider.nowMillis())
                    }
                }
            } ?: flowOf(emptyList())
        }
    }

    override fun observeRecord(recordId: Long): Flow<CoffeeRecord?> =
        brewRecordDao.observeById(recordId).map { it?.toDomain() }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecentRecords(limit: Int): Flow<List<CoffeeRecord>> {
        return currentArchiveIdFlow().flatMapLatest { archiveId ->
            archiveId?.let {
                brewRecordDao.observeRecentCompleted(it, limit).map { rows ->
                    rows.map { row -> row.toDomain() }
                }
            } ?: flowOf(emptyList())
        }
    }

    override suspend fun getRecord(recordId: Long): CoffeeRecord? =
        brewRecordDao.getById(recordId)?.toDomain()

    override suspend fun getActiveDraftId(): Long? {
        val archiveId = requireCurrentArchiveId()
        return brewRecordDao.getActiveDraft(archiveId)?.id
    }

    override suspend fun createDraft(
        prefillSource: RecordPrefillSource,
        replacePolicy: DraftReplacePolicy,
    ): Long = database.withTransaction {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val activeDraft = brewRecordDao.getActiveDraft(archiveId)
        val reusableDraftId = when {
            activeDraft != null && replacePolicy == DraftReplacePolicy.KEEP_CURRENT -> activeDraft.id
            activeDraft != null && replacePolicy == DraftReplacePolicy.REPLACE_CURRENT -> {
                brewRecordDao.deleteDrafts(archiveId)
                null
            }
            else -> null
        }

        val draftId = when (prefillSource) {
            RecordPrefillSource.Blank,
            RecordPrefillSource.Draft -> reusableDraftId ?: insertEmptyDraft(archiveId)

            is RecordPrefillSource.Recipe -> {
                reusableDraftId ?: createRecipePrefilledDraft(
                    archiveId = archiveId,
                    recipeId = prefillSource.recipeId,
                )
            }

            is RecordPrefillSource.Record -> {
                reusableDraftId ?: createRecordPrefilledDraft(
                    archiveId = archiveId,
                    recordId = prefillSource.recordId,
                )
            }

            is RecordPrefillSource.Bean -> {
                reusableDraftId ?: createBeanPrefilledDraft(
                    archiveId = archiveId,
                    beanId = prefillSource.beanId,
                )
            }
        }
        frozenDataBridge.upsertRunFromLegacy(draftId)
        draftId
    }

    override suspend fun createEmptyDraft(replaceActiveDraft: Boolean): Long {
        return createDraft(
            prefillSource = RecordPrefillSource.Blank,
            replacePolicy = if (replaceActiveDraft) DraftReplacePolicy.REPLACE_CURRENT else DraftReplacePolicy.KEEP_CURRENT,
        )
    }

    override suspend fun createDraftFromRecipe(recipeId: Long, replaceActiveDraft: Boolean): Long {
        return createDraft(
            prefillSource = RecordPrefillSource.Recipe(recipeId),
            replacePolicy = if (replaceActiveDraft) DraftReplacePolicy.REPLACE_CURRENT else DraftReplacePolicy.KEEP_CURRENT,
        )
    }

    override suspend fun getOrCreateActiveDraftId(autoRestore: Boolean): Long {
        return createDraft(
            prefillSource = RecordPrefillSource.Draft,
            replacePolicy = if (autoRestore) DraftReplacePolicy.KEEP_CURRENT else DraftReplacePolicy.REPLACE_CURRENT,
        )
    }

    override suspend fun applyRecipeToDraft(recordId: Long, recipeId: Long) {
        database.withTransaction {
            val archiveId = requireCurrentArchiveId()
            ensureArchiveWritable(archiveId)
            val recipe = checkNotNull(recipeTemplateDao.getById(recipeId)) { "配方不存在。" }
            val current = checkNotNull(brewRecordDao.getEntityById(recordId)) { "记录不存在。" }
            brewRecordDao.update(applyRecipeToEntity(current, recipe))
            frozenDataBridge.upsertRunFromLegacy(recordId)
        }
    }

    override suspend fun getLatestComparableRecord(
        beanId: Long?,
        brewMethod: BrewMethod?,
        excludingRecordId: Long?,
    ): CoffeeRecord? {
        if (beanId == null && brewMethod == null) return null
        val archiveId = requireCurrentArchiveId()
        return brewRecordDao.getLatestComparable(
            archiveId = archiveId,
            beanId = beanId,
            brewMethodCode = brewMethod?.code,
            excludingRecordId = excludingRecordId,
        )?.toDomain()
    }

    override suspend fun duplicateLatestComparableAsDraft(beanId: Long?, brewMethod: BrewMethod?): Long? {
        val comparable = getLatestComparableRecord(beanId = beanId, brewMethod = brewMethod)
        return comparable?.let {
            createDraft(
                prefillSource = RecordPrefillSource.Record(it.id),
                replacePolicy = DraftReplacePolicy.REPLACE_CURRENT,
            )
        }
    }

    override suspend fun saveRecordAsRecipe(recordId: Long, name: String, targetRecipeId: Long?): Long = database.withTransaction {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val record = checkNotNull(getRecord(recordId)) { "记录不存在。" }
        val existingRecipe = targetRecipeId?.let { recipeTemplateDao.getById(it)?.toDomain() }
        val resolvedName = name.trim()
            .takeIf { it.isNotBlank() }
            ?: existingRecipe?.name
            ?: record.recipeNameSnapshot
            ?: buildDefaultRecipeName(record)
        val bean = record.beanProfileId?.let { beanProfileDao.getById(it) }
        val grinder = record.grinderProfileId?.let { grinderProfileDao.getById(it) }
        val now = timeProvider.nowMillis()
        val entity = buildRecipeTemplateFromRecord(
            record = record,
            name = resolvedName,
            archiveId = archiveId,
            beanNameSnapshot = bean?.name ?: record.beanNameSnapshot,
            grinderNameSnapshot = grinder?.name ?: record.grinderNameSnapshot,
            existingRecipe = existingRecipe,
            now = now,
        ).toEntity()
        val recipeId = if (existingRecipe == null) {
            recipeTemplateDao.insert(entity)
        } else {
            recipeTemplateDao.update(entity)
            existingRecipe.id
        }
        val currentRecord = checkNotNull(brewRecordDao.getEntityById(recordId)) { "记录不存在。" }
        brewRecordDao.update(
            currentRecord.copy(
                recipeTemplateId = recipeId,
                recipeNameSnapshot = resolvedName,
                updatedAt = now,
            ),
        )
        frozenDataBridge.upsertRecipeFromLegacy(recipeId)
        frozenDataBridge.upsertRunFromLegacy(recordId)
        recipeId
    }

    override suspend fun updateObjective(recordId: Long, update: ObjectiveDraftUpdate) {
        database.withTransaction {
            ensureArchiveWritable(requireCurrentArchiveId())
            val current = checkNotNull(brewRecordDao.getEntityById(recordId)) { "记录不存在。" }
            val bean = update.beanProfileId?.let { beanProfileDao.getById(it) }
            val grinder = update.grinderProfileId?.let { grinderProfileDao.getById(it) }
            val derived = resolveWaterCurveDerivedValues(
                waterCurve = update.waterCurve,
                brewWaterMl = update.brewWaterMl,
                bypassWaterMl = update.bypassWaterMl,
                waterTempC = update.waterTempC,
                brewDurationSeconds = update.brewDurationSeconds,
                coffeeDoseG = update.coffeeDoseG,
            )
            brewRecordDao.update(
                current.copy(
                    recipeTemplateId = update.recipeTemplateId,
                    recipeNameSnapshot = update.recipeNameSnapshot,
                    brewMethodCode = update.brewMethod?.code,
                    beanId = bean?.id,
                    beanNameSnapshot = bean?.name,
                    beanRoastLevelSnapshotValue = bean?.roastLevelValue,
                    beanProcessMethodSnapshotValue = bean?.processValue,
                    grinderId = grinder?.id,
                    grinderNameSnapshot = grinder?.name,
                    grindSetting = update.grindSetting,
                    coffeeDoseG = update.coffeeDoseG,
                    brewWaterMl = derived.brewWaterMl,
                    bypassWaterMl = derived.bypassWaterMl,
                    waterTempC = derived.waterTempC,
                    waterCurveJson = WaterCurveJsonCodec.encode(update.waterCurve),
                    brewedAt = update.brewedAt ?: current.brewedAt,
                    brewDurationSeconds = derived.brewDurationSeconds,
                    notes = update.notes,
                    updatedAt = timeProvider.nowMillis(),
                    totalWaterMl = derived.totalWaterMl,
                    brewRatio = derived.brewRatio,
                ),
            )
            frozenDataBridge.upsertRunFromLegacy(recordId)
        }
    }

    override suspend fun updateSubjective(recordId: Long, evaluation: SubjectiveEvaluation) {
        database.withTransaction {
            val archiveId = requireCurrentArchiveId()
            ensureArchiveWritable(archiveId)
            if (evaluation.isEmpty()) {
                subjectiveEvaluationDao.deleteByRecordId(recordId)
                recordFlavorTagDao.deleteForRecord(recordId)
                frozenDataBridge.upsertRunFromLegacy(recordId)
                return@withTransaction
            }
            subjectiveEvaluationDao.upsert(evaluation.copy(recordId = recordId).toEntity())
            recordFlavorTagDao.deleteForRecord(recordId)
            val tagIds = evaluation.flavorTags.map { tag ->
                if (tag.id > 0L) {
                    tag.id
                } else {
                    val existing = flavorTagDao.findByName(archiveId, tag.name.trim())
                    existing?.id ?: flavorTagDao.insert(
                        FlavorTagEntity(
                            archiveId = archiveId,
                            name = tag.name.trim(),
                            isPreset = false,
                        ),
                    )
                }
            }.filter { it > 0L }
            recordFlavorTagDao.insertAll(tagIds.map { RecordFlavorTagCrossRef(recordId, it) })
            frozenDataBridge.upsertRunFromLegacy(recordId)
        }
    }

    override suspend fun completeRecord(recordId: Long): RecordValidationResult = database.withTransaction {
        ensureArchiveWritable(requireCurrentArchiveId())
        val current = checkNotNull(brewRecordDao.getById(recordId)) { "记录不存在。" }.toDomain()
        val validation = validator.validate(current, current.grinderProfile)
        if (!validation.isValid) {
            return@withTransaction validation
        }
        val entity = checkNotNull(brewRecordDao.getEntityById(recordId))
        brewRecordDao.update(
            entity.copy(
                status = RecordStatus.COMPLETED.code,
                updatedAt = timeProvider.nowMillis(),
            ),
        )
        frozenDataBridge.upsertRunFromLegacy(recordId)
        RecordValidationResult.success()
    }

    override suspend fun duplicateRecordAsDraft(recordId: Long): Long {
        return createDraft(
            prefillSource = RecordPrefillSource.Record(recordId),
            replacePolicy = DraftReplacePolicy.REPLACE_CURRENT,
        )
    }

    override suspend fun deleteRecord(recordId: Long) {
        database.withTransaction {
            ensureArchiveWritable(requireCurrentArchiveId())
            brewRecordDao.deleteById(recordId)
            frozenDataBridge.deleteRun(recordId)
        }
    }

    private suspend fun createRecipePrefilledDraft(archiveId: Long, recipeId: Long): Long {
        val recipe = checkNotNull(recipeTemplateDao.getById(recipeId)) { "配方不存在。" }
        val draftId = insertEmptyDraft(archiveId)
        val current = checkNotNull(brewRecordDao.getEntityById(draftId)) { "记录不存在。" }
        brewRecordDao.update(applyRecipeToEntity(current, recipe))
        return draftId
    }

    private suspend fun createBeanPrefilledDraft(archiveId: Long, beanId: Long): Long {
        val bean = checkNotNull(beanProfileDao.getById(beanId)) { "咖啡豆不存在。" }
        val draftId = insertEmptyDraft(archiveId)
        val current = checkNotNull(brewRecordDao.getEntityById(draftId)) { "记录不存在。" }
        brewRecordDao.update(
            current.copy(
                recipeTemplateId = null,
                recipeNameSnapshot = null,
                beanId = bean.id,
                beanNameSnapshot = bean.name,
                beanRoastLevelSnapshotValue = bean.roastLevelValue,
                beanProcessMethodSnapshotValue = bean.processValue,
                updatedAt = timeProvider.nowMillis(),
            ),
        )
        return draftId
    }

    private suspend fun createRecordPrefilledDraft(archiveId: Long, recordId: Long): Long {
        val source = checkNotNull(brewRecordDao.getById(recordId)) { "记录不存在。" }
        val now = timeProvider.nowMillis()
        val newId = brewRecordDao.insert(
            source.record.copy(
                id = 0L,
                archiveId = archiveId,
                status = RecordStatus.DRAFT.code,
                brewedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
        source.subjectiveEvaluation?.let { evaluationWithTags ->
            subjectiveEvaluationDao.upsert(
                evaluationWithTags.evaluation.copy(recordId = newId),
            )
            recordFlavorTagDao.insertAll(
                evaluationWithTags.flavorTags.map { tag ->
                    RecordFlavorTagCrossRef(recordId = newId, flavorTagId = tag.id)
                },
            )
        }
        return newId
    }

    private fun buildDefaultRecipeName(record: CoffeeRecord): String {
        val beanName = record.beanNameSnapshot ?: "未命名豆子"
        val methodName = record.brewMethod?.displayName ?: "记录"
        return "$beanName $methodName"
    }

    private suspend fun insertEmptyDraft(archiveId: Long): Long {
        val now = timeProvider.nowMillis()
        return brewRecordDao.insert(
            BrewRecordEntity(
                archiveId = archiveId,
                status = RecordStatus.DRAFT.code,
                brewMethodCode = null,
                beanId = null,
                beanNameSnapshot = null,
                beanRoastLevelSnapshotValue = null,
                beanProcessMethodSnapshotValue = null,
                recipeTemplateId = null,
                recipeNameSnapshot = null,
                grinderId = null,
                grinderNameSnapshot = null,
                grindSetting = null,
                coffeeDoseG = null,
                brewWaterMl = null,
                bypassWaterMl = null,
                waterTempC = null,
                waterCurveJson = null,
                notes = "",
                brewedAt = now,
                brewDurationSeconds = null,
                createdAt = now,
                updatedAt = now,
                totalWaterMl = null,
                brewRatio = null,
            ),
        )
    }

    private suspend fun applyRecipeToEntity(
        current: BrewRecordEntity,
        recipe: RecipeTemplateEntity,
    ): BrewRecordEntity {
        val bean = recipe.beanId?.let { beanProfileDao.getById(it) }
        val grinder = recipe.grinderId?.let { grinderProfileDao.getById(it) }
        val waterCurve = WaterCurveJsonCodec.decode(recipe.waterCurveJson)
        val derived = resolveWaterCurveDerivedValues(
            waterCurve = waterCurve,
            brewWaterMl = recipe.brewWaterMl,
            bypassWaterMl = recipe.bypassWaterMl,
            waterTempC = recipe.waterTempC,
            brewDurationSeconds = null,
            coffeeDoseG = recipe.coffeeDoseG,
        )
        return current.copy(
            recipeTemplateId = recipe.id,
            recipeNameSnapshot = recipe.name,
            brewMethodCode = recipe.brewMethodCode,
            beanId = bean?.id ?: recipe.beanId,
            beanNameSnapshot = bean?.name ?: recipe.beanNameSnapshot,
            beanRoastLevelSnapshotValue = bean?.roastLevelValue,
            beanProcessMethodSnapshotValue = bean?.processValue,
            grinderId = grinder?.id ?: recipe.grinderId,
            grinderNameSnapshot = grinder?.name ?: recipe.grinderNameSnapshot,
            grindSetting = recipe.grindSetting,
            coffeeDoseG = recipe.coffeeDoseG,
            brewWaterMl = derived.brewWaterMl,
            bypassWaterMl = derived.bypassWaterMl,
            waterTempC = derived.waterTempC,
            waterCurveJson = recipe.waterCurveJson,
            brewDurationSeconds = derived.brewDurationSeconds,
            notes = recipe.notes,
            updatedAt = timeProvider.nowMillis(),
            totalWaterMl = derived.totalWaterMl,
            brewRatio = derived.brewRatio,
        )
    }

    private fun currentArchiveIdFlow(): Flow<Long?> =
        dataStore.data.map { prefs -> prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] }

    private suspend fun requireCurrentArchiveId(): Long =
        checkNotNull(dataStore.data.first()[PreferenceKeys.CURRENT_ARCHIVE_ID]) { "当前没有活动存档。" }

    private suspend fun ensureArchiveWritable(archiveId: Long) {
        check(!(archiveDao.getById(archiveId)?.isReadOnly ?: false)) { "当前存档为只读，不能修改内容。" }
    }
}

internal fun buildRecipeTemplateFromRecord(
    record: CoffeeRecord,
    name: String,
    archiveId: Long,
    beanNameSnapshot: String?,
    grinderNameSnapshot: String?,
    existingRecipe: RecipeTemplate? = null,
    now: Long,
): RecipeTemplate {
    val derived = resolveWaterCurveDerivedValues(
        waterCurve = record.waterCurve,
        brewWaterMl = record.brewWaterMl,
        bypassWaterMl = record.bypassWaterMl,
        waterTempC = record.waterTempC,
        brewDurationSeconds = record.brewDurationSeconds,
        coffeeDoseG = record.coffeeDoseG,
    )
    return RecipeTemplate(
        id = existingRecipe?.id ?: 0L,
        archiveId = existingRecipe?.archiveId ?: archiveId,
        name = name,
        brewMethod = record.brewMethod,
        beanProfileId = record.beanProfileId,
        beanNameSnapshot = beanNameSnapshot ?: record.beanNameSnapshot,
        grinderProfileId = record.grinderProfileId,
        grinderNameSnapshot = grinderNameSnapshot ?: record.grinderNameSnapshot,
        grindSetting = record.grindSetting,
        coffeeDoseG = record.coffeeDoseG,
        brewWaterMl = derived.brewWaterMl,
        bypassWaterMl = derived.bypassWaterMl,
        waterTempC = derived.waterTempC,
        waterCurve = record.waterCurve,
        notes = record.notes,
        createdAt = existingRecipe?.createdAt ?: now,
        updatedAt = now,
    )
}

private fun resolveWaterCurveDerivedValues(
    waterCurve: WaterCurve?,
    brewWaterMl: Double?,
    bypassWaterMl: Double?,
    waterTempC: Double?,
    brewDurationSeconds: Int?,
    coffeeDoseG: Double?,
): WaterCurveDerivedValues {
    if (waterCurve != null) {
        return waterCurve.deriveValues(coffeeDoseG)
    }
    val totalWater = when {
        brewWaterMl == null -> null
        bypassWaterMl == null -> brewWaterMl
        else -> brewWaterMl + bypassWaterMl
    }
    val brewRatio = if (totalWater != null && coffeeDoseG != null && coffeeDoseG > 0.0) {
        totalWater / coffeeDoseG
    } else {
        null
    }
    return WaterCurveDerivedValues(
        brewWaterMl = brewWaterMl,
        bypassWaterMl = bypassWaterMl,
        waterTempC = waterTempC,
        brewDurationSeconds = brewDurationSeconds,
        totalWaterMl = totalWater,
        brewRatio = brewRatio,
    )
}

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val brewRecordDao: BrewRecordDao,
    private val dataStore: DataStore<Preferences>,
    private val analyticsEngine: AnalyticsEngine,
) : AnalyticsRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDashboard(filter: AnalysisFilter): Flow<AnalyticsDashboard> {
        return dataStore.data
            .map { prefs -> prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] }
            .flatMapLatest { currentArchiveId ->
                val effectiveArchiveId = filter.archiveId ?: currentArchiveId
                effectiveArchiveId?.let {
                    brewRecordDao.observeAll(it).map { rows ->
                        analyticsEngine.buildDashboard(rows.map { row -> row.toDomain() }, filter.copy(archiveId = it))
                    }
                } ?: flowOf(AnalyticsDashboard(filter = filter))
            }
    }
}

@Singleton
class PreferenceRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferenceRepository {

    override fun observeSettings(): Flow<UserSettings> {
        return dataStore.data.map { prefs ->
            UserSettings(
                autoRestoreDraft = prefs[PreferenceKeys.AUTO_RESTORE_DRAFT] ?: true,
                showInsightConfidence = prefs[PreferenceKeys.SHOW_CONFIDENCE] ?: true,
                showLearnInDock = prefs[PreferenceKeys.SHOW_LEARN_IN_DOCK] ?: false,
                defaultAnalysisTimeRange = AnalysisTimeRange.entries.firstOrNull {
                    it.name == prefs[PreferenceKeys.DEFAULT_ANALYSIS_RANGE]
                } ?: AnalysisTimeRange.LAST_90_DAYS,
                defaultBeanProfileId = prefs[PreferenceKeys.DEFAULT_BEAN_ID],
                defaultGrinderProfileId = prefs[PreferenceKeys.DEFAULT_GRINDER_ID],
            )
        }
    }

    override suspend fun setAutoRestoreDraft(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferenceKeys.AUTO_RESTORE_DRAFT] = enabled }
    }

    override suspend fun setShowInsightConfidence(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferenceKeys.SHOW_CONFIDENCE] = enabled }
    }

    override suspend fun setDefaultAnalysisTimeRange(range: AnalysisTimeRange) {
        dataStore.edit { prefs -> prefs[PreferenceKeys.DEFAULT_ANALYSIS_RANGE] = range.name }
    }

    override suspend fun setDefaultBeanProfile(beanId: Long?) {
        dataStore.edit { prefs ->
            if (beanId == null || beanId <= 0L) {
                prefs.remove(PreferenceKeys.DEFAULT_BEAN_ID)
            } else {
                prefs[PreferenceKeys.DEFAULT_BEAN_ID] = beanId
            }
        }
    }

    override suspend fun setDefaultGrinderProfile(grinderId: Long?) {
        dataStore.edit { prefs ->
            if (grinderId == null || grinderId <= 0L) {
                prefs.remove(PreferenceKeys.DEFAULT_GRINDER_ID)
            } else {
                prefs[PreferenceKeys.DEFAULT_GRINDER_ID] = grinderId
            }
        }
    }

    override suspend fun setShowLearnInDock(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[PreferenceKeys.SHOW_LEARN_IN_DOCK] = enabled }
    }
}
