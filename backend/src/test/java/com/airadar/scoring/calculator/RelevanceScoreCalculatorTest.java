package com.airadar.scoring.calculator;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RelevanceScoreCalculatorTest {

    private static final double NEUTRAL_BASELINE = 50.0;
    private final RelevanceScoreCalculator calculator = new RelevanceScoreCalculator();

    @Test
    void compute_withoutDiscoverySources_returnsNeutralBaseline() {
        ScoringContext context = context(Map.of(), Map.of());

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(NEUTRAL_BASELINE);
        assertThat(result.reasons()).anyMatch(r -> r.contains("no_discovery_source"));
    }

    @Test
    void compute_withDiscoverySources_aggregatesMaxAndAverage() {
        NormalizedSignal bingSignal = searchSignal(80.0, 1);
        NormalizedSignal duckSignal = searchSignal(60.0, 2);
        ScoringContext context = context(
                Map.of(1L, bingSignal, 2L, duckSignal),
                Map.of(SourceRole.DISCOVERY, List.of(bingSignal, duckSignal)));

        ScoreComponent result = calculator.compute(context);

        // max=80, avg=70, score = 80 + 70*0.10 = 87
        assertThat(result.score()).isCloseTo(87.0, within(0.01));
        assertThat(result.reasons()).anyMatch(r -> r.contains("best_rank=1"));
    }

    private NormalizedSignal searchSignal(double relevance, int rank) {
        return new NormalizedSignal(
                SourceType.BING_SEARCH, SourceRole.DISCOVERY,
                0.0, 0.0, 0.0, 0.0, relevance, rank, null);
    }

    private ScoringContext context(
            Map<Long, NormalizedSignal> signals,
            Map<SourceRole, List<NormalizedSignal>> signalsByRole
    ) {
        return new ScoringContext(
                new HotClusterEntity(),
                List.of(),
                null,
                signals,
                Map.of(),
                null,
                Map.of(),
                signalsByRole,
                Set.of(),
                Instant.parse("2026-07-17T12:00:00Z"),
                Instant.parse("2026-07-17T12:00:00Z"));
    }
}
