package com.qoffee.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.qoffee.ui.theme.QoffeeDashboardTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val safeRangeMax = maxValue.coerceAtLeast(minValue + step)
    val currentValue = remember(value, minValue) { value.toDoubleOrNull() ?: minValue }
    val rulerMajorColor = MaterialTheme.colorScheme.onSurface
    val rulerMinorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val rulerIndicatorColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QoffeeDashboardTheme.colors.panelStrong.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, QoffeeDashboardTheme.colors.panelStroke.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall)
                    if (!referenceValue.isNullOrBlank()) {
                        Text(
                            text = "参考上次：$referenceValue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(formatInlineNumber(currentValue, decimals))
                        if (unit.isNotBlank()) append(unit)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = QoffeeDashboardTheme.colors.titleText,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onValueChange(
                            stepFormattedValue(
                                current = currentValue,
                                delta = -step,
                                minValue = minValue,
                                maxValue = safeRangeMax,
                                step = step,
                                decimals = decimals,
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "减小")
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(74.dp)
                        .background(
                            color = QoffeeDashboardTheme.colors.panelMuted.copy(alpha = 0.84f),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .pointerInput(currentValue, minValue, safeRangeMax, step, decimals) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val rawDelta = (-dragAmount / 14f) * step
                                val next = snapToStep(
                                    value = currentValue + rawDelta,
                                    minValue = minValue,
                                    maxValue = safeRangeMax,
                                    step = step,
                                )
                                onValueChange(formatInlineNumber(next, decimals))
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(74.dp)) {
                        val centerX = size.width / 2f
                        val baseY = size.height * 0.78f
                        val spacing = 14.dp.toPx()
                        val stepCount = 22
                        for (index in -stepCount..stepCount) {
                            val x = centerX + (index * spacing)
                            if (x < 0f || x > size.width) continue
                            val major = index % 5 == 0
                            drawLine(
                                color = if (major) rulerMajorColor else rulerMinorColor,
                                start = Offset(x, baseY - if (major) 28.dp.toPx() else 16.dp.toPx()),
                                end = Offset(x, baseY),
                                strokeWidth = if (major) 3.dp.toPx() else 1.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                        drawLine(
                            color = rulerIndicatorColor,
                            start = Offset(centerX, 10.dp.toPx()),
                            end = Offset(centerX, baseY + 2.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onValueChange(
                            stepFormattedValue(
                                current = currentValue,
                                delta = step,
                                minValue = minValue,
                                maxValue = safeRangeMax,
                                step = step,
                                decimals = decimals,
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "增大")
                }
            }

            supportingText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
    val currentValue = remember(value, minValue) { value.toDoubleOrNull() ?: minValue }
    val range = (maxValue - minValue).coerceAtLeast(step)
    val sweepAngle = 300f
    val startAngle = 120f
    val safeStep = step.coerceAtLeast(0.1)
    val dialBaseColor = QoffeeDashboardTheme.colors.panelMuted
    val dialMajorColor = MaterialTheme.colorScheme.onSurface
    val dialMinorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val dialPrimaryColor = MaterialTheme.colorScheme.primary
    val dialCenterColor = MaterialTheme.colorScheme.background

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QoffeeDashboardTheme.colors.panelStrong.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, QoffeeDashboardTheme.colors.panelStroke.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = label, style = MaterialTheme.typography.titleSmall)
                referenceValue?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "参考上次：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(232.dp)
                    .pointerInput(currentValue, minValue, maxValue, safeStep, decimals) {
                    detectDragGestures { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val x = change.position.x - center.x
                            val y = change.position.y - center.y
                            val touchAngle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
                            val normalizedAngle = normalizeDialAngle(touchAngle)
                            val clampedAngle = normalizedAngle.coerceIn(startAngle, startAngle + sweepAngle)
                            val fraction = ((clampedAngle - startAngle) / sweepAngle).coerceIn(0f, 1f)
                            val next = snapToStep(
                                value = minValue + (range * fraction),
                                minValue = minValue,
                                maxValue = maxValue,
                                step = safeStep,
                            )
                            onValueChange(formatInlineNumber(next, decimals))
                        }
                    },
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val strokeWidth = 16.dp.toPx()
                    val inset = strokeWidth / 2f + 8.dp.toPx()
                    val radius = (size.minDimension / 2f) - inset
                    val center = Offset(size.width / 2f, size.height / 2f)

                    drawArc(
                        color = dialBaseColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )

                    val steps = ((range / safeStep).roundToInt()).coerceAtLeast(1)
                    repeat(steps + 1) { index ->
                        val fraction = index / steps.toFloat()
                        val angle = Math.toRadians((startAngle + (sweepAngle * fraction) - 90f).toDouble())
                        val outer = Offset(
                            x = center.x + (cos(angle) * radius).toFloat(),
                            y = center.y + (sin(angle) * radius).toFloat(),
                        )
                        val innerRadius = radius - if (index % 5 == 0) 18.dp.toPx() else 10.dp.toPx()
                        val inner = Offset(
                            x = center.x + (cos(angle) * innerRadius).toFloat(),
                            y = center.y + (sin(angle) * innerRadius).toFloat(),
                        )
                        drawLine(
                            color = if (index % 5 == 0) dialMajorColor else dialMinorColor,
                            start = inner,
                            end = outer,
                            strokeWidth = if (index % 5 == 0) 3.dp.toPx() else 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    val fraction = ((currentValue - minValue) / range).toFloat().coerceIn(0f, 1f)
                    val activeAngle = Math.toRadians((startAngle + (sweepAngle * fraction) - 90f).toDouble())
                    val knobRadius = radius - 2.dp.toPx()
                    val knobCenter = Offset(
                        x = center.x + (cos(activeAngle) * knobRadius).toFloat(),
                        y = center.y + (sin(activeAngle) * knobRadius).toFloat(),
                    )
                    drawCircle(
                        color = dialPrimaryColor,
                        radius = 12.dp.toPx(),
                        center = knobCenter,
                    )
                    drawCircle(
                        color = dialCenterColor,
                        radius = 5.dp.toPx(),
                        center = knobCenter,
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = formatInlineNumber(currentValue, decimals),
                        style = MaterialTheme.typography.displaySmall,
                        color = QoffeeDashboardTheme.colors.titleText,
                    )
                    Text(
                        text = "原始格数",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    normalizedValueText?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "归一化 $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        onValueChange(
                            stepFormattedValue(
                                current = currentValue,
                                delta = -safeStep,
                                minValue = minValue,
                                maxValue = maxValue,
                                step = safeStep,
                                decimals = decimals,
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "减小")
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
                )
                IconButton(
                    onClick = {
                        onValueChange(
                            stepFormattedValue(
                                current = currentValue,
                                delta = safeStep,
                                minValue = minValue,
                                maxValue = maxValue,
                                step = safeStep,
                                decimals = decimals,
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "增大")
                }
            }
        }
    }
}

private fun normalizeDialAngle(rawAngle: Float): Float {
    var adjusted = rawAngle
    if (adjusted < 0f) adjusted += 360f
    if (adjusted < 120f) adjusted += 360f
    return adjusted
}

private fun stepFormattedValue(
    current: Double,
    delta: Double,
    minValue: Double,
    maxValue: Double,
    step: Double,
    decimals: Int,
): String {
    val next = snapToStep(
        value = current + delta,
        minValue = minValue,
        maxValue = maxValue,
        step = step,
    )
    return formatInlineNumber(next, decimals)
}

private fun snapToStep(
    value: Double,
    minValue: Double,
    maxValue: Double,
    step: Double,
): Double {
    val clamped = value.coerceIn(minValue, maxValue)
    val relative = (clamped - minValue) / step
    val snapped = (relative.roundToInt() * step) + minValue
    return snapped.coerceIn(minValue, maxValue)
}

private fun formatInlineNumber(value: Double, decimals: Int): String {
    return if (decimals <= 0) {
        value.roundToInt().toString()
    } else {
        "%.${decimals}f".format(value).trimEnd('0').trimEnd('.')
    }
}
