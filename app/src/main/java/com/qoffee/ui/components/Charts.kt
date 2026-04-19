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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qoffee.core.model.MethodAverage
import com.qoffee.core.model.ScatterPoint
import com.qoffee.core.model.SubjectiveDimensionAverage
import com.qoffee.core.model.TimelinePoint
import com.qoffee.ui.theme.Copper
import com.qoffee.ui.theme.Espresso
import com.qoffee.ui.theme.Sage
import kotlin.math.max

@Composable
fun MethodBarChart(
    values: List<MethodAverage>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) {
        Box(modifier = modifier.height(180.dp).fillMaxWidth()) {
            Text(
                text = "暂无图表数据",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val maxValue = max(10f, values.maxOf { it.averageScore }.toFloat())
        val barWidth = size.width / (values.size * 1.5f)
        val labelPaint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 12.dp.toPx()
        }
        values.forEachIndexed { index, item ->
            val left = (index * 1.5f + 0.5f) * barWidth
            val barHeight = (item.averageScore.toFloat() / maxValue) * (size.height - 48.dp.toPx())
            drawRoundRect(
                color = Copper,
                topLeft = Offset(left, size.height - barHeight - 28.dp.toPx()),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(18f, 18f),
            )
            drawContext.canvas.nativeCanvas.drawText(
                item.brewMethod.displayName,
                left,
                size.height - 4.dp.toPx(),
                labelPaint,
            )
        }
    }
}

@Composable
fun ScoreTrendChart(
    points: List<TimelinePoint>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Box(modifier = modifier.height(180.dp).fillMaxWidth()) {
            Text(
                text = "至少需要两条带评分记录才能显示趋势",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val minScore = 1f
        val maxScore = 10f
        val usableHeight = size.height - 24.dp.toPx()
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        points.windowed(2).forEachIndexed { index, window ->
            val start = Offset(
                x = index * stepX,
                y = usableHeight - ((window.first().score.toFloat() - minScore) / (maxScore - minScore)) * usableHeight,
            )
            val end = Offset(
                x = (index + 1) * stepX,
                y = usableHeight - ((window.last().score.toFloat() - minScore) / (maxScore - minScore)) * usableHeight,
            )
            drawLine(
                color = Espresso,
                start = start,
                end = end,
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
        points.forEachIndexed { index, point ->
            val center = Offset(
                x = index * stepX,
                y = usableHeight - ((point.score.toFloat() - minScore) / (maxScore - minScore)) * usableHeight,
            )
            drawCircle(color = Sage, radius = 10f, center = center)
        }
    }
}

@Composable
fun ScatterChart(
    points: List<ScatterPoint>,
    xLabel: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(modifier = modifier.height(180.dp).fillMaxWidth()) {
            Text(
                text = "当前参数暂无可分析样本",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val pointColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val xSpan = (maxX - minX).takeIf { it > 0 } ?: 1.0
        val minY = 1.0
        val maxY = 10.0
        val plotWidth = size.width - 24.dp.toPx()
        val plotHeight = size.height - 40.dp.toPx()

        drawLine(
            color = axisColor,
            start = Offset(24.dp.toPx(), plotHeight),
            end = Offset(size.width, plotHeight),
            strokeWidth = 3f,
        )
        drawLine(
            color = axisColor,
            start = Offset(24.dp.toPx(), 0f),
            end = Offset(24.dp.toPx(), plotHeight),
            strokeWidth = 3f,
        )
        points.forEach { point ->
            val normalizedX = ((point.x - minX) / xSpan).toFloat()
            val normalizedY = ((point.y - minY) / (maxY - minY)).toFloat()
            drawCircle(
                color = pointColor,
                radius = 9f,
                center = Offset(
                    x = 24.dp.toPx() + normalizedX * plotWidth,
                    y = plotHeight - normalizedY * plotHeight,
                ),
            )
        }
    }
    Text(
        text = xLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SubjectiveRadarLikeBars(
    values: List<SubjectiveDimensionAverage>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) {
        Box(modifier = modifier.height(180.dp).fillMaxWidth()) {
            Text(
                text = "暂无主观维度均值",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 72.dp),
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val maxValue = 5f
        val barWidth = size.width / (values.size * 1.4f)
        val labelPaint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 12.dp.toPx()
        }
        values.forEachIndexed { index, item ->
            val left = (index * 1.4f + 0.4f) * barWidth
            val barHeight = (item.average.toFloat() / maxValue) * (size.height - 40.dp.toPx())
            drawRoundRect(
                color = Sage,
                topLeft = Offset(left, size.height - barHeight - 22.dp.toPx()),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(16f, 16f),
            )
            drawContext.canvas.nativeCanvas.drawText(
                item.label,
                left,
                size.height - 4.dp.toPx(),
                labelPaint,
            )
        }
    }
}
