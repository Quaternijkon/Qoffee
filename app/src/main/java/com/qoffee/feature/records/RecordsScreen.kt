package com.qoffee.feature.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.ArchiveSummary
import com.qoffee.core.model.BeanInventory
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
import com.qoffee.core.model.resolveRecordDraftLaunchBehavior
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.domain.repository.SessionRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.BeanInventoryCard
import com.qoffee.ui.components.FeatureEntryCard
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.navigation.RecordEditorEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
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
    val timelineGroups: List<RecordTimelineGroup> = emptyList(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val recipes: List<RecipeTemplate> = emptyList(),
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
                timelineGroups = buildRecordTimelineGroups(completedRecords),
                beans = stageThree.beans,
                grinders = stageThree.grinders,
                recipes = recipes,
            )
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

    DashboardPage(
        paddingValues = paddingValues,
        testTag = QoffeeTestTags.BREW_SCREEN,
    ) {
        PageHeader(
            title = "冲煮",
            subtitle = currentArchive?.archive?.name,
            eyebrow = "QOFFEE / BREW",
        )

        if (uiState.inventory.isNotEmpty()) {
            SectionCard(title = "库存豆子", subtitle = "直接点击一颗豆子，就开始一条以它为原料的记录。") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.inventory.forEach { inventory ->
                        BeanInventoryCard(
                            inventory = inventory,
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
            title = "主要入口",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureEntryCard(
                    title = "添加记录",
                    hint = "空白新建",
                    icon = Icons.Outlined.Add,
                    onClick = { handlePrefillRequest(RecordPrefillSource.Blank) },
                    modifier = Modifier.weight(1f),
                    badge = "NEW",
                    selected = true,
                    enabled = !isReadOnlyArchive,
                )
                FeatureEntryCard(
                    title = "继续草稿",
                    hint = if (uiState.activeDraft != null) "上次未完成" else "暂无草稿",
                    icon = Icons.Outlined.Restore,
                    onClick = { handlePrefillRequest(RecordPrefillSource.Draft) },
                    modifier = Modifier.weight(1f),
                    badge = "DRAFT",
                    enabled = !isReadOnlyArchive && uiState.activeDraft != null,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureEntryCard(
                    title = "开始冲煮",
                    hint = "主动会话",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = { onOpenSession(BrewMethod.POUR_OVER, null) },
                    modifier = Modifier.weight(1f),
                    badge = "LIVE",
                    selected = true,
                )
                FeatureEntryCard(
                    title = "实验工作台",
                    hint = "控制变量实验",
                    icon = Icons.Outlined.FolderCopy,
                    onClick = onOpenExperiments,
                    modifier = Modifier.weight(1f),
                    badge = "LAB",
                    enabled = !isReadOnlyArchive,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureEntryCard(
                    title = "指导库",
                    hint = "先预览，再开始",
                    icon = Icons.Outlined.PlayArrow,
                    onClick = onOpenGuides,
                    modifier = Modifier.weight(1f),
                    badge = "GUIDE",
                    enabled = !isReadOnlyArchive,
                )
                FeatureEntryCard(
                    title = "常用配方",
                    hint = if (uiState.recipes.isEmpty()) "暂无配方" else "从复用参数直接开记",
                    icon = Icons.Outlined.FolderCopy,
                    onClick = {
                        uiState.recipes.firstOrNull()?.let { recipe ->
                            handlePrefillRequest(RecordPrefillSource.Recipe(recipe.id))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    badge = "REUSE",
                    enabled = !isReadOnlyArchive && uiState.recipes.isNotEmpty(),
                )
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
            subtitle = "配方来自真实记录，点击就能直接进入一条预填记录。",
        ) {
            if (uiState.recipes.isEmpty()) {
                EmptyStateCard(
                    title = "还没有常用配方",
                    subtitle = "在记录页或详情页把一条真实记录设为配方，这里就会自然出现。",
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

        uiState.activeDraft?.let { draft ->
            SectionCard(
                title = "当前草稿",
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
            }
        }

        SectionCard(
            title = "最近记录与复盘",
            subtitle = "最近做了什么、接下来怎么复盘，都回到记录这条主链路里。",
        ) {
            if (uiState.recentRecords.isEmpty()) {
                EmptyStateCard(
                    title = "还没有最近记录",
                    subtitle = "完成几条带评分的记录后，这里会显示最近样本并直接进入统计复盘。",
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

private fun formatValue(value: Double?, unit: String): String {
    return value?.let { "${formatNumber(it)}$unit" } ?: "--"
}

private fun formatNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}

private sealed interface PendingDraftAction {
    data object NewRecord : PendingDraftAction
    data class Recipe(val recipeId: Long) : PendingDraftAction
    data class Bean(val beanId: Long) : PendingDraftAction
    data class Duplicate(val recordId: Long) : PendingDraftAction
    data object ResumeDraft : PendingDraftAction
}
