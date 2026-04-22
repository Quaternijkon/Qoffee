package com.qoffee.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalysisFilterTest {

    @Test
    fun matchesUsesNormalizedBeanNameKey() {
        val filter = AnalysisFilter(
            timeRange = AnalysisTimeRange.ALL,
            beanNameKey = normalizedBeanNameKey("Kenya AA"),
        )
        val record = CoffeeRecord(
            id = 1L,
            archiveId = 10L,
            status = RecordStatus.COMPLETED,
            beanProfileId = 2L,
            beanNameSnapshot = "  kenya   aa ",
            brewedAt = 1_700_000_000_000L,
        )

        assertThat(filter.matches(record, nowMillis = 1_700_000_000_000L)).isTrue()
    }
}
