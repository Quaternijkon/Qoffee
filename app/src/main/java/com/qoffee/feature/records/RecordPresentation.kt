package com.qoffee.feature.records

import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class RecordComparisonSummary(
    val headline: String,
    val details: List<String>,
)

data class RecordTimelineItem(
    val record: CoffeeRecord,
    val comparison: RecordComparisonSummary?,
)

data class RecordTimelineGroup(
    val label: String,
    val items: List<RecordTimelineItem>,
)

internal fun buildRecordTimelineGroups(records: List<CoffeeRecord>): List<RecordTimelineGroup> {
    val comparisonMap = buildComparisonSummaryMap(records)
    val formatter = SimpleDateFormat("M 月 d 日 EEEE", Locale.CHINA)
    return records
        .sortedByDescending { it.brewedAt }
        .map { record ->
            RecordTimelineItem(
                record = record,
                comparison = comparisonMap[record.id],
            )
        }
        .groupBy { formatter.format(Date(it.record.brewedAt)) }
        .map { (label, items) -> RecordTimelineGroup(label = label, items = items) }
}

internal fun buildComparisonSummaryMap(records: List<CoffeeRecord>): Map<Long, RecordComparisonSummary> {
    val comparisons = mutableMapOf<Long, RecordComparisonSummary>()
    val previousByKey = mutableMapOf<ComparableKey, CoffeeRecord>()
    records
        .filter { it.status == RecordStatus.COMPLETED }
        .sortedBy { it.brewedAt }
        .forEach { record ->
            val key = record.comparableKey() ?: return@forEach
            previousByKey[key]?.let { previous ->
                comparisons[record.id] = buildComparisonSummary(record, previous)
            }
            previousByKey[key] = record
        }
    return comparisons
}

internal fun findPreviousComparableRecord(
    records: List<CoffeeRecord>,
    current: CoffeeRecord,
): CoffeeRecord? {
    val key = current.comparableKey() ?: return null
    return records
        .asSequence()
        .filter { it.status == RecordStatus.COMPLETED }
        .filter { it.id != current.id }
        .filter { it.comparableKey() == key }
        .filter { it.brewedAt < current.brewedAt }
        .sortedByDescending { it.brewedAt }
        .firstOrNull()
}

internal fun buildComparisonSummary(
    current: CoffeeRecord,
    previous: CoffeeRecord,
): RecordComparisonSummary {
    val scoreDelta = current.subjectiveEvaluation?.overall?.let { currentScore ->
        previous.subjectiveEvaluation?.overall?.let { currentScore - it }
    }
    val details = listOfNotNull(
        scoreDelta?.takeIf { it != 0 }?.let { formatDeltaText("总分", it, "") },
        formatNumericChange("水温", current.waterTempC, previous.waterTempC, "°C", decimals = 0),
        formatNumericChange("水量", current.brewWaterMl, previous.brewWaterMl, "ml", decimals = 0),
        formatNumericChange("粉量", current.coffeeDoseG, previous.coffeeDoseG, "g", decimals = 1),
        formatNumericChange("研磨", current.grindSetting, previous.grindSetting, "", decimals = 1),
        formatNumericChange("粉水比", current.brewRatio, previous.brewRatio, "", decimals = 1),
    ).take(3)

    val headline = when {
        scoreDelta != null && scoreDelta > 0 -> "这一杯比上一杯更高分"
        scoreDelta != null && scoreDelta < 0 -> "这一杯比上一杯低 ${abs(scoreDelta)} 分"
        details.isNotEmpty() -> "参数与上一杯有变化"
        else -> "和上一杯几乎相同"
    }

    return RecordComparisonSummary(
        headline = headline,
        details = details.ifEmpty { listOf("参数和评分都比较接近。") },
    )
}

internal fun buildBeanHistorySummary(records: List<CoffeeRecord>, beanId: Long?): String? {
    val safeBeanId = beanId ?: return null
    val beanRecords = records.filter {
        it.status == RecordStatus.COMPLETED &&
            it.beanProfileId == safeBeanId &&
            it.subjectiveEvaluation?.overall != null
    }
    if (beanRecords.isEmpty()) return null
    val averageScore = beanRecords.mapNotNull { it.subjectiveEvaluation?.overall }.average()
    return "同豆已记录 ${beanRecords.size} 杯，平均总分 ${"%.1f".format(Locale.CHINA, averageScore)}。"
}

private fun formatNumericChange(
    label: String,
    current: Double?,
    previous: Double?,
    unit: String,
    decimals: Int,
): String? {
    if (current == null || previous == null) return null
    val delta = current - previous
    if (abs(delta) < 0.0001) return null
    val formatted = if (decimals == 0) {
        delta.toInt().toString()
    } else {
        "%.${decimals}f".format(Locale.CHINA, delta).trimEnd('0').trimEnd('.')
    }
    val prefix = if (delta > 0) "+" else ""
    return "$label $prefix$formatted$unit"
}

private fun formatDeltaText(label: String, delta: Int, unit: String): String {
    val prefix = if (delta > 0) "+" else ""
    return "$label $prefix$delta$unit"
}

private fun CoffeeRecord.comparableKey(): ComparableKey? {
    val safeBeanId = beanProfileId ?: return null
    val safeMethod = brewMethod ?: return null
    return ComparableKey(
        beanId = safeBeanId,
        brewMethod = safeMethod,
    )
}

private data class ComparableKey(
    val beanId: Long,
    val brewMethod: BrewMethod,
)
