package com.qoffee.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, dashboardColors.panelStroke),
    ) {
        Box(
            modifier = Modifier
                .background(brush = qoffeePanelBrush())
                .padding(18.dp),
        ) {
            Column(
                modifier = Modifier.animateContentSize(animationSpec = spring(stiffness = 420f)),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    content()
                },
            )
        }
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true },
    ) {
        OutlinedTextField(
            value = selectedLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = QoffeeDashboardTheme.colors.panelMuted,
                focusedContainerColor = QoffeeDashboardTheme.colors.panelMuted,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = QoffeeDashboardTheme.colors.panelStroke,
            ),
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingSelector(
    label: String,
    value: Int?,
    range: IntRange,
    onSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            FilterChip(
                selected = value == null,
                onClick = { onSelected(null) },
                label = { Text("清空") },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = QoffeeDashboardTheme.colors.panelMuted,
                    selectedContainerColor = QoffeeDashboardTheme.colors.accentSoft,
                ),
            )
            range.forEach { score ->
                FilterChip(
                    selected = value == score,
                    onClick = { onSelected(score) },
                    label = { Text(score.toString()) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = QoffeeDashboardTheme.colors.panelMuted,
                        selectedContainerColor = QoffeeDashboardTheme.colors.accentSoft,
                    ),
                )
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
