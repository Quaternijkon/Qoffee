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

    @Test
    fun wheelNumericInputSnapsAndPreservesOptionalEmptyValue() {
        assertThat(
            normalizeWheelNumericValue(
                raw = "17",
                minValue = 5.0,
                maxValue = 1200.0,
                step = 5.0,
                decimals = 0,
                allowEmpty = false,
            ),
        ).isEqualTo("15")

        assertThat(
            normalizeWheelNumericValue(
                raw = "",
                minValue = 0.0,
                maxValue = 100.0,
                step = 1.0,
                decimals = 0,
                allowEmpty = true,
            ),
        ).isEmpty()

        assertThat(
            normalizeWheelNumericValue(
                raw = "",
                minValue = 5.0,
                maxValue = 7200.0,
                step = 5.0,
                decimals = 0,
                allowEmpty = false,
            ),
        ).isEqualTo("5")
    }

    @Test
    fun wheelIndexUsesSnappedValueWithinBounds() {
        assertThat(wheelIndexForValue(value = 17.0, minValue = 5.0, maxValue = 1200.0, step = 5.0)).isEqualTo(2)
        assertThat(wheelIndexForValue(value = 9999.0, minValue = 5.0, maxValue = 20.0, step = 5.0)).isEqualTo(3)
    }

    @Test
    fun durationWheelCompositionClampsAndSnapsSplitTimeValues() {
        assertThat(
            composeDurationWheelSeconds(
                hours = 1,
                minutes = 2,
                seconds = 17,
                minSeconds = 5,
                maxSeconds = 7200,
                secondStep = 5,
            ),
        ).isEqualTo(3735)

        assertThat(
            normalizeDurationWheelValue(
                valueSeconds = null,
                minSeconds = 5,
                maxSeconds = 7200,
                secondStep = 5,
            ),
        ).isEqualTo(5)
    }
}
