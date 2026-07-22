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

class DiscussionScoreCalculatorTest {

    private final DiscussionScoreCalculator calculator = new DiscussionScoreCalculator();

    @Test
    void compute_withoutCommunitySources_returnsZero() {
        ScoringContext context = context(Map.of(), Map.of());

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reasons()).anyMatch(r -> r.contains("no_community_source"));
    }

    @Test
    void compute_withCommunitySources_usesMaxAndAverage() {
        NormalizedSignal hnSignal = signal(SourceRole.COMMUNITY, 50.0);
        NormalizedSignal twitterSignal = signal(SourceRole.COMMUNITY, 30.0);
        ScoringContext context = context(
                Map.of(1L, hnSignal, 2L, twitterSignal),
                Map.of(SourceRole.COMMUNITY, List.of(hnSignal, twitterSignal)));

        ScoreComponent result = calculator.compute(context);

        // max=50, avg=40, score = 50 + 40*0.15 = 56
        assertThat(result.score()).isCloseTo(56.0, within(0.01));
    }

    private NormalizedSignal signal(SourceRole role, double discussion) {
        return new NormalizedSignal(
                SourceType.HACKER_NEWS, role,
                0.0, discussion, 0.0, 0.0, 0.0, null, null);
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
