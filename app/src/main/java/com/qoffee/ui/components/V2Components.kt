package com.qoffee.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qoffee.ui.theme.QoffeeDashboardTheme
import com.qoffee.ui.theme.qoffeeBottomShellBrush
import kotlin.math.roundToInt

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
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.pageHorizontal, vertical = spacing.pageVertical)
            .padding(bottom = 10.dp)
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
    val dashboardColors = QoffeeDashboardTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(brush = qoffeeBottomShellBrush())
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dashboardColors.shellDivider.copy(alpha = 0.72f)),
        )
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(dashboardColors.shellElevated)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
    val dashboardColors = QoffeeDashboardTheme.colors
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            shadow = Shadow(
                color = dashboardColors.titleShadow,
                offset = Offset(0f, 2.5f),
                blurRadius = 18f,
            ),
        ),
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
    val dashboardColors = QoffeeDashboardTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .background(
                    color = dashboardColors.titleScrim,
                    shape = MaterialTheme.shapes.large,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            DashboardEmphasisText(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = dashboardColors.titleText,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.84f),
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
fun MetricCard(
    label: String,
    value: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
) {
    val dashboardColors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier,
        color = dashboardColors.panelStrong,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, dashboardColors.panelStrokeStrong),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DashboardEmphasisText(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) dashboardColors.panelStrokeStrong else dashboardColors.panelStroke,
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
            if (!referenceValue.isNullOrBlank()) {
                Text(
                    text = "参考上次：$referenceValue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onValueChange(stepValue(value, -step, decimals)) }) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = "减少",
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    unfocusedContainerColor = QoffeeDashboardTheme.colors.panelMuted,
                    focusedContainerColor = QoffeeDashboardTheme.colors.panelMuted,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = QoffeeDashboardTheme.colors.panelStroke,
                ),
            )
            IconButton(onClick = { onValueChange(stepValue(value, step, decimals)) }) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "增加",
                )
            }
        }
        if (quickValues.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickValues.forEach { quick ->
                    FilterChip(
                        selected = value == quick,
                        onClick = { onValueChange(quick) },
                        label = { Text(quick) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = QoffeeDashboardTheme.colors.panelMuted,
                            selectedContainerColor = QoffeeDashboardTheme.colors.accentSoft,
                        ),
                    )
                }
            }
        }
    }
}

private fun stepValue(current: String, delta: Double, decimals: Int): String {
    val currentValue = current.toDoubleOrNull() ?: 0.0
    val next = (currentValue + delta).coerceAtLeast(0.0)
    val multiplier = 10.0.pow(decimals)
    val rounded = (next * multiplier).roundToInt() / multiplier
    return if (decimals == 0) {
        rounded.toInt().toString()
    } else {
        "%.${decimals}f".format(rounded).trimEnd('0').trimEnd('.')
    }
}

private fun Double.pow(exponent: Int): Double {
    var result = 1.0
    repeat(exponent) { result *= this }
    return result
}
