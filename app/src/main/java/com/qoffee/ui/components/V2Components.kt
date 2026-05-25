package com.qoffee.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.BeanInventoryPriority
import com.qoffee.ui.theme.QoffeeDashboardTheme
import com.qoffee.ui.theme.qoffeeBottomShellBrush
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun QoffeeScreen(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = QoffeeDashboardTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = spacing.pageHorizontal,
                vertical = spacing.pageVertical,
            )
            .padding(bottom = 8.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalArrangement = Arrangement.spacedBy(spacing.section),
        content = content,
    )
}

@Composable
fun QoffeeFormScreen(
    paddingValues: PaddingValues,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = QoffeeDashboardTheme.spacing
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = bottomBar,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = spacing.pageHorizontal,
                    vertical = spacing.pageVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.section),
            content = content,
        )
    }
}

@Composable
fun DashboardPage(
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = QoffeeDashboardTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = spacing.pageHorizontal,
                vertical = spacing.pageVertical,
            )
            .padding(bottom = 8.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalArrangement = Arrangement.spacedBy(spacing.section),
        content = content,
    )
}

@Composable
fun DashboardActionBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BottomAppBar(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun DashboardEmphasisText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = QoffeeDashboardTheme.colors.titleText,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            DashboardEmphasisText(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = QoffeeDashboardTheme.colors.titleText,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

@Composable
fun DashboardArtworkBanner(
    @DrawableRes imageRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    height: Dp = 132.dp,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        color = dashboardColors.panelMuted.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        border = BorderStroke(1.dp, dashboardColors.panelStroke.copy(alpha = 0.16f)),
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun FeatureEntryCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    hint: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    val background = when {
        !enabled -> dashboardColors.panelMuted.copy(alpha = 0.55f)
        selected -> dashboardColors.accentSoft.copy(alpha = 0.55f)
        else -> dashboardColors.panel.copy(alpha = 0.42f)
    }
    val shape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .graphicsLayer {
                scaleX = if (pressed) 0.98f else 1f
                scaleY = if (pressed) 0.98f else 1f
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        color = background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        border = BorderStroke(
            1.dp,
            if (selected) dashboardColors.panelStrokeStrong.copy(alpha = 0.5f) else dashboardColors.panelStroke.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = if (selected) dashboardColors.accentSoft else dashboardColors.panelMuted,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(22.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                hint?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            badge?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier,
        color = dashboardColors.panelStrong.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, dashboardColors.panelStroke.copy(alpha = 0.14f)),
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
            DashboardEmphasisText(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = dashboardColors.titleText,
            )
            if (!supporting.isNullOrBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun BeanInventoryCard(
    inventory: BeanInventory,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    priority: BeanInventoryPriority = BeanInventoryPriority.UNKNOWN,
    onClick: (() -> Unit)? = null,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    val priorityColors = beanInventoryPriorityColors(priority)
    Surface(
        modifier = modifier
            .width(252.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        color = dashboardColors.panelStrong.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (priority == BeanInventoryPriority.URGENT) {
                priorityColors.progress.copy(alpha = 0.52f)
            } else {
                dashboardColors.panelStroke.copy(alpha = 0.18f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = inventory.beanName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = priorityColors.badgeContainer,
                    contentColor = priorityColors.badgeContent,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = priority.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = buildString {
                    append("烘焙日 ")
                    append(
                        inventory.roastDateEpochDay?.let {
                            LocalDate.ofEpochDay(it).format(DateTimeFormatter.ISO_LOCAL_DATE)
                        } ?: "未填写",
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(82.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(82.dp)) {
                        val strokeWidth = 9.dp.toPx()
                        drawArc(
                            color = priorityColors.track,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            size = Size(size.width, size.height),
                        )
                        drawArc(
                            color = priorityColors.progress,
                            startAngle = -90f,
                            sweepAngle = inventory.remainingRatio.coerceIn(0f, 1f) * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            size = Size(size.width, size.height),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${inventory.remainingPercentage}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = QoffeeDashboardTheme.colors.titleText,
                        )
                        Text(
                            text = "剩余",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${formatInventoryNumber(inventory.remainingStockG)}g 剩余",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "已用 ${formatInventoryNumber(inventory.usedStockG)}g / 共 ${formatInventoryNumber(inventory.initialStockG)}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = inventory.roastAgeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun beanInventoryPriorityColors(priority: BeanInventoryPriority): BeanInventoryPriorityColors {
    val dashboardColors = QoffeeDashboardTheme.colors
    val scheme = MaterialTheme.colorScheme
    return when (priority) {
        BeanInventoryPriority.RESTING -> BeanInventoryPriorityColors(
            progress = scheme.tertiary,
            track = scheme.tertiaryContainer.copy(alpha = 0.44f),
            badgeContainer = scheme.tertiaryContainer.copy(alpha = 0.72f),
            badgeContent = scheme.onTertiaryContainer,
        )
        BeanInventoryPriority.FRESH -> BeanInventoryPriorityColors(
            progress = dashboardColors.success,
            track = dashboardColors.success.copy(alpha = 0.18f),
            badgeContainer = dashboardColors.success.copy(alpha = 0.16f),
            badgeContent = dashboardColors.success,
        )
        BeanInventoryPriority.AGING -> BeanInventoryPriorityColors(
            progress = dashboardColors.warning,
            track = dashboardColors.warning.copy(alpha = 0.18f),
            badgeContainer = dashboardColors.warning.copy(alpha = 0.16f),
            badgeContent = dashboardColors.warning,
        )
        BeanInventoryPriority.URGENT -> BeanInventoryPriorityColors(
            progress = scheme.error,
            track = scheme.errorContainer.copy(alpha = 0.36f),
            badgeContainer = scheme.errorContainer.copy(alpha = 0.82f),
            badgeContent = scheme.onErrorContainer,
        )
        BeanInventoryPriority.EMPTY -> BeanInventoryPriorityColors(
            progress = scheme.outline,
            track = dashboardColors.panelMuted,
            badgeContainer = dashboardColors.panelMuted,
            badgeContent = scheme.onSurfaceVariant,
        )
        BeanInventoryPriority.UNKNOWN -> BeanInventoryPriorityColors(
            progress = scheme.primary,
            track = dashboardColors.panelMuted,
            badgeContainer = dashboardColors.accentSoft.copy(alpha = 0.52f),
            badgeContent = scheme.onSurfaceVariant,
        )
    }
}

private data class BeanInventoryPriorityColors(
    val progress: Color,
    val track: Color,
    val badgeContainer: Color,
    val badgeContent: Color,
)

private fun formatInventoryNumber(value: Double): String {
    return if (value == value.roundToInt().toDouble()) {
        value.roundToInt().toString()
    } else {
        String.format(java.util.Locale.CHINA, "%.1f", value)
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: String? = null,
    highlighted: Boolean = false,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    val background = when {
        !enabled -> dashboardColors.panelMuted
        highlighted -> dashboardColors.panelStrong
        else -> dashboardColors.panel
    }
    val shape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .graphicsLayer {
                scaleX = if (pressed) 0.985f else 1f
                scaleY = if (pressed) 0.985f else 1f
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        color = background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) dashboardColors.panelStrokeStrong.copy(alpha = 0.45f) else dashboardColors.panelStroke.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!badge.isNullOrBlank()) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun <T> CompactDropdownChip(
    label: String,
    selectedLabel: String?,
    options: List<DropdownOption<T>>,
    onSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    FilterChip(
        selected = selectedLabel != null,
        onClick = { expanded = true },
        label = {
            Text(
                text = buildString {
                    append(label)
                    selectedLabel?.let {
                        append(" · ")
                        append(it)
                    }
                },
            )
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = QoffeeDashboardTheme.colors.panelMuted,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = QoffeeDashboardTheme.colors.accentSoft,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        if (allowClear) {
            DropdownMenuItem(
                text = { Text("全部") },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactFilterBar(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
fun NumericStepField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    referenceValue: String? = null,
    step: Double = 1.0,
    quickValues: List<String> = emptyList(),
    decimals: Int = 1,
) {
    NumericInputField(
        spec = NumericInputSpec(
            label = label,
            value = value,
            min = 0.0,
            step = step,
            decimals = decimals,
            quickValues = quickValues,
            referenceValue = referenceValue,
        ),
        onValueChange = onValueChange,
        modifier = modifier,
    )
}
