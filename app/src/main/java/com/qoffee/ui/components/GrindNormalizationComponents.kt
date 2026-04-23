package com.qoffee.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qoffee.core.model.GrindNormalizationCurve
import com.qoffee.core.model.formatNormalizedGrind
import com.qoffee.ui.theme.QoffeeDashboardTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrindNormalizationChart(
    curve: GrindNormalizationCurve?,
    modifier: Modifier = Modifier,
    placeholder: String = "填写四类锚点后，这里会显示该磨豆机的归一化映射曲线。",
) {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val anchorColor = MaterialTheme.colorScheme.tertiary
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QoffeeDashboardTheme.colors.panelStrong.copy(alpha = 0.82f),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, QoffeeDashboardTheme.colors.panelStroke.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "归一化映射曲线",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (curve == null || curve.points.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    val padding = 18.dp.toPx()
                    val minX = curve.points.minOf { it.rawSetting }
                    val maxX = curve.points.maxOf { it.rawSetting }
                    val xRange = (maxX - minX).takeIf { it > 0.0 } ?: 1.0
                    val chartWidth = size.width - (padding * 2)
                    val chartHeight = size.height - (padding * 2)

                    repeat(5) { index ->
                        val y = padding + (chartHeight * (index / 4f))
                        drawLine(
                            color = outlineColor,
                            start = Offset(padding, y),
                            end = Offset(size.width - padding, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    val path = Path()
                    curve.points.forEachIndexed { index, point ->
                        val x = padding + (((point.rawSetting - minX) / xRange).toFloat() * chartWidth)
                        val y = padding + ((1f - point.normalizedValue.toFloat()) * chartHeight)
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )

                    curve.anchors.forEach { anchor ->
                        val x = padding + (((anchor.rawSetting - minX) / xRange).toFloat() * chartWidth)
                        val y = padding + ((1f - anchor.normalizedValue.toFloat()) * chartHeight)
                        drawCircle(
                            color = anchorColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    curve.anchors.forEach { anchor ->
                        StatChip(
                            text = "${anchor.label} ${anchor.rawSetting} → ${formatNormalizedGrind(anchor.normalizedValue)}",
                        )
                    }
                }
            }
        }
    }
}
