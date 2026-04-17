package com.qoffee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qoffee.ui.theme.Copper
import com.qoffee.ui.theme.Crema
import com.qoffee.ui.theme.Espresso
import com.qoffee.ui.theme.Hazel
import com.qoffee.ui.theme.Latte

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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Espresso, Hazel, Copper),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Crema,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Latte,
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
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
    placeholder: String = "Select",
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
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background,
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (allowClear) {
                DropdownMenuItem(
                    text = { Text("Clear") },
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
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value?.toString() ?: "Not rated",
                style = MaterialTheme.typography.bodyMedium,
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
                label = { Text("Clear") },
            )
            range.forEach { score ->
                FilterChip(
                    selected = value == score,
                    onClick = { onSelected(score) },
                    label = { Text(score.toString()) },
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
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        modifier = modifier,
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 100.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "Not filled" },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
