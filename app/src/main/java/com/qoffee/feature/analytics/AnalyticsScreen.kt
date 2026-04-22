@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.qoffee.feature.analytics

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalyticsDashboard
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanProcessMethod
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.Experiment
import com.qoffee.core.model.ExperimentRun
import com.qoffee.core.model.FileExportPayload
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.PracticeBlock
import com.qoffee.core.model.RecordHighlight
import com.qoffee.core.model.RoastLevel
import com.qoffee.core.model.UserSettings
import com.qoffee.core.model.normalizedBeanNameKey
import com.qoffee.domain.repository.AnalyticsRepository
import com.qoffee.domain.repository.BackupRepository
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.feature.records.buildComparisonSummaryMap
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.CompactDropdownChip
import com.qoffee.ui.components.CompactFilterBar
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.ScatterChart
import com.qoffee.ui.components.ScoreTrendChart
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.SubjectiveRadarLikeBars
import com.qoffee.ui.components.MethodBarChart
import com.qoffee.ui.theme.QoffeeDashboardTheme
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_SECTION = "analysis.section"
private const val KEY_PARAMETER = "analysis.parameter"
private const val KEY_TIME_RANGE = "analysis.timeRange"
private const val KEY_BREW_METHOD = "analysis.brewMethod"
private const val KEY_BEAN_NAME = "analysis.beanName"
private const val KEY_ROAST_LEVEL = "analysis.roastLevel"
private const val KEY_PROCESS_METHOD = "analysis.processMethod"
private const val KEY_GRINDER_ID = "analysis.grinderId"
private const val KEY_EXPORTING = "analysis.exporting"
private const val KEY_EXPORT_MESSAGE = "analysis.exportMessage"

enum class HistorySection(val value: String, val displayName: String) {
    OVERVIEW("overview", "总览"),
    TRENDS("trends", "趋势"),
    SAMPLES("samples", "样本"),
    EXPERIMENTS("experiments", "实验"),
    ;

    companion object {
        fun fromValue(value: String?): HistorySection = entries.firstOrNull { it.value == value } ?: OVERVIEW
    }
}

data class ReviewUiState(
    val filter: AnalysisFilter = AnalysisFilter(),
    val dashboard: AnalyticsDashboard = AnalyticsDashboard(filter = AnalysisFilter()),
    val records: List<CoffeeRecord> = emptyList(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val selectedParameter: NumericParameter = NumericParameter.WATER_TEMP,
    val selectedSection: HistorySection = HistorySection.OVERVIEW,
    val settings: UserSettings = UserSettings(),
    val practiceBlocks: List<PracticeBlock> = emptyList(),
    val experiments: List<Experiment> = emptyList(),
    val experimentRuns: List<ExperimentRun> = emptyList(),
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
)

private data class ReviewBaseState(
    val filter: AnalysisFilter,
    val dashboard: AnalyticsDashboard,
    val records: List<CoffeeRecord>,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
    val settings: UserSettings,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    analyticsRepository: AnalyticsRepository,
    private val backupRepository: BackupRepository,
    catalogRepository: CatalogRepository,
    preferenceRepository: PreferenceRepository,
    recordRepository: RecordRepository,
    experimentRepository: ExperimentRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(savedStateHandle.restoreFilter())
    private val selectedParameter = MutableStateFlow(
        savedStateHandle.get<String>(KEY_PARAMETER)?.let(NumericParameter::valueOf) ?: NumericParameter.WATER_TEMP,
    )
    private val selectedSection = MutableStateFlow(
        HistorySection.fromValue(savedStateHandle.get<String>(KEY_SECTION)),
    )
    private val isExporting = MutableStateFlow(savedStateHandle.get<Boolean>(KEY_EXPORTING) ?: false)
    private val exportMessage = MutableStateFlow(savedStateHandle.get<String?>(KEY_EXPORT_MESSAGE))

    init {
        viewModelScope.launch {
            if (!savedStateHandle.contains(KEY_TIME_RANGE)) {
                val defaultRange = preferenceRepository.observeSettings().first().defaultAnalysisTimeRange
                updateTimeRange(defaultRange)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dashboardFlow = filter.flatMapLatest { currentFilter ->
        analyticsRepository.observeDashboard(currentFilter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recordsFlow = filter.flatMapLatest { currentFilter ->
        recordRepository.observeRecords(currentFilter)
    }

    private val baseStateFlow = filter
        .combine(dashboardFlow) { currentFilter, dashboard ->
            currentFilter to dashboard
        }
        .combine(recordsFlow) { filterAndDashboard, records ->
            Triple(filterAndDashboard.first, filterAndDashboard.second, records)
        }
        .combine(catalogRepository.observeBeanProfiles()) { filterDashboardAndRecords, beans ->
            Quadruple(
                filterDashboardAndRecords.first,
                filterDashboardAndRecords.second,
                filterDashboardAndRecords.third,
                beans,
            )
        }
        .combine(catalogRepository.observeGrinderProfiles()) { state, grinders ->
            ReviewBaseState(
                filter = state.first,
                dashboard = state.second,
                records = state.third,
                beans = state.fourth,
                grinders = grinders,
                settings = UserSettings(),
            )
        }
        .combine(preferenceRepository.observeSettings()) { state, settings ->
            state.copy(settings = settings)
        }

    private val experimentStateFlow = combine(
        experimentRepository.observePracticeBlocks(),
        experimentRepository.observeExperiments(),
        experimentRepository.observeExperimentRuns(),
    ) { practiceBlocks, experiments, experimentRuns ->
        Triple(practiceBlocks, experiments, experimentRuns)
    }

    val uiState: StateFlow<ReviewUiState> = baseStateFlow
        .combine(selectedParameter) { baseState, parameter ->
            baseState to parameter
        }
        .combine(selectedSection) { baseStateAndParameter, section ->
            Triple(baseStateAndParameter.first, baseStateAndParameter.second, section)
        }
        .combine(experimentStateFlow) { state, experimentState ->
            ReviewUiState(
                filter = state.first.filter,
                dashboard = state.first.dashboard,
                records = state.first.records,
                beans = state.first.beans,
                grinders = state.first.grinders,
                selectedParameter = state.second,
                selectedSection = state.third,
                settings = state.first.settings,
                practiceBlocks = experimentState.first,
                experiments = experimentState.second,
                experimentRuns = experimentState.third,
            )
        }
        .combine(isExporting) { state, busy ->
            state.copy(isExporting = busy)
        }
        .combine(exportMessage) { state, message ->
            state.copy(exportMessage = message)
        }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewUiState(),
    )

    fun updateTimeRange(range: AnalysisTimeRange) {
        updateFilter(filter.value.copy(timeRange = range))
    }

    fun updateBrewMethod(method: BrewMethod?) {
        updateFilter(filter.value.copy(brewMethod = method))
    }

    fun updateBeanNameKey(beanNameKey: String?) {
        updateFilter(filter.value.copy(beanNameKey = beanNameKey))
    }

    fun updateRoastLevel(roastLevel: RoastLevel?) {
        updateFilter(filter.value.copy(roastLevel = roastLevel))
    }

    fun updateProcessMethod(processMethod: BeanProcessMethod?) {
        updateFilter(filter.value.copy(processMethod = processMethod))
    }

    fun updateGrinder(grinderId: Long?) {
        updateFilter(filter.value.copy(grinderId = grinderId))
    }

    fun resetFilters() {
        val defaultRange = uiState.value.settings.defaultAnalysisTimeRange
        updateFilter(AnalysisFilter(timeRange = defaultRange))
    }

    fun updateSelectedParameter(parameter: NumericParameter) {
        selectedParameter.value = parameter
        savedStateHandle[KEY_PARAMETER] = parameter.name
    }

    fun updateSelectedSection(section: HistorySection) {
        selectedSection.value = section
        savedStateHandle[KEY_SECTION] = section.value
    }

    suspend fun prepareCsvExport(): FileExportPayload? {
        updateExportState(isBusy = true, message = "正在准备导出文件…")
        return runCatching {
            backupRepository.exportRecordsCsv(filter.value)
        }.onFailure { error ->
            updateExportState(
                isBusy = false,
                message = "CSV 导出失败：${error.message ?: "未知错误"}",
            )
        }.getOrNull()
    }

    fun onExportCancelled() {
        updateExportState(isBusy = false, message = "已取消导出。")
    }

    fun onExportSucceeded() {
        updateExportState(isBusy = false, message = "CSV 已导出。")
    }

    fun onExportFailed(message: String) {
        updateExportState(isBusy = false, message = message)
    }

    fun clearExportMessage() {
        exportMessage.value = null
        savedStateHandle[KEY_EXPORT_MESSAGE] = null
    }

    private fun updateFilter(updatedFilter: AnalysisFilter) {
        filter.value = updatedFilter
        savedStateHandle.persistFilter(updatedFilter)
    }

    private fun updateExportState(isBusy: Boolean, message: String?) {
        isExporting.value = isBusy
        exportMessage.value = message
        savedStateHandle[KEY_EXPORTING] = isBusy
        savedStateHandle[KEY_EXPORT_MESSAGE] = message
    }
}

@Composable
fun AnalysisRoute(
    paddingValues: PaddingValues,
    onOpenRecord: (Long, String?) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<FileExportPayload?>(null) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (payload == null) {
            viewModel.onExportCancelled()
            return@rememberLauncherForActivityResult
        }
        if (uri == null) {
            viewModel.onExportCancelled()
            return@rememberLauncherForActivityResult
        }
        context.writeTextToUri(uri, payload.content)
            .onSuccess { viewModel.onExportSucceeded() }
            .onFailure { error ->
                viewModel.onExportFailed("CSV 导出失败：${error.message ?: "未知错误"}")
            }
    }

    LaunchedEffect(uiState.exportMessage) {
        val message = uiState.exportMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearExportMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnalysisScreen(
            paddingValues = paddingValues,
            uiState = uiState,
            onTimeRangeChange = viewModel::updateTimeRange,
            onMethodChange = viewModel::updateBrewMethod,
            onBeanChange = viewModel::updateBeanNameKey,
            onRoastLevelChange = viewModel::updateRoastLevel,
            onProcessMethodChange = viewModel::updateProcessMethod,
            onGrinderChange = viewModel::updateGrinder,
            onParameterChange = viewModel::updateSelectedParameter,
            onSectionChange = viewModel::updateSelectedSection,
            onResetFilters = viewModel::resetFilters,
            onOpenRecord = { recordId ->
                onOpenRecord(recordId, buildReviewContext(uiState))
            },
            onExportCsv = {
                coroutineScope.launch {
                    val payload = viewModel.prepareCsvExport() ?: return@launch
                    pendingExport = payload
                    csvLauncher.launch(payload.fileName)
                }
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AnalysisScreen(
    paddingValues: PaddingValues,
    uiState: ReviewUiState,
    onTimeRangeChange: (AnalysisTimeRange) -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (String?) -> Unit,
    onRoastLevelChange: (RoastLevel?) -> Unit,
    onProcessMethodChange: (BeanProcessMethod?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onParameterChange: (NumericParameter) -> Unit,
    onSectionChange: (HistorySection) -> Unit,
    onResetFilters: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onExportCsv: () -> Unit,
) {
    val listState = rememberLazyListState()
    val spacing = QoffeeDashboardTheme.spacing
    val beanNameOptions = remember(uiState.beans) {
        uiState.beans
            .mapNotNull { bean -> bean.name.trim().takeIf { it.isNotBlank() } }
            .distinctBy { normalizedBeanNameKey(it) }
            .sorted()
    }
    val selectedBeanName = beanNameOptions.firstOrNull { normalizedBeanNameKey(it) == uiState.filter.beanNameKey }
    val scoredRecords = remember(uiState.records) {
        uiState.records
            .filter { it.subjectiveEvaluation?.overall != null }
            .sortedByDescending { it.brewedAt }
    }
    val comparisonMap = remember(scoredRecords) { buildComparisonSummaryMap(scoredRecords) }
    val hasActiveFilters = uiState.filter.brewMethod != null ||
        uiState.filter.beanNameKey != null ||
        uiState.filter.roastLevel != null ||
        uiState.filter.processMethod != null ||
        uiState.filter.grinderId != null ||
        uiState.filter.timeRange != uiState.settings.defaultAnalysisTimeRange
    val selectedCorrelation = uiState.dashboard.parameterCorrelations.firstOrNull {
        it.parameter == uiState.selectedParameter
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .statusBarsPadding()
            .testTag(QoffeeTestTags.HISTORY_SCREEN),
        contentPadding = PaddingValues(
            start = spacing.pageHorizontal,
            end = spacing.pageHorizontal,
            top = spacing.pageVertical,
            bottom = spacing.pageVertical + 80.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.section),
    ) {
        item {
            PageHeader(
                title = "复盘看板",
                subtitle = "把记录、趋势、离群样本和实验放在同一个视角下看。",
                eyebrow = "QOFFEE / HISTORY",
            )
        }

        item {
            ReviewToolPanel(
                modifier = Modifier.padding(bottom = 10.dp),
                uiState = uiState,
                selectedBeanName = selectedBeanName,
                beanNameOptions = beanNameOptions,
                hasActiveFilters = hasActiveFilters,
                onTimeRangeChange = onTimeRangeChange,
                onMethodChange = onMethodChange,
                onBeanChange = onBeanChange,
                onRoastLevelChange = onRoastLevelChange,
                onProcessMethodChange = onProcessMethodChange,
                onGrinderChange = onGrinderChange,
                onResetFilters = onResetFilters,
                onExportCsv = onExportCsv,
            )
        }

        item {
            SectionTabs(
                selectedSection = uiState.selectedSection,
                onSectionChange = onSectionChange,
            )
        }

        when (uiState.selectedSection) {
            HistorySection.OVERVIEW -> {
                overviewItems(
                    uiState = uiState,
                    scoredRecords = scoredRecords,
                    onOpenRecord = onOpenRecord,
                )
            }

            HistorySection.TRENDS -> {
                trendsItems(
                    uiState = uiState,
                    selectedCorrelation = selectedCorrelation,
                    onParameterChange = onParameterChange,
                )
            }

            HistorySection.SAMPLES -> {
                sampleItems(
                    scoredRecords = scoredRecords,
                    comparisonMap = comparisonMap,
                    onOpenRecord = onOpenRecord,
                )
            }

            HistorySection.EXPERIMENTS -> {
                experimentItems(
                    uiState = uiState,
                    onOpenRecord = onOpenRecord,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(
    uiState: ReviewUiState,
    scoredRecords: List<CoffeeRecord>,
    onOpenRecord: (Long) -> Unit,
) {
    val recentAverage = scoredRecords.take(5)
        .mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
        .takeIf { it.isNotEmpty() }
        ?.average()
    val bestScore = scoredRecords.maxOfOrNull { it.subjectiveEvaluation?.overall ?: 0 }
    val leadingInsight = uiState.dashboard.insightCards.maxWithOrNull(
        compareBy<com.qoffee.core.model.InsightCard> { it.confidence.ordinal }.thenBy { it.sampleCount },
    )

    item {
        SectionCard(title = "概览指标", subtitle = "优先看样本量、近期均分和最强洞察。") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "样本量",
                    value = uiState.dashboard.sampleCount.toString(),
                    supporting = uiState.dashboard.summary.lastRecordAt?.let { "最近 ${formatShortDate(it)}" } ?: "暂无",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "近 5 杯均分",
                    value = recentAverage?.let { formatScore(it) } ?: "--",
                    supporting = "用于观察最近状态",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "当前高分",
                    value = bestScore?.let { "$it/5" } ?: "--",
                    supporting = "当前筛选内最佳评分",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "覆盖方式",
                    value = uiState.dashboard.summary.methodCount.toString(),
                    supporting = "已形成评分样本的方法数",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    item {
        if (leadingInsight == null) {
            EmptyStateCard(
                title = "还没有足够稳定的洞察",
                subtitle = "继续补充带评分记录后，这里会优先显示最值得行动的结论。",
            )
        } else {
            InsightHeroCard(insight = leadingInsight)
        }
    }

    item {
        SectionCard(title = "下一步建议", subtitle = "把复盘结果直接转成下一轮动作。") {
            if (uiState.dashboard.suggestedNextSteps.isEmpty()) {
                Text(
                    text = "当前样本还不足以生成可靠建议。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.dashboard.suggestedNextSteps.forEach { step ->
                    InsightLine(title = step.title, body = step.message)
                }
            }
        }
    }

    item {
        SectionCard(title = "样本亮点", subtitle = "高分、低分和最近样本放在同一处快速回看。") {
            if (uiState.dashboard.highlightRecords.isEmpty()) {
                Text(
                    text = "暂无可展示的样本亮点。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.dashboard.highlightRecords.forEach { highlight ->
                    HighlightRecordCard(
                        highlight = highlight,
                        onClick = { onOpenRecord(highlight.recordId) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trendsItems(
    uiState: ReviewUiState,
    selectedCorrelation: com.qoffee.core.model.ParameterCorrelation?,
    onParameterChange: (NumericParameter) -> Unit,
) {
    if (!uiState.dashboard.hasEnoughData) {
        item {
            EmptyStateCard(
                title = "当前样本不足以展示趋势",
                subtitle = "至少需要一批带评分的完成记录，才能形成稳定的复盘看板。",
            )
        }
        return
    }

    item {
        SectionCard(title = "参数洞察", subtitle = "先看区间洞察，再看相关性强弱。") {
            if (uiState.dashboard.rangeInsights.isEmpty() && uiState.dashboard.parameterCorrelations.isEmpty()) {
                Text(
                    text = "当前筛选下还没有足够稳定的参数关系。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.dashboard.rangeInsights.take(2).forEach { insight ->
                    InsightLine(
                        title = insight.parameter.displayName,
                        body = "${insight.message} · 样本 ${insight.sampleCount} · ${insight.confidence.displayName}",
                    )
                }
                uiState.dashboard.parameterCorrelations.take(3).forEach { correlation ->
                    val direction = if (correlation.coefficient >= 0) "正相关" else "负相关"
                    InsightLine(
                        title = "${correlation.parameter.displayName} 敏感度",
                        body = "$direction · ρ=${formatCoefficient(correlation.coefficient)} · 样本 ${correlation.sampleCount}",
                    )
                }
            }
        }
    }

    item {
        SectionCard(title = "评分趋势", subtitle = "用 5 分制统一观察最近评分变化。") {
            ScoreTrendChart(
                points = uiState.dashboard.timelinePoints,
                scoreRange = uiState.dashboard.scoreRange,
            )
            ChartSummaryText(
                text = "当前看板使用 ${uiState.dashboard.scoreRange.first}-${uiState.dashboard.scoreRange.last} 分评分尺度。",
            )
        }
    }

    item {
        SectionCard(title = "方式表现", subtitle = "按制作方式比较均分和样本覆盖。") {
            MethodBarChart(values = uiState.dashboard.methodAverages)
            uiState.dashboard.methodAverages.forEach { average ->
                StatChip(text = "${average.brewMethod.displayName} ${formatScore(average.averageScore)}/5 · ${average.sampleCount} 杯")
            }
        }
    }

    item {
        SectionCard(title = "参数关系", subtitle = "选择一个变量查看散点分布与摘要。") {
            DropdownField(
                label = "参数",
                selectedLabel = uiState.selectedParameter.displayName,
                options = NumericParameter.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { selected -> selected?.let(onParameterChange) },
                allowClear = false,
            )
            ScatterChart(
                points = uiState.dashboard.scatterSeries[uiState.selectedParameter].orEmpty(),
                xLabel = uiState.selectedParameter.displayName,
                yRange = uiState.dashboard.scoreRange,
            )
            ChartSummaryText(
                text = selectedCorrelation?.let { correlation ->
                    val direction = if (correlation.coefficient >= 0) "正相关" else "负相关"
                    "${correlation.parameter.displayName} 与评分呈$direction（ρ=${formatCoefficient(correlation.coefficient)}），当前样本 ${correlation.sampleCount}。"
                } ?: "当前参数的样本不足，暂时只展示分布。"
            )
        }
    }

    item {
        SectionCard(title = "主观维度", subtitle = "把感官评分拆开看，避免只盯总体分。") {
            SubjectiveRadarLikeBars(values = uiState.dashboard.dimensionAverages)
            uiState.dashboard.dimensionAverages.forEach { value ->
                StatChip(text = "${value.label} ${formatScore(value.average)}/5")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sampleItems(
    scoredRecords: List<CoffeeRecord>,
    comparisonMap: Map<Long, com.qoffee.feature.records.RecordComparisonSummary>,
    onOpenRecord: (Long) -> Unit,
) {
    if (scoredRecords.isEmpty()) {
        item {
            EmptyStateCard(
                title = "暂无可复盘样本",
                subtitle = "完成记录并补上主观评分后，这里会生成可点击的样本列表。",
            )
        }
        return
    }

    item {
        SectionCard(title = "样本列表", subtitle = "整行点击进入详情，保留当前复盘上下文。") {
            Text(
                text = "按最近时间排序，卡片会直接展示评分、核心参数、风味标签和对比提示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    items(scoredRecords, key = { it.id }) { record ->
        ReviewRecordCard(
            record = record,
            comparison = comparisonMap[record.id],
            onClick = { onOpenRecord(record.id) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.experimentItems(
    uiState: ReviewUiState,
    onOpenRecord: (Long) -> Unit,
) {
    item {
        SectionCard(title = "实验工作台", subtitle = "保留最相关的实验线索，但不和主复盘抢焦点。") {
            if (uiState.practiceBlocks.isEmpty() && uiState.experiments.isEmpty() && uiState.experimentRuns.isEmpty()) {
                Text(
                    text = "暂无可展示的实验内容。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.practiceBlocks.take(2).forEach { block ->
                    CompactWorkbenchCard(
                        title = block.title,
                        subtitle = "${block.focus} · ${block.sessionTarget} 次训练",
                        badge = if (block.proOnly) "PRO" else block.level.displayName,
                    )
                }
                uiState.experiments.take(2).forEach { experiment ->
                    CompactWorkbenchCard(
                        title = experiment.title,
                        subtitle = experiment.status.displayName,
                        badge = experiment.comparedParameter?.displayName,
                    )
                }
                uiState.experimentRuns.take(3).forEach { run ->
                    CompactWorkbenchCard(
                        title = run.label,
                        subtitle = run.deltaSummary ?: "实验样本",
                        badge = run.score?.let { "$it/5" },
                        onClick = run.recordId?.let { recordId -> { onOpenRecord(recordId) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewToolPanel(
    modifier: Modifier = Modifier,
    uiState: ReviewUiState,
    selectedBeanName: String?,
    beanNameOptions: List<String>,
    hasActiveFilters: Boolean,
    onTimeRangeChange: (AnalysisTimeRange) -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (String?) -> Unit,
    onRoastLevelChange: (RoastLevel?) -> Unit,
    onProcessMethodChange: (BeanProcessMethod?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onResetFilters: () -> Unit,
    onExportCsv: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(QoffeeDashboardTheme.colors.pageTop.copy(alpha = 0.96f)),
        color = QoffeeDashboardTheme.colors.panelStrong.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "样本 ${uiState.dashboard.sampleCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildFilterSummary(uiState.filter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onResetFilters,
                        enabled = hasActiveFilters,
                        modifier = Modifier
                            .semantics { contentDescription = "重置筛选" }
                            .testTag(QoffeeTestTags.ANALYSIS_RESET_BUTTON),
                    ) {
                        Text("重置")
                    }
                    Button(
                        onClick = onExportCsv,
                        enabled = !uiState.isExporting,
                        modifier = Modifier
                            .semantics { contentDescription = "导出 CSV" }
                            .testTag(QoffeeTestTags.ANALYSIS_EXPORT_BUTTON),
                    ) {
                        Text(if (uiState.isExporting) "导出中…" else "导出 CSV")
                    }
                }
            }

            CompactFilterBar(
                modifier = Modifier.testTag(QoffeeTestTags.ANALYSIS_FILTERS),
            ) {
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
                    selectedLabel = selectedBeanName,
                    options = beanNameOptions.map { DropdownOption(it, normalizedBeanNameKey(it) ?: it) },
                    onSelected = onBeanChange,
                )
                CompactDropdownChip(
                    label = "烘焙",
                    selectedLabel = uiState.filter.roastLevel?.displayName,
                    options = RoastLevel.entries.map { DropdownOption(it.displayName, it) },
                    onSelected = onRoastLevelChange,
                )
                CompactDropdownChip(
                    label = "处理",
                    selectedLabel = uiState.filter.processMethod?.displayName,
                    options = BeanProcessMethod.entries.map { DropdownOption(it.displayName, it) },
                    onSelected = onProcessMethodChange,
                )
                CompactDropdownChip(
                    label = "磨豆机",
                    selectedLabel = uiState.grinders.firstOrNull { it.id == uiState.filter.grinderId }?.name,
                    options = uiState.grinders.map { DropdownOption(it.name, it.id) },
                    onSelected = onGrinderChange,
                )
            }
        }
    }
}

@Composable
private fun SectionTabs(
    selectedSection: HistorySection,
    onSectionChange: (HistorySection) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistorySection.entries.forEach { section ->
            FilterChip(
                selected = section == selectedSection,
                onClick = { onSectionChange(section) },
                label = { Text(section.displayName) },
                leadingIcon = {
                    val icon = when (section) {
                        HistorySection.OVERVIEW -> Icons.Outlined.Insights
                        HistorySection.TRENDS -> Icons.Outlined.QueryStats
                        HistorySection.SAMPLES -> Icons.Outlined.Description
                        HistorySection.EXPERIMENTS -> Icons.Outlined.AutoGraph
                    }
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = QoffeeDashboardTheme.colors.panelMuted,
                    selectedContainerColor = QoffeeDashboardTheme.colors.accentSoft,
                ),
            )
        }
    }
}

@Composable
private fun InsightHeroCard(insight: com.qoffee.core.model.InsightCard) {
    Surface(
        color = QoffeeDashboardTheme.colors.panelStrong.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatChip(text = "首要洞察")
                StatChip(text = "样本 ${insight.sampleCount}")
                StatChip(text = insight.confidence.displayName)
            }
            Text(
                text = insight.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = insight.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = insight.filterContext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InsightLine(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HighlightRecordCard(
    highlight: RecordHighlight,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = QoffeeDashboardTheme.colors.panelMuted,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = highlight.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = highlight.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatChip(text = highlight.kind.displayName)
        }
    }
}

@Composable
private fun ReviewRecordCard(
    record: CoffeeRecord,
    comparison: com.qoffee.feature.records.RecordComparisonSummary?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${record.beanNameSnapshot ?: "未命名记录"}，评分 ${record.subjectiveEvaluation?.overall ?: 0}"
            },
        color = QoffeeDashboardTheme.colors.panelStrong.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = record.beanNameSnapshot ?: record.brewMethod?.displayName ?: "未命名记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = buildString {
                            append(record.brewMethod?.displayName ?: "未指定方式")
                            append(" · ")
                            append(formatDateTime(record.brewedAt))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatChip(text = "${record.subjectiveEvaluation?.overall ?: "--"}/5")
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                record.brewRatio?.let { StatChip(text = "粉水比 ${formatScore(it)}") }
                record.waterTempC?.let { StatChip(text = "水温 ${formatNumber(it)}°C") }
                record.brewDurationSeconds?.let { StatChip(text = "时长 ${it}s") }
                record.grinderNameSnapshot?.let { StatChip(text = it) }
            }

            record.subjectiveEvaluation?.flavorTags?.take(3)?.takeIf { it.isNotEmpty() }?.let { tags ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag -> StatChip(text = tag.name) }
                }
            }

            comparison?.let {
                Text(
                    text = it.headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = it.details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CompactWorkbenchCard(
    title: String,
    subtitle: String,
    badge: String?,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = QoffeeDashboardTheme.colors.panelMuted,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            badge?.let { StatChip(text = it) }
        }
    }
}

@Composable
private fun ChartSummaryText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private fun SavedStateHandle.restoreFilter(): AnalysisFilter {
    return AnalysisFilter(
        timeRange = get<String>(KEY_TIME_RANGE)?.let(AnalysisTimeRange::valueOf) ?: AnalysisTimeRange.LAST_90_DAYS,
        brewMethod = get<String>(KEY_BREW_METHOD)?.let(BrewMethod::fromCode),
        beanNameKey = get<String>(KEY_BEAN_NAME),
        roastLevel = get<String>(KEY_ROAST_LEVEL)?.let(RoastLevel::valueOf),
        processMethod = get<String>(KEY_PROCESS_METHOD)?.let(BeanProcessMethod::valueOf),
        grinderId = get<Long>(KEY_GRINDER_ID),
    )
}

private fun SavedStateHandle.persistFilter(filter: AnalysisFilter) {
    this[KEY_TIME_RANGE] = filter.timeRange.name
    this[KEY_BREW_METHOD] = filter.brewMethod?.code
    this[KEY_BEAN_NAME] = filter.beanNameKey
    this[KEY_ROAST_LEVEL] = filter.roastLevel?.name
    this[KEY_PROCESS_METHOD] = filter.processMethod?.name
    this[KEY_GRINDER_ID] = filter.grinderId
}

private fun buildFilterSummary(filter: AnalysisFilter): String {
    val parts = buildList {
        add(filter.timeRange.displayName)
        filter.brewMethod?.let { add(it.displayName) }
        filter.beanNameKey?.let { add("豆子 ${it.replaceFirstChar { ch -> ch.titlecase(Locale.CHINA) }}") }
        filter.roastLevel?.let { add(it.displayName) }
        filter.processMethod?.let { add(it.displayName) }
        filter.grinderId?.let { add("已选磨豆机") }
    }
    return parts.joinToString(" / ")
}

private fun buildReviewContext(uiState: ReviewUiState): String {
    return "来自复盘看板 · ${uiState.selectedSection.displayName} · ${buildFilterSummary(uiState.filter)} · ${uiState.dashboard.sampleCount} 个样本"
}

private fun formatShortDate(timestampMillis: Long): String {
    return SimpleDateFormat("M/d", Locale.CHINA).format(Date(timestampMillis))
}

private fun formatDateTime(timestampMillis: Long): String {
    return SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(Date(timestampMillis))
}

private fun formatNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}

private fun formatScore(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}

private fun formatCoefficient(value: Double): String {
    return String.format(Locale.CHINA, "%.2f", value)
}

private fun Context.writeTextToUri(uri: Uri, content: String): Result<Unit> {
    return runCatching {
        val stream = contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("无法打开目标文件。")
        stream.bufferedWriter().use { writer ->
            writer.write(content)
        }
    }
}
