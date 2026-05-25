package com.qoffee.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiCoachRulesTest {

    @Test
    fun lowScoreLatestCupSuggestsDuplicateWithSourceCitation() {
        val suggestions = buildLocalAiCoachSuggestions(
            records = listOf(
                record(id = 1L, brewedAt = 1_000L, overall = 4),
                record(id = 2L, brewedAt = 2_000L, overall = 2),
            ),
        )

        val first = suggestions.first()
        assertThat(first.action).isEqualTo(AiCoachAction.DuplicateRecord(2L))
        assertThat(first.sourceRecords.map { it.recordId }).containsExactly(2L)
        assertThat(first.sourceRecords.first().score).isEqualTo(2)
    }

    @Test
    fun repeatedHighScoreCombinationCitesMultipleSamples() {
        val suggestions = buildLocalAiCoachSuggestions(
            records = listOf(
                record(id = 1L, brewedAt = 1_000L, overall = 4, recipeId = 8L),
                record(id = 2L, brewedAt = 2_000L, overall = 5, recipeId = 8L),
                record(id = 3L, brewedAt = 3_000L, overall = 3, recipeId = 9L),
            ),
        )

        val highScoreSuggestion = suggestions.first { it.id.startsWith("repeat-high") }
        assertThat(highScoreSuggestion.action).isEqualTo(AiCoachAction.OpenAnalysis)
        assertThat(highScoreSuggestion.sourceRecords.map { it.recordId }).containsExactly(2L, 1L).inOrder()
    }

    private fun record(
        id: Long,
        brewedAt: Long,
        overall: Int?,
        recipeId: Long? = null,
    ): CoffeeRecord = CoffeeRecord(
        id = id,
        status = RecordStatus.COMPLETED,
        brewMethod = BrewMethod.POUR_OVER,
        beanProfileId = 10L,
        beanNameSnapshot = "Kenya AA",
        recipeTemplateId = recipeId,
        grindSetting = 20.0,
        waterTempC = 92.0,
        brewedAt = brewedAt,
        subjectiveEvaluation = overall?.let {
            SubjectiveEvaluation(recordId = id, overall = it)
        },
    )
}
