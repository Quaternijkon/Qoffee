package com.qoffee.core.model

import java.util.Locale
import kotlin.math.max

data class ReviewExperimentDraftPlan(
    val draft: ExperimentProjectDraft,
    val selectedParameter: NumericParameter,
    val variableType: ExperimentVariableType,
)

fun buildReviewExperimentDraftPlan(
    record: CoffeeRecord,
    requestedParameter: NumericParameter,
): ReviewExperimentDraftPlan? {
    if (record.status != RecordStatus.COMPLETED) return null

    val candidateParameters = buildList {
        add(requestedParameter)
        add(NumericParameter.WATER_TEMP)
        add(NumericParameter.GRIND_SETTING)
        add(NumericParameter.NORMALIZED_GRIND)
        add(NumericParameter.BREW_RATIO)
        add(NumericParameter.TOTAL_WATER)
    }.distinct()

    val selected = candidateParameters
        .firstNotNullOfOrNull { parameter ->
            buildExperimentVariable(record, parameter)?.let { variable -> parameter to variable }
        } ?: return null

    val parameter = selected.first
    val variable = selected.second
    val recordLabel = record.beanNameSnapshot
        ?: record.recipeNameSnapshot
        ?: record.brewMethod?.displayName
        ?: "记录 ${record.id}"
    val parameterLabel = parameter.displayName

    return ReviewExperimentDraftPlan(
        selectedParameter = parameter,
        variableType = variable.type,
        draft = ExperimentProjectDraft(
            title = "复盘实验 · $recordLabel · $parameterLabel",
            description = "由复盘样本 ${record.id} 生成，所有实验格子会回链到记录。",
            hypothesis = "只调整$parameterLabel，观察评分、风味和稳定性是否改善。",
            baseRecordId = record.id,
            baseline = record.toObjectiveSnapshot(),
            variables = listOf(variable),
        ),
    )
}

private fun buildExperimentVariable(
    record: CoffeeRecord,
    parameter: NumericParameter,
): ExperimentVariableDefinition? {
    return when (parameter) {
        NumericParameter.WATER_TEMP -> record.waterTempC?.let { current ->
            numericVariable(
                type = ExperimentVariableType.WATER_TEMP,
                prefix = "review-temp",
                values = threePointValues(current, step = 2.0, floor = 70.0),
                unit = "C",
            )
        }

        NumericParameter.GRIND_SETTING,
        NumericParameter.NORMALIZED_GRIND,
        -> record.grindSetting?.let { current ->
            val step = record.grinderProfile?.stepSize?.takeIf { it > 0.0 } ?: 1.0
            numericVariable(
                type = ExperimentVariableType.GRIND_SETTING,
                prefix = "review-grind",
                values = threePointValues(current, step = step, floor = 0.0),
                unit = "",
            )
        }

        NumericParameter.TOTAL_WATER -> record.brewWaterMl?.let { current ->
            numericVariable(
                type = ExperimentVariableType.BREW_WATER,
                prefix = "review-water",
                values = threePointValues(current, step = 15.0, floor = 0.0),
                unit = "ml",
            )
        }

        NumericParameter.BREW_RATIO -> buildBrewRatioWaterVariable(record)
        NumericParameter.BREW_TIME,
        NumericParameter.BYPASS_WATER,
        -> null
    }
}

private fun buildBrewRatioWaterVariable(record: CoffeeRecord): ExperimentVariableDefinition? {
    val dose = record.coffeeDoseG?.takeIf { it > 0.0 } ?: return null
    val currentRatio = record.brewRatio
        ?: record.totalWaterMl?.let { it / dose }
        ?: record.brewWaterMl?.let { it / dose }
        ?: return null
    val bypass = record.bypassWaterMl ?: 0.0
    val levels = threePointValues(currentRatio, step = 1.0, floor = 1.0)
        .mapIndexed { index, ratio ->
            val brewWater = max(0.0, dose * ratio - bypass)
            ExperimentVariableLevel(
                id = "review-ratio-$index",
                label = "1:${formatPlannerNumber(ratio)}",
                numericValue = brewWater,
            )
        }
    return ExperimentVariableDefinition(
        type = ExperimentVariableType.BREW_WATER,
        levels = levels,
    )
}

private fun numericVariable(
    type: ExperimentVariableType,
    prefix: String,
    values: List<Double>,
    unit: String,
): ExperimentVariableDefinition {
    return ExperimentVariableDefinition(
        type = type,
        levels = values.mapIndexed { index, value ->
            ExperimentVariableLevel(
                id = "$prefix-$index",
                label = buildString {
                    append(formatPlannerNumber(value))
                    append(unit)
                },
                numericValue = value,
            )
        },
    )
}

private fun threePointValues(
    current: Double,
    step: Double,
    floor: Double,
): List<Double> {
    val low = max(floor, current - step)
    return listOf(low, current, current + step).distinctBy { formatPlannerNumber(it) }
}

private fun formatPlannerNumber(value: Double): String =
    String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
