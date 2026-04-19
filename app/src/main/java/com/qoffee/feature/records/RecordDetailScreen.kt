package com.qoffee.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.LabeledValue
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RecordDetailUiState(
    val record: CoffeeRecord? = null,
    val comparison: RecordComparisonSummary? = null,
    val beanHistorySummary: String? = null,
)

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    recordRepository: RecordRepository,
) : ViewModel() {
    private val recordId = checkNotNull(savedStateHandle.get<Long>(QoffeeDestinations.recordIdArg))

    val uiState: StateFlow<RecordDetailUiState> = combine(
        recordRepository.observeRecord(recordId),
        recordRepository.observeRecords(AnalysisFilter(timeRange = AnalysisTimeRange.ALL)),
    ) { record, allRecords ->
        RecordDetailUiState(
            record = record,
            comparison = record?.let { current ->
                findPreviousComparableRecord(allRecords, current)?.let { previous ->
                    buildComparisonSummary(current, previous)
                }
            },
            beanHistorySummary = record?.beanProfileId?.let { beanId ->
                buildBeanHistorySummary(allRecords, beanId)
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordDetailUiState(),
    )
}

@Composable
fun RecordDetailRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    isReadOnlyArchive: Boolean,
    viewModel: RecordDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecordDetailScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        isReadOnlyArchive = isReadOnlyArchive,
        onBack = onBack,
        onEdit = onEdit,
        onDuplicate = onDuplicate,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordDetailScreen(
    paddingValues: PaddingValues,
    uiState: RecordDetailUiState,
    isReadOnlyArchive: Boolean,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
) {
    val record = uiState.record
    if (record == null) {
        EmptyStateCard(
            title = "未找到这条记录",
            subtitle = "这条记录可能还在加载，或者已经不存在了。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }

    DashboardPage(paddingValues = paddingValues) {
        PageHeader(
            title = record.beanNameSnapshot ?: (record.brewMethod?.displayName ?: "咖啡记录"),
            subtitle = "${record.brewMethod?.displayName ?: "未指定方式"} · ${formatter.format(Date(record.brewedAt))}",
            eyebrow = "QOFFEE / CUP REPORT",
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            if (!isReadOnlyArchive) {
                Button(onClick = { onEdit(record.id) }) { Text("编辑") }
                OutlinedButton(onClick = { onDuplicate(record.id) }) { Text("再冲一杯") }
            }
        }

        SectionCard(
            title = "本次记录",
            subtitle = "先看这一杯的核心客观参数，再结合主观结果判断是否值得复用。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "总分",
                    value = record.subjectiveEvaluation?.overall?.toString() ?: "--",
                    supporting = "主观总体评分",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "粉水比",
                    value = record.brewRatio?.let { String.format(Locale.CHINA, "%.1f", it) } ?: "--",
                    supporting = "客观冲煮比值",
                    modifier = Modifier.weight(1f),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                record.beanRoastLevelSnapshot?.let { StatChip(text = it.displayName) }
                record.beanProcessMethodSnapshot?.let { StatChip(text = it.displayName) }
                record.recipeNameSnapshot?.let { StatChip(text = "配方 $it") }
                record.grinderNameSnapshot?.let { StatChip(text = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "粉量", value = record.coffeeDoseG?.let { "${formatNumber(it)} g" }.orEmpty())
                LabeledValue(label = "水量", value = record.brewWaterMl?.let { "${formatNumber(it)} ml" }.orEmpty())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "水温", value = record.waterTempC?.let { "${formatNumber(it)} °C" }.orEmpty())
                LabeledValue(label = "研磨", value = record.grindSetting?.let(::formatNumber).orEmpty())
            }
            LabeledValue(
                label = "咖啡豆",
                value = record.beanNameSnapshot.orEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (record.notes.isNotBlank()) {
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = "主观感受",
            subtitle = "把各维度评分、风味标签和文字备注整合成复盘结果。",
        ) {
            if (record.subjectiveEvaluation == null || record.subjectiveEvaluation.isEmpty()) {
                Text(
                    text = "这条记录还没有填写主观感受。",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                val evaluation = record.subjectiveEvaluation
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "香气", value = evaluation.aroma?.toString().orEmpty())
                    LabeledValue(label = "酸质", value = evaluation.acidity?.toString().orEmpty())
                    LabeledValue(label = "甜感", value = evaluation.sweetness?.toString().orEmpty())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "苦感", value = evaluation.bitterness?.toString().orEmpty())
                    LabeledValue(label = "醇厚", value = evaluation.body?.toString().orEmpty())
                    LabeledValue(label = "余韵", value = evaluation.aftertaste?.toString().orEmpty())
                }
                if (evaluation.flavorTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        evaluation.flavorTags.forEach { tag -> StatChip(text = tag.name) }
                    }
                }
                if (evaluation.notes.isNotBlank()) {
                    Text(
                        text = evaluation.notes,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        uiState.comparison?.let { comparison ->
            SectionCard(
                title = "和上一杯相比",
                subtitle = "快速判断这次调整是否产生了正向变化。",
            ) {
                Text(text = comparison.headline, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = comparison.details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        uiState.beanHistorySummary?.let { summary ->
            SectionCard(
                title = "同豆历史表现",
                subtitle = "把这支豆子的长期表现放到当前复盘语境里。",
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}
