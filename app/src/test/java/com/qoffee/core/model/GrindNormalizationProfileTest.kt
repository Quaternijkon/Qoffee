package com.qoffee.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GrindNormalizationProfileTest {

    @Test
    fun forwardDial_hitsAnchorValues() {
        val profile = GrindNormalizationProfile(
            espressoRange = GrindCalibrationRange(8.0, 10.0),
            mokaRange = GrindCalibrationRange(12.0, 14.0),
            pourOverRange = GrindCalibrationRange(18.0, 20.0),
            coldBrewPoint = 30.0,
        )

        assertThat(profile.normalize(9.0)).isWithin(0.0001).of(300.0 / 1400.0)
        assertThat(profile.normalize(13.0)).isWithin(0.0001).of(500.0 / 1400.0)
        assertThat(profile.normalize(19.0)).isWithin(0.0001).of(800.0 / 1400.0)
        assertThat(profile.normalize(30.0)).isWithin(0.0001).of(1.0)
    }

    @Test
    fun reverseDial_hitsAnchorValues() {
        val profile = GrindNormalizationProfile(
            espressoRange = GrindCalibrationRange(30.0, 32.0),
            mokaRange = GrindCalibrationRange(24.0, 26.0),
            pourOverRange = GrindCalibrationRange(16.0, 18.0),
            coldBrewPoint = 8.0,
        )

        assertThat(profile.normalize(31.0)).isWithin(0.0001).of(300.0 / 1400.0)
        assertThat(profile.normalize(25.0)).isWithin(0.0001).of(500.0 / 1400.0)
        assertThat(profile.normalize(17.0)).isWithin(0.0001).of(800.0 / 1400.0)
        assertThat(profile.normalize(8.0)).isWithin(0.0001).of(1.0)
    }

    @Test
    fun invalidAnchors_areRejected() {
        val profile = GrindNormalizationProfile(
            espressoRange = GrindCalibrationRange(8.0, 10.0),
            mokaRange = GrindCalibrationRange(7.0, 9.0),
            pourOverRange = GrindCalibrationRange(18.0, 20.0),
            coldBrewPoint = 30.0,
        )

        assertThat(profile.validationErrors()).isNotEmpty()
        assertThat(profile.normalize(9.0)).isNull()
    }

    @Test
    fun finerSideLinearExtrapolation_isClippedAtZero() {
        val profile = GrindNormalizationProfile(
            espressoRange = GrindCalibrationRange(8.0, 10.0),
            mokaRange = GrindCalibrationRange(12.0, 14.0),
            pourOverRange = GrindCalibrationRange(18.0, 20.0),
            coldBrewPoint = 30.0,
        )

        assertThat(profile.normalize(0.0)).isAtLeast(0.0)
        assertThat(profile.normalize(0.0)).isAtMost((300.0 / 1400.0))
    }

    @Test
    fun coldBrewSide_isClampedToOne() {
        val profile = GrindNormalizationProfile(
            espressoRange = GrindCalibrationRange(8.0, 10.0),
            mokaRange = GrindCalibrationRange(12.0, 14.0),
            pourOverRange = GrindCalibrationRange(18.0, 20.0),
            coldBrewPoint = 30.0,
        )

        assertThat(profile.normalize(60.0)).isWithin(0.0001).of(1.0)
    }
}
