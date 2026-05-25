package com.qoffee.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NumericInputComponentsTest {

    @Test
    fun normalizeNumericInputClampsAndSnapsToStep() {
        val spec = NumericInputSpec(
            label = "Dose",
            value = "",
            min = 0.0,
            max = 60.0,
            step = 0.5,
            decimals = 1,
        )

        assertThat(normalizeNumericInput("15.24", spec)).isEqualTo("15")
        assertThat(normalizeNumericInput("15.26", spec)).isEqualTo("15.5")
        assertThat(normalizeNumericInput("99", spec)).isEqualTo("60")
        assertThat(normalizeNumericInput("-5", spec)).isEqualTo("0")
    }

    @Test
    fun normalizeNumericInputRespectsRequiredMinimum() {
        val spec = NumericInputSpec(
            label = "Step",
            value = "",
            min = 0.1,
            max = 20.0,
            step = 0.1,
            decimals = 1,
            allowEmpty = false,
        )

        assertThat(normalizeNumericInput("", spec)).isEqualTo("0.1")
        assertThat(normalizeNumericInput("abc", spec)).isEqualTo("0.1")
    }

    @Test
    fun applyNumericKeyLimitsDecimalsAndDeletes() {
        val spec = NumericInputSpec(
            label = "Temperature",
            value = "",
            step = 1.0,
            decimals = 1,
        )

        val typed = listOf("9", "2", ".", "5", "7")
            .fold("") { current, key -> applyNumericKey(current, key, spec) }

        assertThat(typed).isEqualTo("92.5")
        assertThat(applyNumericKey(typed, "Del", spec)).isEqualTo("92.")
    }

    @Test
    fun formatNumericDisplayAddsUnitOnlyWhenValueExists() {
        assertThat(formatNumericDisplay("15", "g")).isEqualTo("15 g")
        assertThat(formatNumericDisplay("", "ml")).isEmpty()
        assertThat(formatNumericDisplay("92", "")).isEqualTo("92")
    }

    @Test
    fun buildWaterQuickValuesUsesDoseAndCommonRatios() {
        assertThat(buildWaterQuickValuesForDose("15", listOf(14, 15, 16)))
            .containsExactly("210", "225", "240")
            .inOrder()
    }

    @Test
    fun buildWaterQuickValuesReturnsEmptyWhenDoseIsMissing() {
        assertThat(buildWaterQuickValuesForDose("", listOf(14, 15, 16))).isEmpty()
        assertThat(buildWaterQuickValuesForDose("abc", listOf(14, 15, 16))).isEmpty()
    }
}
