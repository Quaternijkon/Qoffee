package com.qoffee.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun DatePickerField(
    label: String,
    valueEpochDay: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "选择日期",
    allowClear: Boolean = true,
) {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }
    val selectedDate = valueEpochDay?.let(LocalDate::ofEpochDay)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val seed = selectedDate ?: LocalDate.now(zoneId)
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onValueChange(LocalDate.of(year, month + 1, dayOfMonth).toEpochDay())
                        },
                        seed.year,
                        seed.monthValue - 1,
                        seed.dayOfMonth,
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(selectedDate?.toString() ?: placeholder)
            }
            if (allowClear && valueEpochDay != null) {
                OutlinedButton(onClick = { onValueChange(null) }) {
                    Text("清除")
                }
            }
        }
    }
}

@Composable
fun DateTimePickerField(
    label: String,
    valueMillis: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "选择日期时间",
) {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }
    val selectedText = valueMillis?.let(::formatDateTimeValue)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            onClick = {
                val seedDateTime = valueMillis?.let {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zoneId)
                } ?: LocalDateTime.now(zoneId)
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val selected = LocalDateTime.of(year, month + 1, dayOfMonth, hourOfDay, minute)
                                onValueChange(selected.atZone(zoneId).toInstant().toEpochMilli())
                            },
                            seedDateTime.hour,
                            seedDateTime.minute,
                            true,
                        ).show()
                    },
                    seedDateTime.year,
                    seedDateTime.monthValue - 1,
                    seedDateTime.dayOfMonth,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedText ?: placeholder)
        }
    }
}

@Composable
fun DurationPickerField(
    label: String,
    valueSeconds: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "选择时长",
    allowClear: Boolean = false,
) {
    var showDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(valueSeconds?.let(::formatDurationValue) ?: placeholder)
            }
            if (allowClear && valueSeconds != null) {
                OutlinedButton(onClick = { onValueChange(null) }) {
                    Text("清除")
                }
            }
        }
    }
    if (showDialog) {
        DurationPickerDialog(
            initialValueSeconds = valueSeconds,
            onDismiss = { showDialog = false },
            onConfirm = {
                onValueChange(it)
                showDialog = false
            },
        )
    }
}

@Composable
internal fun DurationPickerDialog(
    initialValueSeconds: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hours by remember(initialValueSeconds) { mutableIntStateOf(((initialValueSeconds ?: 0) / 3600).coerceIn(0, 99)) }
    var minutes by remember(initialValueSeconds) { mutableIntStateOf((((initialValueSeconds ?: 0) % 3600) / 60).coerceIn(0, 59)) }
    var seconds by remember(initialValueSeconds) { mutableIntStateOf(((initialValueSeconds ?: 0) % 60).coerceIn(0, 59)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时长") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "冷萃等长时间场景也能直接选小时、分钟和秒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DurationNumberPicker(
                        label = "时",
                        range = 0..99,
                        value = hours,
                        onValueChange = { hours = it },
                        modifier = Modifier.weight(1f),
                    )
                    DurationNumberPicker(
                        label = "分",
                        range = 0..59,
                        value = minutes,
                        onValueChange = { minutes = it },
                        modifier = Modifier.weight(1f),
                    )
                    DurationNumberPicker(
                        label = "秒",
                        range = 0..59,
                        value = seconds,
                        onValueChange = { seconds = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "当前：${formatDurationValue(hours * 3600 + minutes * 60 + seconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            OutlinedButton(onClick = { onConfirm(hours * 3600 + minutes * 60 + seconds) }) {
                Text("确定")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DurationNumberPicker(
    label: String,
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = range.first
                    maxValue = range.last
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    wrapSelectorWheel = false
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    setOnValueChangedListener { _, _, newValue ->
                        onValueChange(newValue)
                    }
                }
            },
            update = { picker ->
                if (picker.value != value) {
                    picker.value = value
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}

fun formatDurationValue(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}

fun formatDateTimeValue(timestampMillis: Long): String {
    val zoneId = ZoneId.systemDefault()
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), zoneId)
    return "%04d-%02d-%02d %02d:%02d".format(
        dateTime.year,
        dateTime.monthValue,
        dateTime.dayOfMonth,
        dateTime.hour,
        dateTime.minute,
    )
}
