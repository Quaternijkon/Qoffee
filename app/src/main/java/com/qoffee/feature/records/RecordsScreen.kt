package com.qoffee.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private data class RecordsIntermediateState(
    val filter: AnalysisFilter,
    val records: List<CoffeeRecord>,
    val beans: List<BeanProfile>,
)

@HiltViewModel
class RecordsViewModel @Inject constructor(
    recordRepository: RecordRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(AnalysisFilter(timeRange = AnalysisTimeRange.ALL))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val recordsFlow = filter.flatMapLatest { current ->
        recordRepository.observeRecords(current)
    }

    val uiState: StateFlow<RecordsUiState> = filter
        .combine(recordsFlow) { currentFilter, records ->
            currentFilter to records
        }
        .combine(catalogRepository.observeBeanProfiles()) { filterAndRecords, beans ->
            RecordsIntermediateState(
                filter = filterAndRecords.first,
                records = filterAndRecords.second,
                beans = beans,
            )
        }
        .combine(catalogRepository.observeGrinderProfiles()) { intermediate, grinders ->
            RecordsUiState(
                filter = intermediate.filter,
                records = intermediate.records,
                beans = intermediate.beans,
                grinders = grinders,
            )
        }
        .stateIn(
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
    isReadOnlyArchive: Boolean,
    viewModel: RecordsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecordsScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        isReadOnlyArchive = isReadOnlyArchive,
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
    isReadOnlyArchive: Boolean,
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
            title = "把参数和感受放进同一条记录",
            subtitle = "客观参数会先保存为草稿，主观感受可以稍后补充，复盘也更连贯。",
        )

        SectionCard(title = "记录筛选") {
            DropdownField(
                label = "时间范围",
                selectedLabel = uiState.filter.timeRange.displayName,
                options = AnalysisTimeRange.entries.map { DropdownOption(it.displayName, it) },
                onSelected = { it?.let(onTimeRangeChange) },
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
                label = "磨豆机",
                selectedLabel = uiState.grinders.firstOrNull { it.id == uiState.filter.grinderId }?.name,
                options = uiState.grinders.map { DropdownOption(it.name, it.id) },
                onSelected = onGrinderChange,
            )
        }

        if (uiState.records.isEmpty()) {
            EmptyStateCard(
                title = "还没有记录",
                subtitle = if (isReadOnlyArchive) {
                    "当前处于只读示范存档，可以先浏览数据分析，再复制为自己的存档。"
                } else {
                    "点击右下角按钮创建第一条冲煮记录。"
                },
            )
        } else {
            uiState.records.forEach { record ->
                RecordSummaryCard(
                    record = record,
                    isReadOnlyArchive = isReadOnlyArchive,
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
    isReadOnlyArchive: Boolean,
    onOpenDetail: () -> Unit,
    onContinue: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
    val scoreFormat = DecimalFormat("0.0")
    SectionCard(
        title = buildString {
            append(record.brewMethod?.displayName ?: "草稿")
            if (!record.beanNameSnapshot.isNullOrBlank()) append(" | ${record.beanNameSnapshot}")
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(text = if (record.status == RecordStatus.DRAFT) "草稿" else "已完成")
            record.subjectiveEvaluation?.overall?.let { StatChip(text = "总体 $it / 10") }
            record.brewRatio?.let { StatChip(text = "粉水比 ${scoreFormat.format(it)}") }
        }
        Text(
            text = "时间 ${formatter.format(Date(record.brewedAt))}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = buildString {
                append("粉量 ${record.coffeeDoseG ?: "-"}g")
                append(" | 水量 ${record.brewWaterMl ?: "-"}ml")
                record.waterTempC?.let { append(" | ${it}°C") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenDetail) {
                Text("详情")
            }
            if (!isReadOnlyArchive) {
                Button(onClick = if (record.status == RecordStatus.DRAFT) onContinue else onDuplicate) {
                    Text(if (record.status == RecordStatus.DRAFT) "继续填写" else "复制一份")
                }
            }
        }
    }
}
