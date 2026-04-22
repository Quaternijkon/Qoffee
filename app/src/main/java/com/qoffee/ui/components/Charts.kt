package com.qoffee.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qoffee.core.model.MethodAverage
import com.qoffee.core.model.ScatterPoint
import com.qoffee.core.model.SubjectiveDimensionAverage
import com.qoffee.core.model.TimelinePoint
import com.qoffee.ui.theme.QoffeeDashboardTheme
import kotlin.math.max

@Composable
fun MethodBarChart(
    values: List<MethodAverage>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) {
        ChartEmptyState(
            text = "暂无图表数据",
            modifier = modifier,
        )
        return
    }

    val primary = MaterialTheme.colorScheme.primary
    val accent = QoffeeDashboardTheme.colors.accentGlow
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp),
    ) {
        val leftPadding = 12.dp.toPx()
        val bottomPadding = 34.dp.toPx()
        val maxValue = max(5f, values.maxOf { it.averageScore }.toFloat())
        val step = (size.width - leftPadding * 2f) / values.size.coerceAtLeast(1)
        val barWidth = step * 0.62f
        val labelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 11.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = onSurfaceColor.toArgb()
            textSize = 11.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        repeat(4) { index ->
            val ratio = index / 3f
            val y = (size.height - bottomPadding) * ratio
            drawLine(
                color = labelColor.copy(alpha = 0.12f),
                start = Offset(leftPadding, y),
                end = Offset(size.width - leftPadding, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        values.forEachIndexed { index, item ->
            val left = leftPadding + step * index + (step - barWidth) / 2f
            val topInset = 20.dp.toPx()
            val barHeight = (item.averageScore.toFloat() / maxValue) * (size.height - bottomPadding - topInset)
            val top = size.height - bottomPadding - barHeight
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(primary, accent),
                ),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(18f, 18f),
            )
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", item.averageScore),
                left + barWidth / 2f,
                top - 6.dp.toPx(),
                valuePaint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                item.brewMethod.displayName,
                left + barWidth / 2f,
                size.height - 10.dp.toPx(),
                labelPaint,
            )
        }
    }
}

@Composable
fun ScoreTrendChart(
    points: List<TimelinePoint>,
    scoreRange: IntRange = 1..5,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        ChartEmptyState(
            text = "至少需要两条带评分记录才能显示趋势",
            modifier = modifier,
        )
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(232.dp),
    ) {
        val leftPadding = 18.dp.toPx()
        val rightPadding = 8.dp.toPx()
        val topPadding = 18.dp.toPx()
        val bottomPadding = 28.dp.toPx()
        val plotWidth = size.width - leftPadding - rightPadding
        val plotHeight = size.height - topPadding - bottomPadding
        val stepX = plotWidth / (points.size - 1).coerceAtLeast(1)
        val minScore = scoreRange.first.toFloat()
        val maxScore = scoreRange.last.toFloat()
        val labelPaint = Paint().apply {
            color = axisLabelColor.toArgb()
            textSize = 11.dp.toPx()
            isAntiAlias = true
        }

        (1..4).forEach { step ->
            val y = topPadding + plotHeight * (step / 4f)
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(size.width - rightPadding, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        points.windowed(2).forEachIndexed { index, window ->
            val start = Offset(
                x = leftPadding + index * stepX,
                y = topPadding + plotHeight - ((window.first().score.toFloat() - minScore) / (maxScore - minScore)) * plotHeight,
            )
            val end = Offset(
                x = leftPadding + (index + 1) * stepX,
                y = topPadding + plotHeight - ((window.last().score.toFloat() - minScore) / (maxScore - minScore)) * plotHeight,
            )
            drawLine(
                color = lineColor,
                start = start,
                end = end,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        points.forEachIndexed { index, point ->
            val center = Offset(
                x = leftPadding + index * stepX,
                y = topPadding + plotHeight - ((point.score.toFloat() - minScore) / (maxScore - minScore)) * plotHeight,
            )
            drawCircle(color = pointColor, radius = 7.dp.toPx(), center = center)
            if (index == 0 || index == points.lastIndex) {
                drawContext.canvas.nativeCanvas.drawText(
                    point.label,
                    center.x,
                    size.height - 6.dp.toPx(),
                    labelPaint.apply { textAlign = Paint.Align.CENTER },
                )
            }
        }
    }
}

@Composable
fun ScatterChart(
    points: List<ScatterPoint>,
    xLabel: String,
    yRange: IntRange = 1..5,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        ChartEmptyState(
            text = "当前参数暂无可分析样本",
            modifier = modifier,
        )
        return
    }

    val pointColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(232.dp),
    ) {
        val leftPadding = 28.dp.toPx()
        val bottomPadding = 24.dp.toPx()
        val topPadding = 14.dp.toPx()
        val rightPadding = 16.dp.toPx()
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val xSpan = (maxX - minX).takeIf { it > 0 } ?: 1.0
        val minY = yRange.first.toDouble()
        val maxY = yRange.last.toDouble()
        val plotWidth = size.width - leftPadding - rightPadding
        val plotHeight = size.height - bottomPadding - topPadding

        drawLine(
            color = axisColor.copy(alpha = 0.35f),
            start = Offset(leftPadding, topPadding),
            end = Offset(leftPadding, topPadding + plotHeight),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = axisColor.copy(alpha = 0.35f),
            start = Offset(leftPadding, topPadding + plotHeight),
            end = Offset(size.width - rightPadding, topPadding + plotHeight),
            strokeWidth = 2.dp.toPx(),
        )
        drawRect(
            color = axisColor.copy(alpha = 0.05f),
            topLeft = Offset(leftPadding, topPadding),
            size = Size(plotWidth, plotHeight),
            style = Stroke(width = 1.dp.toPx()),
        )

        points.forEach { point ->
            val normalizedX = ((point.x - minX) / xSpan).toFloat()
            val normalizedY = ((point.y - minY) / (maxY - minY)).toFloat()
            drawCircle(
                color = pointColor,
                radius = 6.dp.toPx(),
                center = Offset(
                    x = leftPadding + normalizedX * plotWidth,
                    y = topPadding + plotHeight - normalizedY * plotHeight,
                ),
            )
        }
    }
    Text(
        text = xLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SubjectiveRadarLikeBars(
    values: List<SubjectiveDimensionAverage>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) {
        ChartEmptyState(
            text = "暂无主观维度均值",
            modifier = modifier,
        )
        return
    }

    val barColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val maxValue = 5f
        val leftPadding = 10.dp.toPx()
        val bottomPadding = 30.dp.toPx()
        val step = (size.width - leftPadding * 2f) / values.size.coerceAtLeast(1)
        val barWidth = step * 0.58f
        val labelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 11.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        values.forEachIndexed { index, item ->
            val left = leftPadding + step * index + (step - barWidth) / 2f
            val barHeight = (item.average.toFloat() / maxValue) * (size.height - bottomPadding - 12.dp.toPx())
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, size.height - bottomPadding - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(16f, 16f),
            )
            drawContext.canvas.nativeCanvas.drawText(
                item.label,
                left + barWidth / 2f,
                size.height - 10.dp.toPx(),
                labelPaint,
            )
        }
    }
}

@Composable
private fun ChartEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(180.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
