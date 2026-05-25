package com.qoffee.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qoffee.ui.theme.QoffeeDashboardTheme

@Composable
fun HeroActionPanel(
    eyebrow: String,
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActions: List<Pair<String, () -> Unit>> = emptyList(),
    enabled: Boolean = true,
) {
    val colors = QoffeeDashboardTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.panelStrong,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, colors.panelStrokeStrong.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.titleText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onPrimaryClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Text(primaryLabel)
            }
            if (secondaryActions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    secondaryActions.take(2).forEach { (label, action) ->
                        OutlinedButton(
                            onClick = action,
                            modifier = Modifier.weight(1f),
                            enabled = enabled,
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

data class RecordSourceRailItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val key: String,
)

@Composable
fun RecordSourceRail(
    items: List<RecordSourceRailItem>,
    onClick: (RecordSourceRailItem) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            PressableSourceCard(
                item = item,
                enabled = enabled,
                onClick = { onClick(item) },
            )
        }
    }
}

@Composable
private fun PressableSourceCard(
    item: RecordSourceRailItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = QoffeeDashboardTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = Modifier
            .width(236.dp)
            .clip(MaterialTheme.shapes.large)
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
        color = colors.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, colors.panelStroke.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = colors.accentSoft, shape = CircleShape) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(7.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = item.badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParameterSummaryStrip(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.filter { it.second.isNotBlank() }.forEach { (label, value) ->
            Surface(
                color = QoffeeDashboardTheme.colors.panelMuted,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedContent(
                        targetState = value,
                        label = "parameter-$label",
                    ) { animatedValue ->
                        Text(
                            text = animatedValue,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SmartNumberControl(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    referenceValue: String? = null,
    step: Double = 1.0,
    quickValues: List<String> = emptyList(),
    decimals: Int = 1,
) {
    NumericStepField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        referenceValue = referenceValue,
        step = step,
        quickValues = quickValues,
        decimals = decimals,
    )
}

data class TastingScoreItem(
    val key: String,
    val label: String,
    val value: Int?,
)

@Composable
fun TastingScorePanel(
    scores: List<TastingScoreItem>,
    onScoreSelected: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        scores.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.label,
                    modifier = Modifier.weight(1.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                (1..5).forEach { score ->
                    val selected = item.value == score
                    val container by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            QoffeeDashboardTheme.colors.panelMuted
                        },
                        animationSpec = spring(),
                        label = "score-color-${item.key}-$score",
                    )
                    FilterChip(
                        selected = selected,
                        onClick = { onScoreSelected(item.key, score) },
                        label = { Text(score.toString()) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = container,
                            selectedContainerColor = container,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun RecordReportHeader(
    title: String,
    subtitle: String,
    scoreText: String,
    parameters: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    HeroActionPanel(
        eyebrow = "杯测报告",
        title = title,
        subtitle = subtitle,
        primaryLabel = scoreText,
        onPrimaryClick = {},
        modifier = modifier,
        enabled = false,
    )
    ParameterSummaryStrip(items = parameters)
}

@Composable
fun InsightActionCard(
    title: String,
    evidence: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = QoffeeDashboardTheme.colors.panel,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, QoffeeDashboardTheme.colors.panelStroke.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = evidence,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (secondaryLabel != null && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun StickyRecordActionBar(
    title: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    DashboardActionBar(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) {
        Button(
            onClick = onPrimaryClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(primaryLabel)
        }
    }
}
