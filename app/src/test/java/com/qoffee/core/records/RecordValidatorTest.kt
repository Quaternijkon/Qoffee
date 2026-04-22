package com.qoffee.core.records

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.GrinderProfile
import com.qoffee.core.model.PourStage
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.WaitStage
import com.qoffee.core.model.WaterCurve
import com.qoffee.core.model.WaterCurveTemperatureMode
import org.junit.Test

class RecordValidatorTest {

    private val validator = RecordValidator()

    @Test
    fun hotBrewRequiresWaterTemperature() {
        val result = validator.validate(
            record = baseRecord.copy(
                brewMethod = BrewMethod.POUR_OVER,
                waterTempC = null,
            ),
            grinderProfile = null,
        )

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).contains("热冲煮方式需要填写水温。")
    }

    @Test
    fun coldBrewDoesNotRequireWaterTemperature() {
        val result = validator.validate(
            record = baseRecord.copy(
                brewMethod = BrewMethod.COLD_BREW,
                waterTempC = null,
            ),
            grinderProfile = null,
        )

        assertThat(result.isValid).isTrue()
    }

    @Test
    fun grindSettingMustRespectSelectedGrinderRange() {
        val grinder = GrinderProfile(
            id = 10L,
            name = "Comandante",
            minSetting = 10.0,
            maxSetting = 30.0,
            stepSize = 1.0,
            unitLabel = "格",
        )

        val result = validator.validate(
            record = baseRecord.copy(
                brewMethod = BrewMethod.POUR_OVER,
                grinderProfileId = grinder.id,
                grindSetting = 35.0,
                waterTempC = 92.0,
            ),
            grinderProfile = grinder,
        )

        assertThat(result.isValid).isFalse()
        assertThat(result.errors.single()).contains("研磨格数需要落在")
    }

    @Test
    fun invalidWaterCurveFailsValidationEvenWhenSummaryFieldsExist() {
        val result = validator.validate(
            record = baseRecord.copy(
                brewMethod = BrewMethod.POUR_OVER,
                waterTempC = 92.0,
                waterCurve = WaterCurve(
                    temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
                    stages = listOf(
                        PourStage(endTimeSeconds = 60, cumulativeWaterMl = 120.0, quickTemperatureC = 92.0),
                        WaitStage(endTimeSeconds = 30),
                    ),
                ),
            ),
            grinderProfile = null,
        )

        assertThat(result.isValid).isFalse()
        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun missingAmbientAndContainerDoNotBlockSavingCurve() {
        val result = validator.validate(
            record = baseRecord.copy(
                brewMethod = BrewMethod.POUR_OVER,
                waterTempC = 92.0,
                waterCurve = WaterCurve(
                    temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
                    ambientTempC = null,
                    containerType = null,
                    stages = listOf(
                        PourStage(endTimeSeconds = 30, cumulativeWaterMl = 120.0, quickTemperatureC = 92.0),
                        WaitStage(endTimeSeconds = 90),
                    ),
                ),
            ),
            grinderProfile = null,
        )

        assertThat(result.isValid).isTrue()
    }

    private val baseRecord = CoffeeRecord(
        id = 1L,
        status = RecordStatus.DRAFT,
        beanProfileId = 2L,
        coffeeDoseG = 15.0,
        brewWaterMl = 240.0,
        totalWaterMl = 240.0,
        brewRatio = 16.0,
    )
}
