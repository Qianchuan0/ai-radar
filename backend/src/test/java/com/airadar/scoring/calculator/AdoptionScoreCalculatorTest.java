package com.airadar.scoring.calculator;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.item.entity.HotItemEntity;
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

class AdoptionScoreCalculatorTest {

    private final AdoptionScoreCalculator calculator = new AdoptionScoreCalculator();

    @Test
    void compute_withNoAdoptionSources_returnsZero() {
        ScoringContext context = context(Map.of(), Map.of());

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.reasons()).anyMatch(r -> r.contains("no_adoption_source"));
    }

    @Test
    void compute_withSingleAdoptionSource_usesMax() {
        NormalizedSignal signal = signal(SourceRole.ADOPTION, 60.0);
        ScoringContext context = context(
                Map.of(1L, signal),
                Map.of(SourceRole.ADOPTION, List.of(signal)));

        ScoreComponent result = calculator.compute(context);

        // max=60, avg=60, score = 60 + 60*0.15 = 69
        assertThat(result.score()).isCloseTo(69.0, within(0.01));
    }

    @Test
    void compute_withMultipleAdoptionSources_boostsFromAverage() {
        NormalizedSignal githubSignal = signal(SourceRole.ADOPTION, 80.0);
        NormalizedSignal hfSignal = signal(SourceRole.ADOPTION, 40.0);
        ScoringContext context = context(
                Map.of(1L, githubSignal, 2L, hfSignal),
                Map.of(SourceRole.ADOPTION, List.of(githubSignal, hfSignal)));

        ScoreComponent result = calculator.compute(context);

        // max=80, avg=60, score = 80 + 60*0.15 = 89
        assertThat(result.score()).isCloseTo(89.0, within(0.01));
    }

    @Test
    void compute_clampsToHundred() {
        NormalizedSignal signal = signal(SourceRole.ADOPTION, 100.0);
        ScoringContext context = context(
                Map.of(1L, signal),
                Map.of(SourceRole.ADOPTION, List.of(signal)));

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(100.0);
    }

    private NormalizedSignal signal(SourceRole role, double adoption) {
        return new NormalizedSignal(
                SourceType.GITHUB, role,
                0.0, 0.0, adoption, 0.0, 0.0, null, null);
    }

    private ScoringContext context(
            Map<Long, NormalizedSignal> signals,
            Map<SourceRole, List<NormalizedSignal>> signalsByRole
    ) {
        HotItemEntity placeholder = new HotItemEntity();
        placeholder.setId(1L);
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
