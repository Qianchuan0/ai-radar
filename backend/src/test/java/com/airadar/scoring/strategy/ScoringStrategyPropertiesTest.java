package com.airadar.scoring.strategy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringStrategyPropertiesTest {

    @Test
    void defaultsToV1Online() {
        ScoringStrategyProperties properties = new ScoringStrategyProperties();
        properties.validate();

        assertThat(properties.getOnlineVersion()).isEqualTo("hn-score-v1");
        assertThat(properties.effectiveOnlineVersion()).isEqualTo("hn-score-v1");
        assertThat(properties.isV2Online()).isFalse();
    }

    @Test
    void normalizesValueAndDetectsV2() {
        ScoringStrategyProperties properties = new ScoringStrategyProperties();
        properties.setOnlineVersion("  CROSS-SOURCE-SCORE-V2  ");
        properties.validate();

        assertThat(properties.getOnlineVersion()).isEqualTo("cross-source-score-v2");
        assertThat(properties.isV2Online()).isTrue();
    }

    @Test
    void blanksFallBackToDefault() {
        ScoringStrategyProperties properties = new ScoringStrategyProperties();
        properties.setOnlineVersion("   ");
        properties.validate();

        assertThat(properties.getOnlineVersion()).isEqualTo("hn-score-v1");
        assertThat(properties.effectiveOnlineVersion()).isEqualTo("hn-score-v1");
    }

    @Test
    void rejectsUnknownVersion() {
        ScoringStrategyProperties properties = new ScoringStrategyProperties();
        properties.setOnlineVersion("v3-beta");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ai-radar.scoring.online-version");
    }
}
