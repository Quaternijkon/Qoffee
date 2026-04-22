package com.qoffee.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WaterCurveModelsTest {

    private val grinder = GrinderProfile(
        id = 10L,
        archiveId = 1L,
        name = "C40",
        minSetting = 10.0,
        maxSetting = 30.0,
        stepSize = 1.0,
        unitLabel = "格",
    )

    @Test
    fun deriveValuesHandlesPourWaitPourAndBypass() {
        val curve = WaterCurve(
            temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 100.0, quickTemperatureC = 92.0),
                WaitStage(endTimeSeconds = 90),
                PourStage(endTimeSeconds = 150, cumulativeWaterMl = 200.0, quickTemperatureC = 91.0),
                BypassStage(waterMl = 100.0, quickTemperatureC = 92.0),
            ),
        )

        val derived = curve.deriveValues(coffeeDoseG = 20.0)

        assertThat(derived.brewWaterMl).isEqualTo(200.0)
        assertThat(derived.bypassWaterMl).isEqualTo(100.0)
        assertThat(derived.totalWaterMl).isEqualTo(300.0)
        assertThat(derived.brewDurationSeconds).isEqualTo(150)
        assertThat(derived.waterTempC).isEqualTo(91.0)
        assertThat(derived.brewRatio).isEqualTo(15.0)
    }

    @Test
    fun validateRejectsNonIncreasingTimeAndCumulativeWater() {
        val curve = WaterCurve(
            temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
            stages = listOf(
                PourStage(endTimeSeconds = 60, cumulativeWaterMl = 120.0, quickTemperatureC = 92.0),
                WaitStage(endTimeSeconds = 50),
                PourStage(endTimeSeconds = 70, cumulativeWaterMl = 110.0, quickTemperatureC = 91.0),
                BypassStage(waterMl = 0.0),
            ),
        )

        val errors = curve.validate(BrewMethod.POUR_OVER)

        assertThat(errors).isNotEmpty()
        assertThat(errors.joinToString()).contains("时间需要晚于上一段")
        assertThat(errors.joinToString()).contains("累计注水量需要大于上一段")
        assertThat(errors.joinToString()).contains("旁路水量需要大于 0")
    }

    @Test
    fun validateRequiresQuickTemperatureForHotBrew() {
        val curve = WaterCurve(
            temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 100.0),
                WaitStage(endTimeSeconds = 90),
            ),
        )

        val errors = curve.validate(BrewMethod.POUR_OVER)

        assertThat(errors.joinToString()).contains("带温度的注水阶段")
    }

    @Test
    fun buildLegacyWaterCurveCreatesSinglePourAndOptionalBypass() {
        val curve = buildLegacyWaterCurve(
            brewWaterMl = 240.0,
            bypassWaterMl = 40.0,
            waterTempC = 92.0,
            brewDurationSeconds = 150,
        )

        assertThat(curve).isNotNull()
        assertThat(curve!!.stages).hasSize(2)
        assertThat(curve.stages.first()).isInstanceOf(PourStage::class.java)
        assertThat(curve.stages.last()).isInstanceOf(BypassStage::class.java)
    }

    @Test
    fun jsonCodecRoundTripsAmbientAndContainerInV2() {
        val curve = WaterCurve(
            version = 2,
            temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
            ambientTempC = 24.0,
            containerType = ThermalContainerType.GLASS_CUP,
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 120.0, quickTemperatureC = 92.0),
            ),
        )

        val decoded = WaterCurveJsonCodec.decode(WaterCurveJsonCodec.encode(curve))

        assertThat(decoded).isEqualTo(curve)
    }

    @Test
    fun jsonCodecDefaultsAmbientAndContainerForV1() {
        val decoded = WaterCurveJsonCodec.decode(
            """
            {
              "version": 1,
              "temperatureMode": "pour_water",
              "stages": [
                {
                  "type": "pour",
                  "endTimeSeconds": 30,
                  "cumulativeWaterMl": 120.0,
                  "quickTemperatureC": 92.0
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.ambientTempC).isNull()
        assertThat(decoded.containerType).isNull()
    }

    @Test
    fun analysisWaitStageCoolsAndNextPourReheats() {
        val curve = WaterCurve(
            ambientTempC = 25.0,
            containerType = ThermalContainerType.GLASS_CUP,
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 120.0, quickTemperatureC = 94.0),
                WaitStage(endTimeSeconds = 90),
                PourStage(endTimeSeconds = 150, cumulativeWaterMl = 240.0, quickTemperatureC = 90.0),
            ),
        )

        val analysis = curve.analyze(
            coffeeDoseG = 15.0,
            grindSetting = 18.0,
            grinderProfile = grinder,
            roastLevel = RoastLevel.MEDIUM,
            brewMethod = BrewMethod.POUR_OVER,
        )

        val tempAt30 = analysis.points.first { it.elapsedSeconds == 30 }.systemTempC
        val tempAt90 = analysis.points.first { it.elapsedSeconds == 90 }.systemTempC
        val tempAt150 = analysis.points.first { it.elapsedSeconds == 150 }.systemTempC

        assertThat(tempAt30).isNotNull()
        assertThat(tempAt90).isNotNull()
        assertThat(tempAt150).isNotNull()
        assertThat(tempAt90!!).isLessThan(tempAt30!!)
        assertThat(tempAt150!!).isGreaterThan(tempAt90)
    }

    @Test
    fun analysisColdBrewLongWaitCoolsTowardAmbient() {
        val curve = WaterCurve(
            ambientTempC = 4.0,
            containerType = ThermalContainerType.OPEN_THERMOS,
            stages = listOf(
                PourStage(endTimeSeconds = 60, cumulativeWaterMl = 200.0, quickTemperatureC = 5.0),
                WaitStage(endTimeSeconds = 12 * 3600),
            ),
        )

        val analysis = curve.analyze(
            coffeeDoseG = 20.0,
            grindSetting = 28.0,
            grinderProfile = grinder,
            roastLevel = RoastLevel.MEDIUM_DARK,
            brewMethod = BrewMethod.COLD_BREW,
        )

        assertThat(analysis.finalSystemTempC).isNotNull()
        assertThat(analysis.finalSystemTempC!!).isLessThan(5.0)
        assertThat(analysis.estimatedCaffeineMg).isNotNull()
        assertThat(analysis.estimatedCaffeineMg!!).isGreaterThan(0.0)
    }

    @Test
    fun analysisBypassDoesNotIncreaseCaffeineMass() {
        val curve = WaterCurve(
            ambientTempC = 24.0,
            containerType = ThermalContainerType.GLASS_CUP,
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 180.0, quickTemperatureC = 92.0),
                WaitStage(endTimeSeconds = 60),
                BypassStage(waterMl = 60.0, quickTemperatureC = 25.0),
            ),
        )

        val analysis = curve.analyze(
            coffeeDoseG = 15.0,
            grindSetting = 18.0,
            grinderProfile = grinder,
            roastLevel = RoastLevel.MEDIUM,
            brewMethod = BrewMethod.POUR_OVER,
        )

        val caffeineAtBypass = analysis.points.filter { it.elapsedSeconds == 60 }.mapNotNull { it.caffeineMg }

        assertThat(caffeineAtBypass).hasSize(2)
        assertThat(caffeineAtBypass[0]).isEqualTo(caffeineAtBypass[1])
    }

    @Test
    fun analysisHotterAndFinerSetupExtractsMoreCaffeine() {
        val hotFineCurve = WaterCurve(
            ambientTempC = 24.0,
            containerType = ThermalContainerType.GLASS_CUP,
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 120.0, quickTemperatureC = 94.0),
                WaitStage(endTimeSeconds = 120),
                PourStage(endTimeSeconds = 180, cumulativeWaterMl = 240.0, quickTemperatureC = 92.0),
            ),
        )
        val coolCoarseCurve = hotFineCurve.copy(
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 120.0, quickTemperatureC = 86.0),
                WaitStage(endTimeSeconds = 120),
                PourStage(endTimeSeconds = 180, cumulativeWaterMl = 240.0, quickTemperatureC = 84.0),
            ),
        )

        val hotFine = hotFineCurve.analyze(
            coffeeDoseG = 15.0,
            grindSetting = 15.0,
            grinderProfile = grinder,
            roastLevel = RoastLevel.MEDIUM,
            brewMethod = BrewMethod.CLEVER_DRIPPER,
        )
        val coolCoarse = coolCoarseCurve.analyze(
            coffeeDoseG = 15.0,
            grindSetting = 28.0,
            grinderProfile = grinder,
            roastLevel = RoastLevel.MEDIUM,
            brewMethod = BrewMethod.POUR_OVER,
        )

        assertThat(hotFine.estimatedCaffeineMg).isNotNull()
        assertThat(coolCoarse.estimatedCaffeineMg).isNotNull()
        assertThat(hotFine.estimatedCaffeineMg!!).isGreaterThan(coolCoarse.estimatedCaffeineMg!!)
    }
}
