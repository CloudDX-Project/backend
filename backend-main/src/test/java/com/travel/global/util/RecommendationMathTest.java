package com.travel.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationMathTest {
    @Test
    void calculatesDistanceDriveTimeAndScores() {
        assertThat(RecommendationMath.distanceKm(33.5104, 126.4914, 33.4591, 126.3096))
                .isBetween(17.0, 19.0);
        assertThat(RecommendationMath.estimatedDriveMinutes(30.0, 40.0)).isEqualTo(45);
        assertThat(RecommendationMath.clamp(1.5, 0.0, 1.0)).isEqualTo(1.0);
        assertThat(RecommendationMath.clamp(-1.0, 0.0, 1.0)).isEqualTo(0.0);
        assertThat(RecommendationMath.round(1.236, 2)).isEqualTo(1.24);
    }

    @Test
    void rejectsInvalidAverageSpeed() {
        assertThatThrownBy(() -> RecommendationMath.estimatedDriveMinutes(10.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
