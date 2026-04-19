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
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.RecordStatus
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.RecipeRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.CompactDropdownChip
import com.qoffee.ui.components.CompactFilterBar
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.QuickActionCard
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
            val completedRecords = stageThree.records.filter { it.status == RecordStatus.COMPLETED }
            RecordsHubUiState(
                filter = stageThree.filter,
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
    onOpenEditor: (Long?, Long?, RecordEditorEntry, Long?) -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecordsScreen(
        paddingValues = paddingValues,
        currentArchive = currentArchive,
        uiState = uiState,
        isReadOnlyArchive = isReadOnlyArchive,
        onTimeRangeChange = viewModel::updateTimeRange,
        onMethodChange = viewModel::updateMethod,
        onBeanChange = viewModel::updateBean,
        onGrinderChange = viewModel::updateGrinder,
        onOpenDetail = onOpenDetail,
        onOpenEditor = onOpenEditor,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordsScreen(
    paddingValues: PaddingValues,
    currentArchive: ArchiveSummary?,
    uiState: RecordsHubUiState,
    isReadOnlyArchive: Boolean,
    onTimeRangeChange: (AnalysisTimeRange) -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenEditor: (Long?, Long?, RecordEditorEntry, Long?) -> Unit,
) {
    var pendingAction by remember { mutableStateOf<PendingDraftAction?>(null) }
    val historyCount = uiState.timelineGroups.sumOf { it.items.size }

    DashboardPage(
        paddingValues = paddingValues,
        testTag = QoffeeTestTags.RECORDS_SCREEN,
    ) {
        PageHeader(
            title = "记录工作台",
            subtitle = currentArchive?.archive?.name ?: "正在准备存档",
            eyebrow = "QOFFEE / OPERATIONS",
        )

        SectionCard(
            title = "今日控制台",
            subtitle = "把活跃草稿、当前筛选和历史样本集中到同一个视图里。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "活跃草稿",
                    value = if (uiState.activeDraft != null) "1" else "0",
                    supporting = uiState.activeDraft?.let { "上次更新 ${formatDateTime(it.updatedAt)}" } ?: "暂无待续写草稿",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "历史样本",
                    value = historyCount.toString(),
                    supporting = if (historyCount == 0) "开始记录第一杯" else "支持按时间和设备筛选",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "配方模板",
                    value = uiState.recipes.size.toString(),
                    supporting = "可一键套用客观参数",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "当前筛选",
                    value = uiState.filter.timeRange.displayName,
                    supporting = uiState.filter.brewMethod?.displayName ?: "全部方式",
                    modifier = Modifier.weight(1f),
                )
            }
            if (isReadOnlyArchive) {
                Text(
                    text = "当前处于只读示范存档，适合浏览结构和复盘方式；如需录入记录，请先复制一个自己的存档。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = "快速开始",
            subtitle = "把最常见的三种进入路径变成高权重操作。",
        ) {
            Column(
                modifier = Modifier.testTag(QoffeeTestTags.RECORDS_PRIMARY_ACTIONS),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickActionCard(
                    title = "新建记录",
                    subtitle = "从空白参数开始，立即记录今天这杯咖啡。",
                    onClick = {
                        if (uiState.activeDraft != null) {
                            pendingAction = PendingDraftAction.NewRecord
                        } else {
                            onOpenEditor(null, null, RecordEditorEntry.NEW, null)
                        }
                    },
                    enabled = !isReadOnlyArchive,
                    badge = "PRIMARY",
                    highlighted = true,
                )
                QuickActionCard(
                    title = "继续草稿",
                    subtitle = uiState.activeDraft?.let { "上次停在 ${formatDateTime(it.updatedAt)}，继续补完这条记录。" }
                        ?: "当前没有未完成草稿。",
                    onClick = { uiState.activeDraft?.let { onOpenEditor(it.id, null, RecordEditorEntry.DRAFT, null) } },
                    enabled = !isReadOnlyArchive && uiState.activeDraft != null,
                    badge = "DRAFT",
                )
                QuickActionCard(
                    title = "复制上一杯",
                    subtitle = uiState.recentRecords.firstOrNull()?.let {
                        "${it.beanNameSnapshot ?: "未命名咖啡豆"} · ${it.brewMethod?.displayName ?: "未指定方式"}"
                    } ?: "最近还没有可复制的完成记录。",
                    onClick = {
                        uiState.recentRecords.firstOrNull()?.let { onOpenEditor(null, it.id, RecordEditorEntry.DUPLICATE, null) }
                    },
                    enabled = !isReadOnlyArchive && uiState.recentRecords.isNotEmpty(),
                    badge = "REUSE",
                )
            }
        }

        SectionCard(
            title = "配方模板",
            subtitle = "将常用客观参数沉淀成模板，减少重复填写。",
        ) {
            if (uiState.recipes.isEmpty()) {
                EmptyStateCard(
                    title = "还没有配方模板",
                    subtitle = "去“我的”里保存常用客观参数后，就能在这里一键采用。",
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.recipes.forEach { recipe ->
                        RecipeTemplateCard(
                            recipe = recipe,
                            enabled = !isReadOnlyArchive,
                            onClick = {
                                if (uiState.activeDraft != null) {
                                    pendingAction = PendingDraftAction.Recipe(recipe.id)
                                } else {
                                    onOpenEditor(null, null, RecordEditorEntry.RECIPE, recipe.id)
                                }
                            },
                        )
                    }
                }
            }
        }

        if (uiState.recentRecords.isNotEmpty()) {
            SectionCard(
                title = "最近记录",
                subtitle = "最近几杯的状态、得分与详情入口。",
            ) {
                uiState.recentRecords.forEach { record ->
                    HubRecentRecordRow(
                        record = record,
                        onOpenDetail = { onOpenDetail(record.id) },
                    )
                }
            }
        }

        SectionCard(
            title = "历史筛选",
            subtitle = "控制时间范围、方式、豆子与磨豆机，收敛当前分析视角。",
        ) {
            CompactFilterBar {
                CompactDropdownChip(
                    label = "时间",
                    selectedLabel = uiState.filter.timeRange.displayName,
                    options = AnalysisTimeRange.entries.map { DropdownOption(it.displayName, it) },
                    onSelected = { selected -> selected?.let(onTimeRangeChange) },
                    allowClear = false,
                )
                CompactDropdownChip(
                    label = "方式",
                    selectedLabel = uiState.filter.brewMethod?.displayName,
                    options = BrewMethod.entries.map { DropdownOption(it.displayName, it) },
                    onSelected = onMethodChange,
                )
                CompactDropdownChip(
                    label = "豆子",
                    selectedLabel = uiState.beans.firstOrNull { it.id == uiState.filter.beanId }?.name,
                    options = uiState.beans.map { DropdownOption(it.name, it.id) },
                    onSelected = onBeanChange,
                )
                CompactDropdownChip(
                    label = "磨豆机",
                    selectedLabel = uiState.grinders.firstOrNull { it.id == uiState.filter.grinderId }?.name,
                    options = uiState.grinders.map { DropdownOption(it.name, it.id) },
                    onSelected = onGrinderChange,
                )
            }
        }

        if (uiState.timelineGroups.isEmpty()) {
            EmptyStateCard(
                title = "还没有完成记录",
                subtitle = if (isReadOnlyArchive) {
                    "当前是只读示范存档，可以先浏览记录结构与分析逻辑。"
                } else {
                    "从上面的快速开始或配方模板进入，写下今天第一杯。"
                },
            )
        } else {
            uiState.timelineGroups.forEach { group ->
                SectionCard(
                    title = group.label,
                    subtitle = "按冲煮日期归档，保留与上一杯的差异信息。",
                ) {
                    group.items.forEach { item ->
                        TimelineRecordCard(
                            item = item,
                            isReadOnlyArchive = isReadOnlyArchive,
                            onOpenDetail = { onOpenDetail(item.record.id) },
                            onOpenEditor = { recordId, duplicateFrom, entry ->
                                onOpenEditor(recordId, duplicateFrom, entry, null)
                            },
                        )
                    }
                }
            }
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
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    when (action) {
                        PendingDraftAction.NewRecord -> onOpenEditor(null, null, RecordEditorEntry.NEW, null)
                        is PendingDraftAction.Recipe -> onOpenEditor(null, null, RecordEditorEntry.RECIPE, action.recipeId)
                    }
                    pendingAction = null
                }) {
                    Text("放弃草稿并继续")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    uiState.activeDraft?.let { onOpenEditor(it.id, null, RecordEditorEntry.DRAFT, null) }
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
    onOpenEditor: (Long?, Long?, RecordEditorEntry) -> Unit,
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
                record.subjectiveEvaluation?.overall?.let { StatChip(text = "总分 $it / 10") }
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
                        onClick = { onOpenEditor(null, record.id, RecordEditorEntry.DUPLICATE) },
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
}
