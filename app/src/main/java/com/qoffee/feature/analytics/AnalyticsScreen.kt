package com.qoffee.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.InsightCard
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.RoastLevel
import com.qoffee.core.model.SuggestedNextStep
import com.qoffee.core.model.UserSettings
import com.qoffee.domain.repository.AnalyticsRepository
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.PreferenceRepository
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.HeroCard
import com.qoffee.ui.components.MethodBarChart
import com.qoffee.ui.components.ScatterChart
import com.qoffee.ui.components.ScoreTrendChart
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.SubjectiveRadarLikeBars
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class AnalyticsUiState(
    val filter: AnalysisFilter = AnalysisFilter(),
    val dashboard: AnalyticsDashboard = AnalyticsDashboard(filter = AnalysisFilter()),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
    val selectedParameter: NumericParameter = NumericParameter.WATER_TEMP,
    val settings: UserSettings = UserSettings(),
)

private data class AnalyticsBaseState(
    val filter: AnalysisFilter,
    val dashboard: AnalyticsDashboard,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
    val settings: UserSettings,
)

private data class AnalyticsIntermediateState(
    val filter: AnalysisFilter,
    val dashboard: AnalyticsDashboard,
    val beans: List<BeanProfile>,
    val grinders: List<GrinderProfile>,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    analyticsRepository: AnalyticsRepository,
    catalogRepository: CatalogRepository,
    preferenceRepository: PreferenceRepository,
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

    private val baseStateFlow = filter
        .combine(dashboardFlow) { currentFilter, dashboard ->
            currentFilter to dashboard
        }
        .combine(catalogRepository.observeBeanProfiles()) { filterAndDashboard, beans ->
            Triple(filterAndDashboard.first, filterAndDashboard.second, beans)
        }
        .combine(catalogRepository.observeGrinderProfiles()) { filterDashboardAndBeans, grinders ->
            AnalyticsIntermediateState(
                filter = filterDashboardAndBeans.first,
                dashboard = filterDashboardAndBeans.second,
                beans = filterDashboardAndBeans.third,
                grinders = grinders,
            )
        }
        .combine(settingsFlow) { intermediate, settings ->
            AnalyticsBaseState(
                filter = intermediate.filter,
                dashboard = intermediate.dashboard,
                beans = intermediate.beans,
                grinders = intermediate.grinders,
                settings = settings,
            )
        }

    val uiState: StateFlow<AnalyticsUiState> = baseStateFlow
        .combine(selectedParameter) { baseState, parameter ->
            AnalyticsUiState(
                filter = baseState.filter,
                dashboard = baseState.dashboard,
                beans = baseState.beans,
                grinders = baseState.grinders,
                selectedParameter = parameter,
                settings = baseState.settings,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnalyticsUiState(),
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
@Suppress("UNUSED_PARAMETER")
fun AnalyticsRoute(
    paddingValues: PaddingValues,
    onOpenRecord: (Long) -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AnalyticsScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onTimeRangeChange = viewModel::updateTimeRange,
        onMethodChange = viewModel::updateBrewMethod,
        onBeanChange = viewModel::updateBean,
        onRoastLevelChange = viewModel::updateRoastLevel,
        onProcessMethodChange = viewModel::updateProcessMethod,
        onGrinderChange = viewModel::updateGrinder,
        onParameterChange = viewModel::updateSelectedParameter,
    )
}

@Composable
private fun AnalyticsScreen(
    paddingValues: PaddingValues,
    uiState: AnalyticsUiState,
    onTimeRangeChange: (AnalysisTimeRange) -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onRoastLevelChange: (RoastLevel?) -> Unit,
    onProcessMethodChange: (BeanProcessMethod?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onParameterChange: (NumericParameter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(
            title = "把记录变成复盘",
            subtitle = "Qoffee 会对比参数和主观评分，帮你在本地找到更清晰的规律。",
        )

        SectionCard(title = "筛选条件") {
            DropdownField(
                label = "时间范围",
                selectedLabel = uiState.filter.timeRange.displayName,
                options = AnalysisTimeRange.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { selected -> selected?.let(onTimeRangeChange) },
                allowClear = false,
            )
            DropdownField(
                label = "制作方式",
                selectedLabel = uiState.filter.brewMethod?.displayName,
                options = BrewMethod.entries.map { DropdownOption(it.displayName, it) },
                onSelected = onMethodChange,
            )
            DropdownField(
                label = "咖啡豆",
                selectedLabel = uiState.beans.firstOrNull { it.id == uiState.filter.beanId }?.name,
                options = uiState.beans.map { DropdownOption(it.name, it.id) },
                onSelected = onBeanChange,
            )
            DropdownField(
                label = "烘焙度",
                selectedLabel = uiState.filter.roastLevel?.displayName,
                options = RoastLevel.entries.map { DropdownOption(it.displayName, it) },
                onSelected = onRoastLevelChange,
            )
            DropdownField(
                label = "处理法",
                selectedLabel = uiState.filter.processMethod?.displayName,
                options = BeanProcessMethod.entries.map { DropdownOption(it.displayName, it) },
                onSelected = onProcessMethodChange,
            )
            DropdownField(
                label = "磨豆机",
                selectedLabel = uiState.grinders.firstOrNull { it.id == uiState.filter.grinderId }?.name,
                options = uiState.grinders.map { DropdownOption(it.name, it.id) },
                onSelected = onGrinderChange,
            )
        }

        if (!uiState.dashboard.hasEnoughData) {
            EmptyStateCard(
                title = "可分析样本还不够",
                subtitle = "先完成几条同时包含客观参数和主观评分的记录，再来看分析会更有帮助。",
            )
            return@Column
        }

        SectionCard(title = "摘要") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(text = "样本 ${uiState.dashboard.summary.sampleCount}")
                StatChip(text = "豆子 ${uiState.dashboard.summary.beanCount}")
                StatChip(text = "磨豆机 ${uiState.dashboard.summary.grinderCount}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(text = "方式 ${uiState.dashboard.summary.methodCount}")
                uiState.dashboard.summary.firstRecordAt?.let { StatChip(text = "开始于 ${formatDate(it)}") }
                uiState.dashboard.summary.lastRecordAt?.let { StatChip(text = "更新于 ${formatDate(it)}") }
            }
        }

        SectionCard(title = "关键洞察") {
            if (uiState.dashboard.insightCards.isEmpty()) {
                Text(
                    text = "当前筛选结果还没有达到自动洞察阈值，继续记录后会看到更完整的分析。",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                uiState.dashboard.insightCards.forEach { insight ->
                    InsightItem(
                        insight = insight,
                        showConfidence = uiState.settings.showInsightConfidence,
                    )
                }
            }
        }

        SectionCard(title = "关系图谱") {
            Text(text = "不同制作方式的平均总体评分", style = MaterialTheme.typography.titleMedium)
            MethodBarChart(values = uiState.dashboard.methodAverages)

            Text(text = "总体评分趋势", style = MaterialTheme.typography.titleMedium)
            ScoreTrendChart(points = uiState.dashboard.timelinePoints)

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

        if (uiState.dashboard.suggestedNextSteps.isNotEmpty()) {
            SectionCard(title = "下一杯实验建议") {
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

private fun formatDate(timestampMillis: Long): String {
    return java.text.SimpleDateFormat("M/d", java.util.Locale.CHINA).format(java.util.Date(timestampMillis))
}
