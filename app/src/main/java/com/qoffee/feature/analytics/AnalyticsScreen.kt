package com.qoffee.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.InsightCard
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.RoastLevel
import com.qoffee.core.model.SuggestedNextStep
import com.qoffee.core.model.UserSettings
import com.qoffee.domain.repository.AnalyticsRepository
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.CompactDropdownChip
import com.qoffee.ui.components.CompactFilterBar
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.MethodBarChart
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.ScatterChart
import com.qoffee.ui.components.ScoreTrendChart
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.SubjectiveRadarLikeBars
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class ReviewHighlight(
    val title: String,
    val message: String,
)

data class ReviewUiState(
    val filter: AnalysisFilter = AnalysisFilter(),
    val dashboard: AnalyticsDashboard = AnalyticsDashboard(filter = AnalysisFilter()),
    val records: List<CoffeeRecord> = emptyList(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val selectedParameter: NumericParameter = NumericParameter.WATER_TEMP,
    val settings: UserSettings = UserSettings(),
)

private data class ReviewBaseState(
    val filter: AnalysisFilter,
    val dashboard: AnalyticsDashboard,
    val records: List<CoffeeRecord>,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
    val settings: UserSettings,
)

private data class ReviewIntermediateState(
    val filter: AnalysisFilter,
    val dashboard: AnalyticsDashboard,
    val records: List<CoffeeRecord>,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    analyticsRepository: AnalyticsRepository,
    catalogRepository: CatalogRepository,
    preferenceRepository: PreferenceRepository,
    recordRepository: RecordRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(AnalysisFilter())
    private val selectedParameter = MutableStateFlow(NumericParameter.WATER_TEMP)

    init {
        viewModelScope.launch {
            val settings = preferenceRepository.observeSettings().first()
            filter.value = filter.value.copy(timeRange = settings.defaultAnalysisTimeRange)
        }
    }

    private val settingsFlow = preferenceRepository.observeSettings()

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
            ReviewIntermediateState(
                filter = filterDashboardAndRecords.first,
                dashboard = filterDashboardAndRecords.second,
                records = filterDashboardAndRecords.third,
                beans = beans,
                grinders = emptyList(),
            )
        }
        .combine(catalogRepository.observeGrinderProfiles()) { intermediate, grinders ->
            intermediate.copy(grinders = grinders)
        }
        .combine(settingsFlow) { intermediate, settings ->
            ReviewBaseState(
                filter = intermediate.filter,
                dashboard = intermediate.dashboard,
                records = intermediate.records,
                beans = intermediate.beans,
                grinders = intermediate.grinders,
                settings = settings,
            )
        }

    val uiState: StateFlow<ReviewUiState> = baseStateFlow
        .combine(selectedParameter) { baseState, parameter ->
            ReviewUiState(
                filter = baseState.filter,
                dashboard = baseState.dashboard,
                records = baseState.records,
                beans = baseState.beans,
                grinders = baseState.grinders,
                selectedParameter = parameter,
                settings = baseState.settings,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReviewUiState(),
        )

    fun updateTimeRange(range: AnalysisTimeRange) {
        filter.value = filter.value.copy(timeRange = range)
    }

    fun updateBrewMethod(method: BrewMethod?) {
        filter.value = filter.value.copy(brewMethod = method)
    }

    fun updateBean(beanId: Long?) {
        filter.value = filter.value.copy(beanId = beanId)
    }

    fun updateRoastLevel(roastLevel: RoastLevel?) {
        filter.value = filter.value.copy(roastLevel = roastLevel)
    }

    fun updateProcessMethod(processMethod: BeanProcessMethod?) {
        filter.value = filter.value.copy(processMethod = processMethod)
    }

    fun updateGrinder(grinderId: Long?) {
        filter.value = filter.value.copy(grinderId = grinderId)
    }

    fun updateSelectedParameter(parameter: NumericParameter) {
        selectedParameter.value = parameter
    }
}

@Composable
fun AnalysisRoute(
    paddingValues: PaddingValues,
    onOpenRecord: (Long) -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AnalysisScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onTimeRangeChange = viewModel::updateTimeRange,
        onMethodChange = viewModel::updateBrewMethod,
        onBeanChange = viewModel::updateBean,
        onRoastLevelChange = viewModel::updateRoastLevel,
        onProcessMethodChange = viewModel::updateProcessMethod,
        onGrinderChange = viewModel::updateGrinder,
        onParameterChange = viewModel::updateSelectedParameter,
        onOpenRecord = onOpenRecord,
    )
}

@Composable
private fun AnalysisScreen(
    paddingValues: PaddingValues,
    uiState: ReviewUiState,
    onTimeRangeChange: (AnalysisTimeRange) -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onRoastLevelChange: (RoastLevel?) -> Unit,
    onProcessMethodChange: (BeanProcessMethod?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onParameterChange: (NumericParameter) -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    val completedRecords = uiState.records.filter { it.subjectiveEvaluation?.overall != null }
    val highlights = buildReviewHighlights(uiState.dashboard, completedRecords)
    val recentAverage = completedRecords
        .take(5)
        .mapNotNull { it.subjectiveEvaluation?.overall }
        .takeIf { it.isNotEmpty() }
        ?.average()
    val bestScore = completedRecords.maxOfOrNull { it.subjectiveEvaluation?.overall ?: 0 }
    val topRecords = completedRecords
        .sortedWith(compareByDescending<CoffeeRecord> { it.subjectiveEvaluation?.overall ?: 0 }.thenByDescending { it.brewedAt })
        .take(3)

    DashboardPage(
        paddingValues = paddingValues,
        testTag = QoffeeTestTags.ANALYSIS_SCREEN,
    ) {
        PageHeader(
            title = "分析驾驶舱",
            subtitle = "从你的记录里提炼稳定区间、趋势变化和高分样本。",
            eyebrow = "QOFFEE / ANALYTICS",
        )

        SectionCard(
            title = "复盘摘要",
            subtitle = "先看样本规模、近期状态与当前上限，再决定怎么继续微调。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "分析样本",
                    value = uiState.dashboard.summary.sampleCount.toString(),
                    supporting = uiState.dashboard.summary.lastRecordAt?.let { "最近记录 ${formatDate(it)}" } ?: "还没有有效样本",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "近期平均",
                    value = recentAverage?.let { String.format(Locale.CHINA, "%.1f", it) } ?: "--",
                    supporting = "最近 5 杯主观总分",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "当前上限",
                    value = bestScore?.toString() ?: "--",
                    supporting = "当前已记录最高分",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "方法覆盖",
                    value = uiState.dashboard.summary.methodCount.toString(),
                    supporting = "参与分析的冲煮方式",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionCard(
            title = "筛选范围",
            subtitle = "通过时间、方式、豆子和设备收窄你的复盘视角。",
            modifier = Modifier.testTag(QoffeeTestTags.ANALYSIS_FILTERS),
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
                    label = "烘焙度",
                    selectedLabel = uiState.filter.roastLevel?.displayName,
                    options = RoastLevel.entries.map { DropdownOption(it.displayName, it) },
                    onSelected = onRoastLevelChange,
                )
                CompactDropdownChip(
                    label = "处理法",
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

        if (!uiState.dashboard.hasEnoughData) {
            EmptyStateCard(
                title = "当前还不够进入复盘",
                subtitle = "先完成几条包含主观评分的记录，这里才会逐步长出趋势、洞察和最佳杯提示。",
            )
            return@DashboardPage
        }

        SectionCard(
            title = "这段时间的结论",
            subtitle = "把当前样本里最值得注意的趋势提炼成几条简洁结论。",
        ) {
            highlights.forEach { highlight ->
                Text(text = highlight.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = highlight.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = "评分趋势",
            subtitle = "先看分数走势，再结合样本规模判断当前参数是否稳定。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(text = "样本 ${uiState.dashboard.summary.sampleCount}")
                StatChip(text = "方式 ${uiState.dashboard.summary.methodCount}")
                uiState.dashboard.summary.lastRecordAt?.let { StatChip(text = "最近 ${formatDate(it)}") }
            }
            ScoreTrendChart(points = uiState.dashboard.timelinePoints)
        }

        SectionCard(
            title = "方式与豆子表现",
            subtitle = "查看常用方式的平均成绩，以及当前最常记录豆子的整体状态。",
        ) {
            Text(
                text = buildBeanPerformanceText(completedRecords),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MethodBarChart(values = uiState.dashboard.methodAverages)
        }

        SectionCard(
            title = "关键参数关联",
            subtitle = "切换不同客观参数，观察它与总分之间的关系。",
        ) {
            DropdownField(
                label = "散点图参数",
                selectedLabel = uiState.selectedParameter.displayName,
                options = NumericParameter.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { selected -> selected?.let(onParameterChange) },
                allowClear = false,
            )
            ScatterChart(
                points = uiState.dashboard.scatterSeries[uiState.selectedParameter].orEmpty(),
                xLabel = uiState.selectedParameter.displayName,
            )
            Text(text = "主观维度均值", style = MaterialTheme.typography.titleMedium)
            SubjectiveRadarLikeBars(values = uiState.dashboard.dimensionAverages)
        }

        SectionCard(
            title = "最佳杯与波动提示",
            subtitle = "把最值得回看的高分样本和异常波动样本并列展示。",
        ) {
            if (uiState.dashboard.insightCards.isEmpty() && uiState.dashboard.outlierInsights.isEmpty()) {
                Text(
                    text = "样本还在积累中，继续记录后，这里会更明确地区分稳定区间和异常波动。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.dashboard.insightCards.take(3).forEach { insight ->
                    InsightItem(
                        insight = insight,
                        showConfidence = uiState.settings.showInsightConfidence,
                    )
                }
                uiState.dashboard.outlierInsights.take(2).forEach { insight ->
                    Text(text = insight.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = insight.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { onOpenRecord(insight.recordId) }) {
                        Text("查看记录")
                    }
                }
            }
        }

        if (topRecords.isNotEmpty()) {
            SectionCard(
                title = "最近高分记录",
                subtitle = "把值得复用的参数组合放到最近高分样本里反复查看。",
            ) {
                topRecords.forEach { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "${record.beanNameSnapshot ?: "未命名咖啡豆"} · ${record.brewMethod?.displayName ?: "未指定方式"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = buildString {
                                    append("总分 ${record.subjectiveEvaluation?.overall ?: "--"} / 10")
                                    record.waterTempC?.let {
                                        append(" · ")
                                        append(String.format(Locale.CHINA, "%.0f", it))
                                        append("°C")
                                    }
                                    record.brewRatio?.let {
                                        append(" · 粉水比 ")
                                        append(String.format(Locale.CHINA, "%.1f", it))
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { onOpenRecord(record.id) }) {
                            Text("查看")
                        }
                    }
                }
            }
        }

        if (uiState.dashboard.suggestedNextSteps.isNotEmpty()) {
            SectionCard(
                title = "下一步建议",
                subtitle = "把当前样本转成下一轮最值得做的实验方向。",
            ) {
                uiState.dashboard.suggestedNextSteps.forEach { step ->
                    NextStepItem(step)
                }
            }
        }
    }
}

@Composable
private fun InsightItem(
    insight: InsightCard,
    showConfidence: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = insight.title, style = MaterialTheme.typography.titleMedium)
        Text(text = insight.message, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(text = "样本 ${insight.sampleCount}")
            if (showConfidence) {
                StatChip(text = "置信度 ${insight.confidence.displayName}")
            }
            StatChip(text = insight.filterContext)
        }
    }
}

@Composable
private fun NextStepItem(step: SuggestedNextStep) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = step.title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = step.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildReviewHighlights(
    dashboard: AnalyticsDashboard,
    records: List<CoffeeRecord>,
): List<ReviewHighlight> {
    val bestScore = records.maxOfOrNull { it.subjectiveEvaluation?.overall ?: 0 }
    val recentAverage = records
        .take(5)
        .mapNotNull { it.subjectiveEvaluation?.overall }
        .takeIf { it.isNotEmpty() }
        ?.average()

    return buildList {
        add(
            ReviewHighlight(
                title = "样本规模",
                message = "当前共有 ${dashboard.summary.sampleCount} 条可分析样本，最近一次记录在 ${dashboard.summary.lastRecordAt?.let(::formatDate) ?: "--"}。",
            ),
        )
        recentAverage?.let {
            add(
                ReviewHighlight(
                    title = "最近状态",
                    message = "最近 5 杯平均总分 ${String.format(Locale.CHINA, "%.1f", it)}，适合继续围绕当前参数带做微调。",
                ),
            )
        }
        bestScore?.takeIf { it > 0 }?.let {
            add(
                ReviewHighlight(
                    title = "当前上限",
                    message = "已记录到最高 ${it} 分的杯子，可以从高分记录里回看参数区间。",
                ),
            )
        }
    }
}

private fun buildBeanPerformanceText(records: List<CoffeeRecord>): String {
    val topBean = records
        .filter { !it.beanNameSnapshot.isNullOrBlank() }
        .groupBy { it.beanNameSnapshot.orEmpty() }
        .maxByOrNull { it.value.size }

    return topBean?.let { (beanName, beanRecords) ->
        val scored = beanRecords.mapNotNull { it.subjectiveEvaluation?.overall }
        val average = scored.takeIf { it.isNotEmpty() }?.average()
        if (average == null) {
            "最近最常记录的是 $beanName，共 ${beanRecords.size} 杯。"
        } else {
            "最近最常记录的是 $beanName，共 ${beanRecords.size} 杯，平均总分 ${String.format(Locale.CHINA, "%.1f", average)}。"
        }
    } ?: "继续积累不同豆子的记录，复盘页会逐渐看出你更稳定的风味偏好。"
}

private fun formatDate(timestampMillis: Long): String {
    return java.text.SimpleDateFormat("M/d", java.util.Locale.CHINA).format(java.util.Date(timestampMillis))
}
