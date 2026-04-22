package com.qoffee.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.qoffee.core.model.BeanProcessMethod
import com.qoffee.core.model.RoastLevel
import com.qoffee.ui.theme.QoffeeDashboardTheme
import com.qoffee.ui.theme.qoffeePanelBrush

data class DropdownOption<T>(
    val label: String,
    val value: T,
)

@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, dashboardColors.panelStrokeStrong),
    ) {
        Box(
            modifier = Modifier
                .background(brush = qoffeePanelBrush(strong = true))
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardEmphasisText(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = dashboardColors.titleText,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = dashboardColors.panel.copy(alpha = 0.18f),
                shape = MaterialTheme.shapes.large,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.animateContentSize(animationSpec = spring(stiffness = 420f)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SingleChoiceChipGroup(
    title: String,
    options: List<DropdownOption<T>>,
    selected: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.value == selected,
                    onClick = { onSelected(option.value) },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = QoffeeDashboardTheme.colors.panelMuted,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = QoffeeDashboardTheme.colors.accentSoft,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}

@Composable
fun RoastLevelSelector(
    selected: RoastLevel,
    onSelected: (RoastLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "烘焙度",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "当前：${selected.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val itemWidth = maxWidth / RoastLevel.entries.size
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                RoastLevel.entries.forEach { level ->
                    val activeColor by animateColorAsState(
                        targetValue = if (level.storageValue <= selected.storageValue) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        label = "beanColor",
                    )
                    Column(
                        modifier = Modifier
                            .widthIn(min = itemWidth)
                            .clickable { onSelected(level) }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CoffeeBeanGlyph(
                            tint = activeColor,
                            filled = level.storageValue <= selected.storageValue,
                        )
                        Text(
                            text = level.shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (level == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoffeeBeanGlyph(
    tint: Color,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val seamColor = if (filled) MaterialTheme.colorScheme.surface else tint
    Canvas(
        modifier = modifier.size(22.dp),
    ) {
        val width = size.width * 0.72f
        val height = size.height * 0.92f
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        drawOval(
            color = if (filled) tint else tint.copy(alpha = 0.12f),
            topLeft = Offset(left, top),
            size = Size(width, height),
        )
        drawOval(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = Stroke(width = 1.6.dp.toPx()),
        )
        drawLine(
            color = seamColor,
            start = Offset(size.width * 0.48f, top + 2.dp.toPx()),
            end = Offset(size.width * 0.55f, top + height - 2.dp.toPx()),
            strokeWidth = 1.8.dp.toPx(),
        )
    }
}

@Composable
fun BeanIdentityCard(
    name: String,
    roastLevel: RoastLevel,
    processMethod: BeanProcessMethod,
    roaster: String,
    modifier: Modifier = Modifier,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = dashboardColors.panelStrong,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, dashboardColors.panelStrokeStrong),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatChip(text = roastLevel.displayName)
                StatChip(text = processMethod.displayName)
                if (roaster.isNotBlank()) {
                    StatChip(text = roaster)
                }
            }
        }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    selectedLabel: String?,
    options: List<DropdownOption<T>>,
    onSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "请选择",
    allowClear: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    val interactionSource = remember { MutableInteractionSource() }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = true },
                ),
            color = QoffeeDashboardTheme.colors.panelMuted,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
            border = BorderStroke(1.dp, QoffeeDashboardTheme.colors.panelStroke),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = selectedLabel ?: placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selectedLabel == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (allowClear) {
                DropdownMenuItem(
                    text = { Text("不选择") },
                    onClick = {
                        onSelected(null)
                        expanded = false
                    },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private enum class RatingGlyph {
    AROMA,
    ACIDITY,
    SWEETNESS,
    BITTERNESS,
    BODY,
    AFTERTASTE,
    OVERALL,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingSelector(
    label: String,
    value: Int?,
    range: IntRange,
    onSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyph = when (label) {
        "香气" -> RatingGlyph.AROMA
        "酸质" -> RatingGlyph.ACIDITY
        "甜感" -> RatingGlyph.SWEETNESS
        "苦感" -> RatingGlyph.BITTERNESS
        "醇厚" -> RatingGlyph.BODY
        "余韵" -> RatingGlyph.AFTERTASTE
        else -> RatingGlyph.OVERALL
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = value?.toString() ?: "未评分",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            range.forEach { score ->
                val selected = value != null && score <= value
                val interactionSource = remember(score, value) { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .graphicsLayer {
                            scaleX = if (pressed) 0.92f else 1f
                            scaleY = if (pressed) 0.92f else 1f
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelected(if (value == score) null else score) },
                        ),
                    color = if (selected) {
                        QoffeeDashboardTheme.colors.accentSoft
                    } else {
                        QoffeeDashboardTheme.colors.panelMuted
                    },
                    contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (selected) QoffeeDashboardTheme.colors.panelStrokeStrong else QoffeeDashboardTheme.colors.panelStroke,
                    ),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        RatingGlyphIcon(
                            glyph = glyph,
                            selected = selected,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieRatingSelector(
    label: String,
    value: Int?,
    range: IntRange,
    onSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = value?.let { "$it / 5" } ?: "未评分",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            range.forEach { score ->
                val selected = value != null && score <= value
                val interactionSource = remember(score, value) { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                Icon(
                    imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "$label $score",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = if (pressed) 0.92f else 1f
                            scaleY = if (pressed) 0.92f else 1f
                        }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelected(if (value == score) null else score) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun RatingGlyphIcon(
    glyph: RatingGlyph,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        when (glyph) {
            RatingGlyph.AROMA -> {
                val x1 = size.width * 0.28f
                val x2 = size.width * 0.5f
                val x3 = size.width * 0.72f
                drawLine(tint, Offset(x1, size.height * 0.82f), Offset(x1, size.height * 0.24f), strokeWidth = 1.5.dp.toPx())
                drawLine(tint, Offset(x2, size.height * 0.82f), Offset(x2, size.height * 0.18f), strokeWidth = 1.5.dp.toPx())
                drawLine(tint, Offset(x3, size.height * 0.82f), Offset(x3, size.height * 0.3f), strokeWidth = 1.5.dp.toPx())
            }
            RatingGlyph.ACIDITY -> {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height * 0.1f)
                    quadraticTo(size.width * 0.82f, size.height * 0.42f, size.width * 0.64f, size.height * 0.78f)
                    quadraticTo(size.width / 2f, size.height * 0.96f, size.width * 0.36f, size.height * 0.78f)
                    quadraticTo(size.width * 0.18f, size.height * 0.42f, size.width / 2f, size.height * 0.1f)
                    close()
                }
                drawPath(path, tint)
            }
            RatingGlyph.SWEETNESS -> {
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.08f), Offset(size.width * 0.5f, size.height * 0.92f), strokeWidth = 1.6.dp.toPx())
                drawLine(tint, Offset(size.width * 0.08f, size.height * 0.5f), Offset(size.width * 0.92f, size.height * 0.5f), strokeWidth = 1.6.dp.toPx())
                drawLine(tint, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), strokeWidth = 1.4.dp.toPx())
                drawLine(tint, Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.2f, size.height * 0.8f), strokeWidth = 1.4.dp.toPx())
            }
            RatingGlyph.BITTERNESS -> {
                val path = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.06f)
                    lineTo(size.width * 0.28f, size.height * 0.54f)
                    lineTo(size.width * 0.5f, size.height * 0.54f)
                    lineTo(size.width * 0.38f, size.height * 0.94f)
                    lineTo(size.width * 0.72f, size.height * 0.42f)
                    lineTo(size.width * 0.5f, size.height * 0.42f)
                    close()
                }
                drawPath(path, tint)
            }
            RatingGlyph.BODY -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.22f),
                    size = Size(size.width * 0.64f, size.height * 0.56f),
                    cornerRadius = CornerRadius(size.width * 0.18f, size.width * 0.18f),
                )
            }
            RatingGlyph.AFTERTASTE -> {
                drawOval(
                    color = tint,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.36f),
                    size = Size(size.width * 0.34f, size.height * 0.34f),
                )
                drawArc(
                    color = tint,
                    startAngle = -40f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.18f),
                    size = Size(size.width * 0.58f, size.height * 0.58f),
                    style = Stroke(width = 1.7.dp.toPx()),
                )
            }
            RatingGlyph.OVERALL -> {
                val path = Path().apply {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outer = size.width * 0.42f
                    val inner = outer * 0.45f
                    for (index in 0 until 10) {
                        val angle = Math.toRadians((-90.0 + index * 36.0))
                        val radius = if (index % 2 == 0) outer else inner
                        val x = cx + (Math.cos(angle) * radius).toFloat()
                        val y = cy + (Math.sin(angle) * radius).toFloat()
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelector(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = tag in selected,
                onClick = { onToggle(tag) },
                label = { Text(tag) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = QoffeeDashboardTheme.colors.panelMuted,
                    selectedContainerColor = QoffeeDashboardTheme.colors.accentSoft,
                ),
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = dashboardColors.panelMuted,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, dashboardColors.panelStroke),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatChip(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier,
        color = dashboardColors.panelMuted,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, dashboardColors.panelStroke),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier.widthIn(min = 108.dp),
        color = dashboardColors.panelMuted,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, dashboardColors.panelStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value.ifBlank { "未填写" },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
