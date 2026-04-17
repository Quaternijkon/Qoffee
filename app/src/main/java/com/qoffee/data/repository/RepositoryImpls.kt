package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
import com.qoffee.data.local.BeanProfileDao
import com.qoffee.data.local.BrewRecordDao
import com.qoffee.data.local.BrewRecordEntity
import com.qoffee.data.local.FlavorTagDao
import com.qoffee.data.local.FlavorTagEntity
import com.qoffee.data.local.GrinderProfileDao
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val flavorTagDao: FlavorTagDao,
    private val timeProvider: TimeProvider,
) : CatalogRepository {

    override fun observeBeanProfiles(): Flow<List<BeanProfile>> =
        beanProfileDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeGrinderProfiles(): Flow<List<GrinderProfile>> =
        grinderProfileDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeFlavorTags(): Flow<List<FlavorTag>> =
        flavorTagDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBeanProfile(id: Long): BeanProfile? = beanProfileDao.getById(id)?.toDomain()

    override suspend fun getGrinderProfile(id: Long): GrinderProfile? = grinderProfileDao.getById(id)?.toDomain()

    override suspend fun saveBeanProfile(profile: BeanProfile): Long {
        val entity = profile.copy(
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
        val entity = profile.copy(
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
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "Flavor tag name can not be blank." }
        flavorTagDao.findByName(normalized)?.let { return it.toDomain() }
        val id = flavorTagDao.insert(FlavorTagEntity(name = normalized, isPreset = false))
        return if (id > 0L) {
            FlavorTag(id = id, name = normalized, isPreset = false)
        } else {
            checkNotNull(flavorTagDao.findByName(normalized)).toDomain()
        }
    }

    override suspend fun seedPresetFlavorTags() {
        val presets = listOf(
            "Floral",
            "Citrus",
            "Berry",
            "Tropical Fruit",
            "Tea",
            "Caramel",
            "Cocoa",
            "Chocolate",
            "Nutty",
            "Spice",
        )
        presets.forEach { name ->
            if (flavorTagDao.findByName(name) == null) {
                flavorTagDao.insert(FlavorTagEntity(name = name, isPreset = true))
            }
        }
    }
}

@Singleton
class RecordRepositoryImpl @Inject constructor(
    private val database: QoffeeDatabase,
    private val brewRecordDao: BrewRecordDao,
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val subjectiveEvaluationDao: SubjectiveEvaluationDao,
    private val flavorTagDao: FlavorTagDao,
    private val recordFlavorTagDao: RecordFlavorTagDao,
    private val validator: RecordValidator,
    private val timeProvider: TimeProvider,
) : RecordRepository {

    override fun observeRecords(filter: AnalysisFilter): Flow<List<CoffeeRecord>> {
        return brewRecordDao.observeAll().map { rows ->
            rows.map { it.toDomain() }.filter { record ->
                record.status == RecordStatus.DRAFT || filter.matches(record, timeProvider.nowMillis())
            }
        }
    }

    override fun observeRecord(recordId: Long): Flow<CoffeeRecord?> =
        brewRecordDao.observeById(recordId).map { it?.toDomain() }

    override suspend fun getRecord(recordId: Long): CoffeeRecord? =
        brewRecordDao.getById(recordId)?.toDomain()

    override suspend fun getActiveDraftId(): Long? = brewRecordDao.getActiveDraft()?.id

    override suspend fun getOrCreateActiveDraftId(autoRestore: Boolean): Long = database.withTransaction {
        val activeDraft = brewRecordDao.getActiveDraft()
        if (activeDraft != null && autoRestore) {
            return@withTransaction activeDraft.id
        }
        if (activeDraft != null && !autoRestore) {
            brewRecordDao.deleteDrafts()
        }
        val now = timeProvider.nowMillis()
        brewRecordDao.insert(
            BrewRecordEntity(
                status = RecordStatus.DRAFT.code,
                brewMethodCode = null,
                beanId = null,
                beanNameSnapshot = null,
                beanRoastLevelSnapshotCode = null,
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
            val current = checkNotNull(brewRecordDao.getEntityById(recordId)) {
                "Record $recordId was not found."
            }
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
                    beanRoastLevelSnapshotCode = bean?.roastLevelCode,
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
                    val existing = flavorTagDao.findByName(tag.name.trim())
                    existing?.id ?: flavorTagDao.insert(
                        FlavorTagEntity(name = tag.name.trim(), isPreset = false),
                    )
                }
            }.filter { it > 0L }
            recordFlavorTagDao.insertAll(tagIds.map { RecordFlavorTagCrossRef(recordId, it) })
        }
    }

    override suspend fun completeRecord(recordId: Long): RecordValidationResult = database.withTransaction {
        val current = checkNotNull(brewRecordDao.getById(recordId)) {
            "Record $recordId was not found."
        }.toDomain()
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
        val source = checkNotNull(brewRecordDao.getById(recordId)) {
            "Record $recordId was not found."
        }
        brewRecordDao.deleteDrafts()
        val now = timeProvider.nowMillis()
        val newId = brewRecordDao.insert(
            source.record.copy(
                id = 0L,
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
}

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val brewRecordDao: BrewRecordDao,
    private val analyticsEngine: AnalyticsEngine,
) : AnalyticsRepository {
    override fun observeDashboard(filter: AnalysisFilter): Flow<AnalyticsDashboard> {
        return brewRecordDao.observeAll().map { rows ->
            analyticsEngine.buildDashboard(rows.map { it.toDomain() }, filter)
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
                autoRestoreDraft = prefs[Keys.AUTO_RESTORE_DRAFT] ?: true,
                showInsightConfidence = prefs[Keys.SHOW_CONFIDENCE] ?: true,
                defaultAnalysisTimeRange = AnalysisTimeRange.entries.firstOrNull {
                    it.name == prefs[Keys.DEFAULT_ANALYSIS_RANGE]
                } ?: AnalysisTimeRange.LAST_90_DAYS,
            )
        }
    }

    override suspend fun setAutoRestoreDraft(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.AUTO_RESTORE_DRAFT] = enabled }
    }

    override suspend fun setShowInsightConfidence(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.SHOW_CONFIDENCE] = enabled }
    }

    override suspend fun setDefaultAnalysisTimeRange(range: AnalysisTimeRange) {
        dataStore.edit { prefs -> prefs[Keys.DEFAULT_ANALYSIS_RANGE] = range.name }
    }

    private object Keys {
        val AUTO_RESTORE_DRAFT = booleanPreferencesKey("auto_restore_draft")
        val SHOW_CONFIDENCE = booleanPreferencesKey("show_insight_confidence")
        val DEFAULT_ANALYSIS_RANGE = stringPreferencesKey("default_analysis_range")
    }
}
