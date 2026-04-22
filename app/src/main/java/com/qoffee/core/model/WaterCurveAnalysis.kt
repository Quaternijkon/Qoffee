package com.qoffee.core.model

import kotlin.math.exp
import kotlin.math.max

private const val GAS_CONSTANT = 8.314
private const val ACTIVATION_ENERGY = 52_000.0
private const val PRE_EXPONENTIAL_FACTOR = 300_000.0
private const val PARTITION_COEFFICIENT = 3.5
private const val BASE_CAFFEINE_PER_GRAM = 12.0

data class WaterCurveAnalysisPoint(
    val elapsedSeconds: Int,
    val brewWaterMl: Double,
    val totalWaterMl: Double,
    val systemTempC: Double? = null,
    val caffeineMg: Double? = null,
)

data class WaterCurveAnalysis(
    val points: List<WaterCurveAnalysisPoint> = emptyList(),
    val finalSystemTempC: Double? = null,
    val estimatedCaffeineMg: Double? = null,
    val estimatedCaffeinePercent: Double? = null,
    val temperatureUnavailableReason: String? = null,
    val caffeineUnavailableReason: String? = null,
)

fun WaterCurve.analyze(
    coffeeDoseG: Double?,
    grindSetting: Double?,
    grinderProfile: GrinderProfile?,
    roastLevel: RoastLevel?,
    brewMethod: BrewMethod?,
): WaterCurveAnalysis {
    if (stages.isEmpty()) {
        return WaterCurveAnalysis()
    }

    val volumeOnlyPoints = buildVolumeOnlyPoints()
    val temperatureUnavailableReason = when {
        temperatureMode != WaterCurveTemperatureMode.POUR_WATER ->
            "当前曲线使用阶段末温模式，暂不估算容器系统温度。"

        ambientTempC == null ->
            "填写室温后可估算容器系统温度。"

        containerType == null ->
            "选择容器后可估算容器系统温度。"

        stages.filterIsInstance<PourStage>().any { it.quickTemperatureC == null } ->
            "为每段注水补全温度后可估算容器系统温度。"

        else -> null
    }

    val caffeineUnavailableReason = when {
        temperatureUnavailableReason != null ->
            "补全温度曲线后可估算咖啡因曲线。"

        coffeeDoseG == null || coffeeDoseG <= 0.0 ->
            "填写咖啡粉量后可估算咖啡因曲线。"

        else -> null
    }

    if (temperatureUnavailableReason != null) {
        return WaterCurveAnalysis(
            points = volumeOnlyPoints,
            temperatureUnavailableReason = temperatureUnavailableReason,
            caffeineUnavailableReason = caffeineUnavailableReason,
        )
    }

    val safeAmbient = checkNotNull(ambientTempC)
    val container = checkNotNull(containerType)
    val canEstimateCaffeine = caffeineUnavailableReason == null
    val totalTimedSeconds = lastTimedSeconds()
    val sampleRateSeconds = when {
        totalTimedSeconds > 12 * 3600 -> 600
        totalTimedSeconds > 3600 -> 60
        totalTimedSeconds > 600 -> 15
        else -> 1
    }
    val grindFactor = grindFactor(grindSetting = grindSetting, grinderProfile = grinderProfile)
    val roastFactor = roastFactor(roastLevel)
    val methodFactor = methodFactor(brewMethod)
    val maxPotentialCaffeineMg = coffeeDoseG?.times(BASE_CAFFEINE_PER_GRAM)

    val points = mutableListOf<WaterCurveAnalysisPoint>()
    var currentBrewWaterMl = 0.0
    var currentTotalWaterMl = 0.0
    var currentSystemTempC = safeAmbient
    var currentCaffeineMg = 0.0
    var elapsedSeconds = 0
    var previousTimedEnd = 0

    fun appendPoint(force: Boolean = false) {
        if (!force && elapsedSeconds > 0 && elapsedSeconds % sampleRateSeconds != 0) {
            return
        }
        points.appendDistinct(
            WaterCurveAnalysisPoint(
                elapsedSeconds = elapsedSeconds,
                brewWaterMl = currentBrewWaterMl,
                totalWaterMl = currentTotalWaterMl,
                systemTempC = currentSystemTempC,
                caffeineMg = if (canEstimateCaffeine) currentCaffeineMg else null,
            ),
        )
    }

    appendPoint(force = true)

    stages.forEach { stage ->
        when (stage) {
            is PourStage -> {
                if (stage.startTempC != null) {
                    currentSystemTempC = stage.startTempC
                    appendPoint(force = true)
                }
                val durationSeconds = (stage.endTimeSeconds - previousTimedEnd).coerceAtLeast(1)
                val targetBrewWaterMl = stage.cumulativeWaterMl
                val deltaWaterMl = (targetBrewWaterMl - currentBrewWaterMl).coerceAtLeast(0.0)
                val waterPerSecondMl = deltaWaterMl / durationSeconds
                val pourTempC = checkNotNull(stage.quickTemperatureC)

                repeat(durationSeconds) {
                    val addedWaterMl = waterPerSecondMl
                    if (addedWaterMl > 0.0) {
                        val totalHeat = (currentTotalWaterMl + container.equivalentWaterMl) * currentSystemTempC +
                            addedWaterMl * pourTempC
                        currentBrewWaterMl += addedWaterMl
                        currentTotalWaterMl += addedWaterMl
                        currentSystemTempC = totalHeat / (currentTotalWaterMl + container.equivalentWaterMl)
                    }
                    currentSystemTempC -=
                        container.coolingConstantPerMinute * (currentSystemTempC - safeAmbient) / 60.0
                    if (canEstimateCaffeine) {
                        currentCaffeineMg = estimateNextCaffeineMg(
                            currentCaffeineMg = currentCaffeineMg,
                            coffeeDoseG = checkNotNull(coffeeDoseG),
                            maxPotentialCaffeineMg = checkNotNull(maxPotentialCaffeineMg),
                            currentBrewWaterMl = currentBrewWaterMl,
                            currentTotalWaterMl = currentTotalWaterMl,
                            currentSystemTempC = currentSystemTempC,
                            grindFactor = grindFactor,
                            roastFactor = roastFactor,
                            methodFactor = methodFactor,
                        )
                    }
                    elapsedSeconds += 1
                    appendPoint()
                }

                previousTimedEnd = stage.endTimeSeconds
                currentBrewWaterMl = targetBrewWaterMl
                if (stage.endTempC != null) {
                    currentSystemTempC = stage.endTempC
                }
                appendPoint(force = true)
            }

            is WaitStage -> {
                if (stage.startTempC != null) {
                    currentSystemTempC = stage.startTempC
                    appendPoint(force = true)
                }
                val durationSeconds = (stage.endTimeSeconds - previousTimedEnd).coerceAtLeast(1)
                val ambientStart = stage.ambientStartTempC ?: safeAmbient
                val ambientEnd = stage.ambientEndTempC ?: safeAmbient

                repeat(durationSeconds) { secondIndex ->
                    val progress = (secondIndex + 1) / durationSeconds.toDouble()
                    val ambientAtStep = ambientStart + ((ambientEnd - ambientStart) * progress)
                    currentSystemTempC -=
                        container.coolingConstantPerMinute * (currentSystemTempC - ambientAtStep) / 60.0
                    if (canEstimateCaffeine) {
                        currentCaffeineMg = estimateNextCaffeineMg(
                            currentCaffeineMg = currentCaffeineMg,
                            coffeeDoseG = checkNotNull(coffeeDoseG),
                            maxPotentialCaffeineMg = checkNotNull(maxPotentialCaffeineMg),
                            currentBrewWaterMl = currentBrewWaterMl,
                            currentTotalWaterMl = currentTotalWaterMl,
                            currentSystemTempC = currentSystemTempC,
                            grindFactor = grindFactor,
                            roastFactor = roastFactor,
                            methodFactor = methodFactor,
                        )
                    }
                    elapsedSeconds += 1
                    appendPoint()
                }

                previousTimedEnd = stage.endTimeSeconds
                if (stage.endTempC != null) {
                    currentSystemTempC = stage.endTempC
                }
                appendPoint(force = true)
            }

            is BypassStage -> {
                if (stage.startTempC != null) {
                    currentSystemTempC = stage.startTempC
                    appendPoint(force = true)
                }
                val bypassTempC = stage.quickTemperatureC ?: currentSystemTempC
                val totalHeat = (currentTotalWaterMl + container.equivalentWaterMl) * currentSystemTempC +
                    stage.waterMl * bypassTempC
                currentTotalWaterMl += stage.waterMl
                currentSystemTempC = totalHeat / (currentTotalWaterMl + container.equivalentWaterMl)
                if (stage.endTempC != null) {
                    currentSystemTempC = stage.endTempC
                }
                appendPoint(force = true)
            }
        }
    }

    return WaterCurveAnalysis(
        points = points,
        finalSystemTempC = currentSystemTempC,
        estimatedCaffeineMg = if (canEstimateCaffeine) currentCaffeineMg else null,
        estimatedCaffeinePercent = if (canEstimateCaffeine && maxPotentialCaffeineMg != null && maxPotentialCaffeineMg > 0.0) {
            (currentCaffeineMg / maxPotentialCaffeineMg) * 100.0
        } else {
            null
        },
        temperatureUnavailableReason = null,
        caffeineUnavailableReason = caffeineUnavailableReason,
    )
}

private fun WaterCurve.buildVolumeOnlyPoints(): List<WaterCurveAnalysisPoint> {
    val points = mutableListOf<WaterCurveAnalysisPoint>()
    var brewWaterMl = 0.0
    var totalWaterMl = 0.0
    var currentTimedSeconds = 0

    points.appendDistinct(
        WaterCurveAnalysisPoint(
            elapsedSeconds = 0,
            brewWaterMl = 0.0,
            totalWaterMl = 0.0,
        ),
    )

    stages.forEach { stage ->
        when (stage) {
            is PourStage -> {
                points.appendDistinct(
                    WaterCurveAnalysisPoint(
                        elapsedSeconds = currentTimedSeconds,
                        brewWaterMl = brewWaterMl,
                        totalWaterMl = totalWaterMl,
                    ),
                )
                val addedWaterMl = (stage.cumulativeWaterMl - brewWaterMl).coerceAtLeast(0.0)
                brewWaterMl = stage.cumulativeWaterMl
                totalWaterMl += addedWaterMl
                currentTimedSeconds = stage.endTimeSeconds
                points.appendDistinct(
                    WaterCurveAnalysisPoint(
                        elapsedSeconds = currentTimedSeconds,
                        brewWaterMl = brewWaterMl,
                        totalWaterMl = totalWaterMl,
                    ),
                )
            }

            is WaitStage -> {
                currentTimedSeconds = stage.endTimeSeconds
                points.appendDistinct(
                    WaterCurveAnalysisPoint(
                        elapsedSeconds = currentTimedSeconds,
                        brewWaterMl = brewWaterMl,
                        totalWaterMl = totalWaterMl,
                    ),
                )
            }

            is BypassStage -> {
                points.appendDistinct(
                    WaterCurveAnalysisPoint(
                        elapsedSeconds = currentTimedSeconds,
                        brewWaterMl = brewWaterMl,
                        totalWaterMl = totalWaterMl,
                    ),
                )
                totalWaterMl += stage.waterMl
                points.appendDistinct(
                    WaterCurveAnalysisPoint(
                        elapsedSeconds = currentTimedSeconds,
                        brewWaterMl = brewWaterMl,
                        totalWaterMl = totalWaterMl,
                    ),
                )
            }
        }
    }

    return points
}

private fun MutableList<WaterCurveAnalysisPoint>.appendDistinct(point: WaterCurveAnalysisPoint) {
    val lastPoint = lastOrNull()
    if (lastPoint == point) return
    add(point)
}

private fun WaterCurve.lastTimedSeconds(): Int {
    return stages.fold(0) { current, stage ->
        when (stage) {
            is PourStage -> stage.endTimeSeconds
            is WaitStage -> stage.endTimeSeconds
            is BypassStage -> current
        }
    }
}

private fun estimateNextCaffeineMg(
    currentCaffeineMg: Double,
    coffeeDoseG: Double,
    maxPotentialCaffeineMg: Double,
    currentBrewWaterMl: Double,
    currentTotalWaterMl: Double,
    currentSystemTempC: Double,
    grindFactor: Double,
    roastFactor: Double,
    methodFactor: Double,
): Double {
    if (currentBrewWaterMl <= 0.0 || currentTotalWaterMl <= 0.0) {
        return currentCaffeineMg
    }
    val waterRatio = currentBrewWaterMl / coffeeDoseG
    val wettingFactor = if (waterRatio < 1.5) waterRatio / 1.5 else 1.0
    if (wettingFactor <= 0.0) {
        return currentCaffeineMg
    }
    val tempKelvin = currentSystemTempC + 273.15
    var rateConstant = PRE_EXPONENTIAL_FACTOR * exp(-ACTIVATION_ENERGY / (GAS_CONSTANT * tempKelvin))
    rateConstant *= grindFactor * roastFactor * methodFactor * wettingFactor

    val remainingCaffeineMg = (maxPotentialCaffeineMg - currentCaffeineMg).coerceAtLeast(0.0)
    if (remainingCaffeineMg <= 0.0) {
        return currentCaffeineMg
    }
    val solidConcentration = remainingCaffeineMg / coffeeDoseG
    val liquidConcentration = currentCaffeineMg / currentTotalWaterMl
    val drivingForce = max(0.0, solidConcentration - (liquidConcentration * PARTITION_COEFFICIENT))
    val extractedIncrement = (drivingForce * coffeeDoseG) * (1 - exp(-rateConstant))
    return (currentCaffeineMg + extractedIncrement).coerceAtMost(maxPotentialCaffeineMg)
}

private fun grindFactor(
    grindSetting: Double?,
    grinderProfile: GrinderProfile?,
): Double {
    val currentSetting = grindSetting ?: return 1.0
    val profile = grinderProfile ?: return 1.0
    if (profile.maxSetting <= profile.minSetting) return 1.0
    if (currentSetting !in profile.minSetting..profile.maxSetting) return 1.0
    return 0.65 + (0.70 * ((profile.maxSetting - currentSetting) / (profile.maxSetting - profile.minSetting)))
}

private fun roastFactor(roastLevel: RoastLevel?): Double {
    return when (roastLevel) {
        RoastLevel.EXTREME_LIGHT -> 0.92
        RoastLevel.LIGHT -> 0.96
        RoastLevel.LIGHT_MEDIUM -> 0.99
        RoastLevel.MEDIUM -> 1.0
        RoastLevel.MEDIUM_DARK -> 1.03
        RoastLevel.DARK -> 1.06
        RoastLevel.EXTREME_DARK -> 1.08
        null -> 1.0
    }
}

private fun methodFactor(method: BrewMethod?): Double {
    return when (method) {
        BrewMethod.ESPRESSO_MACHINE -> 1.35
        BrewMethod.MOKA_POT -> 1.20
        BrewMethod.POUR_OVER -> 1.0
        BrewMethod.CLEVER_DRIPPER -> 1.10
        BrewMethod.AEROPRESS -> 1.15
        BrewMethod.COLD_BREW -> 0.70
        null -> 1.0
    }
}
