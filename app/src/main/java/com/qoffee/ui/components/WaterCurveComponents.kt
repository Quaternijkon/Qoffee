package com.qoffee.ui.components

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.BypassStage
import com.qoffee.core.model.PourStage
import com.qoffee.core.model.ThermalContainerType
import com.qoffee.core.model.WaitStage
import com.qoffee.core.model.WaterCurve
import com.qoffee.core.model.WaterCurveAnalysis
import com.qoffee.core.model.WaterCurveAnalysisPoint
import com.qoffee.core.model.WaterCurveDerivedValues
import com.qoffee.core.model.WaterCurveStage
import com.qoffee.core.model.WaterCurveTemperatureMode
import com.qoffee.core.model.formatWaterCurveDuration
import com.qoffee.core.model.formatWaterCurveNumber
import com.qoffee.core.model.stageSummaryLines
import com.qoffee.core.model.validate
import kotlin.math.ln
import kotlin.math.max

enum class WaterCurveStageKind(val displayName: String) {
    POUR("注水"),
    WAIT("等待"),
    BYPASS("旁路"),
}

data class WaterCurveStageEditorState(
    val kind: WaterCurveStageKind,
    val endTimeSeconds: Int? = null,
    val cumulativeWaterText: String = "",
    val waterText: String = "",
    val quickTemperatureText: String = "",
    val startTempText: String = "",
    val endTempText: String = "",
    val ambientStartTempText: String = "",
    val ambientEndTempText: String = "",
    val showAdvancedTemperature: Boolean = false,
)

data class WaterCurveFormResult(
    val curve: WaterCurve? = null,
    val errors: List<String> = emptyList(),
)

fun WaterCurve?.toEditorStageStates(): List<WaterCurveStageEditorState> {
    return this?.stages?.map { stage ->
        when (stage) {
            is PourStage -> WaterCurveStageEditorState(
                kind = WaterCurveStageKind.POUR,
                endTimeSeconds = stage.endTimeSeconds,
                cumulativeWaterText = formatOptionalCurveNumber(stage.cumulativeWaterMl),
                quickTemperatureText = formatOptionalCurveNumber(stage.quickTemperatureC),
                startTempText = formatOptionalCurveNumber(stage.startTempC),
                endTempText = formatOptionalCurveNumber(stage.endTempC),
            )

            is WaitStage -> WaterCurveStageEditorState(
                kind = WaterCurveStageKind.WAIT,
                endTimeSeconds = stage.endTimeSeconds,
                startTempText = formatOptionalCurveNumber(stage.startTempC),
                endTempText = formatOptionalCurveNumber(stage.endTempC),
                ambientStartTempText = formatOptionalCurveNumber(stage.ambientStartTempC),
                ambientEndTempText = formatOptionalCurveNumber(stage.ambientEndTempC),
            )

            is BypassStage -> WaterCurveStageEditorState(
                kind = WaterCurveStageKind.BYPASS,
                waterText = formatOptionalCurveNumber(stage.waterMl),
                quickTemperatureText = formatOptionalCurveNumber(stage.quickTemperatureC),
                startTempText = formatOptionalCurveNumber(stage.startTempC),
                endTempText = formatOptionalCurveNumber(stage.endTempC),
            )
        }
    }.orEmpty()
}

fun createDefaultWaterCurveStage(
    kind: WaterCurveStageKind,
    existingStages: List<WaterCurveStageEditorState>,
): WaterCurveStageEditorState {
    val lastTimedSeconds = existingStages.mapNotNull(WaterCurveStageEditorState::endTimeSeconds).maxOrNull() ?: 0
    val lastPourCumulative = existingStages
        .asSequence()
        .filter { it.kind == WaterCurveStageKind.POUR }
        .mapNotNull { it.cumulativeWaterText.toDoubleOrNull() }
        .lastOrNull()
        ?: 0.0
    return when (kind) {
        WaterCurveStageKind.POUR -> WaterCurveStageEditorState(
            kind = kind,
            endTimeSeconds = (lastTimedSeconds + 30).coerceAtLeast(30),
            cumulativeWaterText = formatWaterCurveNumber(max(100.0, lastPourCumulative + 100.0)),
        )

        WaterCurveStageKind.WAIT -> WaterCurveStageEditorState(
            kind = kind,
            endTimeSeconds = (lastTimedSeconds + 30).coerceAtLeast(30),
        )

        WaterCurveStageKind.BYPASS -> WaterCurveStageEditorState(
            kind = kind,
            waterText = "100",
        )
    }
}

fun buildWaterCurveFormResult(
    temperatureMode: WaterCurveTemperatureMode,
    ambientTempText: String,
    containerType: ThermalContainerType?,
    stages: List<WaterCurveStageEditorState>,
    brewMethod: BrewMethod?,
): WaterCurveFormResult {
    if (stages.isEmpty()) {
        return WaterCurveFormResult()
    }
    val parsedStages = mutableListOf<WaterCurveStage>()
    val errors = mutableListOf<String>()
    stages.forEachIndexed { index, stage ->
        val stageNumber = index + 1
        when (stage.kind) {
            WaterCurveStageKind.POUR -> {
                val endTime = stage.endTimeSeconds
                val cumulativeWater = stage.cumulativeWaterText.toDoubleOrNull()
                when {
                    endTime == null -> errors += "第${stageNumber}段注水的到达时间未填写。"
                    cumulativeWater == null -> errors += "第${stageNumber}段注水的累计水量无效。"
                    else -> {
                        parsedStages += PourStage(
                            endTimeSeconds = endTime,
                            cumulativeWaterMl = cumulativeWater,
                            quickTemperatureC = stage.quickTemperatureText.toDoubleOrNull(),
                            startTempC = stage.startTempText.toDoubleOrNull(),
                            endTempC = stage.endTempText.toDoubleOrNull(),
                        )
                    }
                }
            }

            WaterCurveStageKind.WAIT -> {
                val endTime = stage.endTimeSeconds
                if (endTime == null) {
                    errors += "第${stageNumber}段等待的结束时间未填写。"
                } else {
                    parsedStages += WaitStage(
                        endTimeSeconds = endTime,
                        startTempC = stage.startTempText.toDoubleOrNull(),
                        endTempC = stage.endTempText.toDoubleOrNull(),
                        ambientStartTempC = stage.ambientStartTempText.toDoubleOrNull(),
                        ambientEndTempC = stage.ambientEndTempText.toDoubleOrNull(),
                    )
                }
            }

            WaterCurveStageKind.BYPASS -> {
                val waterMl = stage.waterText.toDoubleOrNull()
                if (waterMl == null) {
                    errors += "第${stageNumber}段旁路水量无效。"
                } else {
                    parsedStages += BypassStage(
                        waterMl = waterMl,
                        quickTemperatureC = stage.quickTemperatureText.toDoubleOrNull(),
                        startTempC = stage.startTempText.toDoubleOrNull(),
                        endTempC = stage.endTempText.toDoubleOrNull(),
                    )
                }
            }
        }
    }
    if (parsedStages.isEmpty()) {
        return WaterCurveFormResult(errors = errors)
    }
    val curve = WaterCurve(
        version = 2,
        temperatureMode = temperatureMode,
        ambientTempC = ambientTempText.toDoubleOrNull(),
        containerType = containerType,
        stages = parsedStages,
    )
    return WaterCurveFormResult(
        curve = curve,
        errors = errors + curve.validate(brewMethod),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterCurveEditor(
    temperatureMode: WaterCurveTemperatureMode,
    onTemperatureModeChange: (WaterCurveTemperatureMode) -> Unit,
    ambientTempText: String,
    onAmbientTempChange: (String) -> Unit,
    containerType: ThermalContainerType?,
    onContainerTypeChange: (ThermalContainerType?) -> Unit,
    stages: List<WaterCurveStageEditorState>,
    onStageChange: (Int, WaterCurveStageEditorState) -> Unit,
    onAddStage: (WaterCurveStageKind) -> Unit,
    onMoveStageUp: (Int) -> Unit,
    onMoveStageDown: (Int) -> Unit,
    onRemoveStage: (Int) -> Unit,
    previewCurve: WaterCurve?,
    derivedValues: WaterCurveDerivedValues?,
    analysis: WaterCurveAnalysis?,
    legacySummary: String?,
    modifier: Modifier = Modifier,
) {
    var showAmbientDialog by remember { mutableStateOf(false) }
    var showContainerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WaterCurveTemperatureMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == temperatureMode,
                    onClick = { onTemperatureModeChange(mode) },
                    label = { Text(mode.displayName) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactSelectionField(
                label = "室温",
                value = ambientTempText.takeIf { it.isNotBlank() }?.let { "${it}°C" } ?: "未填",
                modifier = Modifier.weight(1f),
                onClick = { showAmbientDialog = true },
            )
            CompactSelectionField(
                label = "容器",
                value = containerType?.displayName ?: "未选",
                modifier = Modifier.weight(1f),
                onClick = { showContainerDialog = true },
            )
        }

        WaterCurveChart(
            curve = previewCurve,
            derivedValues = derivedValues,
            analysis = analysis,
            modifier = Modifier.fillMaxWidth(),
        )

        if (temperatureMode == WaterCurveTemperatureMode.STAGE_END_SYSTEM) {
            Text(
                text = "当前使用阶段末温模式，注水量曲线会继续显示，容器温度和咖啡因估算将保持关闭。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!legacySummary.isNullOrBlank()) {
            Text(
                text = legacySummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (stages.isEmpty()) {
            Text(
                text = "先添加注水、等待或旁路阶段，让这杯怎么做出来更清楚。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        stages.forEachIndexed { index, stage ->
            WaterCurveStageRow(
                index = index,
                stage = stage,
                temperatureMode = temperatureMode,
                isFirst = index == 0,
                isLast = index == stages.lastIndex,
                onStageChange = { onStageChange(index, it) },
                onMoveStageUp = { onMoveStageUp(index) },
                onMoveStageDown = { onMoveStageDown(index) },
                onRemoveStage = { onRemoveStage(index) },
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WaterCurveStageKind.entries.forEach { kind ->
                OutlinedButton(onClick = { onAddStage(kind) }) {
                    Text("添加${kind.displayName}")
                }
            }
        }
    }

    if (showAmbientDialog) {
        NumericValueDialog(
            title = "室温",
            value = ambientTempText,
            unit = "°C",
            step = 1.0,
            decimals = 0,
            onDismiss = { showAmbientDialog = false },
            onValueChange = onAmbientTempChange,
        )
    }

    if (showContainerDialog) {
        ThermalContainerDialog(
            selected = containerType,
            onDismiss = { showContainerDialog = false },
            onSelected = {
                onContainerTypeChange(it)
                showContainerDialog = false
            },
        )
    }
}

@Composable
private fun WaterCurveStageRow(
    index: Int,
    stage: WaterCurveStageEditorState,
    temperatureMode: WaterCurveTemperatureMode,
    isFirst: Boolean,
    isLast: Boolean,
    onStageChange: (WaterCurveStageEditorState) -> Unit,
    onMoveStageUp: () -> Unit,
    onMoveStageDown: () -> Unit,
    onRemoveStage: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var activeEditor by remember { mutableStateOf<StageEditField?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}. ${stage.kind.displayName}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = 46.dp),
                    maxLines = 1,
                )

                when (stage.kind) {
                    WaterCurveStageKind.POUR -> {
                        SummaryValueChip(
                            label = "到达",
                            value = stage.endTimeSeconds?.let(::formatDurationValue) ?: "未填",
                            modifier = Modifier.weight(1f),
                            onClick = { activeEditor = StageEditField.END_TIME },
                        )
                        SummaryValueChip(
                            label = "累计",
                            value = stage.cumulativeWaterText.takeIf { it.isNotBlank() }?.let { "${it}ml" } ?: "未填",
                            modifier = Modifier.weight(1f),
                            onClick = { activeEditor = StageEditField.CUMULATIVE_WATER },
                        )
                        SummaryValueChip(
                            label = quickTemperatureShortLabel(temperatureMode),
                            value = stage.quickTemperatureText.takeIf { it.isNotBlank() }?.let { "${it}°C" } ?: "未填",
                            modifier = Modifier.weight(1f),
                            onClick = { activeEditor = StageEditField.QUICK_TEMPERATURE },
                        )
                    }

                    WaterCurveStageKind.WAIT -> {
                        SummaryValueChip(
                            label = "到达",
                            value = stage.endTimeSeconds?.let(::formatDurationValue) ?: "未填",
                            modifier = Modifier.weight(1f),
                            onClick = { activeEditor = StageEditField.END_TIME },
                        )
                    }

                    WaterCurveStageKind.BYPASS -> {
                        SummaryValueChip(
                            label = "旁路",
                            value = stage.waterText.takeIf { it.isNotBlank() }?.let { "${it}ml" } ?: "未填",
                            modifier = Modifier.weight(1f),
                            onClick = { activeEditor = StageEditField.BYPASS_WATER },
                        )
                        SummaryValueChip(
                            label = quickTemperatureShortLabel(temperatureMode),
                            value = stage.quickTemperatureText.takeIf { it.isNotBlank() }?.let { "${it}°C" } ?: "未填",
                            modifier = Modifier.weight(1f),
                            onClick = { activeEditor = StageEditField.QUICK_TEMPERATURE },
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (stage.showAdvancedTemperature) "收起高级温度" else "高级温度") },
                            onClick = {
                                menuExpanded = false
                                onStageChange(stage.copy(showAdvancedTemperature = !stage.showAdvancedTemperature))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("上移") },
                            onClick = {
                                menuExpanded = false
                                onMoveStageUp()
                            },
                            enabled = !isFirst,
                        )
                        DropdownMenuItem(
                            text = { Text("下移") },
                            onClick = {
                                menuExpanded = false
                                onMoveStageDown()
                            },
                            enabled = !isLast,
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                onRemoveStage()
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = stage.showAdvancedTemperature) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        NumericStepField(
                            label = "起始温度 (°C)",
                            value = stage.startTempText,
                            onValueChange = { onStageChange(stage.copy(startTempText = it)) },
                            step = 1.0,
                            decimals = 0,
                            modifier = Modifier.weight(1f),
                        )
                        NumericStepField(
                            label = "结束温度 (°C)",
                            value = stage.endTempText,
                            onValueChange = { onStageChange(stage.copy(endTempText = it)) },
                            step = 1.0,
                            decimals = 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (stage.kind == WaterCurveStageKind.WAIT) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            NumericStepField(
                                label = "环境起始 (°C)",
                                value = stage.ambientStartTempText,
                                onValueChange = { onStageChange(stage.copy(ambientStartTempText = it)) },
                                step = 1.0,
                                decimals = 0,
                                modifier = Modifier.weight(1f),
                            )
                            NumericStepField(
                                label = "环境结束 (°C)",
                                value = stage.ambientEndTempText,
                                onValueChange = { onStageChange(stage.copy(ambientEndTempText = it)) },
                                step = 1.0,
                                decimals = 0,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    when (activeEditor) {
        StageEditField.END_TIME -> DurationPickerDialog(
            initialValueSeconds = stage.endTimeSeconds,
            onDismiss = { activeEditor = null },
            onConfirm = {
                onStageChange(stage.copy(endTimeSeconds = it))
                activeEditor = null
            },
        )

        StageEditField.CUMULATIVE_WATER -> NumericValueDialog(
            title = "累计注水",
            value = stage.cumulativeWaterText,
            unit = "ml",
            step = 10.0,
            decimals = 0,
            onDismiss = { activeEditor = null },
            onValueChange = { onStageChange(stage.copy(cumulativeWaterText = it)) },
        )

        StageEditField.BYPASS_WATER -> NumericValueDialog(
            title = "旁路水量",
            value = stage.waterText,
            unit = "ml",
            step = 10.0,
            decimals = 0,
            onDismiss = { activeEditor = null },
            onValueChange = { onStageChange(stage.copy(waterText = it)) },
        )

        StageEditField.QUICK_TEMPERATURE -> NumericValueDialog(
            title = quickTemperatureShortLabel(temperatureMode),
            value = stage.quickTemperatureText,
            unit = "°C",
            step = 1.0,
            decimals = 0,
            onDismiss = { activeEditor = null },
            onValueChange = { onStageChange(stage.copy(quickTemperatureText = it)) },
        )

        null -> Unit
    }
}

@Composable
private fun CompactSelectionField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SummaryValueChip(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NumericValueDialog(
    title: String,
    value: String,
    unit: String,
    step: Double,
    decimals: Int,
    onDismiss: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NumericStepField(
                label = "$title ($unit)",
                value = draft,
                onValueChange = { draft = it },
                step = step,
                decimals = decimals,
            )
        },
        confirmButton = {
            OutlinedButton(onClick = {
                onValueChange(draft)
                onDismiss()
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThermalContainerDialog(
    selected: ThermalContainerType?,
    onDismiss: () -> Unit,
    onSelected: (ThermalContainerType?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择容器") },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelected(null) },
                    label = { Text("清空") },
                )
                ThermalContainerType.entries.forEach { container ->
                    FilterChip(
                        selected = selected == container,
                        onClick = { onSelected(container) },
                        label = { Text(container.displayName) },
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterCurveChart(
    curve: WaterCurve?,
    derivedValues: WaterCurveDerivedValues?,
    analysis: WaterCurveAnalysis?,
    modifier: Modifier = Modifier,
) {
    val timeline = curve?.let(::buildWaterCurveChartLayout).orEmpty()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "多曲线冲煮分析",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (curve == null || timeline.isEmpty()) {
                Text(
                    text = "还没有可绘制的曲线，添加阶段后这里会同步显示注水、系统温度和咖啡因估算。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val points = analysis?.points.orEmpty()
                CurvePlotPanel(
                    title = "注水 / 旁路",
                    timeline = timeline,
                    series = buildList {
                        if (points.isNotEmpty()) {
                            add(
                                CurvePanelSeries(
                                    label = "萃取水",
                                    points = points.map { PlotPoint(it.elapsedSeconds, it.brewWaterMl) },
                                    color = MaterialTheme.colorScheme.primary,
                                ),
                            )
                            add(
                                CurvePanelSeries(
                                    label = "总液量",
                                    points = points.map { PlotPoint(it.elapsedSeconds, it.totalWaterMl) },
                                    color = MaterialTheme.colorScheme.tertiary,
                                    dashed = true,
                                ),
                            )
                        }
                    },
                    unavailableText = if (points.isEmpty()) "还没有可用于绘制的注水数据。" else null,
                    useZeroBaseline = true,
                    showXAxisLabels = false,
                )
                CurvePlotPanel(
                    title = "系统温度",
                    timeline = timeline,
                    series = buildList {
                        val temperaturePoints = points.mapNotNull { point ->
                            point.systemTempC?.let { PlotPoint(point.elapsedSeconds, it) }
                        }
                        if (temperaturePoints.isNotEmpty()) {
                            add(
                                CurvePanelSeries(
                                    label = "系统温度",
                                    points = temperaturePoints,
                                    color = MaterialTheme.colorScheme.secondary,
                                ),
                            )
                        }
                    },
                    unavailableText = analysis?.temperatureUnavailableReason,
                    useZeroBaseline = false,
                    showXAxisLabels = false,
                )
                CurvePlotPanel(
                    title = "咖啡因估算",
                    timeline = timeline,
                    series = buildList {
                        val caffeinePoints = points.mapNotNull { point ->
                            point.caffeineMg?.let { PlotPoint(point.elapsedSeconds, it) }
                        }
                        if (caffeinePoints.isNotEmpty()) {
                            add(
                                CurvePanelSeries(
                                    label = "咖啡因",
                                    points = caffeinePoints,
                                    color = MaterialTheme.colorScheme.error,
                                ),
                            )
                        }
                    },
                    unavailableText = analysis?.caffeineUnavailableReason,
                    useZeroBaseline = true,
                    showXAxisLabels = true,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                derivedValues?.brewWaterMl?.let { StatChip(text = "萃取水 ${formatWaterCurveNumber(it)}ml") }
                derivedValues?.bypassWaterMl?.let { StatChip(text = "旁路 ${formatWaterCurveNumber(it)}ml") }
                derivedValues?.totalWaterMl?.let { StatChip(text = "总水 ${formatWaterCurveNumber(it)}ml") }
                derivedValues?.brewDurationSeconds?.let { StatChip(text = "总时长 ${formatWaterCurveDuration(it)}") }
                analysis?.finalSystemTempC?.let { StatChip(text = "末温 ${formatWaterCurveNumber(it)}°C") }
                analysis?.estimatedCaffeineMg?.let { StatChip(text = "咖啡因 ${formatWaterCurveNumber(it)}mg") }
                analysis?.estimatedCaffeinePercent?.let { StatChip(text = "咖啡因 ${formatWaterCurveNumber(it)}%") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurvePlotPanel(
    title: String,
    timeline: List<WaterCurveChartSegment>,
    series: List<CurvePanelSeries>,
    unavailableText: String?,
    useZeroBaseline: Boolean,
    showXAxisLabels: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                series.forEach { item ->
                    Text(
                        text = if (item.dashed) "${item.label} · 虚线" else item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = item.color,
                    )
                }
            }
        }

        if (!unavailableText.isNullOrBlank()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = unavailableText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return
        }

        if (series.isEmpty()) {
            return
        }

        val values = series.flatMap { item -> item.points.map(PlotPoint::value) }
        val axisMin = if (useZeroBaseline) {
            0.0
        } else {
            values.minOrNull()?.let { it - 2.0 } ?: 0.0
        }
        val axisMax = if (useZeroBaseline) {
            values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        } else {
            (values.maxOrNull()?.let { it + 2.0 } ?: 1.0).coerceAtLeast(axisMin + 1.0)
        }
        val range = (axisMax - axisMin).takeIf { it > 0.0 } ?: 1.0
        val tickValues = listOf(axisMin, axisMin + (range / 2.0), axisMax)
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (showXAxisLabels) 132.dp else 112.dp),
        ) {
            val leftPadding = 42.dp.toPx()
            val rightPadding = 10.dp.toPx()
            val topPadding = 12.dp.toPx()
            val bottomPadding = if (showXAxisLabels) 26.dp.toPx() else 10.dp.toPx()
            val plotWidth = size.width - leftPadding - rightPadding
            val plotHeight = size.height - topPadding - bottomPadding
            val yLabelPaint = Paint().apply {
                color = axisColor.toArgb()
                textSize = 10.dp.toPx()
                textAlign = Paint.Align.RIGHT
                isAntiAlias = true
            }
            val xLabelPaint = Paint().apply {
                color = axisColor.toArgb()
                textSize = 10.dp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            tickValues.forEach { value ->
                val y = topPadding + plotHeight - (((value - axisMin) / range).toFloat() * plotHeight)
                drawLine(
                    color = gridColor,
                    start = Offset(leftPadding, y),
                    end = Offset(size.width - rightPadding, y),
                    strokeWidth = 1.dp.toPx(),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    formatWaterCurveNumber(value),
                    leftPadding - 6.dp.toPx(),
                    y + 4.dp.toPx(),
                    yLabelPaint,
                )
            }

            timeline.filter { it.isTimed }.forEach { segment ->
                val x = leftPadding + segment.endFraction * plotWidth
                drawLine(
                    color = axisColor.copy(alpha = 0.18f),
                    start = Offset(x, topPadding),
                    end = Offset(x, topPadding + plotHeight),
                    strokeWidth = 1.dp.toPx(),
                )
                if (showXAxisLabels) {
                    drawContext.canvas.nativeCanvas.drawText(
                        formatWaterCurveDuration(segment.cumulativeEndSeconds),
                        x,
                        size.height - 8.dp.toPx(),
                        xLabelPaint,
                    )
                }
            }

            series.forEach { line ->
                val plotPoints = line.points.map { point ->
                    val fraction = fractionAtTime(point.elapsedSeconds, timeline)
                    val y = topPadding + plotHeight - (((point.value - axisMin) / range).toFloat() * plotHeight)
                    Offset(
                        x = leftPadding + (fraction * plotWidth),
                        y = y,
                    )
                }
                plotPoints.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = line.color,
                        start = start,
                        end = end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (line.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f) else null,
                    )
                }
            }
        }
    }
}

@Composable
fun WaterCurveSummaryList(
    curve: WaterCurve,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        curve.stageSummaryLines().forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal data class WaterCurveChartSegment(
    val startFraction: Float,
    val endFraction: Float,
    val startValueMl: Double,
    val endValueMl: Double,
    val maxLabelValueMl: Double,
    val startSeconds: Int,
    val endSeconds: Int,
    val cumulativeEndSeconds: Int,
    val isTimed: Boolean,
    val isBypass: Boolean,
    val calloutLines: List<String>,
)

internal fun buildWaterCurveChartLayout(curve: WaterCurve): List<WaterCurveChartSegment> {
    val timedDurations = mutableListOf<Double>()
    var previousTimedEnd = 0
    curve.stages.forEach { stage ->
        when (stage) {
            is PourStage -> {
                timedDurations += ln((stage.endTimeSeconds - previousTimedEnd).coerceAtLeast(1) + 1.0)
                previousTimedEnd = stage.endTimeSeconds
            }

            is WaitStage -> {
                timedDurations += ln((stage.endTimeSeconds - previousTimedEnd).coerceAtLeast(1) + 1.0)
                previousTimedEnd = stage.endTimeSeconds
            }

            is BypassStage -> Unit
        }
    }
    val totalTimedWeight = timedDurations.sum().takeIf { it > 0.0 } ?: 1.0
    val segments = mutableListOf<WaterCurveChartSegment>()
    var accumulatedWeight = 0.0
    var currentExtraction = 0.0
    var currentBypass = 0.0
    previousTimedEnd = 0

    curve.stages.forEach { stage ->
        when (stage) {
            is PourStage -> {
                val duration = (stage.endTimeSeconds - previousTimedEnd).coerceAtLeast(1)
                val width = ln(duration + 1.0) / totalTimedWeight
                val startFraction = accumulatedWeight.toFloat()
                accumulatedWeight += width
                val endFraction = accumulatedWeight.toFloat()
                val startExtraction = currentExtraction
                currentExtraction = stage.cumulativeWaterMl
                segments += WaterCurveChartSegment(
                    startFraction = startFraction,
                    endFraction = endFraction,
                    startValueMl = startExtraction,
                    endValueMl = currentExtraction,
                    maxLabelValueMl = currentExtraction,
                    startSeconds = previousTimedEnd,
                    endSeconds = stage.endTimeSeconds,
                    cumulativeEndSeconds = stage.endTimeSeconds,
                    isTimed = true,
                    isBypass = false,
                    calloutLines = listOf(
                        formatWaterCurveDuration(stage.endTimeSeconds),
                        "${formatWaterCurveNumber(stage.cumulativeWaterMl)}ml",
                    ),
                )
                previousTimedEnd = stage.endTimeSeconds
            }

            is WaitStage -> {
                val duration = (stage.endTimeSeconds - previousTimedEnd).coerceAtLeast(1)
                val width = ln(duration + 1.0) / totalTimedWeight
                val startFraction = accumulatedWeight.toFloat()
                accumulatedWeight += width
                val endFraction = accumulatedWeight.toFloat()
                segments += WaterCurveChartSegment(
                    startFraction = startFraction,
                    endFraction = endFraction,
                    startValueMl = currentExtraction,
                    endValueMl = currentExtraction,
                    maxLabelValueMl = currentExtraction,
                    startSeconds = previousTimedEnd,
                    endSeconds = stage.endTimeSeconds,
                    cumulativeEndSeconds = stage.endTimeSeconds,
                    isTimed = true,
                    isBypass = false,
                    calloutLines = listOf(formatWaterCurveDuration(stage.endTimeSeconds)),
                )
                previousTimedEnd = stage.endTimeSeconds
            }

            is BypassStage -> {
                val fraction = accumulatedWeight.toFloat().coerceIn(0f, 1f)
                val startTotal = currentExtraction + currentBypass
                val endTotal = startTotal + stage.waterMl
                currentBypass += stage.waterMl
                segments += WaterCurveChartSegment(
                    startFraction = fraction,
                    endFraction = fraction,
                    startValueMl = startTotal,
                    endValueMl = endTotal,
                    maxLabelValueMl = endTotal,
                    startSeconds = previousTimedEnd,
                    endSeconds = previousTimedEnd,
                    cumulativeEndSeconds = previousTimedEnd,
                    isTimed = false,
                    isBypass = true,
                    calloutLines = listOf("旁路 ${formatWaterCurveNumber(stage.waterMl)}ml"),
                )
            }
        }
    }
    return segments
}

internal fun buildStageSummaryTokens(
    stage: WaterCurveStageEditorState,
    temperatureMode: WaterCurveTemperatureMode,
): List<String> {
    return when (stage.kind) {
        WaterCurveStageKind.POUR -> listOf(
            "到达 ${stage.endTimeSeconds?.let(::formatDurationValue) ?: "未填"}",
            "累计 ${stage.cumulativeWaterText.ifBlank { "未填" }}ml",
            "${quickTemperatureShortLabel(temperatureMode)} ${stage.quickTemperatureText.ifBlank { "未填" }}°C",
        )

        WaterCurveStageKind.WAIT -> listOf(
            "到达 ${stage.endTimeSeconds?.let(::formatDurationValue) ?: "未填"}",
        )

        WaterCurveStageKind.BYPASS -> listOf(
            "旁路 ${stage.waterText.ifBlank { "未填" }}ml",
            "${quickTemperatureShortLabel(temperatureMode)} ${stage.quickTemperatureText.ifBlank { "未填" }}°C",
        )
    }
}

private fun fractionAtTime(
    elapsedSeconds: Int,
    timeline: List<WaterCurveChartSegment>,
): Float {
    val timedSegments = timeline.filter { it.isTimed }
    if (timedSegments.isEmpty()) {
        return 0f
    }
    if (elapsedSeconds <= timedSegments.first().startSeconds) {
        return timedSegments.first().startFraction
    }
    timedSegments.forEach { segment ->
        if (elapsedSeconds <= segment.endSeconds) {
            val durationSeconds = (segment.endSeconds - segment.startSeconds).coerceAtLeast(1)
            val localSeconds = (elapsedSeconds - segment.startSeconds).coerceIn(0, durationSeconds)
            val localWeight = ln(localSeconds + 1.0) / ln(durationSeconds + 1.0)
            return segment.startFraction + ((segment.endFraction - segment.startFraction) * localWeight.toFloat())
        }
    }
    return timedSegments.last().endFraction
}

private fun quickTemperatureShortLabel(mode: WaterCurveTemperatureMode): String {
    return when (mode) {
        WaterCurveTemperatureMode.POUR_WATER -> "温度"
        WaterCurveTemperatureMode.STAGE_END_SYSTEM -> "末温"
    }
}

private fun formatOptionalCurveNumber(value: Double?): String {
    return value?.let(::formatWaterCurveNumber).orEmpty()
}

private enum class StageEditField {
    END_TIME,
    CUMULATIVE_WATER,
    BYPASS_WATER,
    QUICK_TEMPERATURE,
}

private data class PlotPoint(
    val elapsedSeconds: Int,
    val value: Double,
)

private data class CurvePanelSeries(
    val label: String,
    val points: List<PlotPoint>,
    val color: androidx.compose.ui.graphics.Color,
    val dashed: Boolean = false,
)
