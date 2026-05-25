package com.qoffee.core.model

import java.util.Locale

fun buildLocalAiCoachSuggestions(
    records: List<CoffeeRecord>,
    maxSuggestions: Int = 3,
): List<AiCoachSuggestion> {
    val completedRecords = records
        .filter { it.status == RecordStatus.COMPLETED }
        .sortedByDescending { it.brewedAt }
    val scoredRecords = completedRecords
        .filter { it.subjectiveEvaluation?.overall != null }

    if (completedRecords.isEmpty()) return emptyList()

    val suggestions = mutableListOf<AiCoachSuggestion>()
    val latest = completedRecords.first()
    val latestScore = latest.subjectiveEvaluation?.overall

    if (latestScore == null) {
        suggestions += AiCoachSuggestion(
            id = "score-latest-${latest.id}",
            title = "先补完最近一杯的评价",
            message = "这条记录已有客观参数，但缺少评分。补上主观评价后，本地 Coach 才能引用它判断下一杯。",
            actionLabel = "打开记录",
            action = AiCoachAction.OpenRecord(latest.id),
            sourceRecords = listOf(latest.toCoachSource()),
        )
    }

    if (latestScore != null && latestScore <= 2) {
        suggestions += AiCoachSuggestion(
            id = "adjust-low-${latest.id}",
            title = "低分样本适合复制后单变量微调",
            message = buildLowScoreAdjustmentMessage(latest),
            actionLabel = "复制这杯",
            action = AiCoachAction.DuplicateRecord(latest.id),
            sourceRecords = listOf(latest.toCoachSource()),
            confidenceLabel = "引用 1 条低分样本",
        )
    }

    val stableHighScoreGroup = scoredRecords
        .filter { (it.subjectiveEvaluation?.overall ?: 0) >= 4 }
        .groupBy { it.stableCupKey() }
        .values
        .filter { it.size >= 2 }
        .maxWithOrNull(compareBy<List<CoffeeRecord>> { it.size }.thenBy { group -> group.maxOf { it.brewedAt } })
        ?.sortedByDescending { it.brewedAt }

    if (!stableHighScoreGroup.isNullOrEmpty()) {
        val representative = stableHighScoreGroup.first()
        suggestions += AiCoachSuggestion(
            id = "repeat-high-${representative.stableCupKey()}",
            title = "高分组合已经出现复现信号",
            message = "同一组合里已有多条 4 分以上记录。下一步优先复用这组参数，再观察是否稳定。",
            actionLabel = "查看复盘",
            action = AiCoachAction.OpenAnalysis,
            sourceRecords = stableHighScoreGroup.take(3).map { it.toCoachSource() },
            confidenceLabel = "引用 ${stableHighScoreGroup.size} 条高分样本",
        )
    }

    buildRecentTrendSuggestion(scoredRecords)?.let { suggestions += it }

    if (suggestions.isEmpty() && scoredRecords.isNotEmpty()) {
        val best = scoredRecords.maxBy { it.subjectiveEvaluation?.overall ?: 0 }
        suggestions += AiCoachSuggestion(
            id = "review-best-${best.id}",
            title = "从最高分样本反推下一杯",
            message = "当前还没有明显趋势，先打开最高分记录，复用它的客观参数作为下一轮基线。",
            actionLabel = "打开高分记录",
            action = AiCoachAction.OpenRecord(best.id),
            sourceRecords = listOf(best.toCoachSource()),
        )
    }

    return suggestions.distinctBy { it.id }.take(maxSuggestions)
}

private fun buildRecentTrendSuggestion(scoredRecords: List<CoffeeRecord>): AiCoachSuggestion? {
    if (scoredRecords.size < 6) return null
    val recent = scoredRecords.take(3)
    val previous = scoredRecords.drop(3).take(3)
    val recentAverage = recent.mapNotNull { it.subjectiveEvaluation?.overall }.average()
    val previousAverage = previous.mapNotNull { it.subjectiveEvaluation?.overall }.average()
    val delta = recentAverage - previousAverage
    if (kotlin.math.abs(delta) < 0.7) return null

    return AiCoachSuggestion(
        id = "recent-trend-${recent.first().id}-${previous.first().id}",
        title = if (delta > 0) "最近三杯表现正在上行" else "最近三杯评分明显回落",
        message = if (delta > 0) {
            "最近三杯均分比前一组三杯高 ${formatCoachNumber(delta)}。建议进入复盘确认是哪个参数带来了改善。"
        } else {
            "最近三杯均分比前一组三杯低 ${formatCoachNumber(-delta)}。建议先看趋势，再决定是否复制低分样本微调。"
        },
        actionLabel = "查看趋势",
        action = AiCoachAction.OpenAnalysis,
        sourceRecords = (recent + previous).take(6).map { it.toCoachSource() },
        confidenceLabel = "引用 6 条近期样本",
    )
}

private fun buildLowScoreAdjustmentMessage(record: CoffeeRecord): String {
    return when {
        record.grindSetting != null -> "先复制这杯，只调整研磨一档，其余参数保持不变，观察酸甜苦和总分是否回到目标区间。"
        record.waterTempC != null -> "先复制这杯，只把水温小幅调整 1-2 度，其余参数保持不变，避免一次改太多。"
        record.brewWaterMl != null -> "先复制这杯，只微调萃取水量，其余参数保持不变，让结果能被复盘。"
        else -> "先复制这杯，只改一个你最确定的问题参数，并在完成后补评分。"
    }
}

private fun CoffeeRecord.toCoachSource(): AiCoachSourceRecord {
    val title = beanNameSnapshot
        ?: recipeNameSnapshot
        ?: brewMethod?.displayName
        ?: "记录 $id"
    return AiCoachSourceRecord(
        recordId = id,
        label = title,
        score = subjectiveEvaluation?.overall,
    )
}

private fun CoffeeRecord.stableCupKey(): String {
    return when {
        recipeTemplateId != null -> "recipe:$recipeTemplateId"
        beanProfileId != null && brewMethod != null -> "bean:$beanProfileId:${brewMethod.code}"
        beanNameSnapshot != null && brewMethod != null -> "bean-name:${normalizedBeanNameKey(beanNameSnapshot)}:${brewMethod.code}"
        else -> "record:$id"
    }
}

private fun formatCoachNumber(value: Double): String =
    String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
