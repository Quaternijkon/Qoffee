package com.qoffee.feature.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.analyze
import com.qoffee.core.model.deriveValues
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.LabeledValue
import com.qoffee.ui.components.MetricCard
import com.qoffee.ui.components.PageHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.WaterCurveChart
import com.qoffee.ui.components.WaterCurveSummaryList
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
import kotlinx.coroutines.launch

data class RecordDetailUiState(
    val record: CoffeeRecord? = null,
    val comparison: RecordComparisonSummary? = null,
    val beanHistorySummary: String? = null,
)

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordRepository: RecordRepository,
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

    fun deleteRecord(onDeleted: () -> Unit) {
        viewModelScope.launch {
            recordRepository.deleteRecord(recordId)
            onDeleted()
        }
    }

    fun saveRecordAsRecipe(name: String) {
        val normalized = name.trim()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            recordRepository.saveRecordAsRecipe(recordId = recordId, name = normalized)
        }
    }

    fun overwriteSourceRecipe() {
        val record = uiState.value.record ?: return
        val recipeId = record.recipeTemplateId ?: return
        val recipeName = record.recipeNameSnapshot ?: return
        viewModelScope.launch {
            recordRepository.saveRecordAsRecipe(
                recordId = recordId,
                name = recipeName,
                targetRecipeId = recipeId,
            )
        }
    }
}

@Composable
fun RecordDetailRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDeleted: () -> Unit,
    isReadOnlyArchive: Boolean,
    reviewContext: String?,
    viewModel: RecordDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecordDetailScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        isReadOnlyArchive = isReadOnlyArchive,
        reviewContext = reviewContext,
        onBack = onBack,
        onEdit = onEdit,
        onDuplicate = onDuplicate,
        onDelete = { viewModel.deleteRecord(onDeleted) },
        onSaveAsRecipe = viewModel::saveRecordAsRecipe,
        onOverwriteSourceRecipe = viewModel::overwriteSourceRecipe,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordDetailScreenLegacy(
    paddingValues: PaddingValues,
    uiState: RecordDetailUiState,
    isReadOnlyArchive: Boolean,
    reviewContext: String?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: () -> Unit,
    onSaveAsRecipe: (String) -> Unit,
    onOverwriteSourceRecipe: () -> Unit,
) {
    RecordDetailScreen(
        paddingValues = paddingValues,
        uiState = uiState,
        isReadOnlyArchive = isReadOnlyArchive,
        reviewContext = reviewContext,
        onBack = onBack,
        onEdit = onEdit,
        onDuplicate = onDuplicate,
        onDelete = onDelete,
        onSaveAsRecipe = onSaveAsRecipe,
        onOverwriteSourceRecipe = onOverwriteSourceRecipe,
    )
    return
    /*
    val record = uiState.record
    if (record == null) {
        EmptyStateCard(
            title = "未找到这条记录",
            subtitle = "这条记录可能仍在加载，或已经被删除。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSaveRecipeDialog by remember { mutableStateOf(false) }

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
            }
        }

        reviewContext?.takeIf { it.isNotBlank() }?.let { contextText ->
            SectionCard(
                title = "复盘上下文",
                subtitle = "你是从复盘看板进入这条样本的，返回后会保留原来的筛选与分段。",
            ) {
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = "复盘摘要",
            subtitle = "先看总分、粉水比和关键标签，再决定是否值得复做。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "总体评分",
                    value = record.subjectiveEvaluation?.overall?.let { "$it/5" } ?: "--",
                    supporting = "统一按 5 分制展示",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "粉水比",
                    value = record.brewRatio?.let(::formatNumber) ?: "--",
                    supporting = "当前记录的客观比值",
                    modifier = Modifier.weight(1f),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                record.brewMethod?.let { StatChip(text = it.displayName) }
                record.beanRoastLevelSnapshot?.let { StatChip(text = it.displayName) }
                record.beanProcessMethodSnapshot?.let { StatChip(text = it.displayName) }
                record.recipeNameSnapshot?.let { StatChip(text = "配方 $it") }
                record.grinderNameSnapshot?.let { StatChip(text = it) }
            }
        }

        SectionCard(
            title = "客观参数",
            subtitle = "把这杯的记录快照集中展示，方便和复盘结论对应起来。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "咖啡豆", value = record.beanNameSnapshot.orEmpty(), modifier = Modifier.weight(1f))
                LabeledValue(label = "磨豆机", value = record.grinderNameSnapshot.orEmpty(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "粉量", value = record.coffeeDoseG?.let { "${formatNumber(it)} g" }.orEmpty(), modifier = Modifier.weight(1f))
                LabeledValue(label = "水量", value = record.brewWaterMl?.let { "${formatNumber(it)} ml" }.orEmpty(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "水温", value = record.waterTempC?.let { "${formatNumber(it)} °C" }.orEmpty(), modifier = Modifier.weight(1f))
                LabeledValue(label = "研磨", value = record.grindSetting?.let(::formatNumber).orEmpty(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "总水量", value = record.totalWaterMl?.let { "${formatNumber(it)} ml" }.orEmpty(), modifier = Modifier.weight(1f))
                LabeledValue(label = "时长", value = record.brewDurationSeconds?.let { "${it}s" }.orEmpty(), modifier = Modifier.weight(1f))
            }
            if (record.notes.isNotBlank()) {
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = "主观感受",
            subtitle = "把维度评分、风味标签和文字备注统一到同一个复盘区域。",
        ) {
            val evaluation = record.subjectiveEvaluation
            if (evaluation == null || evaluation.isEmpty()) {
                Text(
                    text = "这条记录还没有填写主观感受。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "香气", value = evaluation.aroma?.toString().orEmpty(), modifier = Modifier.weight(1f))
                    LabeledValue(label = "酸质", value = evaluation.acidity?.toString().orEmpty(), modifier = Modifier.weight(1f))
                    LabeledValue(label = "甜感", value = evaluation.sweetness?.toString().orEmpty(), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "苦感", value = evaluation.bitterness?.toString().orEmpty(), modifier = Modifier.weight(1f))
                    LabeledValue(label = "醇厚", value = evaluation.body?.toString().orEmpty(), modifier = Modifier.weight(1f))
                    LabeledValue(label = "余韵", value = evaluation.aftertaste?.toString().orEmpty(), modifier = Modifier.weight(1f))
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!isReadOnlyArchive) {
            SectionCard(
                title = "如何复用这条记录",
                subtitle = "记录是核心对象，配方只是它的客观参数复用版本。",
            ) {
                OutlinedButton(
                    onClick = { showSaveRecipeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为配方")
                }
                if (record.recipeTemplateId != null && !record.recipeNameSnapshot.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onOverwriteSourceRecipe,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("覆盖原配方")
                    }
                }
                Button(
                    onClick = { onDuplicate(record.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("再冲一杯")
                }
            }
        }

        uiState.comparison?.let { comparison ->
            SectionCard(
                title = "与上一杯相比",
                subtitle = "快速判断本次参数调整是否产生了正向变化。",
            ) {
                Text(
                    text = comparison.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = comparison.details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        uiState.beanHistorySummary?.let { summary ->
            SectionCard(
                title = "同豆历史表现",
                subtitle = "把同一支豆子的长期表现放回这次复盘语境里。",
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!isReadOnlyArchive) {
            SectionCard(
                title = "记录管理",
                subtitle = "删除操作不可撤销，建议先确认这条样本不再需要。",
            ) {
                OutlinedButton(onClick = { showDeleteConfirm = true }) {
                    Text("删除这条记录")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除记录") },
            text = { Text("这会永久删除当前记录及其主观评价，确认继续吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showSaveRecipeDialog) {
        SaveRecordAsRecipeDialog(
            initialName = record.recipeNameSnapshot ?: buildDefaultRecipeName(record),
            onDismiss = { showSaveRecipeDialog = false },
            onConfirm = { name ->
                onSaveAsRecipe(name)
                showSaveRecipeDialog = false
            },
        )
    }
    */
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordDetailScreen(
    paddingValues: PaddingValues,
    uiState: RecordDetailUiState,
    isReadOnlyArchive: Boolean,
    reviewContext: String?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: () -> Unit,
    onSaveAsRecipe: (String) -> Unit,
    onOverwriteSourceRecipe: () -> Unit,
) {
    val record = uiState.record
    if (record == null) {
        EmptyStateCard(
            title = "未找到这条记录",
            subtitle = "这条记录可能仍在加载，或已经被删除。",
            modifier = Modifier.padding(paddingValues),
        )
        return
    }

    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSaveRecipeDialog by remember { mutableStateOf(false) }
    val waterCurveDerivedValues = record.waterCurve?.deriveValues(record.coffeeDoseG)
    val waterCurveAnalysis = record.waterCurve?.analyze(
        coffeeDoseG = record.coffeeDoseG,
        grindSetting = record.grindSetting,
        grinderProfile = record.grinderProfile,
        roastLevel = record.beanRoastLevelSnapshot,
        brewMethod = record.brewMethod,
    )

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
            }
        }

        reviewContext?.takeIf { it.isNotBlank() }?.let { contextText ->
            SectionCard(
                title = "复盘上下文",
                subtitle = "你是从复盘看板进入这条样本的，返回后会保留原来的筛选与分段。",
            ) {
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(
            title = "复盘摘要",
            subtitle = "先看总分、粉水比和关键标签，再决定是否值得复做。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "总体评分",
                    value = record.subjectiveEvaluation?.overall?.let { "$it/5" } ?: "--",
                    supporting = "统一按 5 分制展示",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "粉水比",
                    value = record.brewRatio?.let(::formatNumber) ?: "--",
                    supporting = "当前记录的客观比值",
                    modifier = Modifier.weight(1f),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                record.brewMethod?.let { StatChip(text = it.displayName) }
                record.beanRoastLevelSnapshot?.let { StatChip(text = it.displayName) }
                record.beanProcessMethodSnapshot?.let { StatChip(text = it.displayName) }
                record.recipeNameSnapshot?.let { StatChip(text = "配方 $it") }
                record.grinderNameSnapshot?.let { StatChip(text = it) }
            }
        }

        SectionCard(
            title = "客观参数",
            subtitle = "把这杯的记录快照集中展示，方便和复盘结论对应起来。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "咖啡豆", value = record.beanNameSnapshot.orEmpty(), modifier = Modifier.weight(1f))
                LabeledValue(label = "磨豆机", value = record.grinderNameSnapshot.orEmpty(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "粉量", value = record.coffeeDoseG?.let { "${formatNumber(it)} g" }.orEmpty(), modifier = Modifier.weight(1f))
                LabeledValue(label = "萃取水", value = record.brewWaterMl?.let { "${formatNumber(it)} ml" }.orEmpty(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "水温", value = record.waterTempC?.let { "${formatNumber(it)} °C" }.orEmpty(), modifier = Modifier.weight(1f))
                LabeledValue(label = "研磨", value = record.grindSetting?.let(::formatNumber).orEmpty(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "总水量", value = record.totalWaterMl?.let { "${formatNumber(it)} ml" }.orEmpty(), modifier = Modifier.weight(1f))
                LabeledValue(label = "时长", value = record.brewDurationSeconds?.let { "${it}s" }.orEmpty(), modifier = Modifier.weight(1f))
            }
            if (record.notes.isNotBlank()) {
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        record.waterCurve?.let { curve ->
            SectionCard(
                title = "冲煮多曲线",
                subtitle = "把这杯的注水、系统温度和咖啡因估算放到同一时间轴里，方便复盘节奏与热量变化。",
            ) {
                WaterCurveChart(
                    curve = curve,
                    derivedValues = waterCurveDerivedValues,
                    analysis = waterCurveAnalysis,
                    modifier = Modifier.fillMaxWidth(),
                )
                WaterCurveSummaryList(
                    curve = curve,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SectionCard(
            title = "主观感受",
            subtitle = "把维度评分、风味标签和文字备注统一到同一个复盘区域。",
        ) {
            val evaluation = record.subjectiveEvaluation
            if (evaluation == null || evaluation.isEmpty()) {
                Text(
                    text = "这条记录还没有填写主观感受。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "香气", value = evaluation.aroma?.toString().orEmpty(), modifier = Modifier.weight(1f))
                    LabeledValue(label = "酸质", value = evaluation.acidity?.toString().orEmpty(), modifier = Modifier.weight(1f))
                    LabeledValue(label = "甜感", value = evaluation.sweetness?.toString().orEmpty(), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledValue(label = "苦感", value = evaluation.bitterness?.toString().orEmpty(), modifier = Modifier.weight(1f))
                    LabeledValue(label = "醇厚", value = evaluation.body?.toString().orEmpty(), modifier = Modifier.weight(1f))
                    LabeledValue(label = "余韵", value = evaluation.aftertaste?.toString().orEmpty(), modifier = Modifier.weight(1f))
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!isReadOnlyArchive) {
            SectionCard(
                title = "如何复用这条记录",
                subtitle = "记录是核心对象，配方只是它的客观参数复用版本。",
            ) {
                OutlinedButton(
                    onClick = { showSaveRecipeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("设为配方")
                }
                if (record.recipeTemplateId != null && !record.recipeNameSnapshot.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onOverwriteSourceRecipe,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("覆盖原配方")
                    }
                }
                Button(
                    onClick = { onDuplicate(record.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("再冲一杯")
                }
            }
        }

        uiState.comparison?.let { comparison ->
            SectionCard(
                title = "与上一杯相比",
                subtitle = "快速判断本次参数调整是否产生了正向变化。",
            ) {
                Text(
                    text = comparison.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = comparison.details.joinToString(" 路 "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        uiState.beanHistorySummary?.let { summary ->
            SectionCard(
                title = "同豆历史表现",
                subtitle = "把同一支豆子的长期表现放回这次复盘语境里。",
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!isReadOnlyArchive) {
            SectionCard(
                title = "记录管理",
                subtitle = "删除操作不可撤销，建议先确认这条样本不再需要。",
            ) {
                OutlinedButton(onClick = { showDeleteConfirm = true }) {
                    Text("删除这条记录")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除记录") },
            text = { Text("这会永久删除当前记录及其主观评价，确认继续吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showSaveRecipeDialog) {
        SaveRecordAsRecipeDialog(
            initialName = record.recipeNameSnapshot ?: buildDefaultRecipeName(record),
            onDismiss = { showSaveRecipeDialog = false },
            onConfirm = { name ->
                onSaveAsRecipe(name)
                showSaveRecipeDialog = false
            },
        )
    }
}

private fun formatNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}

@Composable
private fun SaveRecordAsRecipeDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设为配方") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("配方名称") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun buildDefaultRecipeName(record: CoffeeRecord): String {
    val beanName = record.beanNameSnapshot ?: "未命名豆子"
    val methodName = record.brewMethod?.displayName ?: "记录"
    return "$beanName $methodName"
}
