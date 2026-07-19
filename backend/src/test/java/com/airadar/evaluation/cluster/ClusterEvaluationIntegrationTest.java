package com.airadar.evaluation.cluster;

import com.airadar.cluster.service.RuleBasedClusterService;
import com.airadar.cluster.strategy.CanonicalUrlClusterStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration test for the Phase 16A evaluation baseline.
 *
 * <p>Replays the frozen default fixture through the V1 strategy
 * ({@link CanonicalUrlClusterStrategy}, which wraps the unchanged
 * {@link RuleBasedClusterService}) and pins the resulting V1 baseline
 * metrics. This is the Phase 16A acceptance test: any change to V1 behavior
 * or to the fixture must be reflected here, and any regression in V1
 * precision will surface as a failing assertion.
 *
 * <p>Expected V1 baseline ({@code phase16a-baseline-v1}):
 * <ul>
 *   <li>recall = 0.20 — only the URL-shared must-merge group merges</li>
 *   <li>precision = 1.00 — no must-not-merge pair is violated</li>
 *   <li>falseMergeCount = 0, falseSplitCount = 4</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(properties = {
        "ai-radar.operations.scheduled-crawl.enabled=false",
        "ai-radar.operations.scheduled-daily-report.enabled=false"
})
class ClusterEvaluationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ClusterEvaluationService evaluationService;

    @Autowired
    private CanonicalUrlClusterStrategy v1Strategy;

    @Test
    void shouldReplayV1BaselineAgainstDefaultFixture() {
        Instant evaluatedAt = Instant.parse("2026-07-17T00:00:00Z");
        ClusterBaselineFixture fixture = ClusterBaselineFixtures.defaultFixture();

        ClusterEvaluationReport report = evaluationService.evaluate(v1Strategy, fixture, evaluatedAt);

        assertThat(report.getStrategyVersion()).isEqualTo(RuleBasedClusterService.RULE_VERSION);
        assertThat(report.getFixtureVersion()).isEqualTo(ClusterBaselineFixtures.DEFAULT_VERSION);
        assertThat(report.getEvaluatedAt()).isEqualTo(evaluatedAt);
        assertThat(report.getTotalItems()).isEqualTo(fixture.getItems().size());
        assertThat(report.getMustMergeTotal()).isEqualTo(5);
        assertThat(report.getMustNotMergeTotal()).isEqualTo(5);

        // V1 must NOT wrongly merge anything (precision-first baseline)
        assertThat(report.getMustNotMergeSatisfied()).isEqualTo(5);
        assertThat(report.getPrecision()).isEqualTo(1.0);
        assertThat(report.getFalseMergeCount()).isEqualTo(0);

        // V1 only merges the URL-shared group; the four cross-URL groups split
        assertThat(report.getMustMergeSatisfied()).isEqualTo(1);
        assertThat(report.getRecall()).isCloseTo(0.20, within(0.0001));
        assertThat(report.getFalseSplitCount()).isEqualTo(4);

        // The single satisfied must-merge group is the shared-URL GPT-5 pair
        ClusterEvaluationCaseResult satisfiedMerge = report.getPerCaseResults().stream()
                .filter(r -> r.getExpectationType() == ClusterEvaluationCaseResult.ExpectationType.MUST_MERGE)
                .filter(ClusterEvaluationCaseResult::isSatisfied)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected at least one satisfied must-merge case"));
        assertThat(satisfiedMerge.getKeys())
                .containsExactlyInAnyOrder("openai-gpt5-blog", "openai-gpt5-hn");
    }

    @Test
    void shouldBeReplayableAcrossMultipleRuns() {
        ClusterBaselineFixture fixture = ClusterBaselineFixtures.defaultFixture();

        ClusterEvaluationReport first = evaluationService.evaluate(
                v1Strategy, fixture, Instant.parse("2026-07-17T01:00:00Z"));
        ClusterEvaluationReport second = evaluationService.evaluate(
                v1Strategy, fixture, Instant.parse("2026-07-17T02:00:00Z"));

        // Truncate-and-replay contract: every run produces identical metrics
        // regardless of the previous run's state.
        assertThat(second.getTotalClusters()).isEqualTo(first.getTotalClusters());
        assertThat(second.getMustMergeSatisfied()).isEqualTo(first.getMustMergeSatisfied());
        assertThat(second.getMustNotMergeSatisfied()).isEqualTo(first.getMustNotMergeSatisfied());
        assertThat(second.getRecall()).isEqualTo(first.getRecall());
        assertThat(second.getPrecision()).isEqualTo(first.getPrecision());
    }
}
