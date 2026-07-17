package com.airadar.scoring;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.scoring.calculator.AdoptionScoreCalculator;
import com.airadar.scoring.calculator.AuthorityScoreCalculator;
import com.airadar.scoring.calculator.DiscussionScoreCalculator;
import com.airadar.scoring.calculator.EvidenceDiversityCalculator;
import com.airadar.scoring.calculator.FreshnessScoreCalculator;
import com.airadar.scoring.calculator.MomentumScoreCalculator;
import com.airadar.scoring.calculator.RelevanceScoreCalculator;
import com.airadar.scoring.calculator.ScoreCalculator;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.GrowthMetrics;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V2 score separates cumulative scale from current growth.
 *
 * <p>Case A is an established GitHub project with massive adoption but no
 * recent momentum. Case B is a brand-new project with modest adoption but
 * explosive growth. The V2 momentum dimension must rank B above A, even though
 * A has far higher adoption.
 */
class V1V2ComparisonTest {

    private final List<ScoreCalculator> calculators = List.of(
            new MomentumScoreCalculator(),
            new AdoptionScoreCalculator(),
            new DiscussionScoreCalculator(),
            new RelevanceScoreCalculator(),
            new FreshnessScoreCalculator(),
            new EvidenceDiversityCalculator(new UrlCanonicalizer()),
            new AuthorityScoreCalculator()
    );

    @Test
    void caseA_establishedProject_outscoresOnAdoptionButNotMomentum() {
        ScoringContext caseA = buildCaseA();

        double adoptionA = score("adoption", caseA);
        double momentumA = score("momentum", caseA);

        // Established project: high adoption, low momentum
        assertThat(adoptionA).isGreaterThan(70.0);
        assertThat(momentumA).isLessThanOrEqualTo(30.0); // UNKNOWN confidence attenuates heavily
    }

    @Test
    void caseB_newProject_outscoresOnMomentum() {
        ScoringContext caseB = buildCaseB();

        double momentumB = score("momentum", caseB);
        double adoptionB = score("adoption", caseB);

        // New project: high momentum, modest adoption
        assertThat(momentumB).isGreaterThan(70.0);
        assertThat(adoptionB).isLessThan(adoption100k());
    }

    @Test
    void momentum_distinguishesCaseAFromCaseB() {
        ScoringContext caseA = buildCaseA();
        ScoringContext caseB = buildCaseB();

        double momentumA = score("momentum", caseA);
        double momentumB = score("momentum", caseB);

        assertThat(momentumB).isGreaterThan(momentumA);
        assertThat(momentumB - momentumA).isGreaterThan(40.0);
    }

    private double score(String name, ScoringContext context) {
        return calculators.stream()
                .filter(c -> c.name().equals(name))
                .map(c -> c.compute(context))
                .mapToDouble(ScoreComponent::score)
                .findFirst()
                .orElseThrow();
    }

    private double adoption100k() {
        // Case A's adoption score baseline (100k stars normalized)
        return score("adoption", buildCaseA());
    }

    private ScoringContext buildCaseA() {
        HotItemEntity primary = item(1L, Instant.parse("2026-07-15T00:00:00Z"));
        NormalizedSignal signal = new NormalizedSignal(
                SourceType.GITHUB, SourceRole.ADOPTION,
                80.0, 20.0, 90.0, 60.0, 50.0, null, null);
        GrowthMetrics growth = new GrowthMetrics(
                1L, "24h", 2.0, 1.0, 5.0, 0.0, null, 20.0, GrowthConfidence.UNKNOWN);
        return new ScoringContext(
                new HotClusterEntity(), List.of(primary), primary,
                Map.of(1L, signal), Map.of(1L, growth),
                Instant.parse("2026-07-17T12:00:00Z"));
    }

    private ScoringContext buildCaseB() {
        HotItemEntity primary = item(2L, Instant.parse("2026-07-17T06:00:00Z"));
        NormalizedSignal signal = new NormalizedSignal(
                SourceType.GITHUB, SourceRole.ADOPTION,
                30.0, 40.0, 35.0, 60.0, 50.0, null, null);
        GrowthMetrics growth = new GrowthMetrics(
                2L, "24h", 40.0, 30.0, 50.0, 0.0, null, 90.0, GrowthConfidence.HIGH);
        return new ScoringContext(
                new HotClusterEntity(), List.of(primary), primary,
                Map.of(2L, signal), Map.of(2L, growth),
                Instant.parse("2026-07-17T12:00:00Z"));
    }

    private HotItemEntity item(long id, Instant publishedAt) {
        HotItemEntity item = new HotItemEntity();
        item.setId(id);
        item.setPublishedAt(publishedAt);
        return item;
    }
}
