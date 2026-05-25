@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.qoffee.feature.analytics

import android.graphics.Paint
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.R
import com.qoffee.core.model.AnalyticsDashboard
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanConsumptionBucket
import com.qoffee.core.model.BeanProcessMethod
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.ConsumptionChartReport
import com.qoffee.core.model.Experiment
import com.qoffee.core.model.ExperimentRun
import com.qoffee.core.model.FileExportPayload
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.HourlyConsumptionCell
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.PracticeBlock
import com.qoffee.core.model.RecordHighlight
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RoastLevel
import com.qoffee.core.model.SweetPointReport
import com.qoffee.core.model.TastingConcentrationPoint
import com.qoffee.core.model.TastingMatrixCell
import com.qoffee.core.model.TastingScoreBucket
import com.qoffee.core.model.UserSettings
import com.qoffee.core.model.WeekdayTimeConsumptionCell
import com.qoffee.core.model.buildReviewExperimentDraftPlan
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
import com.qoffee.ui.components.DashboardArtworkBanner
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.InsightActionCard
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.ScatterChart
import com.qoffee.ui.components.ScoreTrendChart
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.SubjectiveRadarLikeBars
import com.qoffee.ui.components.MethodBarChart
import com.qoffee.ui.theme.GoogleBlueRefined
import com.qoffee.ui.theme.GoogleGreenRefined
import com.qoffee.ui.theme.GoogleRedRefined
import com.qoffee.ui.theme.GoogleYellowRefined
import com.qoffee.ui.theme.QoffeeDashboardTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
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
    REPORT("report", "报告"),
    TRENDS("trends", "趋势"),
    SAMPLES("samples", "样本"),
    EXPERIMENTS("experiments", "实验"),
    ;

    companion object {
        fun fromValue(value: String?): HistorySection {
            if (value == "overview") return REPORT
            return entries.firstOrNull { it.value == value } ?: REPORT
        }
    }
}

data class ReviewUiState(
    val filter: AnalysisFilter = AnalysisFilter(),
    val dashboard: AnalyticsDashboard = AnalyticsDashboard(filter = AnalysisFilter()),
    val report: com.qoffee.core.model.AnalyticsReport = com.qoffee.core.model.AnalyticsReport(
        generatedAt = 0L,
        filter = AnalysisFilter(),
        filterContext = AnalysisTimeRange.LAST_90_DAYS.displayName,
    ),
    val records: List<CoffeeRecord> = emptyList(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val selectedParameter: NumericParameter = NumericParameter.WATER_TEMP,
    val selectedSection: HistorySection = HistorySection.REPORT,
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
    val report: com.qoffee.core.model.AnalyticsReport,
    val records: List<CoffeeRecord>,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
    val settings: UserSettings,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val analyticsRepository: AnalyticsRepository,
    private val backupRepository: BackupRepository,
    catalogRepository: CatalogRepository,
    preferenceRepository: PreferenceRepository,
    private val recordRepository: RecordRepository,
    private val experimentRepository: ExperimentRepository,
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
    private val reportFlow = filter.flatMapLatest { currentFilter ->
        analyticsRepository.observeReport(currentFilter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recordsFlow = filter.flatMapLatest { currentFilter ->
        recordRepository.observeRecords(currentFilter)
    }

    private val baseStateFlow = filter
        .combine(dashboardFlow) { currentFilter, dashboard ->
            currentFilter to dashboard
        }
        .combine(reportFlow) { filterAndDashboard, report ->
            Triple(filterAndDashboard.first, filterAndDashboard.second, report)
        }
        .combine(recordsFlow) { filterDashboardAndReport, records ->
            Quadruple(
                filterDashboardAndReport.first,
                filterDashboardAndReport.second,
                filterDashboardAndReport.third,
                records,
            )
        }
        .combine(catalogRepository.observeBeanProfiles()) { state, beans ->
            ReviewBaseState(
                filter = state.first,
                dashboard = state.second,
                report = state.third,
                records = state.fourth,
                beans = beans,
                grinders = emptyList(),
                settings = UserSettings(),
            )
        }
        .combine(catalogRepository.observeGrinderProfiles()) { state, grinders ->
            state.copy(grinders = grinders)
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
                report = state.first.report,
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

    suspend fun prepareReportExport(): FileExportPayload? {
        updateExportState(isBusy = true, message = "正在生成分析报告…")
        return runCatching {
            analyticsRepository.exportReportMarkdown(filter.value)
        }.onFailure { error ->
            updateExportState(
                isBusy = false,
                message = "报告导出失败：${error.message ?: "未知错误"}",
            )
        }.getOrNull()
    }

    suspend fun prepareCsvExport(): FileExportPayload? {
        updateExportState(isBusy = true, message = "正在准备 CSV…")
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

    fun onReportExportSucceeded() {
        updateExportState(isBusy = false, message = "分析报告已导出。")
    }

    fun onCsvExportSucceeded() {
        updateExportState(isBusy = false, message = "CSV 已导出。")
    }

    fun onExportFailed(message: String) {
        updateExportState(isBusy = false, message = message)
    }

    fun clearExportMessage() {
        exportMessage.value = null
        savedStateHandle[KEY_EXPORT_MESSAGE] = null
    }

    fun createExperimentFromRecord(
        recordId: Long,
        onCreated: (Long) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                val record = recordRepository.getRecord(recordId)
                    ?: uiState.value.records.firstOrNull { it.id == recordId }
                    ?: error("未找到可创建实验的记录。")
                val plan = buildReviewExperimentDraftPlan(record, selectedParameter.value)
                    ?: error("这条记录缺少当前参数，暂时无法生成实验。")
                val projectId = experimentRepository.createProject(plan.draft)
                selectedSection.value = HistorySection.EXPERIMENTS
                savedStateHandle[KEY_SECTION] = HistorySection.EXPERIMENTS.value
                projectId
            }.onSuccess(onCreated)
                .onFailure { error ->
                    onError(error.message ?: "创建实验失败。")
                }
        }
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
    isReadOnlyArchive: Boolean,
    onOpenRecord: (Long, String?) -> Unit,
    onOpenExperimentProject: (Long) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<FileExportPayload?>(null) }

    val reportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
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
            .onSuccess { viewModel.onReportExportSucceeded() }
            .onFailure { error ->
                viewModel.onExportFailed("报告导出失败：${error.message ?: "未知错误"}")
            }
    }

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
            .onSuccess { viewModel.onCsvExportSucceeded() }
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
            isReadOnlyArchive = isReadOnlyArchive,
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
            onOpenExperimentProject = onOpenExperimentProject,
            onCreateExperimentFromRecord = { recordId ->
                if (isReadOnlyArchive) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("只读示范存档不能创建实验。")
                    }
                } else {
                    viewModel.createExperimentFromRecord(
                        recordId = recordId,
                        onCreated = onOpenExperimentProject,
                        onError = { message ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                    )
                }
            },
            onExportReport = {
                coroutineScope.launch {
                    val payload = viewModel.prepareReportExport() ?: return@launch
                    pendingExport = payload
                    reportLauncher.launch(payload.fileName)
                }
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
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AnalysisScreen(
    paddingValues: PaddingValues,
    uiState: ReviewUiState,
    isReadOnlyArchive: Boolean,
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
    onOpenExperimentProject: (Long) -> Unit,
    onCreateExperimentFromRecord: (Long) -> Unit,
    onExportReport: () -> Unit,
    onExportCsv: () -> Unit,
) {
    val listState = rememberLazyListState()
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
    val reviewInsights = remember(uiState.dashboard, uiState.records.size) {
        buildReviewInsights(
            dashboard = uiState.dashboard,
            recordsCount = uiState.records.size,
        )
    }

    fun handleReviewInsightAction(action: ReviewInsightAction) {
        when (action) {
            ReviewInsightAction.StartRecord -> onSectionChange(HistorySection.SAMPLES)
            ReviewInsightAction.OpenSamples -> onSectionChange(HistorySection.SAMPLES)
            ReviewInsightAction.OpenTrends -> onSectionChange(HistorySection.TRENDS)
            ReviewInsightAction.OpenExperiments -> onSectionChange(HistorySection.EXPERIMENTS)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .testTag(QoffeeTestTags.HISTORY_SCREEN),
        contentPadding = PaddingValues(
            start = 10.dp,
            end = 10.dp,
            top = 8.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AnalysisCompactHeader(uiState = uiState)
        }

        item {
            SectionCard(
                title = "复盘行动",
                subtitle = "先看结论，再进入样本、趋势或实验。",
                modifier = Modifier.testTag(QoffeeTestTags.HISTORY_INSIGHTS),
            ) {
                reviewInsights.forEach { insight ->
                    InsightActionCard(
                        title = insight.title,
                        evidence = insight.evidence,
                        actionLabel = insight.primaryLabel,
                        onAction = { handleReviewInsightAction(insight.primaryAction) },
                        secondaryLabel = insight.secondaryLabel,
                        onSecondaryAction = insight.secondaryAction?.let { secondaryAction ->
                            { handleReviewInsightAction(secondaryAction) }
                        },
                    )
                }
            }
        }

        item {
            DashboardArtworkBanner(
                imageRes = R.drawable.art_review_insight,
                height = 92.dp,
            )
        }

        item {
            ReviewToolPanel(
                modifier = Modifier.padding(bottom = 2.dp),
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
                onExportReport = onExportReport,
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
            HistorySection.REPORT -> {
                reportItems(
                    uiState = uiState,
                    isReadOnlyArchive = isReadOnlyArchive,
                    onOpenRecord = onOpenRecord,
                    onCreateExperimentFromRecord = onCreateExperimentFromRecord,
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
                    isReadOnlyArchive = isReadOnlyArchive,
                    onOpenRecord = onOpenRecord,
                    onCreateExperimentFromRecord = onCreateExperimentFromRecord,
                )
            }

            HistorySection.EXPERIMENTS -> {
                experimentItems(
                    uiState = uiState,
                    onOpenRecord = onOpenRecord,
                    onOpenExperimentProject = onOpenExperimentProject,
                )
            }
        }
    }
}

@Composable
private fun AnalysisCompactHeader(uiState: ReviewUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "冲煮质量分析",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = QoffeeDashboardTheme.colors.titleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildFilterSummary(uiState.filter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${uiState.report.summary.sampleCount} 杯",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = uiState.report.sampleQuality.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reportItems(
    uiState: ReviewUiState,
    isReadOnlyArchive: Boolean,
    onOpenRecord: (Long) -> Unit,
    onCreateExperimentFromRecord: (Long) -> Unit,
) {
    val report = uiState.report

    item {
        SectionCard(title = "报告摘要", subtitle = report.filterContext) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "样本质量",
                    value = report.sampleQuality.displayName,
                    supporting = report.sampleQuality.description,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "有效样本",
                    value = report.summary.sampleCount.toString(),
                    supporting = report.summary.lastRecordAt?.let { "最近 ${formatShortDate(it)}" } ?: "暂无评分",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "均分",
                    value = report.scoreDistribution.mean?.let { "${formatScore(it)}/5" } ?: "--",
                    supporting = "当前筛选内总体评分",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "稳定性",
                    value = report.scoreDistribution.standardDeviation?.let { formatScore(it) } ?: "--",
                    supporting = "评分标准差，越低越稳定",
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = report.executiveSummary.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = report.executiveSummary.supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = "完成 ${report.dataQuality.completedRecordCount}")
                StatChip(text = "缺评分 ${report.dataQuality.missingScoreCount}")
                StatChip(text = "可检验参数 ${report.dataQuality.testableParameterCount}")
            }
        }
    }

    item {
        SectionCard(title = "专业结论", subtitle = "每条结论都带样本量、效应量、CI 和 p 值。") {
            if (report.executiveSummary.keyFindings.isEmpty()) {
                Text(
                    text = "当前没有达到显著标准的统计结论。继续补样本，尤其是水温、粉水比、时长和研磨记录。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.executiveSummary.keyFindings.forEach { finding ->
                    InsightLine(
                        title = finding.title,
                        body = "${finding.summary} · ${formatEvidence(finding.evidence)}",
                    )
                }
            }
        }
    }

    item {
        SectionCard(title = "下一杯动作", subtitle = "把报告结论落回记录和单变量验证。") {
            if (report.executiveSummary.nextActions.isEmpty()) {
                Text(
                    text = "继续补齐完成记录与主观评分，保持一次只改一个变量。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.executiveSummary.nextActions.forEachIndexed { index, action ->
                    InsightLine(title = "动作 ${index + 1}", body = action)
                }
            }
        }
    }

    item {
        SectionCard(title = "数据质量", subtitle = "报告只纳入完成且有总评分的记录。") {
            report.dataQuality.notes.ifEmpty {
                listOf("当前样本记录完整度可用于生成报告。")
            }.forEach { note ->
                InsightLine(title = "质量提示", body = note)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                report.dataQuality.missingParameterCounts
                    .filterValues { it > 0 }
                    .toList()
                    .sortedBy { it.second }
                    .take(4)
                    .forEach { (parameter, count) ->
                        StatChip(text = "${parameter.displayName} 缺 $count")
                    }
            }
        }
    }

    item {
        SectionCard(title = "评分表现", subtitle = "均值、分布与近期状态。") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = "中位数 ${report.scoreDistribution.median?.let { formatScore(it) } ?: "--"}")
                StatChip(text = "IQR ${report.scoreDistribution.interquartileRange?.let { formatScore(it) } ?: "--"}")
                StatChip(text = "最高 ${report.scoreDistribution.maxScore ?: "--"}/5")
                StatChip(text = "最低 ${report.scoreDistribution.minScore ?: "--"}/5")
                StatChip(text = "近 5 杯 ${report.scoreDistribution.recentAverage?.let { formatScore(it) } ?: "--"}")
            }
            report.trendFinding?.let { finding ->
                InsightLine(
                    title = finding.title,
                    body = "${finding.summary} · ${formatEvidence(finding.evidence)}",
                )
            }
        }
    }

    item {
        SectionCard(title = "专业图表矩阵", subtitle = "借鉴 intervals.icu 与 betterFly 的多图谱表达，全部映射到杯感、评分和冲煮参数。") {
            ProfessionalChartMatrix(uiState = uiState)
        }
    }

    item {
        SectionCard(title = "品鉴图谱", subtitle = "参考多图表分析样式，但只分析杯感、评分、参数和复做线索。") {
            val charts = report.tastingCharts
            if (charts.scoredSampleCount == 0) {
                Text(
                    text = "当前没有带总评的品鉴样本。补齐总评分和感官维度后，可生成评分分布、感官热力和参数响应图谱。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = "高分样本",
                        value = charts.scoreBuckets
                            .filter { it.score >= 4 }
                            .sumOf { it.count }
                            .toString(),
                        supporting = "4-5 分杯数",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "感官样本",
                        value = charts.dimensionSampleCount.toString(),
                        supporting = "有维度评分的记录",
                        modifier = Modifier.weight(1f),
                    )
                }
                InsightLine(title = "图谱摘要", body = charts.insight)
                ScoreHistogramChart(buckets = charts.scoreBuckets)
                if (charts.dimensionMatrix.isNotEmpty()) {
                    TastingMatrixChart(cells = charts.dimensionMatrix)
                }
                if (charts.balancePoints.isNotEmpty()) {
                    TastingBalanceSummary(points = charts.balancePoints)
                }
                if (charts.concentrationCurve.isNotEmpty()) {
                    ScoreConcentrationChart(points = charts.concentrationCurve)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    charts.dimensionSignals.take(3).forEach { signal ->
                        StatChip(
                            text = "${signal.dimensionLabel} ρ=${signal.finding.evidence.effectSize?.let { formatCoefficient(it) } ?: "--"}",
                        )
                    }
                    charts.parameterResponse
                        .groupBy { it.parameter }
                        .mapNotNull { (parameter, cells) -> cells.maxByOrNull { it.averageOverall }?.let { parameter to it } }
                        .take(4)
                        .forEach { (parameter, cell) ->
                            StatChip(text = "${parameter.displayName} ${cell.zoneLabel} · ${formatScore(cell.averageOverall)}/5")
                        }
                }
                if (charts.dimensionSignals.isEmpty()) {
                    Text(
                        text = "感官维度信号样本不足。至少需要 6 条同一维度评分，才能检验该维度与总评的关系。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    charts.dimensionSignals.take(3).forEach { signal ->
                        InsightLine(
                            title = signal.finding.title,
                            body = "${signal.finding.summary} · ${formatEvidence(signal.finding.evidence)}",
                        )
                    }
                }
            }
        }
    }

    item {
        SectionCard(title = "Sweet Point", subtitle = "从高甜感杯反推可复做的客观参数中心。") {
            SweetPointPanel(report = report.sweetPoint, onOpenRecord = onOpenRecord)
        }
    }

    item {
        SectionCard(title = "消耗与摄入图谱", subtitle = "以记录粉量估算咖啡豆消耗、咖啡因和饮用时间分布。") {
            ConsumptionChartsPanel(report = report.consumptionCharts)
        }
    }

    item {
        SectionCard(title = "质量管理曲线", subtitle = "参考训练平台的长期/短期模型，跟踪咖啡质量状态。") {
            val form = report.qualityForm
            if (form.points.isEmpty()) {
                Text(
                    text = "暂无可生成质量曲线的评分样本。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = "近期质量",
                        value = form.shortTermQuality?.let { "${formatScore(it)}/5" } ?: "--",
                        supporting = "7 天 EWMA",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "长期基线",
                        value = form.longTermQuality?.let { "${formatScore(it)}/5" } ?: "--",
                        supporting = "42 天 EWMA",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = "Form",
                        value = form.formDelta?.let { formatSignedScore(it) } ?: "--",
                        supporting = "近期质量 - 长期基线",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "记录负荷差",
                        value = form.loadDelta?.let { formatSignedScore(it) } ?: "--",
                        supporting = "近期记录完整度 - 长期记录完整度",
                        modifier = Modifier.weight(1f),
                    )
                }
                InsightLine(
                    title = "状态解释",
                    body = when {
                        (form.formDelta ?: 0.0) <= -0.25 -> "近期质量低于长期基线，优先复盘最近低分杯和共同参数漂移。"
                        (form.formDelta ?: 0.0) >= 0.25 -> "近期质量高于长期基线，适合把高分窗口转成可复做实验。"
                        else -> "近期质量接近长期基线，继续观察稳定性和参数分区。"
                    },
                )
            }
        }
    }

    item {
        SectionCard(title = "周期对比", subtitle = "比较当前周期与前一周期的质量和稳定性。") {
            val comparison = report.periodComparison
            if (comparison == null) {
                Text(
                    text = "暂无足够样本进行周期对比。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        label = comparison.currentLabel,
                        value = comparison.currentAverageScore?.let { "${formatScore(it)}/5" } ?: "--",
                        supporting = "样本 ${comparison.currentSampleCount}",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = comparison.previousLabel,
                        value = comparison.previousAverageScore?.let { "${formatScore(it)}/5" } ?: "--",
                        supporting = "样本 ${comparison.previousSampleCount}",
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatChip(text = "均分变化 ${comparison.averageScoreDelta?.let { formatSignedScore(it) } ?: "--"}")
                    StatChip(text = "稳定性变化 ${comparison.consistencyDelta?.let { formatSignedScore(it) } ?: "--"}")
                }
                InsightLine(title = "周期结论", body = comparison.finding)
            }
        }
    }

    item {
        SectionCard(title = "最佳可复做曲线", subtitle = "类似 power curve，但衡量不同样本窗口内的最高可复现质量。") {
            if (report.qualityCurve.isEmpty()) {
                Text(
                    text = "暂无足够样本生成复做窗口曲线。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.qualityCurve.forEach { point ->
                    InsightLine(
                        title = point.label,
                        body = "最佳均分 ${formatScore(point.bestAverageScore)}/5 · ${formatShortDate(point.startAt)}-${formatShortDate(point.endAt)} · 样本 ${point.recordIds.joinToString { "#$it" }}",
                    )
                }
            }
        }
    }

    item {
        SectionCard(title = "感官画像", subtitle = "维度均值和波动帮助判断杯感结构。") {
            if (report.sensoryProfile.isEmpty()) {
                Text(
                    text = "暂无维度评分。补齐香气、酸质、甜感、苦感、醇厚和余韵后可形成感官画像。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.sensoryProfile.forEach { dimension ->
                    InsightLine(
                        title = dimension.label,
                        body = "均值 ${formatScore(dimension.average)}/5 · 标准差 ${formatScore(dimension.standardDeviation)} · 样本 ${dimension.sampleCount}",
                    )
                }
            }
        }
    }

    item {
        SectionCard(title = "参数分区", subtitle = "把水温、粉水比、时长和研磨拆成表现分区。") {
            if (report.parameterZones.isEmpty()) {
                Text(
                    text = "当前没有足够样本生成参数分区。至少需要 5 条带同一参数的评分样本。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.parameterZones.take(4).forEach { zoneReport ->
                    InsightLine(
                        title = zoneReport.parameter.displayName,
                        body = zoneReport.insight,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        zoneReport.zones.forEach { zone ->
                            StatChip(
                                text = "${zone.label} ${formatScore(zone.minValue)}-${formatScore(zone.maxValue)}${zoneReport.parameter.unitLabel} · ${formatScore(zone.averageScore)}/5",
                            )
                        }
                    }
                }
            }
        }
    }

    item {
        SectionCard(title = "关键参数", subtitle = "按参数三分段检验更高评分窗口。") {
            if (report.parameterFindings.isEmpty()) {
                Text(
                    text = "当前没有达到检验门槛的参数窗口。至少需要 12 条带该参数的评分样本。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.parameterFindings.take(5).forEach { window ->
                    InsightLine(
                        title = "${window.parameter.displayName} · ${window.preferredRangeLabel}",
                        body = "${window.finding.summary} · ${formatEvidence(window.finding.evidence)}",
                    )
                    window.finding.action?.let { action ->
                        Text(
                            text = action,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    item {
        SectionCard(title = "分组对比", subtitle = "比较方式、豆子、烘焙、处理法和磨豆机表现。") {
            if (report.segmentFindings.isEmpty()) {
                Text(
                    text = "当前没有达到检验门槛的分组差异。每个目标组与对照组至少各需要 4 条评分样本。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.segmentFindings.take(6).forEach { segment ->
                    InsightLine(
                        title = "${segment.segmentType} · ${segment.segmentName}",
                        body = "${segment.finding.summary} · ${formatEvidence(segment.finding.evidence)}",
                    )
                }
            }
        }
    }

    item {
        SectionCard(title = "离群与参考杯", subtitle = "高分、低分、最近样本和 IQR 离群杯。") {
            if (report.outliers.isEmpty()) {
                Text(
                    text = "暂无可展示的离群或参考样本。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                report.outliers.forEach { item ->
                    CompactWorkbenchCard(
                        title = item.title,
                        subtitle = "${item.subtitle} · ${item.reason}",
                        badge = "${item.score}/5",
                        onClick = { onOpenRecord(item.recordId) },
                    )
                    OutlinedButton(
                        onClick = { onCreateExperimentFromRecord(item.recordId) },
                        enabled = !isReadOnlyArchive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("用这条样本创建实验")
                    }
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
    isReadOnlyArchive: Boolean,
    onOpenRecord: (Long) -> Unit,
    onCreateExperimentFromRecord: (Long) -> Unit,
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
        OutlinedButton(
            onClick = { onCreateExperimentFromRecord(record.id) },
            enabled = !isReadOnlyArchive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("创建单变量实验")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.experimentItems(
    uiState: ReviewUiState,
    onOpenRecord: (Long) -> Unit,
    onOpenExperimentProject: (Long) -> Unit,
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
                        onClick = experiment.id.removePrefix("project-").toLongOrNull()?.let { projectId ->
                            { onOpenExperimentProject(projectId) }
                        },
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
    onExportReport: () -> Unit,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onResetFilters,
                    enabled = hasActiveFilters,
                    modifier = Modifier
                        .semantics { contentDescription = "重置筛选" }
                        .testTag(QoffeeTestTags.ANALYSIS_RESET_BUTTON),
                ) {
                    Text("重置")
                }
                OutlinedButton(
                    onClick = onExportCsv,
                    enabled = !uiState.isExporting,
                    modifier = Modifier.semantics { contentDescription = "导出 CSV" },
                ) {
                    Text("CSV")
                }
                Button(
                    onClick = onExportReport,
                    enabled = !uiState.isExporting,
                    modifier = Modifier
                        .semantics { contentDescription = "导出报告" }
                        .testTag(QoffeeTestTags.ANALYSIS_EXPORT_BUTTON),
                ) {
                    Text(if (uiState.isExporting) "导出中…" else "导出报告")
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = QoffeeDashboardTheme.colors.panelMuted.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
        HistorySection.entries.forEach { section ->
                val selected = section == selectedSection
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSectionChange(section) },
                    color = if (selected) QoffeeDashboardTheme.colors.accentSoft else Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = section.displayName,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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

@Composable
private fun ProfessionalChartMatrix(uiState: ReviewUiState) {
    val scoredRecords = remember(uiState.records) {
        uiState.records
            .filter { it.status == RecordStatus.COMPLETED && it.subjectiveEvaluation?.overall != null }
            .sortedBy { it.brewedAt }
    }
    if (scoredRecords.size < 2) {
        Text(
            text = "至少需要 2 条带总评的品鉴记录，才能生成专业图表矩阵。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var trendMode by remember { mutableStateOf(ChartSeriesMode.LINE) }
    ChartPanel(
        title = "评分趋势",
        subtitle = "折线/柱状切换，对应 betterFly 的趋势图控件。",
        trailing = {
            MiniSegmentedToggle(
                selected = trendMode,
                options = listOf(ChartSeriesMode.LINE, ChartSeriesMode.BAR),
                label = { if (it == ChartSeriesMode.LINE) "折线" else "柱状" },
                onSelected = { trendMode = it },
            )
        },
    ) {
        CoffeeScoreTrendChart(records = scoredRecords, mode = trendMode)
    }

    ChartPanel(
        title = "质量状态曲线",
        subtitle = "把短期质量、长期基线和 Form 画成训练状态曲线，但语义只表示冲煮质量状态。",
    ) {
        QualityStateCurveChart(points = uiState.report.qualityForm.points)
    }

    ChartPanel(
        title = "评分序列分解",
        subtitle = "Observed / Trend / Sweetness / Residual 垂直堆叠，用于分辨长期进步和单杯噪声。",
    ) {
        ScoreDecompositionChart(records = scoredRecords)
    }

    ChartPanel(
        title = "评分自相关 ACF",
        subtitle = "观察高分或低分是否会在后续杯中延续，辅助判断手法稳定性。",
    ) {
        ScoreAutocorrelationChart(records = scoredRecords)
    }

    ChartPanel(
        title = "甜酸四象限",
        subtitle = "X=甜感，Y=酸质，十字线为样本中位数，颜色深浅表示总评。",
    ) {
        SensoryQuadrantScatterChart(records = scoredRecords)
    }

    ChartPanel(
        title = "甜酸极坐标",
        subtitle = "角度表示甜酸倾向，半径表示总评，借鉴极坐标散点图的表达。",
    ) {
        SensoryPolarBalanceChart(records = scoredRecords)
    }

    ChartPanel(
        title = "参数响应山峦",
        subtitle = "按参数和分区展示总评强度，借鉴山峦图/光谱图的密集表达。",
    ) {
        ParameterResponseRidgeChart(cells = uiState.report.tastingCharts.parameterResponse)
    }

    ChartPanel(
        title = "日历质量热力",
        subtitle = "按天聚合评分强度，类似 contribution calendar，但指标是杯感质量。",
    ) {
        DailyQualityCalendarHeatmap(records = scoredRecords)
    }
}

private enum class ChartSeriesMode {
    LINE,
    BAR,
}

@Composable
private fun ChartPanel(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun <T> MiniSegmentedToggle(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            options.forEach { option ->
                Surface(
                    modifier = Modifier.clickable { onSelected(option) },
                    color = if (option == selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    contentColor = if (option == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = label(option),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoffeeScoreTrendChart(
    records: List<CoffeeRecord>,
    mode: ChartSeriesMode,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
    ) {
        val left = 30.dp.toPx()
        val right = 12.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = 26.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - top - bottom
        val labelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        (1..5).forEach { score ->
            val y = top + plotHeight - ((score - 1) / 4f) * plotHeight
            drawLine(
                color = axis.copy(alpha = if (score == 3) 0.24f else 0.12f),
                start = Offset(left, y),
                end = Offset(size.width - right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawContext.canvas.nativeCanvas.drawText("1", 12.dp.toPx(), top + plotHeight + 3.dp.toPx(), labelPaint)
        drawContext.canvas.nativeCanvas.drawText("5", 12.dp.toPx(), top + 3.dp.toPx(), labelPaint)

        val step = plotWidth / records.size.coerceAtLeast(1)
        fun x(index: Int): Float = if (mode == ChartSeriesMode.BAR) {
            left + index * step + step / 2f
        } else {
            left + (index / (records.lastIndex.coerceAtLeast(1)).toFloat()) * plotWidth
        }
        fun y(score: Double): Float = top + plotHeight - (((score - 1.0) / 4.0).toFloat().coerceIn(0f, 1f) * plotHeight)

        if (mode == ChartSeriesMode.BAR) {
            val barWidth = (step * 0.58f).coerceAtLeast(4.dp.toPx())
            records.forEachIndexed { index, record ->
                val score = record.subjectiveEvaluation?.overall?.toDouble() ?: return@forEachIndexed
                val barHeight = (score / 5.0).toFloat() * plotHeight
                val barLeft = x(index) - barWidth / 2f
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(primary, tertiary.copy(alpha = 0.60f))),
                    topLeft = Offset(barLeft, top + plotHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                )
            }
        } else {
            records.windowed(2).forEachIndexed { index, pair ->
                val y1 = y(pair.first().subjectiveEvaluation?.overall?.toDouble() ?: return@forEachIndexed)
                val y2 = y(pair.last().subjectiveEvaluation?.overall?.toDouble() ?: return@forEachIndexed)
                drawLine(
                    color = primary,
                    start = Offset(x(index), y1),
                    end = Offset(x(index + 1), y2),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            records.forEachIndexed { index, record ->
                val score = record.subjectiveEvaluation?.overall?.toDouble() ?: return@forEachIndexed
                drawCircle(
                    color = if (score >= 4.0) tertiary else primary,
                    radius = 4.2.dp.toPx(),
                    center = Offset(x(index), y(score)),
                )
            }
        }
        records.firstOrNull()?.let {
            drawContext.canvas.nativeCanvas.drawText(formatShortDate(it.brewedAt), left, size.height - 6.dp.toPx(), labelPaint)
        }
        records.lastOrNull()?.let {
            drawContext.canvas.nativeCanvas.drawText(formatShortDate(it.brewedAt), size.width - right, size.height - 6.dp.toPx(), labelPaint)
        }
    }
}

@Composable
private fun QualityStateCurveChart(points: List<com.qoffee.core.model.QualityFormPoint>) {
    if (points.size < 2) {
        ChartEmptyText("暂无足够样本生成质量状态曲线。")
        return
    }
    val shortColor = GoogleBlueRefined
    val longColor = GoogleGreenRefined
    val formPositive = GoogleYellowRefined
    val formNegative = GoogleRedRefined
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
    ) {
        val left = 30.dp.toPx()
        val right = 10.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = 28.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - top - bottom
        val scores = points.flatMap { listOf(it.shortTermQuality, it.longTermQuality, it.score) }
        val minScore = (scores.minOrNull() ?: 1.0).coerceAtMost(1.0)
        val maxScore = (scores.maxOrNull() ?: 5.0).coerceAtLeast(5.0)
        val scoreRange = (maxScore - minScore).takeIf { it > 0.001 } ?: 1.0
        val maxForm = points.maxOf { abs(it.formDelta) }.coerceAtLeast(0.3)
        val formZeroY = top + plotHeight * 0.82f

        fun x(index: Int): Float = left + (index / points.lastIndex.coerceAtLeast(1).toFloat()) * plotWidth
        fun scoreY(value: Double): Float = top + plotHeight * 0.64f -
            (((value - minScore) / scoreRange).toFloat().coerceIn(0f, 1f) * plotHeight * 0.58f)
        fun formY(value: Double): Float = formZeroY - (value / maxForm).toFloat().coerceIn(-1f, 1f) * plotHeight * 0.16f

        listOf(0f, 0.5f, 1f).forEach { ratio ->
            val y = top + ratio * plotHeight * 0.64f
            drawLine(axis.copy(alpha = 0.13f), Offset(left, y), Offset(size.width - right, y), 1.dp.toPx())
        }
        drawLine(axis.copy(alpha = 0.24f), Offset(left, formZeroY), Offset(size.width - right, formZeroY), 1.dp.toPx())

        points.forEachIndexed { index, point ->
            val barX = x(index)
            val y = formY(point.formDelta)
            drawLine(
                color = if (point.formDelta >= 0.0) formPositive.copy(alpha = 0.76f) else formNegative.copy(alpha = 0.72f),
                start = Offset(barX, formZeroY),
                end = Offset(barX, y),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        points.windowed(2).forEachIndexed { index, pair ->
            drawLine(shortColor, Offset(x(index), scoreY(pair.first().shortTermQuality)), Offset(x(index + 1), scoreY(pair.last().shortTermQuality)), 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(longColor, Offset(x(index), scoreY(pair.first().longTermQuality)), Offset(x(index + 1), scoreY(pair.last().longTermQuality)), 2.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatChip(text = "蓝=短期质量")
        StatChip(text = "绿=长期基线")
        StatChip(text = "黄/红=Form")
    }
}

@Composable
private fun ScoreDecompositionChart(records: List<CoffeeRecord>) {
    val daily = remember(records) { buildDailyScoreSeries(records) }
    if (daily.size < 3) {
        ChartEmptyText("至少需要 3 个有评分的日期生成分解图。")
        return
    }
    val observed = daily.map { it.score }
    val trend = observed.movingAverage(window = 5)
    val sweetness = daily.map { it.sweetness ?: it.score }
    val residual = observed.mapIndexed { index, value -> value - trend[index] }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniSeriesStrip("Observed", observed, GoogleBlueRefined, symmetric = false)
        MiniSeriesStrip("Trend", trend, GoogleGreenRefined, symmetric = false)
        MiniSeriesStrip("Sweetness", sweetness, GoogleYellowRefined, symmetric = false)
        MiniSeriesStrip("Residual", residual, GoogleRedRefined, symmetric = true)
    }
}

@Composable
private fun MiniSeriesStrip(
    label: String,
    values: List<Double>,
    color: Color,
    symmetric: Boolean,
) {
    if (values.size < 2) return
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(0.24f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Canvas(
            modifier = Modifier
                .weight(0.76f)
                .height(58.dp),
        ) {
            val left = 4.dp.toPx()
            val right = 4.dp.toPx()
            val width = size.width - left - right
            val zeroY = size.height / 2f
            val minValue = if (symmetric) -values.maxOf { abs(it) }.coerceAtLeast(0.1) else values.minOrNull() ?: 0.0
            val maxValue = if (symmetric) values.maxOf { abs(it) }.coerceAtLeast(0.1) else values.maxOrNull() ?: 1.0
            val range = (maxValue - minValue).takeIf { it > 0.0001 } ?: 1.0
            fun x(index: Int): Float = left + (index / values.lastIndex.coerceAtLeast(1).toFloat()) * width
            fun y(value: Double): Float = if (symmetric) {
                zeroY - (value / maxValue).toFloat().coerceIn(-1f, 1f) * size.height * 0.38f
            } else {
                size.height - (((value - minValue) / range).toFloat().coerceIn(0f, 1f) * size.height * 0.78f) - size.height * 0.10f
            }
            drawLine(axis.copy(alpha = 0.16f), Offset(left, if (symmetric) zeroY else size.height * 0.86f), Offset(size.width - right, if (symmetric) zeroY else size.height * 0.86f), 1.dp.toPx())
            values.windowed(2).forEachIndexed { index, pair ->
                drawLine(color, Offset(x(index), y(pair.first())), Offset(x(index + 1), y(pair.last())), 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ScoreAutocorrelationChart(records: List<CoffeeRecord>) {
    val values = records.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }
    val acf = remember(values) { buildAutocorrelation(values, maxLag = 8) }
    if (acf.isEmpty()) {
        ChartEmptyText("至少需要 4 条评分记录生成自相关图。")
        return
    }
    val positiveColor = GoogleBlueRefined
    val negativeColor = GoogleRedRefined
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
    ) {
        val left = 34.dp.toPx()
        val right = 12.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = 28.dp.toPx()
        val plotWidth = size.width - left - right
        val centerY = top + (size.height - top - bottom) / 2f
        val halfHeight = (size.height - top - bottom) * 0.44f
        drawLine(axis.copy(alpha = 0.18f), Offset(left, top), Offset(left, size.height - bottom), 1.dp.toPx())
        drawLine(axis.copy(alpha = 0.30f), Offset(left, centerY), Offset(size.width - right, centerY), 1.dp.toPx())
        acf.forEachIndexed { index, point ->
            val x = left + ((index + 1).toFloat() / acf.size.coerceAtLeast(1)) * plotWidth
            val y = centerY - point.correlation.toFloat().coerceIn(-1f, 1f) * halfHeight
            val color = if (point.correlation >= 0.0) positiveColor else negativeColor
            drawLine(color, Offset(x, centerY), Offset(x, y), 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color, 4.dp.toPx(), Offset(x, y))
        }
    }
    ChartSummaryText("正相关表示相邻杯评分有延续性；负相关可能意味着参数修正后反弹，仍需结合样本量解释。")
}

@Composable
private fun SensoryQuadrantScatterChart(records: List<CoffeeRecord>) {
    val points = remember(records) {
        records.mapNotNull { record ->
            val evaluation = record.subjectiveEvaluation ?: return@mapNotNull null
            val sweetness = evaluation.sweetness?.toDouble() ?: return@mapNotNull null
            val acidity = evaluation.acidity?.toDouble() ?: return@mapNotNull null
            val overall = evaluation.overall?.toDouble() ?: return@mapNotNull null
            SensoryPoint(sweetness, acidity, overall)
        }
    }
    if (points.size < 2) {
        ChartEmptyText("至少需要 2 条带甜感和酸质评分的记录。")
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val medianSweetness = points.map { it.x }.medianValue()
    val medianAcidity = points.map { it.y }.medianValue()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val left = 34.dp.toPx()
        val right = 18.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = 30.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - top - bottom
        fun x(v: Double): Float = left + (((v - 1.0) / 4.0).toFloat().coerceIn(0f, 1f) * plotWidth)
        fun y(v: Double): Float = top + plotHeight - (((v - 1.0) / 4.0).toFloat().coerceIn(0f, 1f) * plotHeight)
        drawLine(axis.copy(alpha = 0.18f), Offset(left, top + plotHeight), Offset(size.width - right, top + plotHeight), 1.dp.toPx())
        drawLine(axis.copy(alpha = 0.18f), Offset(left, top), Offset(left, top + plotHeight), 1.dp.toPx())
        drawLine(axis.copy(alpha = 0.36f), Offset(x(medianSweetness), top), Offset(x(medianSweetness), top + plotHeight), 1.dp.toPx())
        drawLine(axis.copy(alpha = 0.36f), Offset(left, y(medianAcidity)), Offset(size.width - right, y(medianAcidity)), 1.dp.toPx())
        points.forEach { point ->
            val t = ((point.overall - 1.0) / 4.0).toFloat().coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(listOf(tertiary.copy(alpha = 0.9f), primary.copy(alpha = 0.35f))),
                radius = (4 + t * 5).dp.toPx(),
                center = Offset(x(point.x), y(point.y)),
            )
        }
    }
}

@Composable
private fun SensoryPolarBalanceChart(records: List<CoffeeRecord>) {
    val points = remember(records) {
        records.mapNotNull { record ->
            val evaluation = record.subjectiveEvaluation ?: return@mapNotNull null
            val sweetness = evaluation.sweetness?.toDouble() ?: return@mapNotNull null
            val acidity = evaluation.acidity?.toDouble() ?: return@mapNotNull null
            val overall = evaluation.overall?.toDouble() ?: return@mapNotNull null
            SensoryPolarPoint(sweetness = sweetness, acidity = acidity, overall = overall)
        }
    }
    if (points.size < 2) {
        ChartEmptyText("至少需要 2 条带甜感、酸质和总评的记录。")
        return
    }
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val sweetColor = GoogleGreenRefined
    val acidColor = GoogleYellowRefined
    val highColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) * 0.36f
        (1..4).forEach { ring ->
            drawCircle(axis.copy(alpha = 0.10f + ring * 0.035f), radius * ring / 4f, center, style = Stroke(width = 1.dp.toPx()))
        }
        drawLine(sweetColor.copy(alpha = 0.35f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(acidColor.copy(alpha = 0.35f), Offset(center.x, center.y + radius), Offset(center.x, center.y - radius), 1.dp.toPx())
        points.forEach { point ->
            val dx = point.sweetness - 3.0
            val dy = point.acidity - 3.0
            val angle = atan2(dy, dx)
            val balanceRadius = sqrt(dx * dx + dy * dy).coerceAtMost(3.0) / 3.0
            val qualityRadius = ((point.overall - 1.0) / 4.0).coerceIn(0.0, 1.0)
            val r = radius * (0.25 + 0.75 * qualityRadius) * (0.55 + 0.45 * balanceRadius)
            val x = center.x + cos(angle).toFloat() * r.toFloat()
            val y = center.y - sin(angle).toFloat() * r.toFloat()
            drawCircle(
                color = if (point.overall >= 4.0) highColor else axis,
                radius = (4 + qualityRadius * 4).dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
    ChartSummaryText("右侧偏甜，上方偏酸；越靠外且越醒目的点，代表甜酸结构更明确且总评更高。")
}

@Composable
private fun ParameterResponseRidgeChart(cells: List<com.qoffee.core.model.TastingParameterResponseCell>) {
    val groups = cells
        .groupBy { it.parameter }
        .toList()
        .sortedByDescending { (_, values) -> values.maxOfOrNull { it.averageOverall } ?: 0.0 }
        .take(7)
    if (groups.isEmpty()) {
        ChartEmptyText("当前参数响应样本不足。")
        return
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height((groups.size * 30 + 34).dp),
    ) {
        val labelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
            isAntiAlias = true
        }
        val left = 76.dp.toPx()
        val right = 10.dp.toPx()
        val rowHeight = 24.dp.toPx()
        val zoneWidth = (size.width - left - right) / 5f
        groups.forEachIndexed { rowIndex, pair ->
            val parameter = pair.first
            val rowCells = pair.second.sortedBy { it.zoneLabel }
            val y = 20.dp.toPx() + rowIndex * 30.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText(parameter.displayName, 4.dp.toPx(), y + 16.dp.toPx(), labelPaint)
            rowCells.forEachIndexed { index, cell ->
                val x = left + index * zoneWidth
                val intensity = ((cell.averageOverall - 1.0) / 4.0).toFloat().coerceIn(0f, 1f)
                drawRoundRect(
                    color = turboColor(intensity),
                    topLeft = Offset(x, y),
                    size = Size(zoneWidth - 3.dp.toPx(), rowHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun DailyQualityCalendarHeatmap(records: List<CoffeeRecord>) {
    val cells = remember(records) { buildDailyQualityCells(records) }
    if (cells.isEmpty()) {
        ChartEmptyText("暂无日期维度评分。")
        return
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp),
    ) {
        val cellSize = 11.dp.toPx()
        val gap = 3.dp.toPx()
        val left = 26.dp.toPx()
        val top = 14.dp.toPx()
        val labelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 9.dp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        listOf(1 to "一", 3 to "三", 5 to "五", 7 to "日").forEach { (weekday, label) ->
            drawContext.canvas.nativeCanvas.drawText(label, left - 6.dp.toPx(), top + (weekday - 1) * (cellSize + gap) + 9.dp.toPx(), labelPaint)
        }
        cells.forEach { cell ->
            val x = left + cell.weekIndex * (cellSize + gap)
            val y = top + (cell.weekday - 1) * (cellSize + gap)
            drawRoundRect(
                color = cell.score?.let { turboColor(((it - 1.0) / 4.0).toFloat().coerceIn(0f, 1f)) } ?: emptyColor.copy(alpha = 0.56f),
                topLeft = Offset(x, y),
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
    ChartSummaryText("颜色越亮表示当天平均总评越高；空格表示当天没有带总评的记录。")
}

@Composable
private fun ChartEmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 18.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SweetPointPanel(
    report: SweetPointReport,
    onOpenRecord: (Long) -> Unit,
) {
    if (report.sampleCount < 3) {
        Text(
            text = report.insight,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        report.sourceRecords.forEach { record ->
            CompactWorkbenchCard(
                title = "#${record.recordId} ${record.label}",
                subtitle = "${formatDateTime(record.brewedAt)} · ${record.parameterSummary}",
                badge = "${record.score}/5",
                onClick = { onOpenRecord(record.recordId) },
            )
        }
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            label = "Sweet 样本",
            value = report.sampleCount.toString(),
            supporting = report.targetDescription,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "Sweet 均值",
            value = report.averageSweetness?.let { "${formatScore(it)}/5" } ?: "--",
            supporting = "高甜感杯均值",
            modifier = Modifier.weight(1f),
        )
    }
    MetricCard(
        label = "高甜感杯总评",
        value = report.averageOverall?.let { "${formatScore(it)}/5" } ?: "--",
        supporting = "用于判断甜感是否真的转化为整体满意度",
    )
    InsightLine(title = "Sweet Point 结论", body = report.insight)
    if (report.parameterAverages.isNotEmpty()) {
        Text(
            text = "参数中心",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            report.parameterAverages.take(7).forEach { average ->
                StatChip(text = "${average.label} ${formatNumber(average.value)}${average.unitLabel} · n=${average.sampleCount}")
            }
        }
    }
    report.sourceRecords.take(3).forEach { record ->
        CompactWorkbenchCard(
            title = "#${record.recordId} ${record.label}",
            subtitle = "${formatDateTime(record.brewedAt)} · ${record.parameterSummary}",
            badge = "${record.score}/5",
            onClick = { onOpenRecord(record.recordId) },
        )
    }
}

@Composable
private fun ConsumptionChartsPanel(report: ConsumptionChartReport) {
    if (report.recordCount == 0) {
        Text(
            text = report.insight,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            label = "咖啡豆消耗",
            value = "${formatNumber(report.totalDoseG)}g",
            supporting = "当前筛选范围",
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "咖啡因估算",
            value = "${formatNumber(report.estimatedCaffeineMg)}mg",
            supporting = "按 10mg/g 粗估",
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            label = "单杯均粉量",
            value = report.averageDoseG?.let { "${formatNumber(it)}g" } ?: "--",
            supporting = "有粉量记录的杯",
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "高频时段",
            value = report.peakHourLabel ?: "--",
            supporting = "按记录时间聚合",
            modifier = Modifier.weight(1f),
        )
    }
    InsightLine(title = "消耗结论", body = "${report.insight} 咖啡因仅按豆量估算，未区分品种、萃取率和饮用残留。")
    BeanConsumptionBars(buckets = report.beanBuckets)
    HourlySpectrumChart(cells = report.hourlySpectrum)
    WeekdayHeatmapChart(cells = report.weekdayHeatmap)
}

@Composable
private fun BeanConsumptionBars(buckets: List<BeanConsumptionBucket>) {
    if (buckets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "豆子消耗排行",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        buckets.take(6).forEach { bucket ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = bucket.beanName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatNumber(bucket.doseG)}g · ${formatPercent(bucket.share)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(bucket.share.toFloat().coerceIn(0.03f, 1f))
                            .height(10.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.76f),
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlySpectrumChart(cells: List<HourlyConsumptionCell>) {
    if (cells.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "饮用时间光谱",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            cells.forEach { cell ->
                val height = (10 + cell.intensity * 48).toFloat().dp
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height)
                            .background(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f + 0.72f * cell.intensity.toFloat()),
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeatmapChart(cells: List<WeekdayTimeConsumptionCell>) {
    if (cells.isEmpty()) return
    val rows = cells.groupBy { it.weekday }.toSortedMap()
    val bandLabels = cells.distinctBy { it.timeBand }.sortedBy { it.timeBand }.map { it.timeBandLabel }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "周内饮用热力图",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "", modifier = Modifier.weight(0.18f))
            bandLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        rows.forEach { (_, rowCells) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rowCells.firstOrNull()?.weekdayLabel.orEmpty(),
                    modifier = Modifier.weight(0.18f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rowCells.sortedBy { it.timeBand }.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + 0.74f * cell.intensity.toFloat()),
                                shape = MaterialTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (cell.recordCount > 0) cell.recordCount.toString() else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreHistogramChart(buckets: List<TastingScoreBucket>) {
    if (buckets.isEmpty()) return
    val maxCount = buckets.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "评分直方图",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val ratio = (bucket.count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                val barHeight = (ratio * 64f).coerceAtLeast(if (bucket.count > 0) 8f else 2f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight.dp)
                                .background(
                                    color = if (bucket.score >= 4) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.76f)
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                                    },
                                    shape = MaterialTheme.shapes.small,
                                ),
                        )
                    }
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TastingMatrixChart(cells: List<TastingMatrixCell>) {
    val rows = cells.groupBy { "${it.groupType}:${it.groupName}" }.entries.take(5)
    if (rows.isEmpty()) return
    val dimensions = listOf("香气", "酸质", "甜感", "苦感", "醇厚", "余韵")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "感官热力矩阵",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rows.forEach { row ->
            val rowCells = row.value.associateBy { it.dimensionLabel }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.value.firstOrNull()?.let { "${it.groupType} ${it.groupName}" } ?: row.key,
                    modifier = Modifier.weight(0.34f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                dimensions.forEach { dimension ->
                    val cell = rowCells[dimension]
                    val intensity = ((cell?.averageScore ?: 0.0) / 5.0).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(0.11f)
                            .height(22.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f + intensity * 0.68f),
                                shape = MaterialTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = cell?.averageScore?.let { formatScore(it) } ?: "--",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TastingBalanceSummary(points: List<com.qoffee.core.model.TastingBalancePoint>) {
    val highScore = points.filter { it.overall >= 4 }
    val averageSweetness = points.map { it.sweetness }.average()
    val averageAcidity = points.map { it.acidity }.average()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "甜酸平衡散点摘要",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                label = "甜感均值",
                value = formatScore(averageSweetness),
                supporting = "散点横轴",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "酸质均值",
                value = formatScore(averageAcidity),
                supporting = "散点纵轴",
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "高分杯 ${highScore.size} 条。甜感高且酸质清晰的样本适合作为复做参考，甜酸同时偏低的样本优先回看研磨、萃取率和豆子状态。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScoreConcentrationChart(points: List<TastingConcentrationPoint>) {
    if (points.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "高分集中度",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        points.forEach { point ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "前 ${formatPercent(point.sampleShare)}",
                    modifier = Modifier.weight(0.24f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .weight(0.56f)
                        .height(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                            shape = MaterialTheme.shapes.small,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(point.cumulativeScoreShare.toFloat().coerceIn(0f, 1f))
                            .height(10.dp)
                            .background(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.78f),
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
                Text(
                    text = formatPercent(point.cumulativeScoreShare),
                    modifier = Modifier.weight(0.2f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private data class DailyScorePoint(
    val dateKey: String,
    val brewedAt: Long,
    val score: Double,
    val sweetness: Double?,
)

private data class AutocorrelationPoint(
    val lag: Int,
    val correlation: Double,
)

private data class SensoryPoint(
    val x: Double,
    val y: Double,
    val overall: Double,
)

private data class SensoryPolarPoint(
    val sweetness: Double,
    val acidity: Double,
    val overall: Double,
)

private data class DailyQualityCell(
    val weekIndex: Int,
    val weekday: Int,
    val score: Double?,
)

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private fun buildDailyScoreSeries(records: List<CoffeeRecord>): List<DailyScorePoint> {
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    return records
        .mapNotNull { record ->
            val score = record.subjectiveEvaluation?.overall?.toDouble() ?: return@mapNotNull null
            if (record.status != RecordStatus.COMPLETED) return@mapNotNull null
            val sweetness = record.subjectiveEvaluation.sweetness?.toDouble()
            DailyScorePoint(
                dateKey = dayFormat.format(Date(record.brewedAt)),
                brewedAt = record.brewedAt,
                score = score,
                sweetness = sweetness,
            )
        }
        .groupBy { it.dateKey }
        .map { (dateKey, points) ->
            val sweetnessValues = points.mapNotNull { it.sweetness }
            DailyScorePoint(
                dateKey = dateKey,
                brewedAt = points.minOf { it.brewedAt },
                score = points.map { it.score }.average(),
                sweetness = sweetnessValues.takeIf { it.isNotEmpty() }?.average(),
            )
        }
        .sortedBy { it.brewedAt }
}

private fun List<Double>.movingAverage(window: Int): List<Double> {
    if (isEmpty()) return emptyList()
    val radius = (window.coerceAtLeast(1) / 2).coerceAtLeast(1)
    return mapIndexed { index, _ ->
        val start = (index - radius).coerceAtLeast(0)
        val endExclusive = (index + radius + 1).coerceAtMost(size)
        subList(start, endExclusive).average()
    }
}

private fun buildAutocorrelation(values: List<Double>, maxLag: Int): List<AutocorrelationPoint> {
    if (values.size < 4 || maxLag < 1) return emptyList()
    val upperLag = maxLag.coerceAtMost(values.size - 2)
    return (1..upperLag).map { lag ->
        val x = values.dropLast(lag)
        val y = values.drop(lag)
        AutocorrelationPoint(
            lag = lag,
            correlation = pearsonCorrelation(x, y),
        )
    }
}

private fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
    if (x.size != y.size || x.size < 2) return 0.0
    val meanX = x.average()
    val meanY = y.average()
    var numerator = 0.0
    var denominatorX = 0.0
    var denominatorY = 0.0
    x.indices.forEach { index ->
        val dx = x[index] - meanX
        val dy = y[index] - meanY
        numerator += dx * dy
        denominatorX += dx * dx
        denominatorY += dy * dy
    }
    val denominator = sqrt(denominatorX * denominatorY)
    return if (denominator <= 1e-9) 0.0 else (numerator / denominator).coerceIn(-1.0, 1.0)
}

private fun List<Double>.medianValue(): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2.0
    } else {
        sorted[mid]
    }
}

private fun turboColor(t: Float): Color {
    val stops = listOf(
        0.00f to Color(0xFF352A87),
        0.20f to GoogleBlueRefined,
        0.45f to GoogleGreenRefined,
        0.70f to GoogleYellowRefined,
        0.88f to Color(0xFFF97316),
        1.00f to GoogleRedRefined,
    )
    val value = t.coerceIn(0f, 1f)
    val upperIndex = stops.indexOfFirst { it.first >= value }.takeIf { it >= 0 } ?: stops.lastIndex
    if (upperIndex == 0) return stops.first().second
    val lower = stops[upperIndex - 1]
    val upper = stops[upperIndex]
    val span = (upper.first - lower.first).takeIf { it > 0f } ?: 1f
    val ratio = (value - lower.first) / span
    return lerpColor(lower.second, upper.second, ratio)
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t,
    )
}

private fun buildDailyQualityCells(records: List<CoffeeRecord>): List<DailyQualityCell> {
    val scoredRecords = records.filter {
        it.status == RecordStatus.COMPLETED && it.subjectiveEvaluation?.overall != null
    }
    if (scoredRecords.isEmpty()) return emptyList()

    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val scoresByDay = scoredRecords
        .groupBy { dayFormat.format(Date(it.brewedAt)) }
        .mapValues { (_, dayRecords) ->
            dayRecords.mapNotNull { it.subjectiveEvaluation?.overall?.toDouble() }.average()
        }

    val endCalendar = Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = scoredRecords.maxOf { it.brewedAt }
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val endWeekday = normalizedWeekday(endCalendar)
    val startCalendar = endCalendar.clone() as Calendar
    startCalendar.add(Calendar.DAY_OF_YEAR, -(9 * 7 + endWeekday - 1))

    return (0 until 70).map { offset ->
        val day = startCalendar.clone() as Calendar
        day.add(Calendar.DAY_OF_YEAR, offset)
        val dateKey = dayFormat.format(Date(day.timeInMillis))
        DailyQualityCell(
            weekIndex = offset / 7,
            weekday = offset % 7 + 1,
            score = scoresByDay[dateKey],
        )
    }
}

private fun normalizedWeekday(calendar: Calendar): Int {
    return ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
}

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
    return "来自冲煮质量分析报告 · ${uiState.selectedSection.displayName} · ${buildFilterSummary(uiState.filter)} · ${uiState.report.summary.sampleCount} 个样本"
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

private fun formatPercent(value: Double): String {
    return String.format(Locale.CHINA, "%.0f%%", value * 100.0)
}

private fun formatCoefficient(value: Double): String {
    return String.format(Locale.CHINA, "%.2f", value)
}

private fun formatEvidence(evidence: com.qoffee.core.model.StatisticalEvidence): String {
    val reference = evidence.referenceSampleCount?.let { " vs $it" }.orEmpty()
    return buildString {
        append("样本 ${evidence.sampleCount}$reference")
        evidence.effectSize?.let { append(" · 效应 ${formatSignedScore(it)}") }
        if (evidence.confidenceLow != null && evidence.confidenceHigh != null) {
            append(" · 95% CI ${formatSignedScore(evidence.confidenceLow)} 到 ${formatSignedScore(evidence.confidenceHigh)}")
        }
        evidence.pValue?.let { append(" · p=${formatPValue(it)}") }
        append(" · ${evidence.significance.displayName}")
    }
}

private fun formatSignedScore(value: Double): String {
    return String.format(Locale.CHINA, "%+.2f", value)
}

private fun formatPValue(value: Double): String {
    return if (value < 0.001) "<0.001" else String.format(Locale.CHINA, "%.3f", value)
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
