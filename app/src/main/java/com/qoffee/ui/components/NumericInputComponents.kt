package com.qoffee.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.round

data class NumericInputSpec(
    val label: String,
    val value: String,
    val unit: String = "",
    val min: Double = 0.0,
    val max: Double? = null,
    val step: Double = 1.0,
    val decimals: Int = 0,
    val quickValues: List<String> = emptyList(),
    val referenceValue: String? = null,
    val allowEmpty: Boolean = true,
    val supportingText: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumericInputField(
    spec: NumericInputSpec,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    var showSheet by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = formatNumericDisplay(spec.value, spec.unit),
        onValueChange = {},
        modifier = modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        readOnly = true,
        singleLine = true,
        label = { Text(spec.label) },
        placeholder = { Text(if (spec.allowEmpty) "未填写" else formatNumericValue(spec.min, spec.decimals)) },
        supportingText = {
            NumericInputSupportingText(
                referenceValue = spec.referenceValue,
                supportingText = spec.supportingText,
            )
        },
        trailingIcon = {
            TextButton(onClick = { showSheet = true }) {
                Text("编辑")
            }
        },
    )
    if (showSheet) {
        NumericInputSheet(
            spec = spec,
            onDismiss = { showSheet = false },
            onConfirm = { nextValue ->
                onValueChange(nextValue)
                showSheet = false
            },
        )
    }
}

@Composable
private fun NumericInputSupportingText(
    referenceValue: String?,
    supportingText: String?,
) {
    val lines = listOfNotNull(
        referenceValue?.takeIf { it.isNotBlank() }?.let { "参考上次: $it" },
        supportingText?.takeIf { it.isNotBlank() },
    )
    if (lines.isNotEmpty()) {
        Text(lines.joinToString("  "))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NumericInputSheet(
    spec: NumericInputSpec,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(spec.value) { mutableStateOf(spec.value) }
    val normalizedDraft = normalizeNumericInput(draft, spec)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = spec.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = formatNumericDisplay(normalizedDraft, spec.unit).ifBlank { "未填写" },
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                NumericInputSupportingText(
                    referenceValue = spec.referenceValue,
                    supportingText = spec.supportingText,
                )
            }
            if (spec.quickValues.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    spec.quickValues.forEach { quick ->
                        val normalizedQuick = normalizeNumericInput(quick, spec)
                        FilterChip(
                            selected = normalizedDraft == normalizedQuick,
                            onClick = { draft = normalizedQuick },
                            label = { Text(formatNumericDisplay(normalizedQuick, spec.unit)) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { draft = stepNumericInput(draft, -spec.step, spec) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("-${formatNumericValue(spec.step, spec.decimals)}")
                }
                OutlinedButton(
                    onClick = { draft = stepNumericInput(draft, spec.step, spec) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text("+${formatNumericValue(spec.step, spec.decimals)}")
                }
            }
            NumericKeypad(
                decimals = spec.decimals,
                onKey = { key -> draft = applyNumericKey(draft, key, spec) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                if (spec.allowEmpty) {
                    OutlinedButton(
                        onClick = { draft = "" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("清空")
                    }
                }
                Button(
                    onClick = { onConfirm(normalizedDraft) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("完成")
                }
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    decimals: Int,
    onKey: (String) -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (decimals > 0) "." else "", "0", "Del"),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { if (key.isNotBlank()) onKey(key) },
                        enabled = key.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Text(
                            text = key,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

internal fun applyNumericKey(
    current: String,
    key: String,
    spec: NumericInputSpec,
): String {
    return when (key) {
        "Del" -> current.dropLast(1)
        "." -> {
            if (spec.decimals <= 0 || current.contains(".")) current else current.ifBlank { "0" } + "."
        }
        else -> {
            val next = if (current == "0") key else current + key
            limitDecimalDigits(next, spec.decimals)
        }
    }
}

internal fun stepNumericInput(
    current: String,
    delta: Double,
    spec: NumericInputSpec,
): String {
    val base = current.toDoubleOrNull() ?: spec.min
    return normalizeNumericInput(
        raw = formatNumericValue(base + delta, spec.decimals),
        spec = spec,
    )
}

internal fun normalizeNumericInput(
    raw: String,
    spec: NumericInputSpec,
): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) {
        return if (spec.allowEmpty) "" else formatNumericValue(spec.min, spec.decimals)
    }
    val parsed = trimmed.toDoubleOrNull()
        ?: return if (spec.allowEmpty) "" else formatNumericValue(spec.min, spec.decimals)
    val clamped = parsed
        .coerceAtLeast(spec.min)
        .let { value -> spec.max?.let { max -> value.coerceAtMost(max) } ?: value }
    val snapped = snapNumericValue(
        value = clamped,
        min = spec.min,
        step = spec.step,
    ).let { value -> spec.max?.let { max -> value.coerceAtMost(max) } ?: value }
    return formatNumericValue(snapped, spec.decimals)
}

internal fun snapNumericValue(
    value: Double,
    min: Double,
    step: Double,
): Double {
    if (step <= 0.0) return value
    val relative = (value - min) / step
    return min + (round(relative) * step)
}

internal fun formatNumericDisplay(value: String, unit: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return ""
    return if (unit.isBlank()) trimmed else "$trimmed $unit"
}

internal fun formatNumericValue(value: Double, decimals: Int): String {
    val safeDecimals = decimals.coerceAtLeast(0)
    val formatted = String.format(Locale.US, "%.${safeDecimals}f", value.coerceAtLeast(0.0))
    return if (safeDecimals == 0) {
        formatted
    } else {
        formatted.trimEnd('0').trimEnd('.')
    }
}

internal fun buildWaterQuickValuesForDose(
    doseText: String,
    ratios: List<Int>,
): List<String> {
    val dose = doseText.toDoubleOrNull() ?: return emptyList()
    if (dose <= 0.0) return emptyList()
    return ratios
        .map { ratio -> dose * ratio.toDouble() }
        .map { value ->
            if (value == value.toInt().toDouble()) {
                value.toInt().toString()
            } else {
                String.format(Locale.CHINA, "%.1f", value)
            }
        }
        .distinct()
}

private fun limitDecimalDigits(value: String, decimals: Int): String {
    if (decimals <= 0 || !value.contains(".")) return value
    val parts = value.split(".", limit = 2)
    return parts.first() + "." + parts.getOrElse(1) { "" }.take(decimals)
}
