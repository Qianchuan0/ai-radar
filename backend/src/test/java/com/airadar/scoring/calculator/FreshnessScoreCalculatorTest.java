package com.airadar.scoring.calculator;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessScoreCalculatorTest {

    private final FreshnessScoreCalculator calculator = new FreshnessScoreCalculator();

    @Test
    void compute_withFreshItem_scoresHigh() {
        HotItemEntity primary = new HotItemEntity();
        primary.setId(1L);
        primary.setPublishedAt(Instant.parse("2026-07-17T10:00:00Z"));

        ScoringContext context = new ScoringContext(
                new HotClusterEntity(), List.of(primary), primary, Map.of(), Map.of(),
                Instant.parse("2026-07-17T12:00:00Z"));

        ScoreComponent result = calculator.compute(context);

        // age = 2h, score = 100 * (1 - 2/72) = 97.22
        assertThat(result.score()).isCloseTo(97.22, within(0.01));
    }

    @Test
    void compute_withStaleItem_scoresZero() {
        HotItemEntity primary = new HotItemEntity();
        primary.setId(1L);
        primary.setPublishedAt(Instant.parse("2026-07-10T12:00:00Z"));

        ScoringContext context = new ScoringContext(
                new HotClusterEntity(), List.of(primary), primary, Map.of(), Map.of(),
                Instant.parse("2026-07-17T12:00:00Z"));

        ScoreComponent result = calculator.compute(context);

        // age > 72h -> 0
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void compute_withoutPublishedAt_returnsZero() {
        HotItemEntity primary = new HotItemEntity();
        primary.setId(1L);

        ScoringContext context = new ScoringContext(
                new HotClusterEntity(), List.of(primary), primary, Map.of(), Map.of(),
                Instant.parse("2026-07-17T12:00:00Z"));

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(0.0);
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
