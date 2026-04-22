package com.qoffee.data.repository

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BypassStage
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.PourStage
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.ThermalContainerType
import com.qoffee.core.model.WaitStage
import com.qoffee.core.model.WaterCurve
import com.qoffee.core.model.WaterCurveTemperatureMode
import org.junit.Test

class RecordRecipeProjectionTest {

    @Test
    fun buildRecipeTemplateFromRecordCopiesObjectiveFields() {
        val record = CoffeeRecord(
            id = 8L,
            archiveId = 3L,
            status = RecordStatus.COMPLETED,
            brewMethod = BrewMethod.POUR_OVER,
            beanProfileId = 11L,
            beanNameSnapshot = "Kenya AA",
            grinderProfileId = 22L,
            grinderNameSnapshot = "C40",
            grindSetting = 23.0,
            coffeeDoseG = 15.0,
            brewWaterMl = 240.0,
            bypassWaterMl = 30.0,
            waterTempC = 92.0,
            waterCurve = WaterCurve(
                ambientTempC = 24.0,
                containerType = ThermalContainerType.GLASS_CUP,
                temperatureMode = WaterCurveTemperatureMode.POUR_WATER,
                stages = listOf(
                    PourStage(endTimeSeconds = 30, cumulativeWaterMl = 120.0, quickTemperatureC = 92.0),
                    WaitStage(endTimeSeconds = 90),
                    PourStage(endTimeSeconds = 150, cumulativeWaterMl = 240.0, quickTemperatureC = 91.0),
                    BypassStage(waterMl = 30.0, quickTemperatureC = 92.0),
                ),
            ),
            notes = "sweet cup",
        )

        val recipe = buildRecipeTemplateFromRecord(
            record = record,
            name = "Morning Pour Over",
            archiveId = 3L,
            beanNameSnapshot = "Kenya AA",
            grinderNameSnapshot = "C40",
            existingRecipe = null,
            now = 1_700_000_000_000L,
        )

        assertThat(recipe.name).isEqualTo("Morning Pour Over")
        assertThat(recipe.archiveId).isEqualTo(3L)
        assertThat(recipe.beanProfileId).isEqualTo(11L)
        assertThat(recipe.grinderProfileId).isEqualTo(22L)
        assertThat(recipe.grindSetting).isEqualTo(23.0)
        assertThat(recipe.coffeeDoseG).isEqualTo(15.0)
        assertThat(recipe.brewWaterMl).isEqualTo(240.0)
        assertThat(recipe.bypassWaterMl).isEqualTo(30.0)
        assertThat(recipe.waterTempC).isEqualTo(91.0)
        assertThat(recipe.waterCurve).isEqualTo(record.waterCurve)
        assertThat(recipe.notes).isEqualTo("sweet cup")
    }

    @Test
    fun buildRecipeTemplateFromRecordPreservesExistingIdentityWhenOverwriting() {
        val record = CoffeeRecord(
            id = 8L,
            archiveId = 3L,
            status = RecordStatus.COMPLETED,
            brewMethod = BrewMethod.POUR_OVER,
            beanProfileId = 11L,
        )
        val existing = RecipeTemplate(
            id = 99L,
            archiveId = 7L,
            name = "Legacy",
            createdAt = 123L,
            updatedAt = 456L,
        )

        val recipe = buildRecipeTemplateFromRecord(
            record = record,
            name = "Overwrite Me",
            archiveId = 3L,
            beanNameSnapshot = null,
            grinderNameSnapshot = null,
            existingRecipe = existing,
            now = 1_700_000_000_000L,
        )

        assertThat(recipe.id).isEqualTo(99L)
        assertThat(recipe.archiveId).isEqualTo(7L)
        assertThat(recipe.createdAt).isEqualTo(123L)
        assertThat(recipe.updatedAt).isEqualTo(1_700_000_000_000L)
    }
}
