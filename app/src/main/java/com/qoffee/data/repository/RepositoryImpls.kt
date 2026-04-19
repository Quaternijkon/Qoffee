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
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.FlavorTag
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.ObjectiveDraftUpdate
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecordValidationResult
import com.qoffee.core.model.SubjectiveEvaluation
import com.qoffee.core.model.UserSettings
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
import com.qoffee.data.local.RecordFlavorTagCrossRef
import com.qoffee.data.local.RecordFlavorTagDao
import com.qoffee.data.local.SubjectiveEvaluationDao
import com.qoffee.data.mapper.toDomain
import com.qoffee.data.mapper.toEntity
import com.qoffee.domain.repository.AnalyticsRepository
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
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
        }
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
}

@Singleton
class RecordRepositoryImpl @Inject constructor(
    private val database: QoffeeDatabase,
    private val archiveDao: ArchiveDao,
    private val brewRecordDao: BrewRecordDao,
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val subjectiveEvaluationDao: SubjectiveEvaluationDao,
    private val flavorTagDao: FlavorTagDao,
    private val recordFlavorTagDao: RecordFlavorTagDao,
    private val dataStore: DataStore<Preferences>,
    private val validator: RecordValidator,
    private val timeProvider: TimeProvider,
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

    override suspend fun getRecord(recordId: Long): CoffeeRecord? =
        brewRecordDao.getById(recordId)?.toDomain()

    override suspend fun getActiveDraftId(): Long? {
        val archiveId = requireCurrentArchiveId()
        return brewRecordDao.getActiveDraft(archiveId)?.id
    }

    override suspend fun getOrCreateActiveDraftId(autoRestore: Boolean): Long = database.withTransaction {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val activeDraft = brewRecordDao.getActiveDraft(archiveId)
        if (activeDraft != null && autoRestore) {
            return@withTransaction activeDraft.id
        }
        if (activeDraft != null && !autoRestore) {
            brewRecordDao.deleteDrafts(archiveId)
        }
        val now = timeProvider.nowMillis()
        brewRecordDao.insert(
            BrewRecordEntity(
                archiveId = archiveId,
                status = RecordStatus.DRAFT.code,
                brewMethodCode = null,
                beanId = null,
                beanNameSnapshot = null,
                beanRoastLevelSnapshotValue = null,
                beanProcessMethodSnapshotValue = null,
                grinderId = null,
                grinderNameSnapshot = null,
                grindSetting = null,
                coffeeDoseG = null,
                brewWaterMl = null,
                bypassWaterMl = null,
                waterTempC = null,
                notes = "",
                brewedAt = now,
                createdAt = now,
                updatedAt = now,
                totalWaterMl = null,
                brewRatio = null,
            ),
        )
    }

    override suspend fun updateObjective(recordId: Long, update: ObjectiveDraftUpdate) {
        database.withTransaction {
            ensureArchiveWritable(requireCurrentArchiveId())
            val current = checkNotNull(brewRecordDao.getEntityById(recordId)) { "记录不存在。" }
            val bean = update.beanProfileId?.let { beanProfileDao.getById(it) }
            val grinder = update.grinderProfileId?.let { grinderProfileDao.getById(it) }
            val totalWater = when {
                update.brewWaterMl == null -> null
                update.bypassWaterMl == null -> update.brewWaterMl
                else -> update.brewWaterMl + update.bypassWaterMl
            }
            val brewRatio = if (totalWater != null && update.coffeeDoseG != null && update.coffeeDoseG > 0.0) {
                totalWater / update.coffeeDoseG
            } else {
                null
            }
            brewRecordDao.update(
                current.copy(
                    brewMethodCode = update.brewMethod?.code,
                    beanId = bean?.id,
                    beanNameSnapshot = bean?.name,
                    beanRoastLevelSnapshotValue = bean?.roastLevelValue,
                    beanProcessMethodSnapshotValue = bean?.processValue,
                    grinderId = grinder?.id,
                    grinderNameSnapshot = grinder?.name,
                    grindSetting = update.grindSetting,
                    coffeeDoseG = update.coffeeDoseG,
                    brewWaterMl = update.brewWaterMl,
                    bypassWaterMl = update.bypassWaterMl,
                    waterTempC = update.waterTempC,
                    notes = update.notes,
                    updatedAt = timeProvider.nowMillis(),
                    totalWaterMl = totalWater,
                    brewRatio = brewRatio,
                ),
            )
        }
    }

    override suspend fun updateSubjective(recordId: Long, evaluation: SubjectiveEvaluation) {
        database.withTransaction {
            val archiveId = requireCurrentArchiveId()
            ensureArchiveWritable(archiveId)
            if (evaluation.isEmpty()) {
                subjectiveEvaluationDao.deleteByRecordId(recordId)
                recordFlavorTagDao.deleteForRecord(recordId)
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
        RecordValidationResult.success()
    }

    override suspend fun duplicateRecordAsDraft(recordId: Long): Long = database.withTransaction {
        val archiveId = requireCurrentArchiveId()
        ensureArchiveWritable(archiveId)
        val source = checkNotNull(brewRecordDao.getById(recordId)) { "记录不存在。" }
        brewRecordDao.deleteDrafts(archiveId)
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
        newId
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
                defaultAnalysisTimeRange = AnalysisTimeRange.entries.firstOrNull {
                    it.name == prefs[PreferenceKeys.DEFAULT_ANALYSIS_RANGE]
                } ?: AnalysisTimeRange.LAST_90_DAYS,
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
}
