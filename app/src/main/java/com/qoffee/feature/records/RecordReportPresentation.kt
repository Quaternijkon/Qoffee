package com.qoffee.feature.records

import com.qoffee.core.model.CoffeeRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class RecordReport(
    val title: String,
    val subtitle: String,
    val scoreText: String,
    val parameters: List<Pair<String, String>>,
    val reuseActions: List<RecordReuseAction>,
)

internal enum class RecordReuseAction(val label: String) {
    DUPLICATE("复刻下一杯"),
    SAVE_AS_RECIPE("设为配方"),
    OVERWRITE_RECIPE("覆盖原配方"),
    CREATE_GUIDE("设为指导"),
    EDIT("编辑"),
}

internal fun buildRecordReport(record: CoffeeRecord): RecordReport {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    val method = record.brewMethod?.displayName ?: "未指定方式"
    val title = record.beanNameSnapshot ?: method
    val score = record.subjectiveEvaluation?.overall?.let { "$it/5" } ?: "未评分"
    val actions = buildList {
        add(RecordReuseAction.DUPLICATE)
        add(RecordReuseAction.SAVE_AS_RECIPE)
        if (record.recipeTemplateId != null && !record.recipeNameSnapshot.isNullOrBlank()) {
            add(RecordReuseAction.OVERWRITE_RECIPE)
        }
        add(RecordReuseAction.CREATE_GUIDE)
        add(RecordReuseAction.EDIT)
    }
    return RecordReport(
        title = title,
        subtitle = "$method · ${formatter.format(Date(record.brewedAt))}",
        scoreText = score,
        parameters = buildReportParameters(record),
        reuseActions = actions,
    )
}

private fun buildReportParameters(record: CoffeeRecord): List<Pair<String, String>> {
    return listOf(
        "粉量" to record.coffeeDoseG.formatUnit("g"),
        "萃取水量" to record.brewWaterMl.formatUnit("ml"),
        "总水量" to record.totalWaterMl.formatUnit("ml"),
        "水温" to record.waterTempC.formatUnit("°C"),
        "时长" to record.brewDurationSeconds.formatDuration(),
        "研磨" to record.grindSetting.formatPlain(),
    ).filter { it.second.isNotBlank() }
}

private fun Double?.formatUnit(unit: String): String {
    return this?.let { "${formatReportNumber(it)}$unit" }.orEmpty()
}

private fun Double?.formatPlain(): String {
    return this?.let(::formatReportNumber).orEmpty()
}

private fun Int?.formatDuration(): String {
    val value = this ?: return ""
    val minutes = value / 60
    val seconds = value % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun formatReportNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}
