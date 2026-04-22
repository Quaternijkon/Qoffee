package com.qoffee.data.repository

import com.qoffee.core.common.TimeProvider
import com.qoffee.core.model.AnalysisFilter
import com.qoffee.core.model.AnalysisTimeRange
import com.qoffee.core.model.BeanInventory
import com.qoffee.core.model.Experiment
import com.qoffee.core.model.ExperimentRun
import com.qoffee.core.model.PracticeBlock
import com.qoffee.core.model.RecordStatus
import com.qoffee.core.model.RecipeVersion
import com.qoffee.domain.repository.CatalogRepository
import com.qoffee.domain.repository.ExperimentRepository
import com.qoffee.domain.repository.RecordRepository
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

@Singleton
class ExperimentRepositoryFacade @Inject constructor(
    private val delegate: ExperimentRepositoryImpl,
    private val catalogRepository: CatalogRepository,
    private val recordRepository: RecordRepository,
    private val timeProvider: TimeProvider,
) : ExperimentRepository {

    override fun observePracticeBlocks(): Flow<List<PracticeBlock>> = delegate.observePracticeBlocks()

    override fun observeRecipeVersions(): Flow<List<RecipeVersion>> = delegate.observeRecipeVersions()

    override fun observeExperiments(): Flow<List<Experiment>> = delegate.observeExperiments()

    override fun observeExperimentRuns(): Flow<List<ExperimentRun>> = delegate.observeExperimentRuns()

    override fun observeBeanInventory(): Flow<List<BeanInventory>> =
        combine(
            catalogRepository.observeBeanProfiles(),
            recordRepository.observeRecords(AnalysisFilter(timeRange = AnalysisTimeRange.ALL)),
        ) { beans, records ->
            val completedUsage = records
                .filter { it.status == RecordStatus.COMPLETED }
                .mapNotNull { record ->
                    val beanId = record.beanProfileId ?: return@mapNotNull null
                    beanId to (record.coffeeDoseG ?: 0.0)
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
            val today = Instant.ofEpochMilli(timeProvider.nowMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            beans
                .filter { it.initialStockG != null }
                .sortedWith(
                    compareByDescending<com.qoffee.core.model.BeanProfile> { it.roastDateEpochDay ?: Long.MIN_VALUE }
                        .thenByDescending { it.createdAt },
                )
                .map { bean ->
                    val initialStock = bean.initialStockG ?: 0.0
                    val usedStock = completedUsage[bean.id].orEmpty().sum()
                    val remainingStock = (initialStock - usedStock).coerceAtLeast(0.0)
                    val ratio = if (initialStock > 0.0) {
                        (remainingStock / initialStock).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    BeanInventory(
                        beanId = bean.id,
                        beanName = bean.name,
                        roastDateEpochDay = bean.roastDateEpochDay,
                        roastAgeLabel = bean.roastDateEpochDay?.let { epochDay ->
                            formatRoastAge(LocalDate.ofEpochDay(epochDay), today)
                        } ?: "未记录烘焙日期",
                        initialStockG = initialStock,
                        usedStockG = usedStock,
                        remainingStockG = remainingStock,
                        remainingRatio = ratio,
                        remainingPercentage = (ratio * 100).roundToInt(),
                        id = "inventory-${bean.id}",
                        gramsRemaining = remainingStock.roundToInt(),
                    )
                }
        }

    private fun formatRoastAge(roastDate: LocalDate, today: LocalDate): String {
        if (roastDate.isAfter(today)) {
            return "0天"
        }
        val period = Period.between(roastDate, today)
        return when {
            period.years >= 1 -> "${period.years}年（${period.months}月）${period.days}天"
            period.months >= 1 -> "${period.months}月${period.days}天"
            else -> "${period.days}天"
        }
    }
}
