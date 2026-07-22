package com.airadar.scoring.calculator;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.model.ClusterTrend;
import com.airadar.cluster.model.ClusterTrendState;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.GrowthConfidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MomentumScoreCalculatorTest {

    private final MomentumScoreCalculator calculator = new MomentumScoreCalculator();

    @Test
    void compute_withHighConfidence_keepsFullScore() {
        ClusterTrend trend = trend(80.0, GrowthConfidence.HIGH);
        ScoringContext context = context(trend);

        ScoreComponent result = calculator.compute(context);

        assertThat(result.name()).isEqualTo("momentum");
        assertThat(result.weight()).isEqualTo(0.25);
        assertThat(result.score()).isEqualTo(80.0);
        assertThat(result.reasons()).anyMatch(r -> r.contains("confidence=HIGH"));
    }

    @Test
    void compute_withUnknownConfidence_attenuatesScore() {
        ClusterTrend trend = trend(80.0, GrowthConfidence.UNKNOWN);
        ScoringContext context = context(trend);

        ScoreComponent result = calculator.compute(context);

        // 80 * 0.3 = 24
        assertThat(result.score()).isEqualTo(24.0);
    }

    @Test
    void compute_withNullMomentum_returnsLowBaseline() {
        ClusterTrend trend = new ClusterTrend(
                1L, "24h", ClusterTrendState.UNKNOWN, null, GrowthConfidence.UNKNOWN,
                List.of(), Map.of(), null, null, List.of(), List.of(),
                Instant.parse("2026-07-17T12:00:00Z"));
        ScoringContext context = context(trend);

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(10.0);
    }

    @Test
    void compute_withoutTrend_returnsLowBaseline() {
        ScoringContext context = context(null);

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(10.0);
        assertThat(result.reasons()).contains("no_cluster_trend");
    }

    private ClusterTrend trend(double momentum, GrowthConfidence confidence) {
        return new ClusterTrend(
                1L, "24h", ClusterTrendState.STABLE, momentum, confidence,
                List.of(), Map.of(), 0.1, 0.0, List.of(1L), List.of(),
                Instant.parse("2026-07-17T12:00:00Z"));
    }

    private ScoringContext context(ClusterTrend trend) {
        return new ScoringContext(
                new HotClusterEntity(),
                List.of(),
                null,
                Map.of(),
                Map.of(),
                trend,
                Map.of(),
                Map.of(),
                Set.of(),
                Instant.parse("2026-07-17T12:00:00Z"),
                Instant.parse("2026-07-17T12:00:00Z")
        );
    }
}
