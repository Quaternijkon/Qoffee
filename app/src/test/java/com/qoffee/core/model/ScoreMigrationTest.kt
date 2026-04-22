package com.qoffee.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScoreMigrationTest {

    @Test
    fun legacyTenPointScoreToFivePoint_mapsAllExpectedValues() {
        val mapped = (1..10).associateWith(::legacyTenPointScoreToFivePoint)

        assertThat(mapped[1]).isEqualTo(1)
        assertThat(mapped[2]).isEqualTo(1)
        assertThat(mapped[3]).isEqualTo(2)
        assertThat(mapped[4]).isEqualTo(2)
        assertThat(mapped[5]).isEqualTo(3)
        assertThat(mapped[6]).isEqualTo(3)
        assertThat(mapped[7]).isEqualTo(4)
        assertThat(mapped[8]).isEqualTo(4)
        assertThat(mapped[9]).isEqualTo(5)
        assertThat(mapped[10]).isEqualTo(5)
    }
}
