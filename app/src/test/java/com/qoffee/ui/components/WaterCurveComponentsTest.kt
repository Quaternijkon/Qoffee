package com.qoffee.ui.components

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BypassStage
import com.qoffee.core.model.PourStage
import com.qoffee.core.model.WaitStage
import com.qoffee.core.model.WaterCurve
import com.qoffee.core.model.WaterCurveTemperatureMode
import org.junit.Test

class WaterCurveComponentsTest {

    @Test
    fun chartLayoutUsesPerStageDurationWeights() {
        val curve = WaterCurve(
            temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 100.0, quickTemperatureC = 92.0),
                WaitStage(endTimeSeconds = 90),
                PourStage(endTimeSeconds = 150, cumulativeWaterMl = 200.0, quickTemperatureC = 91.0),
            ),
        )

        val segments = buildWaterCurveChartLayout(curve)
        val firstWidth = segments[0].endFraction - segments[0].startFraction
        val secondWidth = segments[1].endFraction - segments[1].startFraction
        val thirdWidth = segments[2].endFraction - segments[2].startFraction

        assertThat(firstWidth).isLessThan(secondWidth)
        assertThat(kotlin.math.abs(secondWidth - thirdWidth)).isLessThan(0.0001f)
    }

    @Test
    fun bypassSegmentKeepsSameHorizontalPosition() {
        val curve = WaterCurve(
            temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
            stages = listOf(
                PourStage(endTimeSeconds = 30, cumulativeWaterMl = 100.0, quickTemperatureC = 92.0),
                BypassStage(waterMl = 80.0, quickTemperatureC = 92.0),
            ),
        )

        val segments = buildWaterCurveChartLayout(curve)
        val bypass = segments.last()

        assertThat(bypass.isBypass).isTrue()
        assertThat(bypass.startFraction).isEqualTo(bypass.endFraction)
        assertThat(bypass.calloutLines.single()).contains("旁路")
        assertThat(bypass.startSeconds).isEqualTo(30)
        assertThat(bypass.endSeconds).isEqualTo(30)
    }

    @Test
    fun stageSummaryTokensExposeCoreValuesWithoutHorizontalOnlyUi() {
        val tokens = buildStageSummaryTokens(
            stage = WaterCurveStageEditorState(
                kind = WaterCurveStageKind.POUR,
                endTimeSeconds = 75,
                cumulativeWaterText = "150",
                quickTemperatureText = "92",
            ),
            temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
        )

        assertThat(tokens).containsExactly(
            "到达 1:15",
            "累计 150ml",
            "温度 92°C",
        )
    }

    @Test
    fun durationFormattingSupportsLongColdBrewDurations() {
        assertThat(formatDurationValue(12 * 3600)).isEqualTo("12:00:00")
    }
}
