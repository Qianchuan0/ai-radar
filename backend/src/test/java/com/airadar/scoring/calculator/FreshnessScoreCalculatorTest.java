package com.airadar.scoring.calculator;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessScoreCalculatorTest {

    private final FreshnessScoreCalculator calculator = new FreshnessScoreCalculator();

    @Test
    void compute_withFreshEvent_scoresHigh() {
        Instant eventTime = Instant.parse("2026-07-17T10:00:00Z");
        ScoringContext context = context(
                eventTime,
                Instant.parse("2026-07-17T12:00:00Z"));

        ScoreComponent result = calculator.compute(context);

        // age = 2h, score = 100 * (1 - 2/72) = 97.22
        assertThat(result.score()).isCloseTo(97.22, within(0.01));
    }

    @Test
    void compute_withStaleEvent_scoresZero() {
        Instant eventTime = Instant.parse("2026-07-10T12:00:00Z");
        ScoringContext context = context(
                eventTime,
                Instant.parse("2026-07-17T12:00:00Z"));

        ScoreComponent result = calculator.compute(context);

        // age > 72h -> 0
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void compute_withoutEventTime_returnsZero() {
        ScoringContext context = context(
                null,
                Instant.parse("2026-07-17T12:00:00Z"));

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reasons()).contains("no_credible_event_time");
    }

    private ScoringContext context(Instant earliestEventAt, Instant now) {
        HotClusterEntity cluster = new HotClusterEntity();
        cluster.setFirstSeenAt(now);
        return new ScoringContext(
                cluster,
                List.of(),
                null,
                Map.of(),
                Map.of(),
                null,
                Map.of(),
                Map.of(),
                Set.of(),
                earliestEventAt,
                now);
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
