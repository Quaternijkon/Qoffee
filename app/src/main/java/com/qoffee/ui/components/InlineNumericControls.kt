package com.qoffee.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun InlineRulerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Double = 0.0,
    maxValue: Double = 100.0,
    step: Double = 1.0,
    decimals: Int = 0,
    unit: String = "",
    referenceValue: String? = null,
    supportingText: String? = null,
) {
    NumericInputField(
        spec = NumericInputSpec(
            label = label,
            value = value,
            unit = unit,
            min = minValue,
            max = maxValue,
            step = step,
            decimals = decimals,
            referenceValue = referenceValue,
            supportingText = supportingText,
        ),
        onValueChange = onValueChange,
        modifier = modifier,
    )
}

@Composable
fun GrindDialField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Double = 0.0,
    maxValue: Double = 40.0,
    step: Double = 1.0,
    decimals: Int = 1,
    referenceValue: String? = null,
    normalizedValueText: String? = null,
) {
    NumericInputField(
        spec = NumericInputSpec(
            label = label,
            value = value,
            min = minValue,
            max = maxValue,
            step = step,
            decimals = decimals,
            referenceValue = referenceValue,
            supportingText = normalizedValueText?.let { "归一化 $it" },
        ),
        onValueChange = onValueChange,
        modifier = modifier,
    )
}
