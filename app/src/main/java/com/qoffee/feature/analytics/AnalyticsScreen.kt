package com.qoffee.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.InsightCard
import com.qoffee.core.model.NumericParameter
import com.qoffee.core.model.RoastLevel
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
    private val dashboardFlow = filter.flatMapLatest { currentFilter ->
        analyticsRepository.observeDashboard(currentFilter)
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        filter,
        dashboardFlow,
        catalogRepository.observeBeanProfiles(),
        catalogRepository.observeGrinderProfiles(),
        settingsFlow,
        selectedParameter,
    ) { currentFilter, dashboard, beans, grinders, settings, parameter ->
        AnalyticsUiState(
            filter = currentFilter,
            dashboard = dashboard,
            beans = beans,
            grinders = grinders,
            selectedParameter = parameter,
            settings = settings,
        )
    }.stateIn(
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

    fun updateGrinder(grinderId: Long?) {
        filter.value = filter.value.copy(grinderId = grinderId)
    }

    fun updateSelectedParameter(parameter: NumericParameter) {
        selectedParameter.value = parameter
    }
}

@Composable
fun AnalyticsRoute(
    paddingValues: PaddingValues,
    _onOpenRecord: (Long) -> Unit,
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
            title = "Turn logs into review sessions",
            subtitle = "Qoffee compares your parameters with subjective scores and surfaces simple local insights.",
        )

        FilterSection(
            filter = uiState.filter,
            beans = uiState.beans,
            grinders = uiState.grinders,
            onTimeRangeChange = onTimeRangeChange,
            onMethodChange = onMethodChange,
            onBeanChange = onBeanChange,
            onRoastLevelChange = onRoastLevelChange,
            onGrinderChange = onGrinderChange,
        )

        if (!uiState.dashboard.hasEnoughData) {
            EmptyStateCard(
                title = "Not enough rated samples yet",
                subtitle = "Complete a few records with both objective parameters and subjective scores first.",
            )
            return@Column
        }

        SectionCard(title = "Overview") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip(text = "Samples ${uiState.dashboard.sampleCount}")
                StatChip(text = uiState.filter.timeRange.displayName)
            }
        }

        SectionCard(title = "Insights") {
            if (uiState.dashboard.insightCards.isEmpty()) {
                Text(
                    text = "The current filter does not yet meet the automatic insight threshold.",
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

        SectionCard(title = "Charts") {
            Text(text = "Average overall score by brew method", style = MaterialTheme.typography.titleMedium)
            MethodBarChart(values = uiState.dashboard.methodAverages)

            Text(text = "Overall score trend", style = MaterialTheme.typography.titleMedium)
            ScoreTrendChart(points = uiState.dashboard.timelinePoints)

            DropdownField(
                label = "Scatter parameter",
                selectedLabel = uiState.selectedParameter.displayName,
                options = NumericParameter.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { selected -> selected?.let(onParameterChange) },
                allowClear = false,
            )
            ScatterChart(
                points = uiState.dashboard.scatterSeries[uiState.selectedParameter].orEmpty(),
                xLabel = uiState.selectedParameter.displayName,
            )

            Text(text = "Average subjective dimensions", style = MaterialTheme.typography.titleMedium)
            SubjectiveRadarLikeBars(values = uiState.dashboard.dimensionAverages)
        }
    }
}

@Composable
private fun FilterSection(
    filter: AnalysisFilter,
    beans: List<BeanProfile>,
    grinders: List<GrinderProfile>,
    onTimeRangeChange: (AnalysisTimeRange) -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onRoastLevelChange: (RoastLevel?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
) {
    SectionCard(title = "Filters") {
        DropdownField(
            label = "Time range",
            selectedLabel = filter.timeRange.displayName,
            options = AnalysisTimeRange.entries.map { DropdownOption(it.displayName, it) },
            onSelected = { selected -> selected?.let(onTimeRangeChange) },
            allowClear = false,
        )
        DropdownField(
            label = "Brew method",
            selectedLabel = filter.brewMethod?.displayName,
            options = BrewMethod.entries.map { DropdownOption(it.displayName, it) },
            onSelected = onMethodChange,
        )
        DropdownField(
            label = "Bean",
            selectedLabel = beans.firstOrNull { it.id == filter.beanId }?.name,
            options = beans.map { DropdownOption(it.name, it.id) },
            onSelected = onBeanChange,
        )
        DropdownField(
            label = "Roast level",
            selectedLabel = filter.roastLevel?.displayName,
            options = RoastLevel.entries.map { DropdownOption(it.displayName, it) },
            onSelected = onRoastLevelChange,
        )
        DropdownField(
            label = "Grinder",
            selectedLabel = grinders.firstOrNull { it.id == filter.grinderId }?.name,
            options = grinders.map { DropdownOption(it.name, it.id) },
            onSelected = onGrinderChange,
        )
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
            StatChip(text = "n=${insight.sampleCount}")
            if (showConfidence) {
                StatChip(text = "Confidence ${insight.confidence.displayName}")
            }
            StatChip(text = insight.filterContext)
        }
    }
}
