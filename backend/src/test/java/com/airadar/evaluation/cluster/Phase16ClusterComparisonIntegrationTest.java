package com.airadar.evaluation.cluster;

import com.airadar.cluster.strategy.EventRuleClusterStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 16 V2 vs V1 comparison on the frozen Phase 16A baseline fixture.
 *
 * <p>Both strategies replay the same {@link ClusterBaselineFixtures#defaultFixture()}
 * through {@link ClusterEvaluationService}. V1 numbers are pinned in
 * {@link ClusterEvaluationIntegrationTest}; this test pins V2 numbers and
 * asserts V2 strictly improves recall over V1 without sacrificing
 * precision.
 *
 * <p>Expected outcomes on the default fixture (Phase 16 V1 of the V2 strategy):
 * <ul>
 *   <li>V2 recall {@code >= 0.80} — at least 4 of the 5 must-merge groups
 *       land in a single cluster (V1 only merges 1)</li>
 *   <li>V2 precision {@code >= 0.80} — at most 1 must-not-merge pair is
 *       violated (V1 violates 0; we allow 1 to keep the test robust against
 *       minor calibration changes)</li>
 *   <li>V2 strictly beats V1 on recall</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(properties = {
        "ai-radar.operations.scheduled-crawl.enabled=false",
        "ai-radar.operations.scheduled-daily-report.enabled=false"
})
class Phase16ClusterComparisonIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ClusterEvaluationService evaluationService;

    @Autowired
    private EventRuleClusterStrategy v2Strategy;

    @Autowired
    private com.airadar.cluster.strategy.CanonicalUrlClusterStrategy v1Strategy;

    @Test
    void v2ShouldBeatV1OnRecallWithoutLosingPrecision() {
        Instant evaluatedAt = Instant.parse("2026-07-17T00:00:00Z");
        ClusterBaselineFixture fixture = ClusterBaselineFixtures.defaultFixture();

        ClusterEvaluationReport v1Report = evaluationService.evaluate(v1Strategy, fixture, evaluatedAt);
        ClusterEvaluationReport v2Report = evaluationService.evaluate(v2Strategy, fixture, evaluatedAt);

        // V2 must strictly improve recall over V1.
        assertThat(v2Report.getRecall())
                .as("V2 recall must strictly beat V1 recall")
                .isGreaterThan(v1Report.getRecall());

        // V2 must keep precision high; we allow a single false merge to
        // absorb calibration noise but never more.
        assertThat(v2Report.getFalseMergeCount())
                .as("V2 must not introduce more than one false merge")
                .isLessThanOrEqualTo(1);
        assertThat(v2Report.getPrecision())
                .as("V2 precision must stay >= 0.80")
                .isGreaterThanOrEqualTo(0.80);

        // V2 must merge at least the 4 cross-URL must-merge groups V1 misses.
        assertThat(v2Report.getMustMergeSatisfied())
                .as("V2 must satisfy at least 4 of the 5 must-merge groups")
                .isGreaterThanOrEqualTo(4);
    }
}
