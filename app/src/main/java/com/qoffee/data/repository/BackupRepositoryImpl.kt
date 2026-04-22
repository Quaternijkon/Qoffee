package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.ArchiveType
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.FileExportPayload
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RestoreOutcome
import com.qoffee.core.model.RestoreStatus
import com.qoffee.core.model.WaterCurveJsonCodec
import com.qoffee.data.local.ArchiveDao
import com.qoffee.data.local.ArchiveEntity
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
import com.qoffee.data.local.SubjectiveEvaluationEntity
import com.qoffee.data.mapper.toDomain
import com.qoffee.domain.repository.BackupRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val database: QoffeeDatabase,
    private val archiveDao: ArchiveDao,
    private val beanProfileDao: BeanProfileDao,
    private val grinderProfileDao: GrinderProfileDao,
    private val recipeTemplateDao: RecipeTemplateDao,
    private val brewRecordDao: BrewRecordDao,
    private val subjectiveEvaluationDao: SubjectiveEvaluationDao,
    private val flavorTagDao: FlavorTagDao,
    private val recordFlavorTagDao: RecordFlavorTagDao,
    private val dataStore: DataStore<Preferences>,
    private val timeProvider: TimeProvider,
) : BackupRepository {

    override suspend fun exportBackup(): FileExportPayload {
        val snapshot = buildSnapshot()
        return FileExportPayload(
            fileName = defaultBackupFileName(snapshot.exportedAt),
            mimeType = "application/json",
            content = BackupJsonCodec.encode(snapshot),
        )
    }

    override suspend fun restoreBackup(json: String): RestoreOutcome {
        return try {
            val snapshot = BackupJsonCodec.decode(json)
            require(snapshot.archives.isNotEmpty()) { "备份文件里没有可恢复的存档。" }

            val importPlan = database.withTransaction {
                importSnapshot(snapshot)
            }

            dataStore.edit { prefs ->
                prefs[PreferenceKeys.AUTO_RESTORE_DRAFT] = snapshot.preferences.autoRestoreDraft
                prefs[PreferenceKeys.SHOW_CONFIDENCE] = snapshot.preferences.showInsightConfidence
                prefs[PreferenceKeys.DEFAULT_ANALYSIS_RANGE] = snapshot.preferences.defaultAnalysisTimeRange
                prefs[PreferenceKeys.SHOW_LEARN_IN_DOCK] = snapshot.preferences.showLearnInDock

                if (importPlan.restoredCurrentArchiveId != null) {
                    prefs[PreferenceKeys.CURRENT_ARCHIVE_ID] = importPlan.restoredCurrentArchiveId
                    prefs[PreferenceKeys.LAST_OPENED_ARCHIVE_ID] = importPlan.restoredCurrentArchiveId
                }

                val restoredBeanId = snapshot.preferences.currentArchiveId
                    ?.let(importPlan.beanIdMaps::get)
                    ?.let { beanMap -> snapshot.preferences.defaultBeanProfileId?.let(beanMap::get) }
                if (restoredBeanId != null) {
                    prefs[PreferenceKeys.DEFAULT_BEAN_ID] = restoredBeanId
                } else {
                    prefs.remove(PreferenceKeys.DEFAULT_BEAN_ID)
                }

                val restoredGrinderId = snapshot.preferences.currentArchiveId
                    ?.let(importPlan.grinderIdMaps::get)
                    ?.let { grinderMap -> snapshot.preferences.defaultGrinderProfileId?.let(grinderMap::get) }
                if (restoredGrinderId != null) {
                    prefs[PreferenceKeys.DEFAULT_GRINDER_ID] = restoredGrinderId
                } else {
                    prefs.remove(PreferenceKeys.DEFAULT_GRINDER_ID)
                }
            }

            RestoreOutcome(
                importedArchiveCount = importPlan.importedArchiveNames.size,
                importedArchiveNames = importPlan.importedArchiveNames,
                switchedArchiveId = importPlan.restoredCurrentArchiveId,
                message = buildRestoreSuccessMessage(importPlan.importedArchiveNames),
                status = RestoreStatus.SUCCESS,
            )
        } catch (error: Throwable) {
            error.toRestoreOutcome()
        }
    }

    override suspend fun exportRecordsCsv(filter: AnalysisFilter): FileExportPayload {
        val archiveId = filter.archiveId ?: requireCurrentArchiveId()
        val effectiveFilter = filter.copy(archiveId = archiveId)
        val now = timeProvider.nowMillis()
        val records = brewRecordDao.getAllByArchive(archiveId)
            .map { it.toDomain() }
            .filter { it.status == RecordStatus.COMPLETED }
            .filter { effectiveFilter.matches(it, now) }
        check(records.isNotEmpty()) { "当前筛选条件下没有可导出的记录。" }
        return FileExportPayload(
            fileName = defaultRecordsCsvFileName(now),
            mimeType = "text/csv",
            content = RecordsCsvExporter.export(records),
        )
    }

    private suspend fun buildSnapshot(): BackupSnapshot {
        val prefs = dataStore.data.first()
        val editableArchives = archiveDao.getAll()
            .filterNot { it.isReadOnly || it.typeCode == ArchiveType.DEMO.code }
        require(editableArchives.isNotEmpty()) { "没有可导出的可编辑存档。" }
        val exportedArchiveIds = editableArchives.map { it.id }.toSet()

        return BackupSnapshot(
            schemaVersion = BackupJsonCodec.SCHEMA_VERSION,
            exportedAt = timeProvider.nowMillis(),
            preferences = BackupPreferences(
                currentArchiveId = prefs[PreferenceKeys.CURRENT_ARCHIVE_ID]?.takeIf(exportedArchiveIds::contains),
                autoRestoreDraft = prefs[PreferenceKeys.AUTO_RESTORE_DRAFT] ?: true,
                showInsightConfidence = prefs[PreferenceKeys.SHOW_CONFIDENCE] ?: true,
                defaultAnalysisTimeRange = prefs[PreferenceKeys.DEFAULT_ANALYSIS_RANGE] ?: AnalysisTimeRange.LAST_90_DAYS.name,
                defaultBeanProfileId = prefs[PreferenceKeys.DEFAULT_BEAN_ID],
                defaultGrinderProfileId = prefs[PreferenceKeys.DEFAULT_GRINDER_ID],
                showLearnInDock = prefs[PreferenceKeys.SHOW_LEARN_IN_DOCK] ?: false,
            ),
            archives = editableArchives.map { archive ->
                BackupArchive(
                    id = archive.id,
                    name = archive.name,
                    createdAt = archive.createdAt,
                    updatedAt = archive.updatedAt,
                    beans = beanProfileDao.getAllByArchive(archive.id),
                    grinders = grinderProfileDao.getAllByArchive(archive.id),
                    recipes = recipeTemplateDao.getAllByArchive(archive.id),
                    flavorTags = flavorTagDao.getAllByArchive(archive.id),
                    records = brewRecordDao.getAllByArchive(archive.id).map { row ->
                        BackupRecord(
                            record = row.record,
                            subjectiveEvaluation = row.subjectiveEvaluation?.evaluation,
                            flavorTagIds = row.subjectiveEvaluation?.flavorTags?.map { it.id }.orEmpty(),
                        )
                    },
                )
            },
        )
    }

    private suspend fun importSnapshot(snapshot: BackupSnapshot): RestoreImportPlan {
        val existingArchives = archiveDao.getAll()
        val existingNames = existingArchives.map { it.name }.toMutableSet()
        var nextSortOrder = (existingArchives.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val archiveIdMaps = mutableMapOf<Long, Long>()
        val beanIdMaps = mutableMapOf<Long, MutableMap<Long, Long>>()
        val grinderIdMaps = mutableMapOf<Long, MutableMap<Long, Long>>()
        val importedArchiveNames = mutableListOf<String>()

        snapshot.archives.forEach { archive ->
            val newName = uniqueArchiveName(archive.name, existingNames)
            existingNames += newName
            val newArchiveId = archiveDao.insert(
                ArchiveEntity(
                    name = newName,
                    typeCode = ArchiveType.NORMAL.code,
                    isReadOnly = false,
                    createdAt = archive.createdAt.takeIf { it > 0L } ?: timeProvider.nowMillis(),
                    updatedAt = archive.updatedAt.takeIf { it > 0L } ?: timeProvider.nowMillis(),
                    sortOrder = nextSortOrder++,
                ),
            )
            archiveIdMaps[archive.id] = newArchiveId
            importedArchiveNames += newName

            val flavorTagIdMap = mutableMapOf<Long, Long>()
            archive.flavorTags.forEach { tag ->
                val newTagId = flavorTagDao.insert(
                    tag.copy(
                        id = 0L,
                        archiveId = newArchiveId,
                    ),
                )
                flavorTagIdMap[tag.id] = newTagId
            }

            val beanIdMap = mutableMapOf<Long, Long>()
            archive.beans.forEach { bean ->
                val newBeanId = beanProfileDao.insert(
                    bean.copy(
                        id = 0L,
                        archiveId = newArchiveId,
                    ),
                )
                beanIdMap[bean.id] = newBeanId
            }
            beanIdMaps[archive.id] = beanIdMap

            val grinderIdMap = mutableMapOf<Long, Long>()
            archive.grinders.forEach { grinder ->
                val newGrinderId = grinderProfileDao.insert(
                    grinder.copy(
                        id = 0L,
                        archiveId = newArchiveId,
                    ),
                )
                grinderIdMap[grinder.id] = newGrinderId
            }
            grinderIdMaps[archive.id] = grinderIdMap

            val recipeIdMap = mutableMapOf<Long, Long>()
            archive.recipes.forEach { recipe ->
                val newRecipeId = recipeTemplateDao.insert(
                    recipe.copy(
                        id = 0L,
                        archiveId = newArchiveId,
                        beanId = recipe.beanId?.let(beanIdMap::get),
                        grinderId = recipe.grinderId?.let(grinderIdMap::get),
                    ),
                )
                recipeIdMap[recipe.id] = newRecipeId
            }

            archive.records.forEach { backupRecord ->
                val newRecordId = brewRecordDao.insert(
                    backupRecord.record.copy(
                        id = 0L,
                        archiveId = newArchiveId,
                        beanId = backupRecord.record.beanId?.let(beanIdMap::get),
                        grinderId = backupRecord.record.grinderId?.let(grinderIdMap::get),
                        recipeTemplateId = backupRecord.record.recipeTemplateId?.let(recipeIdMap::get),
                    ),
                )
                backupRecord.subjectiveEvaluation?.let { evaluation ->
                    subjectiveEvaluationDao.upsert(
                        evaluation.copy(recordId = newRecordId),
                    )
                    val restoredTagIds = backupRecord.flavorTagIds.mapNotNull(flavorTagIdMap::get)
                    if (restoredTagIds.isNotEmpty()) {
                        recordFlavorTagDao.insertAll(
                            restoredTagIds.map { tagId ->
                                RecordFlavorTagCrossRef(recordId = newRecordId, flavorTagId = tagId)
                            },
                        )
                    }
                }
            }
        }

        val restoredCurrentArchiveId = snapshot.preferences.currentArchiveId?.let(archiveIdMaps::get)
            ?: archiveIdMaps.values.firstOrNull()

        return RestoreImportPlan(
            importedArchiveNames = importedArchiveNames,
            restoredCurrentArchiveId = restoredCurrentArchiveId,
            beanIdMaps = beanIdMaps,
            grinderIdMaps = grinderIdMaps,
        )
    }

    private suspend fun requireCurrentArchiveId(): Long {
        return checkNotNull(dataStore.data.first()[PreferenceKeys.CURRENT_ARCHIVE_ID]) { "当前没有活动存档。" }
    }
}

internal fun buildRestoreSuccessMessage(importedArchiveNames: List<String>): String {
    return if (importedArchiveNames.isEmpty()) {
        "恢复完成。"
    } else {
        val preview = importedArchiveNames.take(3).joinToString("、")
        "已恢复 ${importedArchiveNames.size} 个存档：$preview"
    }
}

internal fun Throwable.toRestoreOutcome(): RestoreOutcome {
    val status = when (this) {
        is IllegalArgumentException, is JSONException -> RestoreStatus.VALIDATION_ERROR
        else -> RestoreStatus.SYSTEM_ERROR
    }
    val fallbackMessage = when (status) {
        RestoreStatus.SUCCESS -> "恢复完成。"
        RestoreStatus.VALIDATION_ERROR -> "备份文件无效，无法恢复。"
        RestoreStatus.SYSTEM_ERROR -> "恢复失败，请稍后重试。"
    }
    return RestoreOutcome(
        message = message ?: fallbackMessage,
        status = status,
    )
}

internal fun defaultBackupFileName(exportedAt: Long): String {
    return "qoffee-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date(exportedAt))}.json"
}

internal fun defaultRecordsCsvFileName(exportedAt: Long): String {
    return "qoffee-records-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date(exportedAt))}.csv"
}

private data class RestoreImportPlan(
    val importedArchiveNames: List<String>,
    val restoredCurrentArchiveId: Long?,
    val beanIdMaps: Map<Long, Map<Long, Long>>,
    val grinderIdMaps: Map<Long, Map<Long, Long>>,
)

internal data class BackupSnapshot(
    val schemaVersion: Int,
    val exportedAt: Long,
    val preferences: BackupPreferences,
    val archives: List<BackupArchive>,
)

internal data class BackupPreferences(
    val currentArchiveId: Long?,
    val autoRestoreDraft: Boolean,
    val showInsightConfidence: Boolean,
    val defaultAnalysisTimeRange: String,
    val defaultBeanProfileId: Long?,
    val defaultGrinderProfileId: Long?,
    val showLearnInDock: Boolean,
)

internal data class BackupArchive(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val beans: List<BeanProfileEntity>,
    val grinders: List<GrinderProfileEntity>,
    val recipes: List<RecipeTemplateEntity>,
    val flavorTags: List<FlavorTagEntity>,
    val records: List<BackupRecord>,
)

internal data class BackupRecord(
    val record: BrewRecordEntity,
    val subjectiveEvaluation: SubjectiveEvaluationEntity?,
    val flavorTagIds: List<Long>,
)

internal object RecordsCsvExporter {
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun export(records: List<CoffeeRecord>): String {
        val header = listOf(
            "brewedAt",
            "method",
            "beanName",
            "recipeName",
            "grinderName",
            "coffeeDoseG",
            "brewWaterMl",
            "bypassWaterMl",
            "totalWaterMl",
            "brewRatio",
            "waterTempC",
            "brewDurationSeconds",
            "waterCurveJson",
            "overallScore",
            "flavorTags",
            "recordNotes",
            "evaluationNotes",
        )
        val rows = buildList {
            add(header.joinToString(","))
            records.sortedByDescending { it.brewedAt }.forEach { record ->
                add(
                    listOf(
                        csvCell(dateFormatter.format(Date(record.brewedAt))),
                        csvCell(record.brewMethod?.code.orEmpty()),
                        csvCell(record.beanNameSnapshot.orEmpty()),
                        csvCell(record.recipeNameSnapshot.orEmpty()),
                        csvCell(record.grinderNameSnapshot.orEmpty()),
                        csvCell(record.coffeeDoseG?.toString().orEmpty()),
                        csvCell(record.brewWaterMl?.toString().orEmpty()),
                        csvCell(record.bypassWaterMl?.toString().orEmpty()),
                        csvCell(record.totalWaterMl?.toString().orEmpty()),
                        csvCell(record.brewRatio?.toString().orEmpty()),
                        csvCell(record.waterTempC?.toString().orEmpty()),
                        csvCell(record.brewDurationSeconds?.toString().orEmpty()),
                        csvCell(WaterCurveJsonCodec.encode(record.waterCurve).orEmpty()),
                        csvCell(record.subjectiveEvaluation?.overall?.toString().orEmpty()),
                        csvCell(record.subjectiveEvaluation?.flavorTags?.joinToString("|") { it.name }.orEmpty()),
                        csvCell(record.notes),
                        csvCell(record.subjectiveEvaluation?.notes.orEmpty()),
                    ).joinToString(","),
                )
            }
        }
        return rows.joinToString(System.lineSeparator())
    }
}

internal object BackupJsonCodec {
    const val SCHEMA_VERSION = 1

    fun encode(snapshot: BackupSnapshot): String {
        return JSONObject().apply {
            put("schemaVersion", snapshot.schemaVersion)
            put("exportedAt", snapshot.exportedAt)
            put("preferences", snapshot.preferences.toJson())
            put("archives", JSONArray().apply {
                snapshot.archives.forEach { put(it.toJson()) }
            })
        }.toString(2)
    }

    fun decode(json: String): BackupSnapshot {
        val root = JSONObject(json)
        val schemaVersion = root.optInt("schemaVersion")
        require(schemaVersion == SCHEMA_VERSION) { "不支持的备份版本：$schemaVersion" }
        return BackupSnapshot(
            schemaVersion = schemaVersion,
            exportedAt = root.optLong("exportedAt"),
            preferences = root.getJSONObject("preferences").toBackupPreferences(),
            archives = root.getJSONArray("archives").mapObjects { it.toBackupArchive() },
        )
    }

    private fun BackupPreferences.toJson(): JSONObject = JSONObject().apply {
        putNullable("currentArchiveId", currentArchiveId)
        put("autoRestoreDraft", autoRestoreDraft)
        put("showInsightConfidence", showInsightConfidence)
        put("defaultAnalysisTimeRange", defaultAnalysisTimeRange)
        putNullable("defaultBeanProfileId", defaultBeanProfileId)
        putNullable("defaultGrinderProfileId", defaultGrinderProfileId)
        put("showLearnInDock", showLearnInDock)
    }

    private fun BackupArchive.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("beans", JSONArray().apply { beans.forEach { put(it.toJson()) } })
        put("grinders", JSONArray().apply { grinders.forEach { put(it.toJson()) } })
        put("recipes", JSONArray().apply { recipes.forEach { put(it.toJson()) } })
        put("flavorTags", JSONArray().apply { flavorTags.forEach { put(it.toJson()) } })
        put("records", JSONArray().apply { records.forEach { put(it.toJson()) } })
    }

    private fun BackupRecord.toJson(): JSONObject = JSONObject().apply {
        put("record", record.toJson())
        putNullable("subjectiveEvaluation", subjectiveEvaluation?.toJson())
        put("flavorTagIds", JSONArray().apply { flavorTagIds.forEach { put(it) } })
    }

    private fun BeanProfileEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("archiveId", archiveId)
        put("name", name)
        put("roaster", roaster)
        put("origin", origin)
        put("processValue", processValue)
        put("variety", variety)
        put("roastLevelValue", roastLevelValue)
        putNullable("roastDateEpochDay", roastDateEpochDay)
        putNullable("initialStockG", initialStockG)
        put("notes", notes)
        put("createdAt", createdAt)
    }

    private fun GrinderProfileEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("archiveId", archiveId)
        put("name", name)
        put("minSetting", minSetting)
        put("maxSetting", maxSetting)
        put("stepSize", stepSize)
        put("unitLabel", unitLabel)
        put("notes", notes)
        put("createdAt", createdAt)
    }

    private fun RecipeTemplateEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("archiveId", archiveId)
        put("name", name)
        putNullable("brewMethodCode", brewMethodCode)
        putNullable("beanId", beanId)
        putNullable("beanNameSnapshot", beanNameSnapshot)
        putNullable("grinderId", grinderId)
        putNullable("grinderNameSnapshot", grinderNameSnapshot)
        putNullable("grindSetting", grindSetting)
        putNullable("coffeeDoseG", coffeeDoseG)
        putNullable("brewWaterMl", brewWaterMl)
        putNullable("bypassWaterMl", bypassWaterMl)
        putNullable("waterTempC", waterTempC)
        putNullable("waterCurveJson", waterCurveJson)
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun FlavorTagEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("archiveId", archiveId)
        put("name", name)
        put("isPreset", isPreset)
    }

    private fun BrewRecordEntity.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("archiveId", archiveId)
        put("status", status)
        putNullable("brewMethodCode", brewMethodCode)
        putNullable("beanId", beanId)
        putNullable("beanNameSnapshot", beanNameSnapshot)
        putNullable("beanRoastLevelSnapshotValue", beanRoastLevelSnapshotValue)
        putNullable("beanProcessMethodSnapshotValue", beanProcessMethodSnapshotValue)
        putNullable("recipeTemplateId", recipeTemplateId)
        putNullable("recipeNameSnapshot", recipeNameSnapshot)
        putNullable("grinderId", grinderId)
        putNullable("grinderNameSnapshot", grinderNameSnapshot)
        putNullable("grindSetting", grindSetting)
        putNullable("coffeeDoseG", coffeeDoseG)
        putNullable("brewWaterMl", brewWaterMl)
        putNullable("bypassWaterMl", bypassWaterMl)
        putNullable("waterTempC", waterTempC)
        putNullable("waterCurveJson", waterCurveJson)
        put("notes", notes)
        put("brewedAt", brewedAt)
        putNullable("brewDurationSeconds", brewDurationSeconds)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        putNullable("totalWaterMl", totalWaterMl)
        putNullable("brewRatio", brewRatio)
    }

    private fun SubjectiveEvaluationEntity.toJson(): JSONObject = JSONObject().apply {
        put("recordId", recordId)
        putNullable("aroma", aroma)
        putNullable("acidity", acidity)
        putNullable("sweetness", sweetness)
        putNullable("bitterness", bitterness)
        putNullable("body", body)
        putNullable("aftertaste", aftertaste)
        putNullable("overall", overall)
        put("notes", notes)
    }

    private fun JSONObject.toBackupPreferences(): BackupPreferences {
        return BackupPreferences(
            currentArchiveId = optNullableLong("currentArchiveId"),
            autoRestoreDraft = optBoolean("autoRestoreDraft", true),
            showInsightConfidence = optBoolean("showInsightConfidence", true),
            defaultAnalysisTimeRange = optString("defaultAnalysisTimeRange", AnalysisTimeRange.LAST_90_DAYS.name),
            defaultBeanProfileId = optNullableLong("defaultBeanProfileId"),
            defaultGrinderProfileId = optNullableLong("defaultGrinderProfileId"),
            showLearnInDock = optBoolean("showLearnInDock", false),
        )
    }

    private fun JSONObject.toBackupArchive(): BackupArchive {
        return BackupArchive(
            id = getLong("id"),
            name = getString("name"),
            createdAt = optLong("createdAt"),
            updatedAt = optLong("updatedAt"),
            beans = getJSONArray("beans").mapObjects { it.toBeanProfileEntity() },
            grinders = getJSONArray("grinders").mapObjects { it.toGrinderProfileEntity() },
            recipes = getJSONArray("recipes").mapObjects { it.toRecipeTemplateEntity() },
            flavorTags = getJSONArray("flavorTags").mapObjects { it.toFlavorTagEntity() },
            records = getJSONArray("records").mapObjects { it.toBackupRecord() },
        )
    }

    private fun JSONObject.toBackupRecord(): BackupRecord {
        return BackupRecord(
            record = getJSONObject("record").toBrewRecordEntity(),
            subjectiveEvaluation = optJSONObject("subjectiveEvaluation")?.toSubjectiveEvaluationEntity(),
            flavorTagIds = getJSONArray("flavorTagIds").mapLongs(),
        )
    }

    private fun JSONObject.toBeanProfileEntity(): BeanProfileEntity {
        return BeanProfileEntity(
            id = getLong("id"),
            archiveId = getLong("archiveId"),
            name = getString("name"),
            roaster = getString("roaster"),
            origin = getString("origin"),
            processValue = getInt("processValue"),
            variety = getString("variety"),
            roastLevelValue = getInt("roastLevelValue"),
            roastDateEpochDay = optNullableLong("roastDateEpochDay"),
            initialStockG = optNullableDouble("initialStockG"),
            notes = getString("notes"),
            createdAt = getLong("createdAt"),
        )
    }

    private fun JSONObject.toGrinderProfileEntity(): GrinderProfileEntity {
        return GrinderProfileEntity(
            id = getLong("id"),
            archiveId = getLong("archiveId"),
            name = getString("name"),
            minSetting = getDouble("minSetting"),
            maxSetting = getDouble("maxSetting"),
            stepSize = getDouble("stepSize"),
            unitLabel = getString("unitLabel"),
            notes = getString("notes"),
            createdAt = getLong("createdAt"),
        )
    }

    private fun JSONObject.toRecipeTemplateEntity(): RecipeTemplateEntity {
        return RecipeTemplateEntity(
            id = getLong("id"),
            archiveId = getLong("archiveId"),
            name = getString("name"),
            brewMethodCode = optNullableString("brewMethodCode"),
            beanId = optNullableLong("beanId"),
            beanNameSnapshot = optNullableString("beanNameSnapshot"),
            grinderId = optNullableLong("grinderId"),
            grinderNameSnapshot = optNullableString("grinderNameSnapshot"),
            grindSetting = optNullableDouble("grindSetting"),
            coffeeDoseG = optNullableDouble("coffeeDoseG"),
            brewWaterMl = optNullableDouble("brewWaterMl"),
            bypassWaterMl = optNullableDouble("bypassWaterMl"),
            waterTempC = optNullableDouble("waterTempC"),
            waterCurveJson = optNullableString("waterCurveJson"),
            notes = getString("notes"),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
        )
    }

    private fun JSONObject.toFlavorTagEntity(): FlavorTagEntity {
        return FlavorTagEntity(
            id = getLong("id"),
            archiveId = getLong("archiveId"),
            name = getString("name"),
            isPreset = getBoolean("isPreset"),
        )
    }

    private fun JSONObject.toBrewRecordEntity(): BrewRecordEntity {
        return BrewRecordEntity(
            id = getLong("id"),
            archiveId = getLong("archiveId"),
            status = getString("status"),
            brewMethodCode = optNullableString("brewMethodCode"),
            beanId = optNullableLong("beanId"),
            beanNameSnapshot = optNullableString("beanNameSnapshot"),
            beanRoastLevelSnapshotValue = optNullableInt("beanRoastLevelSnapshotValue"),
            beanProcessMethodSnapshotValue = optNullableInt("beanProcessMethodSnapshotValue"),
            recipeTemplateId = optNullableLong("recipeTemplateId"),
            recipeNameSnapshot = optNullableString("recipeNameSnapshot"),
            grinderId = optNullableLong("grinderId"),
            grinderNameSnapshot = optNullableString("grinderNameSnapshot"),
            grindSetting = optNullableDouble("grindSetting"),
            coffeeDoseG = optNullableDouble("coffeeDoseG"),
            brewWaterMl = optNullableDouble("brewWaterMl"),
            bypassWaterMl = optNullableDouble("bypassWaterMl"),
            waterTempC = optNullableDouble("waterTempC"),
            waterCurveJson = optNullableString("waterCurveJson"),
            notes = getString("notes"),
            brewedAt = getLong("brewedAt"),
            brewDurationSeconds = optNullableInt("brewDurationSeconds"),
            createdAt = getLong("createdAt"),
            updatedAt = getLong("updatedAt"),
            totalWaterMl = optNullableDouble("totalWaterMl"),
            brewRatio = optNullableDouble("brewRatio"),
        )
    }

    private fun JSONObject.toSubjectiveEvaluationEntity(): SubjectiveEvaluationEntity {
        return SubjectiveEvaluationEntity(
            recordId = getLong("recordId"),
            aroma = optNullableInt("aroma"),
            acidity = optNullableInt("acidity"),
            sweetness = optNullableInt("sweetness"),
            bitterness = optNullableInt("bitterness"),
            body = optNullableInt("body"),
            aftertaste = optNullableInt("aftertaste"),
            overall = optNullableInt("overall"),
            notes = getString("notes"),
        )
    }
}

internal fun uniqueArchiveName(baseName: String, existingNames: Set<String>): String {
    if (baseName !in existingNames) return baseName
    var counter = 2
    while (true) {
        val candidate = "$baseName ($counter)"
        if (candidate !in existingNames) return candidate
        counter++
    }
}

internal fun csvCell(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (escaped.any { it == ',' || it == '\n' || it == '\r' || it == '"' }) {
        "\"$escaped\""
    } else {
        escaped
    }
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key)

private fun JSONObject.optNullableLong(key: String): Long? =
    if (isNull(key)) null else getLong(key)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (isNull(key)) null else getInt(key)

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (isNull(key)) null else getDouble(key)

private fun JSONArray.mapLongs(): List<Long> = buildList {
    for (index in 0 until length()) {
        add(getLong(index))
    }
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = buildList {
    for (index in 0 until length()) {
        add(transform(getJSONObject(index)))
    }
}
