package com.qoffee.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReviewExperimentPlannerTest {

    @Test
    fun waterTemperaturePlanKeepsRecordObjectiveBaseline() {
        val record = record(
            id = 42L,
            waterTemp = 92.0,
            grindSetting = 20.0,
            brewWater = 240.0,
        )

        val plan = buildReviewExperimentDraftPlan(record, NumericParameter.WATER_TEMP)

        assertThat(plan).isNotNull()
        val draft = checkNotNull(plan).draft
        assertThat(draft.baseRecordId).isEqualTo(42L)
        assertThat(draft.baseline.beanProfileId).isEqualTo(record.beanProfileId)
        assertThat(draft.baseline.grindSetting).isEqualTo(20.0)
        assertThat(draft.variables.single().type).isEqualTo(ExperimentVariableType.WATER_TEMP)
        assertThat(draft.variables.single().levels.map { it.numericValue })
            .containsExactly(90.0, 92.0, 94.0)
            .inOrder()
    }

    @Test
    fun brewRatioPlanUsesBrewWaterVariableWithoutChangingDose() {
        val record = record(
            id = 43L,
            waterTemp = 92.0,
            grindSetting = 20.0,
            brewWater = 240.0,
            dose = 15.0,
            ratio = 16.0,
        )

        val plan = buildReviewExperimentDraftPlan(record, NumericParameter.BREW_RATIO)

        assertThat(plan).isNotNull()
        val draft = checkNotNull(plan).draft
        assertThat(draft.baseline.coffeeDoseG).isEqualTo(15.0)
        assertThat(draft.variables.single().type).isEqualTo(ExperimentVariableType.BREW_WATER)
        assertThat(draft.variables.single().levels.map { it.numericValue })
            .containsExactly(225.0, 240.0, 255.0)
            .inOrder()
    }

    private fun record(
        id: Long,
        waterTemp: Double?,
        grindSetting: Double?,
        brewWater: Double?,
        dose: Double = 15.0,
        ratio: Double? = null,
    ): CoffeeRecord = CoffeeRecord(
        id = id,
        status = RecordStatus.COMPLETED,
        brewMethod = BrewMethod.POUR_OVER,
        beanProfileId = 10L,
        beanNameSnapshot = "Kenya AA",
        grinderProfileId = 20L,
        grinderNameSnapshot = "C40",
        grindSetting = grindSetting,
        coffeeDoseG = dose,
        brewWaterMl = brewWater,
        waterTempC = waterTemp,
        totalWaterMl = brewWater,
        brewRatio = ratio,
        brewedAt = 1_000L,
        subjectiveEvaluation = SubjectiveEvaluation(recordId = id, overall = 4),
    )
}
