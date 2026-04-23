package com.qoffee.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.Experiment
import com.qoffee.core.model.ExperimentProject
import com.qoffee.core.model.ExperimentProjectConfigJsonCodec
import com.qoffee.core.model.ExperimentProjectDraft
import com.qoffee.core.model.ExperimentRun
import com.qoffee.core.model.ExperimentRunSummary
import com.qoffee.core.model.ExperimentStatus
import com.qoffee.core.model.ExperimentVariableType
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.PracticeBlock
import com.qoffee.core.model.RecordPrefillSource
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecipeVersion
import com.qoffee.core.model.toObjectiveSnapshot
import com.qoffee.data.local.CollectionEntity
import com.qoffee.data.local.CollectionItemLinkEntity
import com.qoffee.data.local.QoffeeDatabase
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.RecordRepository
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.roundToInt

private const val COLLECTION_TYPE_EXPERIMENT_PROJECT = "experiment_project"
private const val COLLECTION_ITEM_RECORD = "record"

@Singleton
class ExperimentRepositoryFacade @Inject constructor(
    private val delegate: ExperimentRepositoryImpl,
    private val catalogRepository: CatalogRepository,
    private val recordRepository: RecordRepository,
    private val dataStore: DataStore<Preferences>,
    private val database: QoffeeDatabase,
    private val timeProvider: TimeProvider,
) : ExperimentRepository {

    private val collectionDao get() = database.collectionDao()
    private val collectionItemLinkDao get() = database.collectionItemLinkDao()

    override fun observePracticeBlocks(): Flow<List<PracticeBlock>> = delegate.observePracticeBlocks()

    override fun observeRecipeVersions(): Flow<List<RecipeVersion>> = delegate.observeRecipeVersions()

    override fun observeExperiments(): Flow<List<Experiment>> = observeProjects().map { projects ->
        projects.map { project ->
            Experiment(
                id = "project-${project.id}",
                title = project.title,
                hypothesis = project.hypothesis,
                brewMethod = project.baseline.brewMethod,
                comparedParameter = project.variables.firstOrNull()?.type.toNumericParameter(),
                status = when {
                    project.runs.isEmpty() -> ExperimentStatus.PLANNED
                    project.runs.count { it.score != null } >= project.cells.size.coerceAtLeast(1) -> ExperimentStatus.REVIEW
                    else -> ExperimentStatus.ACTIVE
                },
            )
        }
    }

    override fun observeExperimentRuns(): Flow<List<ExperimentRun>> = observeProjects().map { projects ->
        projects.flatMap { project ->
            project.runs.map { run ->
                ExperimentRun(
                    id = "project-${project.id}-run-${run.recordId}",
                    experimentId = "project-${project.id}",
                    label = run.recordTitle,
                    recordId = run.recordId,
                    score = run.score,
                    deltaSummary = run.deltaSummary,
                    notes = if (run.isOffPlan) "偏离计划" else "",
                )
            }
        }
    }

    override fun observeBeanInventory(): Flow<List<BeanInventory>> =
        combine(
            catalogRepository.observeBeanProfiles(),
            recordRepository.observeRecords(AnalysisFilter(timeRange = AnalysisTimeRange.ALL)),
        ) { beans, records ->
            val completedUsage = records
                .filter { it.status == RecordStatus.COMPLETED }
                .mapNotNull { record ->
                    val beanId = record.beanProfileId ?: return@mapNotNull null
                    beanId to (record.coffeeDoseG ?: 0.0)
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
            val today = Instant.ofEpochMilli(timeProvider.nowMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            beans
                .filter { it.initialStockG != null }
                .sortedWith(
                    compareByDescending<com.qoffee.core.model.BeanProfile> { it.roastDateEpochDay ?: Long.MIN_VALUE }
                        .thenByDescending { it.createdAt },
                )
                .map { bean ->
                    val initialStock = bean.initialStockG ?: 0.0
                    val usedStock = completedUsage[bean.id].orEmpty().sum()
                    val remainingStock = (initialStock - usedStock).coerceAtLeast(0.0)
                    val ratio = if (initialStock > 0.0) {
                        (remainingStock / initialStock).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    BeanInventory(
                        beanId = bean.id,
                        beanName = bean.name,
                        roastDateEpochDay = bean.roastDateEpochDay,
                        roastAgeLabel = bean.roastDateEpochDay?.let { epochDay ->
                            formatRoastAge(LocalDate.ofEpochDay(epochDay), today)
                        } ?: "未记录烘焙日期",
                        initialStockG = initialStock,
                        usedStockG = usedStock,
                        remainingStockG = remainingStock,
                        remainingRatio = ratio,
                        remainingPercentage = (ratio * 100).roundToInt(),
                        id = "inventory-${bean.id}",
                        gramsRemaining = remainingStock.roundToInt(),
                    )
                }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProjects(): Flow<List<ExperimentProject>> {
        return currentArchiveIdFlow().flatMapLatest { archiveId ->
            archiveId?.let { safeArchiveId ->
                combine(
                    collectionDao.observeByArchiveAndType(safeArchiveId, COLLECTION_TYPE_EXPERIMENT_PROJECT),
                    collectionItemLinkDao.observeByArchive(safeArchiveId),
                    recordRepository.observeRecords(AnalysisFilter(timeRange = AnalysisTimeRange.ALL)),
                ) { collections, links, records ->
                    collections.mapNotNull { collection ->
                        decodeProject(
                            collection = collection,
                            links = links.filter { it.collectionId == collection.id && it.itemType == COLLECTION_ITEM_RECORD },
                            records = records,
                        )
                    }
                }
            } ?: flowOf(emptyList())
        }
    }

    override fun observeProject(projectId: Long): Flow<ExperimentProject?> {
        return combine(
            collectionDao.observeById(projectId),
            collectionItemLinkDao.observeForCollection(projectId),
            recordRepository.observeRecords(AnalysisFilter(timeRange = AnalysisTimeRange.ALL)),
        ) { collection, links, records ->
            collection?.let {
                decodeProject(
                    collection = it,
                    links = links.filter { link -> link.itemType == COLLECTION_ITEM_RECORD },
                    records = records,
                )
            }
        }
    }

    override suspend fun createProject(draft: ExperimentProjectDraft): Long {
        val archiveId = requireCurrentArchiveId()
        val now = timeProvider.nowMillis()
        val project = ExperimentProjectConfigJsonCodec.fromDraft(
            archiveId = archiveId,
            createdAt = now,
            updatedAt = now,
            draft = draft,
        )
        return collectionDao.insert(
            CollectionEntity(
                archiveId = archiveId,
                typeCode = COLLECTION_TYPE_EXPERIMENT_PROJECT,
                title = project.title,
                description = project.description,
                hypothesis = project.hypothesis,
                configJson = ExperimentProjectConfigJsonCodec.encode(project),
                notes = "",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun createOrOpenCellDraft(projectId: Long, cellId: String): Long {
        val collection = checkNotNull(collectionDao.getById(projectId)) { "实验项目不存在。" }
        val project = checkNotNull(
            ExperimentProjectConfigJsonCodec.decode(
                id = collection.id,
                archiveId = collection.archiveId,
                title = collection.title,
                description = collection.description,
                hypothesis = collection.hypothesis,
                configJson = collection.configJson,
                createdAt = collection.createdAt,
                updatedAt = collection.updatedAt,
                runs = emptyList(),
            ),
        ) { "实验项目配置无效。" }
        val cell = checkNotNull(project.cells.firstOrNull { it.id == cellId }) { "实验格子不存在。" }

        val existingDraftId = run {
            val links = collectionItemLinkDao.getForCollection(projectId)
            var draftId: Long? = null
            links.forEach { link ->
                if (draftId == null && link.itemType == COLLECTION_ITEM_RECORD && link.groupLabel == cellId) {
                    val record = recordRepository.getRecord(link.itemId)
                    if (record?.status == RecordStatus.DRAFT) {
                        draftId = record.id
                    }
                }
            }
            draftId
        }
        if (existingDraftId != null) {
            return existingDraftId
        }

        val draftId = when {
            project.baseRecordId != null -> {
                recordRepository.createDraft(
                    prefillSource = RecordPrefillSource.Record(project.baseRecordId),
                    replacePolicy = com.qoffee.core.model.DraftReplacePolicy.REPLACE_CURRENT,
                )
            }

            project.baseRecipeId != null -> {
                recordRepository.createDraft(
                    prefillSource = RecordPrefillSource.Recipe(project.baseRecipeId),
                    replacePolicy = com.qoffee.core.model.DraftReplacePolicy.REPLACE_CURRENT,
                )
            }

            else -> {
                recordRepository.createDraft(
                    prefillSource = RecordPrefillSource.Blank,
                    replacePolicy = com.qoffee.core.model.DraftReplacePolicy.REPLACE_CURRENT,
                )
            }
        }
        recordRepository.updateObjective(
            recordId = draftId,
            update = cell.overrides.toDraftUpdate(),
        )
        val existingLinks = collectionItemLinkDao.getForCollection(projectId)
        collectionItemLinkDao.upsert(
            CollectionItemLinkEntity(
                archiveId = collection.archiveId,
                collectionId = projectId,
                itemType = COLLECTION_ITEM_RECORD,
                itemId = draftId,
                sortOrder = existingLinks.size,
                groupLabel = cellId,
                roleText = "experiment_cell",
            ),
        )
        return draftId
    }

    private suspend fun requireCurrentArchiveId(): Long {
        return checkNotNull(dataStore.data.first()[PreferenceKeys.CURRENT_ARCHIVE_ID]) { "当前没有活动存档。" }
    }

    private fun currentArchiveIdFlow(): Flow<Long?> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.CURRENT_ARCHIVE_ID]
    }

    private fun decodeProject(
        collection: CollectionEntity,
        links: List<CollectionItemLinkEntity>,
        records: List<com.qoffee.core.model.CoffeeRecord>,
    ): ExperimentProject? {
        val recordMap = records.associateBy { it.id }
        val project = ExperimentProjectConfigJsonCodec.decode(
            id = collection.id,
            archiveId = collection.archiveId,
            title = collection.title,
            description = collection.description,
            hypothesis = collection.hypothesis,
            configJson = collection.configJson,
            createdAt = collection.createdAt,
            updatedAt = collection.updatedAt,
            runs = links.mapNotNull { link ->
                val record = recordMap[link.itemId] ?: return@mapNotNull null
                ExperimentRunSummary(
                    recordId = record.id,
                    cellId = link.groupLabel,
                    recordTitle = record.recipeNameSnapshot ?: record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "实验记录",
                    score = record.subjectiveEvaluation?.overall,
                    deltaSummary = buildDeltaSummary(record),
                )
            },
        ) ?: return null

        val enrichedRuns = project.runs.map { run ->
            val record = recordMap[run.recordId] ?: return@map run
            val cell = project.cells.firstOrNull { it.id == run.cellId }
            run.copy(
                isOffPlan = cell?.let { isOffPlan(record, it) } ?: false,
            )
        }
        return project.copy(runs = enrichedRuns)
    }

    private fun isOffPlan(
        record: com.qoffee.core.model.CoffeeRecord,
        cell: com.qoffee.core.model.ExperimentCellPlan,
    ): Boolean {
        val expected = cell.overrides
        return listOf(
            record.beanProfileId != expected.beanProfileId,
            record.grinderProfileId != expected.grinderProfileId,
            expected.grindSetting?.let { abs((record.grindSetting ?: Double.NaN) - it) > 0.001 } == true,
            expected.coffeeDoseG?.let { abs((record.coffeeDoseG ?: Double.NaN) - it) > 0.001 } == true,
            expected.brewWaterMl?.let { abs((record.brewWaterMl ?: Double.NaN) - it) > 0.001 } == true,
            expected.waterTempC?.let { abs((record.waterTempC ?: Double.NaN) - it) > 0.001 } == true,
        ).any { it }
    }

    private fun buildDeltaSummary(record: com.qoffee.core.model.CoffeeRecord): String {
        return buildString {
            append(record.brewMethod?.displayName ?: "未指定方式")
            record.grindSetting?.let {
                append(" · 研磨 ")
                append(it)
            }
            record.waterTempC?.let {
                append(" · 水温 ")
                append(it.roundToInt())
                append("°C")
            }
            record.subjectiveEvaluation?.overall?.let {
                append(" · ")
                append(it)
                append("/5")
            }
        }
    }

    private fun formatRoastAge(roastDate: LocalDate, today: LocalDate): String {
        if (roastDate.isAfter(today)) {
            return "0天"
        }
        val period = Period.between(roastDate, today)
        return when {
            period.years >= 1 -> "${period.years}年（${period.months}月）${period.days}天"
            period.months >= 1 -> "${period.months}月${period.days}天"
            else -> "${period.days}天"
        }
    }

    private fun ExperimentVariableType?.toNumericParameter(): NumericParameter? {
        return when (this) {
            ExperimentVariableType.GRIND_SETTING -> NumericParameter.GRIND_SETTING
            ExperimentVariableType.WATER_TEMP -> NumericParameter.WATER_TEMP
            ExperimentVariableType.COFFEE_DOSE -> null
            ExperimentVariableType.BREW_WATER -> NumericParameter.TOTAL_WATER
            ExperimentVariableType.BEAN -> null
            ExperimentVariableType.GRINDER -> NumericParameter.NORMALIZED_GRIND
            null -> null
        }
    }
}
