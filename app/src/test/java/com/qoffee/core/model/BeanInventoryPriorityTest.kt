package com.qoffee.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BeanInventoryPriorityTest {

    @Test
    fun buildBeanInventoryPrioritiesMarksOnlyOneUrgentBeanByAgeAndRemainingStock() {
        val priorities = buildBeanInventoryPriorities(
            todayEpochDay = 100L,
            inventory = listOf(
                inventory(id = "old-small", roastDateEpochDay = 50L, remainingStockG = 30.0, remainingRatio = 0.2f),
                inventory(id = "old-large", roastDateEpochDay = 60L, remainingStockG = 200.0, remainingRatio = 0.8f),
                inventory(id = "fresh", roastDateEpochDay = 82L, remainingStockG = 240.0, remainingRatio = 0.9f),
            ),
        )

        assertThat(priorities["old-large"]).isEqualTo(BeanInventoryPriority.URGENT)
        assertThat(priorities["old-small"]).isEqualTo(BeanInventoryPriority.AGING)
        assertThat(priorities.values.count { it == BeanInventoryPriority.URGENT }).isEqualTo(1)
    }

    @Test
    fun buildBeanInventoryPrioritiesSeparatesRestingFreshUnknownAndEmptyBeans() {
        val priorities = buildBeanInventoryPriorities(
            todayEpochDay = 100L,
            inventory = listOf(
                inventory(id = "resting", roastDateEpochDay = 96L, remainingStockG = 100.0, remainingRatio = 0.8f),
                inventory(id = "fresh", roastDateEpochDay = 80L, remainingStockG = 100.0, remainingRatio = 0.8f),
                inventory(id = "unknown", roastDateEpochDay = null, remainingStockG = 100.0, remainingRatio = 0.8f),
                inventory(id = "empty", roastDateEpochDay = 80L, remainingStockG = 0.0, remainingRatio = 0f),
            ),
        )

        assertThat(priorities["resting"]).isEqualTo(BeanInventoryPriority.RESTING)
        assertThat(priorities["fresh"]).isEqualTo(BeanInventoryPriority.FRESH)
        assertThat(priorities["unknown"]).isEqualTo(BeanInventoryPriority.UNKNOWN)
        assertThat(priorities["empty"]).isEqualTo(BeanInventoryPriority.EMPTY)
    }

    private fun inventory(
        id: String,
        roastDateEpochDay: Long?,
        remainingStockG: Double,
        remainingRatio: Float,
    ) = BeanInventory(
        id = id,
        beanName = id,
        roastDateEpochDay = roastDateEpochDay,
        initialStockG = 250.0,
        remainingStockG = remainingStockG,
        remainingRatio = remainingRatio,
        remainingPercentage = (remainingRatio * 100).toInt(),
    )
}
