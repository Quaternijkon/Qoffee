package com.qoffee.feature.records

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.qoffee.core.model.formatNormalizedGrind
import com.qoffee.domain.repository.GuideRepository
import com.qoffee.domain.repository.RecordRepository
import com.qoffee.ui.QoffeeTestTags
import com.qoffee.ui.components.DashboardPage
import com.qoffee.ui.components.EmptyStateCard
import com.qoffee.ui.components.GrindNormalizationChart
import com.qoffee.ui.components.LabeledValue
import com.qoffee.ui.components.RecordReportHeader
import com.qoffee.ui.components.SectionCard
import com.qoffee.ui.components.StatChip
import com.qoffee.ui.components.WaterCurveChart
import com.qoffee.ui.components.WaterCurveSummaryList
import com.qoffee.ui.navigation.QoffeeDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val guideRepository: GuideRepository,
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

    fun createGuide(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(guideRepository.createGuideFromRecord(recordId))
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
    onGuideCreated: (Long) -> Unit,
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
        onCreateGuide = { viewModel.createGuide(onGuideCreated) },
    )
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
    onCreateGuide: () -> Unit,
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

    val report = remember(record) { buildRecordReport(record) }
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
    val grindCurve = record.grinderProfile?.normalization?.buildCurve(
        minSetting = record.grinderProfile.minSetting,
        maxSetting = record.grinderProfile.maxSetting,
    )

    DashboardPage(paddingValues = paddingValues) {
        OutlinedButton(onClick = onBack) {
            Text("返回")
        }

        RecordReportHeader(
            title = report.title,
            subtitle = report.subtitle,
            scoreText = report.scoreText,
            parameters = report.parameters,
            modifier = Modifier.testTag(QoffeeTestTags.RECORD_REPORT_HEADER),
        )

        if (!isReadOnlyArchive) {
            SectionCard(
                title = "下一步",
                subtitle = "把这杯直接变成下一次行动。",
                modifier = Modifier.testTag(QoffeeTestTags.RECORD_REPORT_REUSE_ACTIONS),
            ) {
                Button(
                    onClick = { onDuplicate(record.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(RecordReuseAction.DUPLICATE.label)
                }
                OutlinedButton(
                    onClick = { showSaveRecipeDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(RecordReuseAction.SAVE_AS_RECIPE.label)
                }
                if (record.recipeTemplateId != null && !record.recipeNameSnapshot.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = onOverwriteSourceRecipe,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(RecordReuseAction.OVERWRITE_RECIPE.label)
                    }
                }
                OutlinedButton(
                    onClick = onCreateGuide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(RecordReuseAction.CREATE_GUIDE.label)
                }
                OutlinedButton(
                    onClick = { onEdit(record.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(RecordReuseAction.EDIT.label)
                }
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
            title = "客观参数",
            subtitle = "把这杯的记录快照集中展示，方便和复盘结论对应起来。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "咖啡豆", value = record.beanNameSnapshot.orUnknown(), modifier = Modifier.weight(1f))
                LabeledValue(label = "研磨机", value = record.grinderNameSnapshot.orUnknown(), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "粉量", value = record.coffeeDoseG.formatUnit("g"), modifier = Modifier.weight(1f))
                LabeledValue(label = "萃取水量", value = record.brewWaterMl.formatUnit("ml"), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "总水量", value = record.totalWaterMl.formatUnit("ml"), modifier = Modifier.weight(1f))
                LabeledValue(label = "水温", value = record.waterTempC.formatUnit("°C"), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledValue(label = "研磨", value = record.grindSetting.formatPlain(), modifier = Modifier.weight(1f))
                LabeledValue(label = "时长", value = record.brewDurationSeconds.formatDurationLabel(), modifier = Modifier.weight(1f))
            }
            record.normalizedGrindSetting?.let {
                LabeledValue(label = "归一化研磨", value = formatNormalizedGrind(it), modifier = Modifier.fillMaxWidth())
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
                title = "冲煮曲线",
                subtitle = "在同一时间轴查看注水与温度变化。",
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

        if (grindCurve != null) {
            SectionCard(
                title = "研磨归一化",
                subtitle = "把原始格数映射到统一 0~1 坐标。",
            ) {
                record.normalizedGrindSetting?.let {
                    StatChip(text = "当前 ${record.grindSetting?.let(::formatNumber) ?: "--"} -> ${formatNormalizedGrind(it)}")
                }
                GrindNormalizationChart(curve = grindCurve)
            }
        }

        SectionCard(
            title = "主观感受",
            subtitle = "集中查看评分、标签与备注。",
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

        uiState.comparison?.let { comparison ->
            SectionCard(
                title = "与上一杯相比",
                subtitle = "快速判断本次调整是否有效。",
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
                subtitle = "查看同一支豆子的长期表现。",
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
                subtitle = "删除后不可恢复，建议只在确认不再需要这条样本时操作。",
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

private fun Double?.formatUnit(unit: String): String {
    return this?.let { "${formatNumber(it)}$unit" } ?: "未记录"
}

private fun Double?.formatPlain(): String {
    return this?.let(::formatNumber) ?: "未记录"
}

private fun Int?.formatDurationLabel(): String {
    val value = this ?: return "未记录"
    val minutes = value / 60
    val seconds = value % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun String?.orUnknown(): String {
    return this?.takeIf { it.isNotBlank() } ?: "未记录"
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
            OutlinedTextField(
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
