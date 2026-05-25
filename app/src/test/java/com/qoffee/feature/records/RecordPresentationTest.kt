package com.qoffee.feature.records

import com.google.common.truth.Truth.assertThat
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.BrewMethod
import com.qoffee.core.model.CoffeeRecord
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecipeTemplate
import com.qoffee.core.model.SubjectiveEvaluation
import org.junit.Test

class RecordPresentationTest {

    @Test
    fun findPreviousComparableRecordReturnsLatestOlderMatch() {
        val records = listOf(
            record(id = 1L, brewedAt = 1_000L, overall = 3, waterTemp = 90.0),
            record(id = 2L, brewedAt = 2_000L, overall = 4, waterTemp = 91.0),
            record(id = 3L, brewedAt = 3_000L, overall = 5, waterTemp = 92.0),
        )

        val previous = findPreviousComparableRecord(records, records.last())

        assertThat(previous?.id).isEqualTo(2L)
    }

    @Test
    fun buildComparisonSummaryMapTracksDeltaAgainstPreviousCup() {
        val records = listOf(
            record(id = 1L, brewedAt = 1_000L, overall = 3, waterTemp = 90.0),
            record(id = 2L, brewedAt = 2_000L, overall = 5, waterTemp = 92.0),
        )

        val comparisonMap = buildComparisonSummaryMap(records)

        assertThat(comparisonMap[2L]?.headline).contains("更高分")
        assertThat(comparisonMap[2L]?.details).contains("总分 +2")
        assertThat(comparisonMap[2L]?.details).contains("水温 +2°C")
    }

    @Test
    fun buildBeanHistorySummaryUsesCompletedScoredRecordsOnly() {
        val summary = buildBeanHistorySummary(
            records = listOf(
                record(id = 1L, brewedAt = 1_000L, overall = 3, waterTemp = 90.0),
                record(id = 2L, brewedAt = 2_000L, overall = 5, waterTemp = 92.0),
            ),
            beanId = 10L,
        )

        assertThat(summary).contains("同豆已记录 2 杯")
        assertThat(summary).contains("平均总分 4.0")
    }

    @Test
    fun buildBrewCoachRecommendationsPrioritizesActiveDraft() {
        val recommendations = buildBrewCoachRecommendations(
            records = listOf(record(id = 1L, brewedAt = 1_000L, overall = 4, waterTemp = 90.0)),
            activeDraft = CoffeeRecord(id = 9L, status = RecordStatus.DRAFT, beanNameSnapshot = "Draft Bean"),
            inventory = emptyList(),
            recipes = emptyList(),
        )

        assertThat(recommendations.first().title).contains("草稿")
        assertThat(recommendations.first().action).isEqualTo(BrewCoachAction.ResumeDraft(9L))
    }

    @Test
    fun buildWorkbenchHeroPrioritizesActiveDraft() {
        val hero = buildRecordWorkbenchHero(
            activeDraft = CoffeeRecord(id = 9L, status = RecordStatus.DRAFT, beanNameSnapshot = "Draft Bean"),
            inventory = emptyList(),
            recipes = emptyList(),
            recentRecords = emptyList(),
            scoredRecords = emptyList(),
        )

        assertThat(hero.title).contains("继续")
        assertThat(hero.primaryAction).isEqualTo(WorkbenchAction.ResumeDraft(9L))
    }

    @Test
    fun buildWorkbenchHeroStartsFirstCupFromInventoryWhenNoDraftExists() {
        val hero = buildRecordWorkbenchHero(
            activeDraft = null,
            inventory = listOf(
                BeanInventory(
                    beanId = 20L,
                    beanName = "Ethiopia Natural",
                    remainingStockG = 120.0,
                    remainingPercentage = 80,
                ),
            ),
            recipes = emptyList(),
            recentRecords = emptyList(),
            scoredRecords = emptyList(),
        )

        assertThat(hero.title).contains("第一杯")
        assertThat(hero.primaryAction).isEqualTo(WorkbenchAction.StartBean(20L))
    }

    @Test
    fun buildWorkbenchHeroSuggestsReviewWhenEnoughScoredRecordsExist() {
        val records = listOf(
            record(id = 1L, brewedAt = 1_000L, overall = 4, waterTemp = 90.0),
            record(id = 2L, brewedAt = 2_000L, overall = 5, waterTemp = 91.0),
            record(id = 3L, brewedAt = 3_000L, overall = 3, waterTemp = 92.0),
        )

        val hero = buildRecordWorkbenchHero(
            activeDraft = null,
            inventory = emptyList(),
            recipes = emptyList(),
            recentRecords = records,
            scoredRecords = records,
        )

        assertThat(hero.secondaryActions.map { it.action }).contains(WorkbenchAction.OpenAnalysis)
    }

    @Test
    fun buildBrewCoachRecommendationsStartsFirstCupFromInventory() {
        val recommendations = buildBrewCoachRecommendations(
            records = emptyList(),
            activeDraft = null,
            inventory = listOf(
                BeanInventory(
                    beanId = 20L,
                    beanName = "Ethiopia Natural",
                    remainingStockG = 120.0,
                    remainingPercentage = 80,
                ),
            ),
            recipes = emptyList(),
        )

        assertThat(recommendations.first().title).contains("第一杯")
        assertThat(recommendations.first().action).isEqualTo(BrewCoachAction.StartBean(20L))
    }

    @Test
    fun buildBrewCoachRecommendationsSuggestsRecipeForHighScoreCup() {
        val recommendations = buildBrewCoachRecommendations(
            records = listOf(record(id = 1L, brewedAt = 1_000L, overall = 5, waterTemp = 92.0)),
            activeDraft = null,
            inventory = emptyList(),
            recipes = emptyList(),
        )

        assertThat(recommendations.first().title).contains("高分杯")
        assertThat(recommendations.first().action).isEqualTo(BrewCoachAction.OpenDetail(1L))
    }

    @Test
    fun buildBrewCoachRecommendationsSuggestsDuplicatingLowScoreCup() {
        val recommendations = buildBrewCoachRecommendations(
            records = listOf(record(id = 1L, brewedAt = 1_000L, overall = 2, waterTemp = 92.0)),
            activeDraft = null,
            inventory = emptyList(),
            recipes = listOf(RecipeTemplate(id = 30L, name = "Daily V60")),
        )

        assertThat(recommendations.first().title).contains("低分杯")
        assertThat(recommendations.first().action).isEqualTo(BrewCoachAction.DuplicateRecord(1L))
    }

    @Test
    fun buildBrewCoachRecommendationsSuggestsAnalysisAfterEnoughScoredSamples() {
        val recommendations = buildBrewCoachRecommendations(
            records = listOf(
                record(id = 1L, brewedAt = 1_000L, overall = 3, waterTemp = 90.0),
                record(id = 2L, brewedAt = 2_000L, overall = 3, waterTemp = 91.0),
                record(id = 3L, brewedAt = 3_000L, overall = 3, waterTemp = 92.0),
            ),
            activeDraft = null,
            inventory = emptyList(),
            recipes = emptyList(),
        )

        assertThat(recommendations.map { it.action }).contains(BrewCoachAction.OpenAnalysis)
    }

    @Test
    fun buildBrewCoachRecommendationsLimitsToThreeDistinctRecommendations() {
        val recommendations = buildBrewCoachRecommendations(
            records = listOf(
                record(id = 1L, brewedAt = 1_000L, overall = 4, waterTemp = 90.0).copy(recipeTemplateId = 30L),
                record(id = 2L, brewedAt = 2_000L, overall = 4, waterTemp = 91.0).copy(recipeTemplateId = 30L),
                record(id = 3L, brewedAt = 3_000L, overall = 3, waterTemp = 92.0),
                record(id = 4L, brewedAt = 4_000L, overall = 2, waterTemp = 93.0),
            ),
            activeDraft = CoffeeRecord(id = 9L, status = RecordStatus.DRAFT, beanNameSnapshot = "Draft Bean"),
            inventory = listOf(
                BeanInventory(
                    beanId = 20L,
                    beanName = "Ethiopia Natural",
                    remainingStockG = 120.0,
                    remainingPercentage = 80,
                ),
            ),
            recipes = listOf(RecipeTemplate(id = 30L, name = "Daily V60")),
        )

        assertThat(recommendations).hasSize(3)
        assertThat(recommendations.map { it.title }.toSet()).hasSize(3)
        assertThat(recommendations.first().action).isEqualTo(BrewCoachAction.ResumeDraft(9L))
    }

    @Test
    fun brewCoachActionWritablePolicyAllowsReviewActionsInReadOnlyArchives() {
        assertThat(BrewCoachAction.OpenAnalysis.requiresWritableArchive()).isFalse()
        assertThat(BrewCoachAction.OpenDetail(1L).requiresWritableArchive()).isFalse()
        assertThat(BrewCoachAction.StartBlank.requiresWritableArchive()).isTrue()
        assertThat(BrewCoachAction.StartBean(1L).requiresWritableArchive()).isTrue()
        assertThat(BrewCoachAction.StartRecipe(1L).requiresWritableArchive()).isTrue()
        assertThat(BrewCoachAction.DuplicateRecord(1L).requiresWritableArchive()).isTrue()
        assertThat(BrewCoachAction.ResumeDraft(1L).requiresWritableArchive()).isTrue()
    }

    private fun record(
        id: Long,
        brewedAt: Long,
        overall: Int,
        waterTemp: Double,
    ) = CoffeeRecord(
        id = id,
        status = RecordStatus.COMPLETED,
        brewMethod = BrewMethod.POUR_OVER,
        beanProfileId = 10L,
        beanNameSnapshot = "Kenya AB",
        coffeeDoseG = 15.0,
        brewWaterMl = 240.0,
        waterTempC = waterTemp,
        brewedAt = brewedAt,
        subjectiveEvaluation = SubjectiveEvaluation(
            recordId = id,
            overall = overall,
        ),
    )
}
