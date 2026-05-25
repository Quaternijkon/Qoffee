package com.qoffee.feature.records

import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecipeTemplate
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

data class BrewCoachRecommendation(
    val title: String,
    val rationale: String,
    val primaryActionLabel: String,
    val action: BrewCoachAction,
    val chips: List<String> = emptyList(),
)

sealed interface BrewCoachAction {
    data class ResumeDraft(val recordId: Long) : BrewCoachAction
    data object StartBlank : BrewCoachAction
    data class StartBean(val beanId: Long) : BrewCoachAction
    data class StartRecipe(val recipeId: Long) : BrewCoachAction
    data class DuplicateRecord(val recordId: Long) : BrewCoachAction
    data class OpenDetail(val recordId: Long) : BrewCoachAction
    data object OpenAnalysis : BrewCoachAction
}

internal fun BrewCoachAction.requiresWritableArchive(): Boolean = when (this) {
    BrewCoachAction.OpenAnalysis,
    is BrewCoachAction.OpenDetail -> false

    is BrewCoachAction.DuplicateRecord,
    is BrewCoachAction.ResumeDraft,
    BrewCoachAction.StartBlank,
    is BrewCoachAction.StartBean,
    is BrewCoachAction.StartRecipe -> true
}

internal fun buildBrewCoachRecommendations(
    records: List<CoffeeRecord>,
    activeDraft: CoffeeRecord?,
    inventory: List<BeanInventory>,
    recipes: List<RecipeTemplate>,
): List<BrewCoachRecommendation> {
    val completedRecords = records
        .filter { it.status == RecordStatus.COMPLETED }
        .sortedByDescending { it.brewedAt }
    val scoredRecords = completedRecords.filter { it.subjectiveEvaluation?.overall != null }
    val recommendations = mutableListOf<BrewCoachRecommendation>()

    activeDraft?.let { draft ->
        recommendations += BrewCoachRecommendation(
            title = "先收尾当前草稿",
            rationale = "你已经有一杯未完成记录，先补齐它，后面的复盘才不会断链。",
            primaryActionLabel = "继续填写",
            action = BrewCoachAction.ResumeDraft(draft.id),
            chips = listOfNotNull(draft.brewMethod?.displayName, draft.beanNameSnapshot, "草稿"),
        )
    }

    if (completedRecords.isEmpty()) {
        val bean = inventory.firstOrNull { it.beanId != null && it.remainingStockG > 0.0 }
        recommendations += if (bean != null) {
            BrewCoachRecommendation(
                title = "用现有豆子开始第一杯",
                rationale = "先建立第一条真实记录，Qoffee 才能帮你复用参数和复盘趋势。",
                primaryActionLabel = "记这颗豆子",
                action = BrewCoachAction.StartBean(checkNotNull(bean.beanId)),
                chips = listOf(bean.beanName, "剩余 ${bean.remainingPercentage}%"),
            )
        } else {
            BrewCoachRecommendation(
                title = "建立第一条咖啡记录",
                rationale = "从粉量、水量、时间和主观评分开始，先让记录闭环跑起来。",
                primaryActionLabel = "添加记录",
                action = BrewCoachAction.StartBlank,
                chips = listOf("新手起点"),
            )
        }
        return recommendations.take(3)
    }

    val latest = completedRecords.first()
    val latestScore = latest.subjectiveEvaluation?.overall
    if (latestScore == null) {
        recommendations += BrewCoachRecommendation(
            title = "补完上一杯的主观评价",
            rationale = "这杯已经有客观参数，但缺少评分和风味，暂时无法沉淀成有效经验。",
            primaryActionLabel = "去复盘这杯",
            action = BrewCoachAction.OpenDetail(latest.id),
            chips = listOfNotNull(latest.beanNameSnapshot, latest.brewMethod?.displayName, "待评分"),
        )
    } else if (latestScore >= 4 && latest.recipeTemplateId == null) {
        recommendations += BrewCoachRecommendation(
            title = "把高分杯沉淀成配方",
            rationale = "最近一杯评分不错，建议保存它的客观参数，下次可以一键复用。",
            primaryActionLabel = "设为配方",
            action = BrewCoachAction.OpenDetail(latest.id),
            chips = listOf("总分 $latestScore/5", latest.beanNameSnapshot ?: "高分记录"),
        )
    } else if (latestScore <= 2) {
        recommendations += BrewCoachRecommendation(
            title = "复制低分杯做一次小幅修正",
            rationale = "低分样本最适合只改一个变量，再冲一杯才能知道问题来自哪里。",
            primaryActionLabel = "复制为下一杯",
            action = BrewCoachAction.DuplicateRecord(latest.id),
            chips = listOf("总分 $latestScore/5", "只改一个变量"),
        )
    }

    val bestRecipe = recipes.firstOrNull { recipe ->
        scoredRecords.any { record ->
            record.recipeTemplateId == recipe.id && (record.subjectiveEvaluation?.overall ?: 0) >= 4
        }
    } ?: recipes.firstOrNull()
    bestRecipe?.let { recipe ->
        recommendations += BrewCoachRecommendation(
            title = "复用一条稳定配方",
            rationale = "当你不想从零填写时，直接从已有配方开始，记录成本最低。",
            primaryActionLabel = "用配方记录",
            action = BrewCoachAction.StartRecipe(recipe.id),
            chips = listOfNotNull(recipe.name, recipe.brewMethod?.displayName, recipe.beanNameSnapshot),
        )
    }

    val underusedBean = inventory
        .filter { it.beanId != null && it.remainingStockG > 0.0 }
        .filterNot { inventoryBean -> completedRecords.any { it.beanProfileId == inventoryBean.beanId } }
        .maxByOrNull { it.remainingStockG }
    underusedBean?.let { bean ->
        recommendations += BrewCoachRecommendation(
            title = "给库存豆子补一条样本",
            rationale = "这颗豆子还有库存但缺少记录，补一杯后库存和复盘会更完整。",
            primaryActionLabel = "记这颗豆子",
            action = BrewCoachAction.StartBean(checkNotNull(bean.beanId)),
            chips = listOf(bean.beanName, "剩余 ${bean.remainingPercentage}%"),
        )
    }

    if (scoredRecords.size >= 3) {
        recommendations += BrewCoachRecommendation(
            title = "查看最近趋势再决定下一杯",
            rationale = "已经有足够评分样本，可以先看变量和评分趋势，避免凭感觉调整。",
            primaryActionLabel = "进入复盘",
            action = BrewCoachAction.OpenAnalysis,
            chips = listOf("${scoredRecords.size} 条评分样本"),
        )
    }

    return recommendations.distinctBy { it.title }.take(3)
}

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
        formatNumericChange("Brew Time", current.brewDurationSeconds?.toDouble(), previous.brewDurationSeconds?.toDouble(), "s", decimals = 0),
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
