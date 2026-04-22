package com.qoffee.core.model

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class WaterCurveTemperatureMode(
    val storageValue: String,
    val displayName: String,
) {
    POUR_WATER("pour_water", "注水温度"),
    STAGE_END_SYSTEM("stage_end_system", "阶段末温"),
    ;

    companion object {
        fun fromStorageValue(value: String?): WaterCurveTemperatureMode =
            entries.firstOrNull { it.storageValue == value } ?: POUR_WATER
    }
}

enum class ThermalContainerType(
    val storageValue: String,
    val displayName: String,
    val equivalentWaterMl: Double,
    val coolingConstantPerMinute: Double,
) {
    GLASS_CUP("glass_cup", "玻璃杯", equivalentWaterMl = 40.0, coolingConstantPerMinute = 0.05),
    PP_PLASTIC_CUP("pp_plastic_cup", "PP塑料杯", equivalentWaterMl = 15.0, coolingConstantPerMinute = 0.06),
    PAPER_CUP("paper_cup", "一次性纸杯", equivalentWaterMl = 5.0, coolingConstantPerMinute = 0.08),
    DISPOSABLE_PLASTIC_CUP("disposable_plastic_cup", "一次性塑料杯", equivalentWaterMl = 5.0, coolingConstantPerMinute = 0.09),
    METAL_CUP("metal_cup", "铁杯", equivalentWaterMl = 55.0, coolingConstantPerMinute = 0.07),
    CERAMIC_CUP("ceramic_cup", "陶瓷杯", equivalentWaterMl = 65.0, coolingConstantPerMinute = 0.04),
    BOWL("bowl", "碗", equivalentWaterMl = 80.0, coolingConstantPerMinute = 0.05),
    OPEN_THERMOS("open_thermos", "敞口保温杯", equivalentWaterMl = 20.0, coolingConstantPerMinute = 0.01),
    ;

    companion object {
        fun fromStorageValue(value: String?): ThermalContainerType? =
            entries.firstOrNull { it.storageValue == value }
    }
}

sealed interface WaterCurveStage

data class PourStage(
    val endTimeSeconds: Int,
    val cumulativeWaterMl: Double,
    val quickTemperatureC: Double? = null,
    val startTempC: Double? = null,
    val endTempC: Double? = null,
) : WaterCurveStage

data class WaitStage(
    val endTimeSeconds: Int,
    val startTempC: Double? = null,
    val endTempC: Double? = null,
    val ambientStartTempC: Double? = null,
    val ambientEndTempC: Double? = null,
) : WaterCurveStage

data class BypassStage(
    val waterMl: Double,
    val quickTemperatureC: Double? = null,
    val startTempC: Double? = null,
    val endTempC: Double? = null,
) : WaterCurveStage

data class WaterCurve(
    val version: Int = 2,
    val temperatureMode: WaterCurveTemperatureMode = WaterCurveTemperatureMode.POUR_WATER,
    val ambientTempC: Double? = null,
    val containerType: ThermalContainerType? = null,
    val stages: List<WaterCurveStage> = emptyList(),
)

data class WaterCurveDerivedValues(
    val brewWaterMl: Double? = null,
    val bypassWaterMl: Double? = null,
    val waterTempC: Double? = null,
    val brewDurationSeconds: Int? = null,
    val totalWaterMl: Double? = null,
    val brewRatio: Double? = null,
)

fun WaterCurve.deriveValues(coffeeDoseG: Double?): WaterCurveDerivedValues {
    val brewWaterMl = stages.filterIsInstance<PourStage>().lastOrNull()?.cumulativeWaterMl
    val bypassWaterMl = stages
        .filterIsInstance<BypassStage>()
        .sumOf(BypassStage::waterMl)
        .takeIf { stages.any { stage -> stage is BypassStage } }
        ?: 0.0
    val waterTempC = stages
        .asReversed()
        .asSequence()
        .filterIsInstance<PourStage>()
        .mapNotNull(PourStage::quickTemperatureC)
        .firstOrNull()
    val brewDurationSeconds = stages.fold(null as Int?) { current, stage ->
        when (stage) {
            is PourStage -> stage.endTimeSeconds
            is WaitStage -> stage.endTimeSeconds
            is BypassStage -> current
        }
    }
    val totalWaterMl = brewWaterMl?.let { extractionWater ->
        extractionWater + bypassWaterMl
    }
    val brewRatio = if (totalWaterMl != null && coffeeDoseG != null && coffeeDoseG > 0.0) {
        totalWaterMl / coffeeDoseG
    } else {
        null
    }
    return WaterCurveDerivedValues(
        brewWaterMl = brewWaterMl,
        bypassWaterMl = bypassWaterMl,
        waterTempC = waterTempC,
        brewDurationSeconds = brewDurationSeconds,
        totalWaterMl = totalWaterMl,
        brewRatio = brewRatio,
    )
}

fun WaterCurve.validate(method: BrewMethod?): List<String> {
    val errors = mutableListOf<String>()
    var lastTimedEnd: Int? = null
    var lastCumulativeWater: Double? = null
    var hasPourStage = false
    var hasQuickTemperature = false

    stages.forEachIndexed { index, stage ->
        val stageNumber = index + 1
        when (stage) {
            is PourStage -> {
                hasPourStage = true
                if (stage.endTimeSeconds <= 0) {
                    errors += "第${stageNumber}段注水需要填写大于 0 的到达时间。"
                }
                if (lastTimedEnd != null && stage.endTimeSeconds <= lastTimedEnd) {
                    errors += "第${stageNumber}段时间需要晚于上一段。"
                }
                if (stage.cumulativeWaterMl <= 0.0) {
                    errors += "第${stageNumber}段累计注水量需要大于 0。"
                }
                if (lastCumulativeWater != null && stage.cumulativeWaterMl <= lastCumulativeWater) {
                    errors += "第${stageNumber}段累计注水量需要大于上一段。"
                }
                if (stage.quickTemperatureC != null) {
                    hasQuickTemperature = true
                }
                lastTimedEnd = stage.endTimeSeconds
                lastCumulativeWater = stage.cumulativeWaterMl
            }

            is WaitStage -> {
                if (stage.endTimeSeconds <= 0) {
                    errors += "第${stageNumber}段等待需要填写大于 0 的结束时间。"
                }
                if (lastTimedEnd != null && stage.endTimeSeconds <= lastTimedEnd) {
                    errors += "第${stageNumber}段时间需要晚于上一段。"
                }
                lastTimedEnd = stage.endTimeSeconds
            }

            is BypassStage -> {
                if (stage.waterMl <= 0.0) {
                    errors += "第${stageNumber}段旁路水量需要大于 0。"
                }
            }
        }
    }

    if (method?.isHotBrew == true) {
        if (!hasPourStage) {
            errors += "热冲煮至少需要一段注水阶段。"
        }
        if (!hasQuickTemperature) {
            errors += "热冲煮至少需要一段带温度的注水阶段。"
        }
    }

    return errors
}

fun buildLegacyWaterCurve(
    brewWaterMl: Double?,
    bypassWaterMl: Double?,
    waterTempC: Double?,
    brewDurationSeconds: Int?,
): WaterCurve? {
    if (brewWaterMl == null || brewWaterMl <= 0.0 || brewDurationSeconds == null || brewDurationSeconds <= 0) {
        return null
    }
    val stages = buildList<WaterCurveStage> {
        add(
            PourStage(
                endTimeSeconds = brewDurationSeconds,
                cumulativeWaterMl = brewWaterMl,
                quickTemperatureC = waterTempC,
            ),
        )
        if (bypassWaterMl != null && bypassWaterMl > 0.0) {
            add(
                BypassStage(
                    waterMl = bypassWaterMl,
                    quickTemperatureC = waterTempC,
                ),
            )
        }
    }
    return WaterCurve(
        version = 1,
        temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
        stages = stages,
    )
}

fun buildLegacyWaterCurveSummary(
    brewWaterMl: Double?,
    bypassWaterMl: Double?,
    waterTempC: Double?,
    brewDurationSeconds: Int?,
): String? {
    if (brewWaterMl == null && bypassWaterMl == null && waterTempC == null && brewDurationSeconds == null) {
        return null
    }
    return buildString {
        brewDurationSeconds?.let {
            append("旧记录摘要：总时长 ")
            append(formatWaterCurveDuration(it))
        }
        brewWaterMl?.let {
            if (isNotEmpty()) append(" · ")
            append("萃取水 ")
            append(formatWaterCurveNumber(it))
            append("ml")
        }
        bypassWaterMl?.let {
            if (isNotEmpty()) append(" · ")
            append("旁路 ")
            append(formatWaterCurveNumber(it))
            append("ml")
        }
        waterTempC?.let {
            if (isNotEmpty()) append(" · ")
            append("水温 ")
            append(formatWaterCurveNumber(it))
            append("°C")
        }
    }.takeIf { it.isNotBlank() }
}

fun WaterCurve.stageSummaryLines(): List<String> {
    return stages.map { stage ->
        when (stage) {
            is PourStage -> "${formatWaterCurveDuration(stage.endTimeSeconds)} -> ${formatWaterCurveNumber(stage.cumulativeWaterMl)}ml"
            is WaitStage -> "等待到 ${formatWaterCurveDuration(stage.endTimeSeconds)}"
            is BypassStage -> "旁路 ${formatWaterCurveNumber(stage.waterMl)}ml"
        }
    }
}

fun formatWaterCurveDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val remainingSeconds = safeSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, remainingSeconds)
        else -> String.format(Locale.CHINA, "%d:%02d", minutes, remainingSeconds)
    }
}

fun formatWaterCurveNumber(value: Double): String {
    return String.format(Locale.CHINA, "%.1f", value).trimEnd('0').trimEnd('.')
}

object WaterCurveJsonCodec {
    private const val KEY_VERSION = "version"
    private const val KEY_TEMPERATURE_MODE = "temperatureMode"
    private const val KEY_AMBIENT_TEMP_C = "ambientTempC"
    private const val KEY_CONTAINER_TYPE = "containerType"
    private const val KEY_STAGES = "stages"

    fun encode(curve: WaterCurve?): String? {
        curve ?: return null
        return JSONObject().apply {
            put(KEY_VERSION, curve.version)
            put(KEY_TEMPERATURE_MODE, curve.temperatureMode.storageValue)
            putNullable(KEY_AMBIENT_TEMP_C, curve.ambientTempC)
            putNullable(KEY_CONTAINER_TYPE, curve.containerType?.storageValue)
            put(
                KEY_STAGES,
                JSONArray().apply {
                    curve.stages.forEach { stage ->
                        put(stage.toJson())
                    }
                },
            )
        }.toString()
    }

    fun decode(raw: String?): WaterCurve? {
        val safeRaw = raw?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        return runCatching {
            val root = JSONObject(safeRaw)
            WaterCurve(
                version = root.optInt(KEY_VERSION, 1),
                temperatureMode = WaterCurveTemperatureMode.fromStorageValue(root.optNullableString(KEY_TEMPERATURE_MODE)),
                ambientTempC = root.optNullableDouble(KEY_AMBIENT_TEMP_C),
                containerType = ThermalContainerType.fromStorageValue(root.optNullableString(KEY_CONTAINER_TYPE)),
                stages = root.getJSONArray(KEY_STAGES).mapStages(),
            )
        }.getOrNull()
    }

    private fun WaterCurveStage.toJson(): JSONObject {
        return when (this) {
            is PourStage -> JSONObject().apply {
                put("type", "pour")
                put("endTimeSeconds", endTimeSeconds)
                put("cumulativeWaterMl", cumulativeWaterMl)
                putNullable("quickTemperatureC", quickTemperatureC)
                putNullable("startTempC", startTempC)
                putNullable("endTempC", endTempC)
            }

            is WaitStage -> JSONObject().apply {
                put("type", "wait")
                put("endTimeSeconds", endTimeSeconds)
                putNullable("startTempC", startTempC)
                putNullable("endTempC", endTempC)
                putNullable("ambientStartTempC", ambientStartTempC)
                putNullable("ambientEndTempC", ambientEndTempC)
            }

            is BypassStage -> JSONObject().apply {
                put("type", "bypass")
                put("waterMl", waterMl)
                putNullable("quickTemperatureC", quickTemperatureC)
                putNullable("startTempC", startTempC)
                putNullable("endTempC", endTempC)
            }
        }
    }

    private fun JSONArray.mapStages(): List<WaterCurveStage> = buildList {
        repeat(length()) { index ->
            val stage = getJSONObject(index)
            add(
                when (stage.getString("type")) {
                    "pour" -> PourStage(
                        endTimeSeconds = stage.getInt("endTimeSeconds"),
                        cumulativeWaterMl = stage.getDouble("cumulativeWaterMl"),
                        quickTemperatureC = stage.optNullableDouble("quickTemperatureC"),
                        startTempC = stage.optNullableDouble("startTempC"),
                        endTempC = stage.optNullableDouble("endTempC"),
                    )

                    "wait" -> WaitStage(
                        endTimeSeconds = stage.getInt("endTimeSeconds"),
                        startTempC = stage.optNullableDouble("startTempC"),
                        endTempC = stage.optNullableDouble("endTempC"),
                        ambientStartTempC = stage.optNullableDouble("ambientStartTempC"),
                        ambientEndTempC = stage.optNullableDouble("ambientEndTempC"),
                    )

                    "bypass" -> BypassStage(
                        waterMl = stage.getDouble("waterMl"),
                        quickTemperatureC = stage.optNullableDouble("quickTemperatureC"),
                        startTempC = stage.optNullableDouble("startTempC"),
                        endTempC = stage.optNullableDouble("endTempC"),
                    )

                    else -> error("Unknown water curve stage type")
                },
            )
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key)
}
