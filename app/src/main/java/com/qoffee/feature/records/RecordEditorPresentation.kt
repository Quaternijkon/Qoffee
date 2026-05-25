package com.qoffee.feature.records

import com.qoffee.ui.components.TastingScoreItem

internal data class EditorProgress(
    val completedCount: Int,
    val totalCount: Int,
    val label: String,
)

internal enum class EditorSection(val label: String) {
    SOURCE("来源"),
    PARAMETERS("参数"),
    WATER("注水"),
    TASTING("感受"),
    REVIEW("检查"),
}

internal fun buildEditorProgress(
    objective: ObjectiveFormState,
    subjective: SubjectiveFormState,
): EditorProgress {
    val checks = listOf(
        objective.brewedAtMillis != null,
        objective.brewMethod != null,
        objective.beanProfileId != null,
        objective.coffeeDoseG.isNotBlank(),
        objective.brewWaterMl.isNotBlank() || objective.waterCurveStages.isNotEmpty(),
        objective.grindSetting.isNotBlank(),
        subjective.overall != null,
    )
    val completed = checks.count { it }
    return EditorProgress(
        completedCount = completed,
        totalCount = checks.size,
        label = "已完成 $completed/${checks.size}",
    )
}

internal fun buildEditorParameterSummary(objective: ObjectiveFormState): List<Pair<String, String>> {
    return listOf(
        "粉量" to objective.coffeeDoseG.withUnit("g"),
        "萃取水" to objective.brewWaterMl.withUnit("ml"),
        "水温" to objective.waterTempC.withUnit("°C"),
        "时长" to objective.brewDurationSeconds.toDurationLabel(),
        "研磨" to objective.grindSetting,
    ).filter { it.second.isNotBlank() }
}

internal fun buildEditorMissingFields(
    objective: ObjectiveFormState,
    subjective: SubjectiveFormState,
): List<String> = buildList {
    if (objective.brewedAtMillis == null) add("记录时间")
    if (objective.brewMethod == null) add("冲煮方式")
    if (objective.beanProfileId == null) add("咖啡豆")
    if (subjective.overall == null) add("总评")
}

internal fun buildTastingScoreItems(subjective: SubjectiveFormState): List<TastingScoreItem> {
    return listOf(
        TastingScoreItem("overall", "总评", subjective.overall),
        TastingScoreItem("aroma", "香气", subjective.aroma),
        TastingScoreItem("acidity", "酸质", subjective.acidity),
        TastingScoreItem("sweetness", "甜感", subjective.sweetness),
        TastingScoreItem("bitterness", "苦感", subjective.bitterness),
        TastingScoreItem("body", "醇厚", subjective.body),
        TastingScoreItem("aftertaste", "余韵", subjective.aftertaste),
    )
}

private fun String.withUnit(unit: String): String {
    return if (isBlank()) "" else "$this$unit"
}

private fun String.toDurationLabel(): String {
    val seconds = toIntOrNull() ?: return ""
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "$minutes:${remainder.toString().padStart(2, '0')}"
}
