package com.qoffee.feature.records

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.core.model.AiCoachAction
import com.qoffee.core.model.AiCoachSuggestion
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.BeanInventoryPriority
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewSession
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.PracticeBlock
import com.qoffee.core.model.RecordDraftLaunchBehavior
import com.qoffee.core.model.RecordPrefillSource
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.SubjectiveDimension
import com.qoffee.core.model.buildBeanInventoryPriorities
import com.qoffee.core.model.resolveRecordDraftLaunchBehavior
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.AiCoachRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.domain.repository.SessionRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.BeanInventoryCard
import com.qoffee.ui.components.FeatureEntryCard
import com.qoffee.ui.components.HeroActionPanel
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.RecordSourceRail
import com.qoffee.ui.components.RecordSourceRailItem
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.navigation.RecordEditorEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class RecordsHubUiState(
    val filter: AnalysisFilter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
    val activeSession: BrewSession? = null,
    val practiceBlocks: List<PracticeBlock> = emptyList(),
    val inventory: List<BeanInventory> = emptyList(),
    val activeDraft: CoffeeRecord? = null,
    val recentRecords: List<CoffeeRecord> = emptyList(),
    val subjectiveRecords: List<CoffeeRecord> = emptyList(),
    val timelineGroups: List<RecordTimelineGroup> = emptyList(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val recipes: List<RecipeTemplate> = emptyList(),
    val coachRecommendations: List<BrewCoachRecommendation> = emptyList(),
    val aiCoachSuggestions: List<AiCoachSuggestion> = emptyList(),
)

private data class RecordsStageOne(
    val filter: AnalysisFilter,
    val records: List<CoffeeRecord>,
    val recentRecords: List<CoffeeRecord>,
)

private data class RecordsStageTwo(
    val filter: AnalysisFilter,
    val records: List<CoffeeRecord>,
    val recentRecords: List<CoffeeRecord>,
    val beans: List<BeanProfile>,
)

private data class RecordsStageThree(
    val filter: AnalysisFilter,
    val records: List<CoffeeRecord>,
    val recentRecords: List<CoffeeRecord>,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
)

@HiltViewModel
class RecordsViewModel @Inject constructor(
    recordRepository: RecordRepository,
    catalogRepository: CatalogRepository,
    recipeRepository: RecipeRepository,
    sessionRepository: SessionRepository,
    experimentRepository: ExperimentRepository,
    aiCoachRepository: AiCoachRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recordsFlow = filter.flatMapLatest { current ->
        recordRepository.observeRecords(current)
    }

    private val baseFlow = filter
        .combine(recordsFlow) { currentFilter, records ->
            currentFilter to records
        }
        .combine(recordRepository.observeRecentRecords(limit = 4)) { filterAndRecords, recentRecords ->
            RecordsStageOne(
                filter = filterAndRecords.first,
                records = filterAndRecords.second,
                recentRecords = recentRecords,
            )
        }
        .combine(catalogRepository.observeBeanProfiles()) { stageOne, beans ->
            RecordsStageTwo(
                filter = stageOne.filter,
                records = stageOne.records,
                recentRecords = stageOne.recentRecords,
                beans = beans,
            )
        }
        .combine(catalogRepository.observeGrinderProfiles()) { stageTwo, grinders ->
            RecordsStageThree(
                filter = stageTwo.filter,
                records = stageTwo.records,
                recentRecords = stageTwo.recentRecords,
                beans = stageTwo.beans,
                grinders = grinders,
            )
        }

    val uiState: StateFlow<RecordsHubUiState> = baseFlow
        .combine(recipeRepository.observeRecipes()) { stageThree, recipes ->
            stageThree to recipes
        }
        .combine(sessionRepository.observeActiveSession()) { stageAndRecipes, activeSession ->
            Triple(stageAndRecipes.first, stageAndRecipes.second, activeSession)
        }
        .combine(experimentRepository.observePracticeBlocks()) { stageRecipesAndSession, practiceBlocks ->
            stageRecipesAndSession to practiceBlocks
        }
        .combine(experimentRepository.observeBeanInventory()) { stageAndPracticeBlocks, inventory ->
            val stageThree = stageAndPracticeBlocks.first.first
            val recipes = stageAndPracticeBlocks.first.second
            val activeSession = stageAndPracticeBlocks.first.third
            val practiceBlocks = stageAndPracticeBlocks.second
            val completedRecords = stageThree.records.filter { it.status == RecordStatus.COMPLETED }
            RecordsHubUiState(
                filter = stageThree.filter,
                activeSession = activeSession,
                practiceBlocks = practiceBlocks,
                inventory = inventory,
                activeDraft = stageThree.records.firstOrNull { it.status == RecordStatus.DRAFT },
                recentRecords = stageThree.recentRecords,
                subjectiveRecords = completedRecords
                    .filter { it.subjectiveEvaluation?.isEmpty() == false }
                    .sortedByDescending { it.brewedAt },
                timelineGroups = buildRecordTimelineGroups(completedRecords),
                beans = stageThree.beans,
                grinders = stageThree.grinders,
                recipes = recipes,
                coachRecommendations = buildBrewCoachRecommendations(
                    records = stageThree.records,
                    activeDraft = stageThree.records.firstOrNull { it.status == RecordStatus.DRAFT },
                    inventory = inventory,
                    recipes = recipes,
                ),
            )
        }
        .combine(aiCoachRepository.observeSuggestions()) { state, suggestions ->
            state.copy(aiCoachSuggestions = suggestions)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordsHubUiState(),
        )

    fun updateTimeRange(range: AnalysisTimeRange) {
        filter.value = filter.value.copy(timeRange = range)
    }

    fun updateMethod(method: BrewMethod?) {
        filter.value = filter.value.copy(brewMethod = method)
    }

    fun updateBean(beanId: Long?) {
        filter.value = filter.value.copy(beanId = beanId)
    }

    fun updateGrinder(grinderId: Long?) {
        filter.value = filter.value.copy(grinderId = grinderId)
    }
}

@Composable
fun RecordsRoute(
    paddingValues: PaddingValues,
    currentArchive: ArchiveSummary?,
    isReadOnlyArchive: Boolean,
    onOpenDetail: (Long) -> Unit,
    onOpenSession: (BrewMethod, Long?) -> Unit,
    onOpenEditor: (Long?, Long?, RecordEditorEntry, Long?, Long?) -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenExperiments: () -> Unit,
    onOpenGuides: () -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecordsScreen(
        paddingValues = paddingValues,
        currentArchive = currentArchive,
        uiState = uiState,
        isReadOnlyArchive = isReadOnlyArchive,
        onOpenDetail = onOpenDetail,
        onOpenSession = onOpenSession,
        onOpenEditor = onOpenEditor,
        onOpenAnalysis = onOpenAnalysis,
        onOpenExperiments = onOpenExperiments,
        onOpenGuides = onOpenGuides,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordsScreen(
    paddingValues: PaddingValues,
    currentArchive: ArchiveSummary?,
    uiState: RecordsHubUiState,
    isReadOnlyArchive: Boolean,
    onOpenDetail: (Long) -> Unit,
    onOpenSession: (BrewMethod, Long?) -> Unit,
    onOpenEditor: (Long?, Long?, RecordEditorEntry, Long?, Long?) -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenExperiments: () -> Unit,
    onOpenGuides: () -> Unit,
) {
    var pendingAction by remember { mutableStateOf<PendingDraftAction?>(null) }

    fun openDraftForSource(source: RecordPrefillSource) {
        when (source) {
            RecordPrefillSource.Blank -> onOpenEditor(null, null, RecordEditorEntry.NEW, null, null)
            RecordPrefillSource.Draft -> uiState.activeDraft?.let {
                onOpenEditor(it.id, null, RecordEditorEntry.DRAFT, null, null)
            }
            is RecordPrefillSource.Recipe -> onOpenEditor(null, null, RecordEditorEntry.RECIPE, source.recipeId, null)
            is RecordPrefillSource.Record -> onOpenEditor(null, source.recordId, RecordEditorEntry.DUPLICATE, null, null)
            is RecordPrefillSource.Bean -> onOpenEditor(null, null, RecordEditorEntry.BEAN, null, source.beanId)
        }
    }

    fun handlePrefillRequest(source: RecordPrefillSource) {
        when (resolveRecordDraftLaunchBehavior(uiState.activeDraft, source)) {
            RecordDraftLaunchBehavior.CREATE_NEW -> openDraftForSource(source)
            RecordDraftLaunchBehavior.CONTINUE_CURRENT -> openDraftForSource(RecordPrefillSource.Draft)
            RecordDraftLaunchBehavior.CONFIRM_REPLACE -> {
                pendingAction = when (source) {
                    RecordPrefillSource.Blank -> PendingDraftAction.NewRecord
                    is RecordPrefillSource.Recipe -> PendingDraftAction.Recipe(source.recipeId)
                    is RecordPrefillSource.Bean -> PendingDraftAction.Bean(source.beanId)
                    RecordPrefillSource.Draft -> PendingDraftAction.ResumeDraft
                    is RecordPrefillSource.Record -> PendingDraftAction.Duplicate(source.recordId)
                }
            }
        }
    }

    fun handleCoachAction(action: BrewCoachAction) {
        when (action) {
            BrewCoachAction.OpenAnalysis -> onOpenAnalysis()
            is BrewCoachAction.OpenDetail -> onOpenDetail(action.recordId)
            BrewCoachAction.StartBlank -> handlePrefillRequest(RecordPrefillSource.Blank)
            is BrewCoachAction.StartBean -> handlePrefillRequest(RecordPrefillSource.Bean(action.beanId))
            is BrewCoachAction.StartRecipe -> handlePrefillRequest(RecordPrefillSource.Recipe(action.recipeId))
            is BrewCoachAction.DuplicateRecord -> handlePrefillRequest(RecordPrefillSource.Record(action.recordId))
            is BrewCoachAction.ResumeDraft -> onOpenEditor(action.recordId, null, RecordEditorEntry.DRAFT, null, null)
        }
    }

    fun handleAiCoachAction(action: AiCoachAction) {
        when (action) {
            AiCoachAction.OpenAnalysis -> onOpenAnalysis()
            is AiCoachAction.OpenRecord -> onOpenDetail(action.recordId)
            is AiCoachAction.DuplicateRecord -> handlePrefillRequest(RecordPrefillSource.Record(action.recordId))
        }
    }

    fun handleWorkbenchAction(action: WorkbenchAction) {
        when (action) {
            WorkbenchAction.StartBlank -> handlePrefillRequest(RecordPrefillSource.Blank)
            WorkbenchAction.OpenAnalysis -> onOpenAnalysis()
            is WorkbenchAction.ResumeDraft -> onOpenEditor(action.recordId, null, RecordEditorEntry.DRAFT, null, null)
            is WorkbenchAction.StartBean -> handlePrefillRequest(RecordPrefillSource.Bean(action.beanId))
            is WorkbenchAction.StartRecipe -> handlePrefillRequest(RecordPrefillSource.Recipe(action.recipeId))
            is WorkbenchAction.DuplicateRecord -> handlePrefillRequest(RecordPrefillSource.Record(action.recordId))
            is WorkbenchAction.OpenRecord -> onOpenDetail(action.recordId)
        }
    }

    val completedRecords = remember(uiState.recentRecords, uiState.timelineGroups) {
        uiState.timelineGroups.sumOf { group ->
            group.items.count { it.record.status == RecordStatus.COMPLETED }
        }.takeIf { it > 0 } ?: uiState.recentRecords.count { it.status == RecordStatus.COMPLETED }
    }
    val scoredRecords = remember(uiState.timelineGroups, uiState.recentRecords) {
        uiState.timelineGroups.sumOf { group ->
            group.items.count { it.record.subjectiveEvaluation?.overall != null }
        }.takeIf { it > 0 } ?: uiState.recentRecords.count { it.subjectiveEvaluation?.overall != null }
    }
    val stopwatchRecord = remember(uiState.recentRecords) {
        uiState.recentRecords.firstOrNull { it.brewDurationSeconds != null } ?: uiState.recentRecords.firstOrNull()
    }
    var subjectiveFilter by remember { mutableStateOf<Map<SubjectiveDimension, Int>>(emptyMap()) }
    var activeSubjectiveDimension by remember { mutableStateOf(SubjectiveDimension.SWEETNESS) }
    var subjectivePage by remember { mutableStateOf(0) }
    val subjectiveDimensions = remember {
        listOf(
            SubjectiveDimension.AROMA,
            SubjectiveDimension.ACIDITY,
            SubjectiveDimension.SWEETNESS,
            SubjectiveDimension.BITTERNESS,
            SubjectiveDimension.BODY,
            SubjectiveDimension.AFTERTASTE,
        )
    }
    val subjectiveMatches = remember(uiState.subjectiveRecords, subjectiveFilter) {
        uiState.subjectiveRecords.filter { record ->
            subjectiveFilter.all { (dimension, score) ->
                dimension.extract(record.subjectiveEvaluation) == score
            }
        }
    }
    val subjectivePageSize = 4
    val subjectivePageCount = ((subjectiveMatches.size + subjectivePageSize - 1) / subjectivePageSize).coerceAtLeast(1)
    val safeSubjectivePage = subjectivePage.coerceIn(0, subjectivePageCount - 1)
    val subjectivePageItems = remember(subjectiveMatches, safeSubjectivePage) {
        subjectiveMatches.drop(safeSubjectivePage * subjectivePageSize).take(subjectivePageSize)
    }
    val inventoryPriorities = remember(uiState.inventory) {
        buildBeanInventoryPriorities(
            inventory = uiState.inventory,
            todayEpochDay = LocalDate.now().toEpochDay(),
        )
    }
    val workbenchHero = remember(
        uiState.activeDraft,
        uiState.inventory,
        uiState.recipes,
        uiState.recentRecords,
        uiState.subjectiveRecords,
    ) {
        buildRecordWorkbenchHero(
            activeDraft = uiState.activeDraft,
            inventory = uiState.inventory,
            recipes = uiState.recipes,
            recentRecords = uiState.recentRecords,
            scoredRecords = uiState.subjectiveRecords,
        )
    }
    val sourceItems = remember(
        uiState.activeDraft,
        uiState.inventory,
        uiState.recipes,
        uiState.recentRecords,
    ) {
        buildWorkbenchSourceItems(
            activeDraft = uiState.activeDraft,
            inventory = uiState.inventory,
            recipes = uiState.recipes,
            recentRecords = uiState.recentRecords,
        )
    }
    val sourceRailItems = remember(sourceItems) {
        sourceItems.mapIndexed { index, item ->
            RecordSourceRailItem(
                title = item.title,
                subtitle = item.subtitle,
                badge = item.badge,
                key = "$index:${item.title}:${item.badge}",
            )
        }
    }
    val sourceActionsByKey = remember(sourceItems, sourceRailItems) {
        sourceRailItems.mapIndexed { index, item -> item.key to sourceItems[index].action }.toMap()
    }
    val pendingFeedback = remember(uiState.recentRecords) {
        buildPendingFeedbackItems(uiState.recentRecords)
    }

    DashboardPage(
        paddingValues = paddingValues,
        testTag = QoffeeTestTags.BREW_SCREEN,
    ) {
        PageHeader(
            title = "记录工作台",
            subtitle = currentArchive?.archive?.name,
            eyebrow = "QOFFEE / RECORD LOOP",
        )

        HeroActionPanel(
            eyebrow = workbenchHero.eyebrow,
            title = workbenchHero.title,
            subtitle = workbenchHero.subtitle,
            primaryLabel = workbenchHero.primaryLabel,
            onPrimaryClick = { handleWorkbenchAction(workbenchHero.primaryAction) },
            secondaryActions = workbenchHero.secondaryActions.map { secondary ->
                secondary.label to { handleWorkbenchAction(secondary.action) }
            },
            enabled = !isReadOnlyArchive || !workbenchHero.primaryAction.requiresWritableArchive(),
            modifier = Modifier.testTag(QoffeeTestTags.BREW_HERO_ACTION),
        )

        SectionCard(
            title = "快速开始",
            subtitle = "从草稿、豆子、配方或历史记录直接生成下一杯。",
            modifier = Modifier.testTag(QoffeeTestTags.BREW_SOURCE_RAIL),
        ) {
            if (sourceRailItems.isEmpty()) {
                EmptyStateCard(
                    title = "先添加一条记录",
                    subtitle = "完成第一杯后，这里会出现可复用的豆子、配方和历史记录。",
                )
            } else {
                RecordSourceRail(
                    items = sourceRailItems,
                    onClick = { item ->
                        sourceActionsByKey[item.key]?.let(::handleWorkbenchAction)
                    },
                    enabled = !isReadOnlyArchive,
                )
            }
        }

        if (pendingFeedback.isNotEmpty()) {
            SectionCard(
                title = "待补感受",
                subtitle = "先补总评，就能让这杯进入复盘样本。",
                modifier = Modifier.testTag(QoffeeTestTags.BREW_PENDING_FEEDBACK),
            ) {
                pendingFeedback.forEach { item ->
                    FeatureEntryCard(
                        title = item.title,
                        hint = item.subtitle,
                        icon = Icons.Outlined.Add,
                        onClick = { onOpenEditor(item.recordId, null, RecordEditorEntry.DRAFT, null, null) },
                        enabled = !isReadOnlyArchive,
                    )
                }
            }
        }

        uiState.activeSession?.let { session ->
            SectionCard(
                title = "继续会话",
            ) {
                FeatureEntryCard(
                    title = session.title,
                    hint = session.currentStage?.title ?: "继续当前练习",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = { onOpenSession(session.method, session.sourceGuideId) },
                    badge = "SESSION",
                    selected = true,
                )
            }
        }

        SectionCard(
            title = "快速记录",
            subtitle = "优先记录，再把好用参数沉淀成配方。",
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val singleColumn = maxWidth < 420.dp
                val cardModifier = if (singleColumn) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth(0.48f)
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = if (singleColumn) 1 else 2,
                ) {
                    FeatureEntryCard(
                        title = "添加记录",
                        hint = "空白开始一杯",
                        icon = Icons.Outlined.Add,
                        onClick = { handlePrefillRequest(RecordPrefillSource.Blank) },
                        modifier = cardModifier,
                        badge = "NEW",
                        selected = true,
                        enabled = !isReadOnlyArchive,
                        testTag = QoffeeTestTags.BREW_ADD_RECORD,
                    )
                    FeatureEntryCard(
                        title = "继续草稿",
                        hint = if (uiState.activeDraft != null) "未完成记录" else "暂无草稿",
                        icon = Icons.Outlined.Restore,
                        onClick = { handlePrefillRequest(RecordPrefillSource.Draft) },
                        modifier = cardModifier,
                        badge = "DRAFT",
                        enabled = !isReadOnlyArchive && uiState.activeDraft != null,
                    )
                    FeatureEntryCard(
                        title = "开始冲煮",
                        hint = "进入引导会话",
                        icon = Icons.Outlined.PlayArrow,
                        onClick = { onOpenSession(BrewMethod.POUR_OVER, null) },
                        modifier = cardModifier,
                        badge = "LIVE",
                        selected = true,
                    )
                    FeatureEntryCard(
                        title = "实验工作台",
                        hint = "变量实验转记录",
                        icon = Icons.Outlined.FolderCopy,
                        onClick = onOpenExperiments,
                        modifier = cardModifier,
                        badge = "LAB",
                        enabled = !isReadOnlyArchive,
                    )
                    FeatureEntryCard(
                        title = "指导库",
                        hint = "跟做后生成记录",
                        icon = Icons.Outlined.PlayArrow,
                        onClick = onOpenGuides,
                        modifier = cardModifier,
                        badge = "GUIDE",
                        enabled = !isReadOnlyArchive,
                    )
                    FeatureEntryCard(
                        title = "常用配方",
                        hint = if (uiState.recipes.isEmpty()) "暂无配方" else "一键预填",
                        icon = Icons.Outlined.FolderCopy,
                        onClick = {
                            uiState.recipes.firstOrNull()?.let { recipe ->
                                handlePrefillRequest(RecordPrefillSource.Recipe(recipe.id))
                            }
                        },
                        modifier = cardModifier,
                        badge = "REUSE",
                        enabled = !isReadOnlyArchive && uiState.recipes.isNotEmpty(),
                    )
                }
            }
        }

        if (uiState.inventory.isNotEmpty()) {
            SectionCard(title = "库存豆子", subtitle = "点一颗豆子，直接开始记录。红色只标记当前最该优先使用的一款。") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.inventory.forEach { inventory ->
                        BeanInventoryCard(
                            inventory = inventory,
                            priority = inventoryPriorities[inventory.id] ?: BeanInventoryPriority.UNKNOWN,
                            onClick = {
                                inventory.beanId?.let { beanId ->
                                    handlePrefillRequest(RecordPrefillSource.Bean(beanId))
                                }
                            },
                            enabled = !isReadOnlyArchive && inventory.beanId != null,
                        )
                    }
                }
            }
        }

        uiState.activeDraft?.let { draft ->
            SectionCard(
                title = "当前草稿",
                subtitle = "建议先完成这杯，再开始新的复用动作。",
            ) {
                Text(
                    text = formatDateTime(draft.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(text = draft.brewMethod?.displayName ?: "未指定方式")
                    draft.beanNameSnapshot?.let { StatChip(text = it) }
                }
                Button(
                    onClick = { onOpenEditor(draft.id, null, RecordEditorEntry.DRAFT, null, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("继续填写草稿")
                }
            }
        }

        if (uiState.practiceBlocks.isNotEmpty()) {
            SectionCard(
                title = "练习计划",
            ) {
                uiState.practiceBlocks.take(4).forEach { block ->
                    FeatureEntryCard(
                        title = block.title,
                        hint = "${block.sessionTarget} 次 · ${block.level.displayName}",
                        icon = Icons.Outlined.PlayArrow,
                        onClick = { onOpenSession(block.method ?: BrewMethod.POUR_OVER, null) },
                        badge = if (block.proOnly) "PRO" else "PLAN",
                    )
                }
            }
        }

        SectionCard(
            title = "常用配方",
            subtitle = "来自真实记录，点击即可预填。",
        ) {
            if (uiState.recipes.isEmpty()) {
                EmptyStateCard(
                    title = "还没有常用配方",
                    subtitle = "在记录页或详情页把一条记录设为配方，这里就会出现。",
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.recipes.take(6).forEach { recipe ->
                        RecipeTemplateCard(
                            recipe = recipe,
                            enabled = !isReadOnlyArchive,
                            onClick = { handlePrefillRequest(RecordPrefillSource.Recipe(recipe.id)) },
                        )
                    }
                }
            }
        }

        SectionCard(
            title = "今日闭环",
            subtitle = "信息放在操作之后，便于先开始记录再复盘。",
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val singleColumn = maxWidth < 420.dp
                val cardModifier = if (singleColumn) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.48f)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = if (singleColumn) 1 else 2,
                ) {
                    MetricCard(
                        label = "已完成记录",
                        value = completedRecords.toString(),
                        supporting = "当前筛选范围",
                        modifier = cardModifier,
                    )
                    MetricCard(
                        label = "可复盘样本",
                        value = scoredRecords.toString(),
                        supporting = "含主观评分",
                        modifier = cardModifier,
                    )
                    MetricCard(
                        label = "复用资产",
                        value = "${uiState.inventory.size + uiState.recipes.size}",
                        supporting = "豆子 ${uiState.inventory.size} · 配方 ${uiState.recipes.size}",
                        modifier = cardModifier,
                    )
                    RecordStopwatchCard(
                        record = stopwatchRecord,
                        modifier = cardModifier,
                    )
                }
            }
        }

        SectionCard(
            title = "Brew Coach 下一杯建议",
            subtitle = "根据草稿、库存、评分和配方，给出下一步最省力动作。",
        ) {
            if (uiState.coachRecommendations.isEmpty()) {
                EmptyStateCard(
                    title = "先记录一杯",
                    subtitle = "有了记录、库存或配方后，这里会给出下一杯建议。",
                )
            } else {
                uiState.coachRecommendations.forEach { recommendation ->
                    val enabled = !isReadOnlyArchive || !recommendation.action.requiresWritableArchive()
                    BrewCoachRecommendationCard(
                        recommendation = recommendation,
                        enabled = enabled,
                        onClick = {
                            if (enabled) {
                                handleCoachAction(recommendation.action)
                            }
                        },
                    )
                }
            }
        }

        if (uiState.aiCoachSuggestions.isNotEmpty()) {
            SectionCard(
                title = "AI Coach beta（本地）",
                subtitle = "只基于本机记录生成建议，并明确引用样本；不联网，不调用外部 AI。",
            ) {
                uiState.aiCoachSuggestions.forEach { suggestion ->
                    val enabled = !isReadOnlyArchive || !suggestion.action.requiresWritableArchive()
                    AiCoachSuggestionCard(
                        suggestion = suggestion,
                        enabled = enabled,
                        onClick = {
                            if (enabled) {
                                handleAiCoachAction(suggestion.action)
                            }
                        },
                    )
                }
            }
        }

        SectionCard(
            title = "主观雷达复刻",
            subtitle = "点雷达轴选择维度，再设定分数，反查当时可复刻的客观参数。",
        ) {
            SubjectiveRadarReplayPanel(
                records = uiState.subjectiveRecords,
                dimensions = subjectiveDimensions,
                selectedFilters = subjectiveFilter,
                activeDimension = activeSubjectiveDimension,
                matchedRecords = subjectiveMatches,
                pageItems = subjectivePageItems,
                pageIndex = safeSubjectivePage,
                pageCount = subjectivePageCount,
                isReadOnlyArchive = isReadOnlyArchive,
                onDimensionSelected = { dimension ->
                    activeSubjectiveDimension = dimension
                },
                onScoreSelected = { dimension, score ->
                    subjectiveFilter = subjectiveFilter.toMutableMap().apply {
                        if (score == null) remove(dimension) else put(dimension, score)
                    }
                    subjectivePage = 0
                },
                onClear = {
                    subjectiveFilter = emptyMap()
                    subjectivePage = 0
                },
                onPreviousPage = { subjectivePage = (safeSubjectivePage - 1).coerceAtLeast(0) },
                onNextPage = { subjectivePage = (safeSubjectivePage + 1).coerceAtMost(subjectivePageCount - 1) },
                onOpenDetail = onOpenDetail,
                onDuplicate = { recordId -> handlePrefillRequest(RecordPrefillSource.Record(recordId)) },
            )
        }

        SectionCard(
            title = "最近记录与复盘",
            subtitle = "先看最近样本，再进入复盘。",
        ) {
            if (uiState.recentRecords.isEmpty()) {
                EmptyStateCard(
                    title = "还没有最近记录",
                    subtitle = "完成几条带评分记录后，这里会出现最近样本。",
                )
            } else {
                uiState.recentRecords.take(3).forEach { record ->
                    HubRecentRecordRow(
                        record = record,
                        onOpenDetail = { onOpenDetail(record.id) },
                    )
                }
            }
            OutlinedButton(
                onClick = onOpenAnalysis,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("进入复盘统计")
            }
        }

        if (isReadOnlyArchive) {
            StatChip(text = "当前为只读示范存档")
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("当前有未完成草稿") },
            text = {
                Text(
                    when (action) {
                        PendingDraftAction.NewRecord -> "你可以继续当前草稿，或放弃它并开始一条新的记录。"
                        is PendingDraftAction.Recipe -> "你可以继续当前草稿，或放弃它并从所选配方开始。"
                        is PendingDraftAction.Bean -> "你可以继续当前草稿，或放弃它并改为记录这颗豆子。"
                        is PendingDraftAction.Duplicate -> "你可以继续当前草稿，或放弃它并复制这条历史记录。"
                        PendingDraftAction.ResumeDraft -> "当前草稿已存在，直接继续填写会更符合当前状态。"
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    when (action) {
                        PendingDraftAction.NewRecord -> onOpenEditor(null, null, RecordEditorEntry.NEW, null, null)
                        is PendingDraftAction.Recipe -> onOpenEditor(null, null, RecordEditorEntry.RECIPE, action.recipeId, null)
                        is PendingDraftAction.Bean -> onOpenEditor(null, null, RecordEditorEntry.BEAN, null, action.beanId)
                        is PendingDraftAction.Duplicate -> onOpenEditor(null, action.recordId, RecordEditorEntry.DUPLICATE, null, null)
                        PendingDraftAction.ResumeDraft -> uiState.activeDraft?.let {
                            onOpenEditor(it.id, null, RecordEditorEntry.DRAFT, null, null)
                        }
                    }
                    pendingAction = null
                }) {
                    Text("替换草稿并继续")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    uiState.activeDraft?.let { onOpenEditor(it.id, null, RecordEditorEntry.DRAFT, null, null) }
                    pendingAction = null
                }) {
                    Text("继续当前草稿")
                }
            },
        )
    }
}

@Composable
private fun SubjectiveRadarReplayPanel(
    records: List<CoffeeRecord>,
    dimensions: List<SubjectiveDimension>,
    selectedFilters: Map<SubjectiveDimension, Int>,
    activeDimension: SubjectiveDimension,
    matchedRecords: List<CoffeeRecord>,
    pageItems: List<CoffeeRecord>,
    pageIndex: Int,
    pageCount: Int,
    isReadOnlyArchive: Boolean,
    onDimensionSelected: (SubjectiveDimension) -> Unit,
    onScoreSelected: (SubjectiveDimension, Int?) -> Unit,
    onClear: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
) {
    if (records.isEmpty()) {
        EmptyStateCard(
            title = "暂无可反查的主观记录",
            subtitle = "完成记录并填写香气、酸质、甜感等主观维度后，这里会形成叠加雷达图。",
        )
        return
    }

    val averages = remember(records, dimensions) {
        dimensions.associateWith { dimension ->
            records.mapNotNull { dimension.extract(it.subjectiveEvaluation)?.toDouble() }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?: 0.0
        }
    }
    SubjectiveOverlayRadarChart(
        averages = averages,
        filters = selectedFilters,
        activeDimension = activeDimension,
        dimensions = dimensions,
        onDimensionTapped = { dimension ->
            if (dimension == activeDimension) {
                val current = selectedFilters[dimension]
                onScoreSelected(dimension, if (current == null) 4 else (current % 5) + 1)
            } else {
                onDimensionSelected(dimension)
            }
        },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dimensions.forEach { dimension ->
            val selectedScore = selectedFilters[dimension]
            Surface(
                modifier = Modifier.clickable { onDimensionSelected(dimension) },
                color = if (dimension == activeDimension) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (dimension == activeDimension) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = selectedScore?.let { "${dimension.displayName}=$it" } ?: dimension.displayName,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${activeDimension.displayName}：",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (1..5).forEach { score ->
            Surface(
                modifier = Modifier.clickable { onScoreSelected(activeDimension, score) },
                color = if (selectedFilters[activeDimension] == score) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (selectedFilters[activeDimension] == score) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = score.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        OutlinedButton(onClick = { onScoreSelected(activeDimension, null) }) {
            Text("清除")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatChip(text = "匹配 ${matchedRecords.size}")
        if (selectedFilters.isNotEmpty()) {
            StatChip(text = selectedFilters.entries.joinToString(" · ") { "${it.key.displayName}=${it.value}" })
        }
        OutlinedButton(onClick = onClear, enabled = selectedFilters.isNotEmpty()) {
            Text("重置筛选")
        }
    }
    if (matchedRecords.isEmpty()) {
        EmptyStateCard(
            title = "没有完全匹配的历史杯",
            subtitle = "降低一个维度的分数，或先用单一维度筛选，再逐步叠加其他主观指标。",
        )
    } else {
        pageItems.forEach { record ->
            SubjectiveMatchRecordCard(
                record = record,
                isReadOnlyArchive = isReadOnlyArchive,
                onOpenDetail = { onOpenDetail(record.id) },
                onDuplicate = { onDuplicate(record.id) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onPreviousPage, enabled = pageIndex > 0) {
                Text("上一页")
            }
            Text(
                text = "${pageIndex + 1} / $pageCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onNextPage, enabled = pageIndex < pageCount - 1) {
                Text("下一页")
            }
        }
    }
}

@Composable
private fun SubjectiveOverlayRadarChart(
    averages: Map<SubjectiveDimension, Double>,
    filters: Map<SubjectiveDimension, Int>,
    activeDimension: SubjectiveDimension,
    dimensions: List<SubjectiveDimension>,
    onDimensionTapped: (SubjectiveDimension) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .pointerInput(dimensions, activeDimension, filters) {
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val angle = atan2(
                        y = (tap.y - center.y).toDouble(),
                        x = (tap.x - center.x).toDouble(),
                    )
                    val selected = dimensions.minByOrNull { dimension ->
                        val index = dimensions.indexOf(dimension)
                        val axis = -PI / 2.0 + index * 2.0 * PI / dimensions.size
                        kotlin.math.abs(atan2(sin(angle - axis), cos(angle - axis)))
                    } ?: activeDimension
                    onDimensionTapped(selected)
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height * 0.50f)
        val radius = (kotlin.math.min(size.width, size.height) * 0.34f).coerceAtLeast(54.dp.toPx())
        val labelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 12.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val activePaint = Paint().apply {
            color = onSurface.toArgb()
            textSize = 12.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        fun point(index: Int, value: Double): Offset {
            val angle = -PI / 2.0 + index * 2.0 * PI / dimensions.size
            val scaled = (value / 5.0).toFloat().coerceIn(0f, 1f) * radius
            return Offset(
                x = center.x + cos(angle).toFloat() * scaled,
                y = center.y + sin(angle).toFloat() * scaled,
            )
        }

        fun polygon(values: List<Double>): Path {
            val path = Path()
            values.forEachIndexed { index, value ->
                val offset = point(index, value)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            path.close()
            return path
        }

        (1..5).forEach { level ->
            drawPath(
                path = polygon(List(dimensions.size) { level.toDouble() }),
                color = gridColor.copy(alpha = if (level == 5) 0.28f else 0.14f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        dimensions.forEachIndexed { index, dimension ->
            val end = point(index, 5.0)
            drawLine(
                color = gridColor.copy(alpha = 0.20f),
                start = center,
                end = end,
                strokeWidth = 1.dp.toPx(),
            )
            val labelPoint = point(index, 5.55)
            drawContext.canvas.nativeCanvas.drawText(
                dimension.displayName,
                labelPoint.x,
                labelPoint.y + 4.dp.toPx(),
                if (dimension == activeDimension) activePaint else labelPaint,
            )
        }

        val averagePath = polygon(dimensions.map { averages[it] ?: 0.0 })
        drawPath(color = primary.copy(alpha = 0.18f), path = averagePath)
        drawPath(color = primary.copy(alpha = 0.82f), path = averagePath, style = Stroke(width = 2.dp.toPx()))

        if (filters.isNotEmpty()) {
            val filterPath = polygon(dimensions.map { filters[it]?.toDouble() ?: 0.0 })
            drawPath(color = tertiary.copy(alpha = 0.22f), path = filterPath)
            drawPath(color = tertiary, path = filterPath, style = Stroke(width = 3.dp.toPx()))
        }
        dimensions.forEachIndexed { index, dimension ->
            val average = averages[dimension] ?: 0.0
            drawCircle(
                color = primary,
                radius = if (dimension == activeDimension) 5.dp.toPx() else 3.5.dp.toPx(),
                center = point(index, average),
            )
            filters[dimension]?.let { score ->
                drawCircle(
                    color = tertiary,
                    radius = 6.dp.toPx(),
                    center = point(index, score.toDouble()),
                )
            }
        }
    }
    Text(
        text = "蓝色为历史均值，强调色为当前筛选形状；点击同一轴可循环设置 1-5 分。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubjectiveMatchRecordCard(
    record: CoffeeRecord,
    isReadOnlyArchive: Boolean,
    onOpenDetail: () -> Unit,
    onDuplicate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${record.beanNameSnapshot ?: "未命名咖啡豆"} · ${record.brewMethod?.displayName ?: "未指定方式"}",
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = formatDateTime(record.brewedAt))
                record.subjectiveEvaluation?.overall?.let { StatChip(text = "总评 $it/5") }
                SubjectiveDimension.entries
                    .filter { it != SubjectiveDimension.OVERALL }
                    .forEach { dimension ->
                        dimension.extract(record.subjectiveEvaluation)?.let { score ->
                            StatChip(text = "${dimension.displayName} $score")
                        }
                    }
            }
            Text(
                text = buildObjectiveParameterLine(record),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDetail) {
                    Text("打开记录")
                }
                Button(onClick = onDuplicate, enabled = !isReadOnlyArchive) {
                    Text("复刻参数")
                }
            }
        }
    }
}

@Composable
private fun RecipeTemplateCard(
    recipe: RecipeTemplate,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(252.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = recipe.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(recipe.brewMethod?.displayName ?: "未指定方式")
                    recipe.beanNameSnapshot?.let {
                        append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recipe.coffeeDoseG?.let { StatChip(text = "${formatNumber(it)}g") }
                recipe.brewWaterMl?.let { StatChip(text = "${formatNumber(it)}ml") }
                recipe.waterTempC?.let { StatChip(text = "${formatNumber(it)}°C") }
            }
        }
    }
}

@Composable
private fun BrewCoachRecommendationCard(
    recommendation: BrewCoachRecommendation,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = recommendation.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = recommendation.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (recommendation.chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recommendation.chips.forEach { chip -> StatChip(text = chip) }
                }
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(recommendation.primaryActionLabel)
            }
        }
    }
}

@Composable
private fun AiCoachSuggestionCard(
    suggestion: AiCoachSuggestion,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(text = suggestion.confidenceLabel)
                StatChip(text = "引用 ${suggestion.sourceRecords.size} 条")
            }
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = suggestion.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (suggestion.sourceRecords.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestion.sourceRecords.forEach { source ->
                        StatChip(text = "#${source.recordId} ${source.score?.let { "$it/5" } ?: "未评分"}")
                    }
                }
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(suggestion.actionLabel)
            }
        }
    }
}

@Composable
private fun RecordStopwatchCard(
    record: CoffeeRecord?,
    modifier: Modifier = Modifier,
) {
    val durationSeconds = record?.brewDurationSeconds
    val progress = ((durationSeconds ?: 0) % 300) / 300f
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(88.dp)) {
                    val strokeWidth = 7.dp.toPx()
                    val inset = strokeWidth / 2f + 3.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = arcSize.width / 2f
                    drawCircle(
                        color = surfaceVariant.copy(alpha = 0.72f),
                        radius = radius + strokeWidth * 0.62f,
                        center = center,
                    )
                    (0 until 12).forEach { tick ->
                        val angle = -PI / 2.0 + tick * 2.0 * PI / 12.0
                        val inner = radius - if (tick % 3 == 0) 6.dp.toPx() else 3.dp.toPx()
                        val outer = radius - 1.dp.toPx()
                        drawLine(
                            color = outline.copy(alpha = if (tick % 3 == 0) 0.48f else 0.24f),
                            start = Offset(
                                x = center.x + cos(angle).toFloat() * inner,
                                y = center.y + sin(angle).toFloat() * inner,
                            ),
                            end = Offset(
                                x = center.x + cos(angle).toFloat() * outer,
                                y = center.y + sin(angle).toFloat() * outer,
                            ),
                            strokeWidth = if (tick % 3 == 0) 1.6.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    drawArc(
                        color = outline.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = if (durationSeconds == null) outline.copy(alpha = 0.28f) else primary,
                        startAngle = -90f,
                        sweepAngle = progress.coerceAtLeast(0.02f) * 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    durationSeconds?.let {
                        val handAngle = -PI / 2.0 + progress * 2.0 * PI
                        drawLine(
                            color = tertiary,
                            start = center,
                            end = Offset(
                                x = center.x + cos(handAngle).toFloat() * (radius * 0.56f),
                                y = center.y + sin(handAngle).toFloat() * (radius * 0.56f),
                            ),
                            strokeWidth = 2.4.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                    drawCircle(color = primary, radius = 4.dp.toPx(), center = center)
                    drawCircle(color = surface, radius = 2.dp.toPx(), center = center)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = durationSeconds?.let(::formatStopwatchDuration) ?: "--:--",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "最近记录秒表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = record?.let {
                        buildString {
                            append(it.beanNameSnapshot ?: "未命名咖啡豆")
                            it.brewMethod?.displayName?.let { method ->
                                append(" · ")
                                append(method)
                            }
                        }
                    } ?: "还没有记录时长",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                record?.let {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StatChip(text = formatDateTime(it.brewedAt))
                        it.subjectiveEvaluation?.overall?.let { score -> StatChip(text = "总分 $score") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubRecentRecordRow(
    record: CoffeeRecord,
    onOpenDetail: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = buildString {
                        append(record.beanNameSnapshot ?: "未命名咖啡豆")
                        append(" · ")
                        append(record.brewMethod?.displayName ?: "未指定方式")
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(text = formatDateTime(record.brewedAt))
                    record.subjectiveEvaluation?.overall?.let { StatChip(text = "总分 $it") }
                }
            }
            OutlinedButton(onClick = onOpenDetail) {
                Text("详情")
            }
        }
    }
}

@Composable
private fun TimelineRecordCard(
    item: RecordTimelineItem,
    isReadOnlyArchive: Boolean,
    onOpenDetail: () -> Unit,
    onOpenEditor: (Long?, Long?, RecordEditorEntry, Long?, Long?) -> Unit,
) {
    val record = item.record
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.CHINA) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = buildString {
                    append(record.brewMethod?.displayName ?: "未指定方式")
                    if (!record.beanNameSnapshot.isNullOrBlank()) {
                        append(" · ")
                        append(record.beanNameSnapshot)
                    }
                },
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = formatter.format(Date(record.brewedAt)))
                record.subjectiveEvaluation?.overall?.let { StatChip(text = "总分 $it / 5") }
                record.recipeNameSnapshot?.let { StatChip(text = it) }
            }
            Text(
                text = buildString {
                    append("粉量 ${formatValue(record.coffeeDoseG, "g")}")
                    append(" · 水量 ${formatValue(record.brewWaterMl, "ml")}")
                    record.grindSetting?.let { append(" · 研磨 ${formatNumber(it)}") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.comparison?.let { comparison ->
                Text(
                    text = "${comparison.headline} · ${comparison.details.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenDetail) {
                    Text("详情")
                }
                if (!isReadOnlyArchive) {
                    Button(
                        onClick = { onOpenEditor(null, record.id, RecordEditorEntry.DUPLICATE, null, null) },
                    ) {
                        Text("复制一杯")
                    }
                }
            }
        }
    }
}

private fun formatDateTime(timestampMillis: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(Date(timestampMillis))
}

private fun formatStopwatchDuration(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
    }
}

private fun buildObjectiveParameterLine(record: CoffeeRecord): String {
    return listOfNotNull(
        record.coffeeDoseG?.let { "粉量 ${formatNumber(it)}g" },
        (record.totalWaterMl ?: record.brewWaterMl)?.let { "总水量 ${formatNumber(it)}ml" },
        record.brewRatio?.let { "粉水比 ${formatNumber(it)}" },
        record.waterTempC?.let { "水温 ${formatNumber(it)}°C" },
        record.brewDurationSeconds?.let { "时长 ${it}s" },
        record.grindSetting?.let { "研磨 ${formatNumber(it)}" },
        record.normalizedGrindSetting?.let { "归一化 ${formatNumber(it)}" },
    ).ifEmpty { listOf("客观冲煮参数未完整") }.joinToString(" · ")
}

private fun formatValue(value: Double?, unit: String): String {
    return value?.let { "${formatNumber(it)}$unit" } ?: "--"
}

private fun formatNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}

private fun AiCoachAction.requiresWritableArchive(): Boolean = when (this) {
    AiCoachAction.OpenAnalysis,
    is AiCoachAction.OpenRecord,
    -> false

    is AiCoachAction.DuplicateRecord -> true
}

private fun WorkbenchAction.requiresWritableArchive(): Boolean = when (this) {
    WorkbenchAction.OpenAnalysis,
    is WorkbenchAction.OpenRecord,
    -> false

    WorkbenchAction.StartBlank,
    is WorkbenchAction.ResumeDraft,
    is WorkbenchAction.StartBean,
    is WorkbenchAction.StartRecipe,
    is WorkbenchAction.DuplicateRecord,
    -> true
}

private sealed interface PendingDraftAction {
    data object NewRecord : PendingDraftAction
    data class Recipe(val recipeId: Long) : PendingDraftAction
    data class Bean(val beanId: Long) : PendingDraftAction
    data class Duplicate(val recordId: Long) : PendingDraftAction
    data object ResumeDraft : PendingDraftAction
}
