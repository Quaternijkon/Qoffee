package com.qoffee.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanProfile
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RoastLevel
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.components.DropdownField
import com.qoffee.ui.components.DropdownOption
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.HeroCard
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class RecordsUiState(
    val filter: AnalysisFilter = AnalysisFilter(timeRange = AnalysisTimeRange.ALL),
    val records: List<CoffeeRecord> = emptyList(),
    val beans: List<BeanProfile> = emptyList(),
    val grinders: List<GrinderProfile> = emptyList(),
)

@HiltViewModel
class RecordsViewModel @Inject constructor(
    recordRepository: RecordRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

    val uiState: StateFlow<RecordsUiState> = combine(
        filter,
        filter.flatMapLatest { current -> recordRepository.observeRecords(current) },
        catalogRepository.observeBeanProfiles(),
        catalogRepository.observeGrinderProfiles(),
    ) { currentFilter, records, beans, grinders ->
        RecordsUiState(
            filter = currentFilter,
            records = records,
            beans = beans,
            grinders = grinders,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsUiState(),
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

    fun updateRoastLevel(roastLevel: RoastLevel?) {
        filter.value = filter.value.copy(roastLevel = roastLevel)
    }

    fun updateGrinder(grinderId: Long?) {
        filter.value = filter.value.copy(grinderId = grinderId)
    }
}

@Composable
fun RecordsRoute(
    paddingValues: PaddingValues,
    onOpenDetail: (Long) -> Unit,
    onOpenEditor: (Long?, Long?) -> Unit,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecordsScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        onTimeRangeChange = viewModel::updateTimeRange,
        onMethodChange = viewModel::updateMethod,
        onBeanChange = viewModel::updateBean,
        onRoastLevelChange = viewModel::updateRoastLevel,
        onGrinderChange = viewModel::updateGrinder,
        onOpenDetail = onOpenDetail,
        onOpenEditor = onOpenEditor,
    )
}

@Composable
private fun RecordsScreen(
    paddingValues: PaddingValues,
    uiState: RecordsUiState,
    onTimeRangeChange: (AnalysisTimeRange) -> Unit,
    onMethodChange: (BrewMethod?) -> Unit,
    onBeanChange: (Long?) -> Unit,
    onRoastLevelChange: (RoastLevel?) -> Unit,
    onGrinderChange: (Long?) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenEditor: (Long?, Long?) -> Unit,
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
            title = "Keep one record for brew and taste",
            subtitle = "Objective parameters save as a draft instantly, then subjective notes can be added later.",
        )

        SectionCard(title = "Record filters") {
            DropdownField(
                label = "Time range",
                selectedLabel = uiState.filter.timeRange.displayName,
                options = AnalysisTimeRange.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { it?.let(onTimeRangeChange) },
                allowClear = false,
            )
            DropdownField(
                label = "Brew method",
                selectedLabel = uiState.filter.brewMethod?.displayName,
                options = BrewMethod.entries.map { DropdownOption(it.displayName, it) },
                onSelected = onMethodChange,
            )
            DropdownField(
                label = "Bean",
                selectedLabel = uiState.beans.firstOrNull { it.id == uiState.filter.beanId }?.name,
                options = uiState.beans.map { DropdownOption(it.name, it.id) },
                onSelected = onBeanChange,
            )
            DropdownField(
                label = "Roast level",
                selectedLabel = uiState.filter.roastLevel?.displayName,
                options = RoastLevel.entries.map { DropdownOption(it.displayName, it) },
                onSelected = onRoastLevelChange,
            )
            DropdownField(
                label = "Grinder",
                selectedLabel = uiState.grinders.firstOrNull { it.id == uiState.filter.grinderId }?.name,
                options = uiState.grinders.map { DropdownOption(it.name, it.id) },
                onSelected = onGrinderChange,
            )
        }

        if (uiState.records.isEmpty()) {
            EmptyStateCard(
                title = "No records yet",
                subtitle = "Use the floating action button to create the first brew log.",
            )
        } else {
            uiState.records.forEach { record ->
                RecordSummaryCard(
                    record = record,
                    onOpenDetail = { onOpenDetail(record.id) },
                    onContinue = { onOpenEditor(record.id, null) },
                    onDuplicate = { onOpenEditor(null, record.id) },
                )
            }
        }
    }
}

@Composable
private fun RecordSummaryCard(
    record: CoffeeRecord,
    onOpenDetail: () -> Unit,
    onContinue: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val scoreFormat = DecimalFormat("0.0")
    SectionCard(
        title = buildString {
            append(record.brewMethod?.displayName ?: "Draft")
            if (!record.beanNameSnapshot.isNullOrBlank()) append(" | ${record.beanNameSnapshot}")
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(text = if (record.status == RecordStatus.DRAFT) "Draft" else "Completed")
            record.subjectiveEvaluation?.overall?.let { StatChip(text = "Overall $it / 10") }
            record.brewRatio?.let { StatChip(text = "Ratio ${scoreFormat.format(it)}") }
        }
        Text(
            text = "Time ${formatter.format(Date(record.brewedAt))}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = buildString {
                append("Dose ${record.coffeeDoseG ?: "-"}g")
                append(" | Water ${record.brewWaterMl ?: "-"}ml")
                record.waterTempC?.let { append(" | ${it}C") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenDetail) {
                Text("Details")
            }
            Button(onClick = if (record.status == RecordStatus.DRAFT) onContinue else onDuplicate) {
                Text(if (record.status == RecordStatus.DRAFT) "Continue" else "Duplicate")
            }
        }
    }
}
