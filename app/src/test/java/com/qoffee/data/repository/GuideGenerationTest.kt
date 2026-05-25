package com.qoffee.data.repository

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.PourStage
import com.qoffee.core.model.WaitStage
import com.qoffee.core.model.WaterCurve
import com.qoffee.core.model.WaterCurveTemperatureMode
import org.junit.Test

class GuideGenerationTest {

    @Test
    fun guideStagesFromRecordUseIncrementalDurationsAndStageWater() {
        val record = CoffeeRecord(
            id = 42L,
            brewMethod = BrewMethod.POUR_OVER,
            waterCurve = WaterCurve(
                temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
                stages = listOf(
                    PourStage(endTimeSeconds = 30, cumulativeWaterMl = 60.0, quickTemperatureC = 92.0),
                    WaitStage(endTimeSeconds = 45),
                    PourStage(endTimeSeconds = 90, cumulativeWaterMl = 180.0, quickTemperatureC = 91.0),
                ),
            ),
        )

        val stages = buildGuideStagesFromRecord(record)

        assertThat(stages.map { it.targetDurationSeconds }).containsExactly(30, 15, 45).inOrder()
        assertThat(stages[0].targetValueLabel).contains("本段 60ml")
        assertThat(stages[2].targetValueLabel).contains("本段 120ml")
        assertThat(stages[2].instruction).contains("累计到 180ml")
    }
}
