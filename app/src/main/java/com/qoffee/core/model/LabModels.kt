package com.qoffee.core.model

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val PARTICLE_REFERENCE_ESPRESSO = 300.0 / 1400.0
private const val PARTICLE_REFERENCE_MOKA = 500.0 / 1400.0
private const val PARTICLE_REFERENCE_POUR_OVER = 800.0 / 1400.0
private const val PARTICLE_REFERENCE_COLD_BREW = 1.0

data class GrindCalibrationRange(
    val start: Double,
    val end: Double,
) {
    val midpoint: Double get() = (start + end) / 2.0
    val lower: Double get() = min(start, end)
    val upper: Double get() = max(start, end)
    val isValid: Boolean get() = start.isFinite() && end.isFinite() && abs(end - start) > 1e-9
}

data class GrindNormalizationAnchor(
    val label: String,
    val rawSetting: Double,
    val normalizedValue: Double,
)

data class GrindNormalizationCurvePoint(
    val rawSetting: Double,
    val normalizedValue: Double,
)

data class GrindNormalizationCurve(
    val anchors: List<GrindNormalizationAnchor>,
    val points: List<GrindNormalizationCurvePoint>,
    val isReverseDial: Boolean,
)

data class GrindNormalizationProfile(
    val espressoRange: GrindCalibrationRange,
    val mokaRange: GrindCalibrationRange,
    val pourOverRange: GrindCalibrationRange,
    val coldBrewPoint: Double,
) {
    fun validationErrors(
        minSetting: Double? = null,
        maxSetting: Double? = null,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (!espressoRange.isValid) errors += "意式刻度区间无效。"
        if (!mokaRange.isValid) errors += "摩卡壶刻度区间无效。"
        if (!pourOverRange.isValid) errors += "手冲刻度区间无效。"
        if (!coldBrewPoint.isFinite()) errors += "冷萃上限刻度无效。"
        val anchors = rawAnchors()
        if (anchors.zipWithNext().any { abs(it.first.rawSetting - it.second.rawSetting) < 1e-9 }) {
            errors += "四类做法的锚点不能重合。"
        }
        val rawValues = anchors.map(GrindNormalizationAnchor::rawSetting)
        val ascending = rawValues.zipWithNext().all { it.first < it.second }
        val descending = rawValues.zipWithNext().all { it.first > it.second }
        if (!ascending && !descending) {
            errors += "意式、摩卡壶、手冲、冷萃四个锚点必须整体单调。"
        }
        if (minSetting != null && coldBrewPoint < minSetting || maxSetting != null && coldBrewPoint > maxSetting) {
            errors += "冷萃上限刻度必须落在磨豆机的机械范围内。"
        }
        return errors
    }

    fun isUsable(
        minSetting: Double? = null,
        maxSetting: Double? = null,
    ): Boolean = validationErrors(minSetting, maxSetting).isEmpty()

    fun rawAnchors(): List<GrindNormalizationAnchor> = listOf(
        GrindNormalizationAnchor("Espresso", espressoRange.midpoint, PARTICLE_REFERENCE_ESPRESSO),
        GrindNormalizationAnchor("Moka", mokaRange.midpoint, PARTICLE_REFERENCE_MOKA),
        GrindNormalizationAnchor("Pour-over", pourOverRange.midpoint, PARTICLE_REFERENCE_POUR_OVER),
        GrindNormalizationAnchor("Cold Brew", coldBrewPoint, PARTICLE_REFERENCE_COLD_BREW),
    )

    fun normalize(rawSetting: Double): Double? {
        val errors = validationErrors()
        if (errors.isNotEmpty()) return null
        val anchors = rawAnchors()
        val espresso = anchors[0]
        val moka = anchors[1]
        val cold = anchors[3]
        val reverseDial = espresso.rawSetting > cold.rawSetting
        val sortedAnchors = anchors.sortedBy(GrindNormalizationAnchor::rawSetting)
        val interpolator = MonotoneCubicInterpolator(
            xs = sortedAnchors.map(GrindNormalizationAnchor::rawSetting),
            ys = sortedAnchors.map(GrindNormalizationAnchor::normalizedValue),
        )

        val value = when {
            !reverseDial && rawSetting < espresso.rawSetting -> {
                linearFromAnchor(
                    x = rawSetting,
                    anchorX = espresso.rawSetting,
                    anchorY = espresso.normalizedValue,
                    neighborX = moka.rawSetting,
                    neighborY = moka.normalizedValue,
                )
            }

            reverseDial && rawSetting > espresso.rawSetting -> {
                linearFromAnchor(
                    x = rawSetting,
                    anchorX = espresso.rawSetting,
                    anchorY = espresso.normalizedValue,
                    neighborX = moka.rawSetting,
                    neighborY = moka.normalizedValue,
                )
            }

            !reverseDial && rawSetting > cold.rawSetting -> PARTICLE_REFERENCE_COLD_BREW
            reverseDial && rawSetting < cold.rawSetting -> PARTICLE_REFERENCE_COLD_BREW
            else -> interpolator.interpolate(rawSetting)
        }
        return value.coerceIn(0.0, 1.0)
    }

    fun buildCurve(
        minSetting: Double,
        maxSetting: Double,
        sampleCount: Int = 72,
    ): GrindNormalizationCurve? {
        if (!isUsable(minSetting, maxSetting)) return null
        if (maxSetting <= minSetting) return null
        val points = buildList {
            repeat(sampleCount) { index ->
                val fraction = if (sampleCount <= 1) 0.0 else index.toDouble() / (sampleCount - 1).toDouble()
                val raw = minSetting + ((maxSetting - minSetting) * fraction)
                normalize(raw)?.let { normalized ->
                    add(
                        GrindNormalizationCurvePoint(
                            rawSetting = raw,
                            normalizedValue = normalized,
                        ),
                    )
                }
            }
        }
        return GrindNormalizationCurve(
            anchors = rawAnchors(),
            points = points,
            isReverseDial = rawAnchors().first().rawSetting > rawAnchors().last().rawSetting,
        )
    }

    private fun linearFromAnchor(
        x: Double,
        anchorX: Double,
        anchorY: Double,
        neighborX: Double,
        neighborY: Double,
    ): Double {
        val safeDelta = neighborX - anchorX
        if (abs(safeDelta) < 1e-9) {
            return anchorY
        }
        val slope = (neighborY - anchorY) / safeDelta
        return anchorY + ((x - anchorX) * slope)
    }
}

data class ObjectiveSnapshot(
    val brewMethod: BrewMethod? = null,
    val beanProfileId: Long? = null,
    val beanNameSnapshot: String? = null,
    val grinderProfileId: Long? = null,
    val grinderNameSnapshot: String? = null,
    val grindSetting: Double? = null,
    val coffeeDoseG: Double? = null,
    val brewWaterMl: Double? = null,
    val bypassWaterMl: Double? = null,
    val waterTempC: Double? = null,
    val waterCurve: WaterCurve? = null,
    val notes: String = "",
) {
    fun toDraftUpdate(
        brewedAt: Long? = null,
    ): ObjectiveDraftUpdate = ObjectiveDraftUpdate(
        brewMethod = brewMethod,
        beanProfileId = beanProfileId,
        grinderProfileId = grinderProfileId,
        grindSetting = grindSetting,
        coffeeDoseG = coffeeDoseG,
        brewWaterMl = brewWaterMl,
        bypassWaterMl = bypassWaterMl,
        waterTempC = waterTempC,
        waterCurve = waterCurve,
        brewedAt = brewedAt,
        notes = notes,
    )
}

fun CoffeeRecord.toObjectiveSnapshot(): ObjectiveSnapshot = ObjectiveSnapshot(
    brewMethod = brewMethod,
    beanProfileId = beanProfileId,
    beanNameSnapshot = beanNameSnapshot,
    grinderProfileId = grinderProfileId,
    grinderNameSnapshot = grinderNameSnapshot,
    grindSetting = grindSetting,
    coffeeDoseG = coffeeDoseG,
    brewWaterMl = brewWaterMl,
    bypassWaterMl = bypassWaterMl,
    waterTempC = waterTempC,
    waterCurve = waterCurve,
    notes = notes,
)

fun RecipeTemplate.toObjectiveSnapshot(): ObjectiveSnapshot = ObjectiveSnapshot(
    brewMethod = brewMethod,
    beanProfileId = beanProfileId,
    beanNameSnapshot = beanNameSnapshot,
    grinderProfileId = grinderProfileId,
    grinderNameSnapshot = grinderNameSnapshot,
    grindSetting = grindSetting,
    coffeeDoseG = coffeeDoseG,
    brewWaterMl = brewWaterMl,
    bypassWaterMl = bypassWaterMl,
    waterTempC = waterTempC,
    waterCurve = waterCurve,
    notes = notes,
)

enum class ExperimentVariableType(
    val storageValue: String,
    val displayName: String,
) {
    BEAN("bean", "豆子"),
    GRINDER("grinder", "磨豆机"),
    GRIND_SETTING("grind_setting", "研磨格数"),
    WATER_TEMP("water_temp", "水温"),
    COFFEE_DOSE("coffee_dose", "粉量"),
    BREW_WATER("brew_water", "萃取水量"),
    ;

    companion object {
        fun fromStorageValue(value: String?): ExperimentVariableType? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class ExperimentVariableLevel(
    val id: String,
    val label: String,
    val numericValue: Double? = null,
    val beanId: Long? = null,
    val grinderId: Long? = null,
)

data class ExperimentVariableDefinition(
    val type: ExperimentVariableType,
    val levels: List<ExperimentVariableLevel>,
)

data class ExperimentCellPlan(
    val id: String,
    val title: String,
    val scenarioId: String,
    val scenarioLabel: String,
    val xLabel: String,
    val yLabel: String,
    val overrides: ObjectiveSnapshot,
)

data class ExperimentRunSummary(
    val recordId: Long,
    val cellId: String,
    val recordTitle: String,
    val score: Int? = null,
    val deltaSummary: String? = null,
    val isOffPlan: Boolean = false,
)

data class ExperimentProject(
    val id: Long,
    val archiveId: Long,
    val title: String,
    val description: String = "",
    val hypothesis: String = "",
    val baseRecordId: Long? = null,
    val baseRecipeId: Long? = null,
    val baseline: ObjectiveSnapshot = ObjectiveSnapshot(),
    val variables: List<ExperimentVariableDefinition> = emptyList(),
    val cells: List<ExperimentCellPlan> = emptyList(),
    val runs: List<ExperimentRunSummary> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val scenarioTabs: List<Pair<String, String>>
        get() = cells
            .map { it.scenarioId to it.scenarioLabel }
            .distinct()
}

data class GuideStageCard(
    val id: String,
    val title: String,
    val instruction: String,
    val targetDurationSeconds: Int,
    val targetValueLabel: String = "",
    val tip: String = "",
)

data class GuideTemplate(
    val id: Long = 0L,
    val archiveId: Long = 0L,
    val title: String,
    val description: String = "",
    val brewMethod: BrewMethod? = null,
    val sourceRecordId: Long? = null,
    val isBuiltIn: Boolean = false,
    val objective: ObjectiveSnapshot = ObjectiveSnapshot(),
    val stages: List<GuideStageCard> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class ExperimentProjectDraft(
    val title: String,
    val description: String = "",
    val hypothesis: String = "",
    val baseRecordId: Long? = null,
    val baseRecipeId: Long? = null,
    val baseline: ObjectiveSnapshot,
    val variables: List<ExperimentVariableDefinition>,
)

object GrindNormalizationJsonCodec {
    fun encode(profile: GrindNormalizationProfile?): String? {
        profile ?: return null
        return JSONObject().apply {
            putRange("espressoRange", profile.espressoRange)
            putRange("mokaRange", profile.mokaRange)
            putRange("pourOverRange", profile.pourOverRange)
            put("coldBrewPoint", profile.coldBrewPoint)
        }.toString()
    }

    fun decode(raw: String?): GrindNormalizationProfile? {
        val safeRaw = raw?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        return runCatching {
            val root = JSONObject(safeRaw)
            GrindNormalizationProfile(
                espressoRange = root.getRange("espressoRange"),
                mokaRange = root.getRange("mokaRange"),
                pourOverRange = root.getRange("pourOverRange"),
                coldBrewPoint = root.getDouble("coldBrewPoint"),
            )
        }.getOrNull()
    }

    private fun JSONObject.putRange(key: String, range: GrindCalibrationRange) {
        put(
            key,
            JSONObject().apply {
                put("start", range.start)
                put("end", range.end)
            },
        )
    }

    private fun JSONObject.getRange(key: String): GrindCalibrationRange {
        val value = getJSONObject(key)
        return GrindCalibrationRange(
            start = value.getDouble("start"),
            end = value.getDouble("end"),
        )
    }
}

object ExperimentProjectConfigJsonCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(project: ExperimentProject): String {
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            putNullable("baseRecordId", project.baseRecordId)
            putNullable("baseRecipeId", project.baseRecipeId)
            put("baseline", project.baseline.toJson())
            put(
                "variables",
                JSONArray().apply {
                    project.variables.forEach { variable ->
                        put(
                            JSONObject().apply {
                                put("type", variable.type.storageValue)
                                put(
                                    "levels",
                                    JSONArray().apply {
                                        variable.levels.forEach { level ->
                                            put(level.toJson())
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
            put(
                "cells",
                JSONArray().apply {
                    project.cells.forEach { cell ->
                        put(
                            JSONObject().apply {
                                put("id", cell.id)
                                put("title", cell.title)
                                put("scenarioId", cell.scenarioId)
                                put("scenarioLabel", cell.scenarioLabel)
                                put("xLabel", cell.xLabel)
                                put("yLabel", cell.yLabel)
                                put("overrides", cell.overrides.toJson())
                            },
                        )
                    }
                },
            )
        }.toString()
    }

    fun decode(
        id: Long,
        archiveId: Long,
        title: String,
        description: String,
        hypothesis: String,
        configJson: String,
        createdAt: Long,
        updatedAt: Long,
        runs: List<ExperimentRunSummary>,
    ): ExperimentProject? {
        val safeRaw = configJson.trim().ifBlank { return null }
        return runCatching {
            val root = JSONObject(safeRaw)
            val variables = root.optJSONArray("variables")?.toVariableDefinitions().orEmpty()
            val cells = root.optJSONArray("cells")?.toCellPlans().orEmpty()
            ExperimentProject(
                id = id,
                archiveId = archiveId,
                title = title,
                description = description,
                hypothesis = hypothesis,
                baseRecordId = root.optNullableLong("baseRecordId"),
                baseRecipeId = root.optNullableLong("baseRecipeId"),
                baseline = root.getJSONObject("baseline").toObjectiveSnapshot(),
                variables = variables,
                cells = cells,
                runs = runs,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.getOrNull()
    }

    fun fromDraft(
        id: Long = 0L,
        archiveId: Long,
        createdAt: Long,
        updatedAt: Long,
        draft: ExperimentProjectDraft,
    ): ExperimentProject {
        val cells = buildCells(draft)
        return ExperimentProject(
            id = id,
            archiveId = archiveId,
            title = draft.title,
            description = draft.description,
            hypothesis = draft.hypothesis,
            baseRecordId = draft.baseRecordId,
            baseRecipeId = draft.baseRecipeId,
            baseline = draft.baseline,
            variables = draft.variables,
            cells = cells,
            runs = emptyList(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun buildCells(draft: ExperimentProjectDraft): List<ExperimentCellPlan> {
        if (draft.variables.isEmpty()) return emptyList()
        val xVariable = draft.variables.first()
        val yVariable = draft.variables.getOrNull(1)
        val scenarioVariables = draft.variables.drop(2)
        val scenarios = if (scenarioVariables.isEmpty()) {
            listOf(emptyList<ExperimentVariableLevel>())
        } else {
            cartesianLevels(scenarioVariables)
        }

        return buildList {
            scenarios.forEachIndexed { scenarioIndex, scenarioLevels ->
                val scenarioId = if (scenarioLevels.isEmpty()) {
                    "default"
                } else {
                    "scenario-${scenarioIndex + 1}"
                }
                val scenarioLabel = if (scenarioLevels.isEmpty()) {
                    "默认场景"
                } else {
                    scenarioLevels.joinToString(" / ") { it.label }
                }
                val yLevels = yVariable?.levels ?: listOf(ExperimentVariableLevel(id = "single-axis", label = "默认"))
                xVariable.levels.forEach { xLevel ->
                    yLevels.forEach { yLevel ->
                        val allLevels = buildList {
                            add(xLevel)
                            if (yVariable != null) add(yLevel)
                            addAll(scenarioLevels)
                        }
                        val overrides = allLevels.fold(draft.baseline) { current, level ->
                            val variable = draft.variables.firstOrNull { definition ->
                                definition.levels.any { it.id == level.id }
                            } ?: return@fold current
                            applyLevel(current, variable.type, level)
                        }
                        add(
                            ExperimentCellPlan(
                                id = buildString {
                                    append(scenarioId)
                                    append(":")
                                    append(xLevel.id)
                                    append(":")
                                    append(yLevel.id)
                                },
                                title = listOf(xLevel.label, yVariable?.let { yLevel.label }).filterNotNull().joinToString(" / "),
                                scenarioId = scenarioId,
                                scenarioLabel = scenarioLabel,
                                xLabel = xLevel.label,
                                yLabel = yVariable?.let { yLevel.label } ?: "默认",
                                overrides = overrides,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun cartesianLevels(variables: List<ExperimentVariableDefinition>): List<List<ExperimentVariableLevel>> {
        var combinations = listOf(emptyList<ExperimentVariableLevel>())
        variables.forEach { variable ->
            combinations = combinations.flatMap { current ->
                variable.levels.map { level -> current + level }
            }
        }
        return combinations
    }

    private fun applyLevel(
        baseline: ObjectiveSnapshot,
        type: ExperimentVariableType,
        level: ExperimentVariableLevel,
    ): ObjectiveSnapshot {
        return when (type) {
            ExperimentVariableType.BEAN -> baseline.copy(beanProfileId = level.beanId)
            ExperimentVariableType.GRINDER -> baseline.copy(grinderProfileId = level.grinderId)
            ExperimentVariableType.GRIND_SETTING -> baseline.copy(grindSetting = level.numericValue)
            ExperimentVariableType.WATER_TEMP -> baseline.copy(waterTempC = level.numericValue)
            ExperimentVariableType.COFFEE_DOSE -> baseline.copy(coffeeDoseG = level.numericValue)
            ExperimentVariableType.BREW_WATER -> baseline.copy(brewWaterMl = level.numericValue)
        }
    }

    private fun JSONArray.toVariableDefinitions(): List<ExperimentVariableDefinition> = buildList {
        repeat(length()) { index ->
            val item = getJSONObject(index)
            val type = ExperimentVariableType.fromStorageValue(item.optString("type")) ?: return@repeat
            add(
                ExperimentVariableDefinition(
                    type = type,
                    levels = item.optJSONArray("levels")?.toLevels().orEmpty(),
                ),
            )
        }
    }

    private fun JSONArray.toLevels(): List<ExperimentVariableLevel> = buildList {
        repeat(length()) { index ->
            add(getJSONObject(index).toLevel())
        }
    }

    private fun JSONArray.toCellPlans(): List<ExperimentCellPlan> = buildList {
        repeat(length()) { index ->
            val item = getJSONObject(index)
            add(
                ExperimentCellPlan(
                    id = item.getString("id"),
                    title = item.optString("title"),
                    scenarioId = item.optString("scenarioId", "default"),
                    scenarioLabel = item.optString("scenarioLabel", "默认场景"),
                    xLabel = item.optString("xLabel"),
                    yLabel = item.optString("yLabel"),
                    overrides = item.getJSONObject("overrides").toObjectiveSnapshot(),
                ),
            )
        }
    }
}

object GuideTemplateConfigJsonCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(guide: GuideTemplate): String {
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            putNullable("sourceRecordId", guide.sourceRecordId)
            put("isBuiltIn", guide.isBuiltIn)
            putNullable("brewMethodCode", guide.brewMethod?.code)
            put("objective", guide.objective.toJson())
            put(
                "stages",
                JSONArray().apply {
                    guide.stages.forEach { stage ->
                        put(
                            JSONObject().apply {
                                put("id", stage.id)
                                put("title", stage.title)
                                put("instruction", stage.instruction)
                                put("targetDurationSeconds", stage.targetDurationSeconds)
                                put("targetValueLabel", stage.targetValueLabel)
                                put("tip", stage.tip)
                            },
                        )
                    }
                },
            )
            put(
                "sharing",
                JSONObject().apply {
                    put("shareEnabled", false)
                    put("remoteId", JSONObject.NULL)
                },
            )
        }.toString()
    }

    fun decode(
        id: Long,
        archiveId: Long,
        title: String,
        description: String,
        hypothesis: String,
        configJson: String,
        createdAt: Long,
        updatedAt: Long,
    ): GuideTemplate? {
        val safeRaw = configJson.trim().ifBlank { return null }
        return runCatching {
            val root = JSONObject(safeRaw)
            GuideTemplate(
                id = id,
                archiveId = archiveId,
                title = title,
                description = listOf(description, hypothesis).filter { it.isNotBlank() }.joinToString("\n"),
                brewMethod = BrewMethod.fromCode(root.optNullableString("brewMethodCode")),
                sourceRecordId = root.optNullableLong("sourceRecordId"),
                isBuiltIn = root.optBoolean("isBuiltIn", false),
                objective = root.getJSONObject("objective").toObjectiveSnapshot(),
                stages = root.optJSONArray("stages")?.toGuideStages().orEmpty(),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.getOrNull()
    }

    private fun JSONArray.toGuideStages(): List<GuideStageCard> = buildList {
        repeat(length()) { index ->
            val item = getJSONObject(index)
            add(
                GuideStageCard(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    instruction = item.getString("instruction"),
                    targetDurationSeconds = item.optInt("targetDurationSeconds", 0),
                    targetValueLabel = item.optString("targetValueLabel"),
                    tip = item.optString("tip"),
                ),
            )
        }
    }
}

private fun ExperimentVariableLevel.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("label", label)
        putNullable("numericValue", numericValue)
        putNullable("beanId", beanId)
        putNullable("grinderId", grinderId)
    }
}

private fun JSONObject.toLevel(): ExperimentVariableLevel = ExperimentVariableLevel(
    id = getString("id"),
    label = getString("label"),
    numericValue = optNullableDouble("numericValue"),
    beanId = optNullableLong("beanId"),
    grinderId = optNullableLong("grinderId"),
)

private fun ObjectiveSnapshot.toJson(): JSONObject {
    return JSONObject().apply {
        putNullable("brewMethodCode", brewMethod?.code)
        putNullable("beanProfileId", beanProfileId)
        putNullable("beanNameSnapshot", beanNameSnapshot)
        putNullable("grinderProfileId", grinderProfileId)
        putNullable("grinderNameSnapshot", grinderNameSnapshot)
        putNullable("grindSetting", grindSetting)
        putNullable("coffeeDoseG", coffeeDoseG)
        putNullable("brewWaterMl", brewWaterMl)
        putNullable("bypassWaterMl", bypassWaterMl)
        putNullable("waterTempC", waterTempC)
        putNullable("waterCurveJson", WaterCurveJsonCodec.encode(waterCurve))
        put("notes", notes)
    }
}

private fun JSONObject.toObjectiveSnapshot(): ObjectiveSnapshot = ObjectiveSnapshot(
    brewMethod = BrewMethod.fromCode(optNullableString("brewMethodCode")),
    beanProfileId = optNullableLong("beanProfileId"),
    beanNameSnapshot = optNullableString("beanNameSnapshot"),
    grinderProfileId = optNullableLong("grinderProfileId"),
    grinderNameSnapshot = optNullableString("grinderNameSnapshot"),
    grindSetting = optNullableDouble("grindSetting"),
    coffeeDoseG = optNullableDouble("coffeeDoseG"),
    brewWaterMl = optNullableDouble("brewWaterMl"),
    bypassWaterMl = optNullableDouble("bypassWaterMl"),
    waterTempC = optNullableDouble("waterTempC"),
    waterCurve = WaterCurveJsonCodec.decode(optNullableString("waterCurveJson")),
    notes = optString("notes"),
)

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key)

private fun JSONObject.optNullableLong(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key)

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key)

private class MonotoneCubicInterpolator(
    private val xs: List<Double>,
    private val ys: List<Double>,
) {
    private val tangents: List<Double> = computeTangents()

    fun interpolate(x: Double): Double {
        if (xs.size == 1) return ys.first()
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        val index = xs.indexOfLast { it <= x }.coerceIn(0, xs.lastIndex - 1)
        val x0 = xs[index]
        val x1 = xs[index + 1]
        val y0 = ys[index]
        val y1 = ys[index + 1]
        val m0 = tangents[index]
        val m1 = tangents[index + 1]
        val h = x1 - x0
        if (abs(h) < 1e-9) return y0
        val t = (x - x0) / h
        val t2 = t * t
        val t3 = t2 * t
        val h00 = (2 * t3) - (3 * t2) + 1
        val h10 = t3 - (2 * t2) + t
        val h01 = (-2 * t3) + (3 * t2)
        val h11 = t3 - t2
        return (h00 * y0) + (h10 * h * m0) + (h01 * y1) + (h11 * h * m1)
    }

    private fun computeTangents(): List<Double> {
        if (xs.size != ys.size || xs.size < 2) {
            return List(xs.size) { 0.0 }
        }
        if (xs.size == 2) {
            val delta = (ys[1] - ys[0]) / (xs[1] - xs[0])
            return listOf(delta, delta)
        }
        val h = xs.zipWithNext().map { it.second - it.first }
        val delta = ys.zipWithNext().mapIndexed { index, pair ->
            (pair.second - pair.first) / h[index]
        }
        val tangents = MutableList(xs.size) { 0.0 }
        tangents[0] = endpointSlope(h[0], h[1], delta[0], delta[1])
        tangents[xs.lastIndex] = endpointSlope(
            h = h[h.lastIndex],
            nextH = h[h.lastIndex - 1],
            delta = delta[delta.lastIndex],
            nextDelta = delta[delta.lastIndex - 1],
        )
        for (index in 1 until xs.lastIndex) {
            tangents[index] = if (delta[index - 1] == 0.0 || delta[index] == 0.0 || delta[index - 1].sign() != delta[index].sign()) {
                0.0
            } else {
                val w1 = (2.0 * h[index]) + h[index - 1]
                val w2 = h[index] + (2.0 * h[index - 1])
                (w1 + w2) / ((w1 / delta[index - 1]) + (w2 / delta[index]))
            }
        }
        return tangents
    }

    private fun endpointSlope(
        h: Double,
        nextH: Double,
        delta: Double,
        nextDelta: Double,
    ): Double {
        val slope = (((2.0 * h) + nextH) * delta - (h * nextDelta)) / (h + nextH)
        return when {
            slope.sign() != delta.sign() -> 0.0
            delta.sign() != nextDelta.sign() && abs(slope) > abs(3.0 * delta) -> 3.0 * delta
            else -> slope
        }
    }

    private fun Double.sign(): Int = when {
        this > 0.0 -> 1
        this < 0.0 -> -1
        else -> 0
    }
}

fun formatNormalizedGrind(value: Double?): String {
    return value?.let { String.format(Locale.CHINA, "%.3f", it).trimEnd('0').trimEnd('.') } ?: "--"
}
