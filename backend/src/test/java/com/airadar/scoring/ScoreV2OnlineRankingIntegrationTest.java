package com.airadar.scoring;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterSummaryVO;
import com.airadar.common.api.PageResponse;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.airadar.scoring.strategy.CrossSourceScoreV2Strategy;
import com.airadar.scoring.strategy.ScoringStrategyProperties;
import com.airadar.scoring.strategy.controller.ScoringStrategyController;
import com.airadar.scoring.strategy.vo.ScoringStrategyStatusVO;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 18B integration coverage: V2 online ranking adoption.
 *
 * <p>Each test method runs in its own transaction so destructive cleanup is
 * unnecessary. The shared {@link ScoringStrategyProperties} bean is mutated
 * per-test and reset in {@link AfterEach} so configuration cannot leak.
 */
@Testcontainers
@SpringBootTest(properties = {
        "ai-radar.operations.scheduled-crawl.enabled=false",
        "ai-radar.operations.scheduled-daily-report.enabled=false"
})
@Transactional
class ScoreV2OnlineRankingIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ScoringStrategyProperties properties;
    @Autowired
    private ScoringStrategyController strategyController;
    @Autowired
    private HotClusterQueryService queryService;
    @Autowired
    private HotClusterMapper clusterMapper;
    @Autowired
    private HotItemMapper hotItemMapper;
    @Autowired
    private HotScoreMapper hotScoreMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        properties.setOnlineVersion(ScoringStrategyProperties.DEFAULT_ONLINE_VERSION);
        properties.validate();
    }

    @AfterEach
    void cleanup() {
        properties.setOnlineVersion(ScoringStrategyProperties.DEFAULT_ONLINE_VERSION);
        properties.validate();
    }

    @Test
    void statusEndpointReturnsV1Defaults() {
        ScoringStrategyStatusVO status = strategyController.status().data();

        assertThat(status.onlineVersion()).isEqualTo("hn-score-v1");
        assertThat(status.shadowVersion()).isEqualTo(CrossSourceScoreV2Strategy.VERSION);
        assertThat(status.v2Online()).isFalse();
        assertThat(status.rolloutStage()).isEqualTo("V1_ONLINE_V2_SHADOW");
    }

    @Test
    void statusEndpointReturnsV2WhenConfigured() {
        properties.setOnlineVersion(CrossSourceScoreV2Strategy.VERSION);
        properties.validate();

        ScoringStrategyStatusVO status = strategyController.status().data();

        assertThat(status.onlineVersion()).isEqualTo(CrossSourceScoreV2Strategy.VERSION);
        assertThat(status.v2Online()).isTrue();
        assertThat(status.rolloutStage()).isEqualTo("V2_ONLINE_V1_SHADOW");
    }

    @Test
    void v1SortingReturnsV1ScoresByDefault() {
        long clusterA = persistClusterWithScore("Cluster A", 70.0, "hn-score-v1", 50.0, CrossSourceScoreV2Strategy.VERSION);
        long clusterB = persistClusterWithScore("Cluster B", 30.0, "hn-score-v1", 90.0, CrossSourceScoreV2Strategy.VERSION);

        PageResponse<HotClusterSummaryVO> page = queryService.list(
                1, 10, HotClusterSort.SCORE_DESC, null, null, null);

        assertThat(page.items()).hasSize(2);
        // V1 score A=70 > B=30, so A comes first
        assertThat(page.items().get(0).id()).isEqualTo(clusterA);
        assertThat(page.items().get(0).score().total()).isEqualByComparingTo(new BigDecimal("70.0"));
        assertThat(page.items().get(1).id()).isEqualTo(clusterB);
    }

    @Test
    void scoringVersionOverrideReturnsV2Scores() {
        long clusterA = persistClusterWithScore("Cluster A", 70.0, "hn-score-v1", 50.0, CrossSourceScoreV2Strategy.VERSION);
        long clusterB = persistClusterWithScore("Cluster B", 30.0, "hn-score-v1", 90.0, CrossSourceScoreV2Strategy.VERSION);

        // Override the configured V1 with V2 explicitly via the list endpoint.
        PageResponse<HotClusterSummaryVO> page = queryService.list(
                1, 10, HotClusterSort.SCORE_DESC, null, null, null, CrossSourceScoreV2Strategy.VERSION);

        assertThat(page.items()).hasSize(2);
        // V2 score B=90 > A=50, so B comes first — V1 order is reversed.
        assertThat(page.items().get(0).id()).isEqualTo(clusterB);
        assertThat(page.items().get(0).score().total()).isEqualByComparingTo(new BigDecimal("90.0"));
        assertThat(page.items().get(1).id()).isEqualTo(clusterA);
    }

    @Test
    void configuredV2OnlineSortsByV2WithoutExplicitParam() {
        properties.setOnlineVersion(CrossSourceScoreV2Strategy.VERSION);
        properties.validate();

        long clusterA = persistClusterWithScore("Cluster A", 70.0, "hn-score-v1", 50.0, CrossSourceScoreV2Strategy.VERSION);
        long clusterB = persistClusterWithScore("Cluster B", 30.0, "hn-score-v1", 90.0, CrossSourceScoreV2Strategy.VERSION);

        PageResponse<HotClusterSummaryVO> page = queryService.list(
                1, 10, HotClusterSort.SCORE_DESC, null, null, null);

        assertThat(page.items()).hasSize(2);
        // V2 score B=90 > A=50, so B comes first without any explicit override.
        assertThat(page.items().get(0).id()).isEqualTo(clusterB);
    }

    @Test
    void detailEndpointFallsBackToV1WhenV2RequestedButMissing() {
        long cluster = persistClusterWithScore("Only V1", 70.0, "hn-score-v1", null, null);

        // Default V1-online config + V2 override in detail → fallback to V1 row.
        var detail = queryService.get(cluster, CrossSourceScoreV2Strategy.VERSION);

        assertThat(detail.score()).isNotNull();
        assertThat(detail.score().version()).isEqualTo("hn-score-v1");
        assertThat(detail.score().total()).isEqualByComparingTo(new BigDecimal("70.0"));
    }

    /**
     * Persists a cluster with one hot item and 0..2 hot_score rows.
     */
    private long persistClusterWithScore(
            String title,
            double v1Score,
            String v1Version,
            Double v2Score,
            String v2Version
    ) {
        Instant now = Instant.now();

        HotClusterEntity cluster = new HotClusterEntity();
        cluster.setTitle(title);
        cluster.setSummary(title + " summary");
        cluster.setStatus("ACTIVE");
        cluster.setFirstSeenAt(now);
        cluster.setLastSeenAt(now);
        cluster.setCreatedAt(now);
        cluster.setUpdatedAt(now);
        clusterMapper.insert(cluster);

        HotItemEntity item = new HotItemEntity();
        item.setSourceType(SourceType.HACKER_NEWS);
        item.setExternalId("ext-" + cluster.getId());
        item.setTitle(title);
        item.setSourceUrl("https://example.com/" + cluster.getId());
        item.setAuthor("tester");
        item.setPublishedAt(now);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", 100);
        metrics.put("commentsCount", 10);
        item.setMetrics(metrics);
        hotItemMapper.insert(item);

        // Link via hot_cluster_item with V1 rule version so list / detail queries see it.
        jdbcTemplate.update(
                "INSERT INTO hot_cluster_item (hot_cluster_id, hot_item_id, is_primary, match_method, rule_version, assigned_at, created_at, updated_at) "
                        + "VALUES (?, ?, TRUE, 'CANONICAL_URL', 'hn-rule-v1', ?, ?, ?)",
                cluster.getId(), item.getId(), now, now, now);

        persistScore(cluster.getId(), v1Score, v1Version, now);
        if (v2Score != null && v2Version != null) {
            persistScore(cluster.getId(), v2Score, v2Version, now);
        }

        // Source config existence check is unnecessary for this test; we only
        // need hot_score + hot_cluster_item rows to drive ranking.
        return cluster.getId();
    }

    private void persistScore(long clusterId, double score, String version, Instant now) {
        HotScoreEntity entity = new HotScoreEntity();
        entity.setHotClusterId(clusterId);
        entity.setTotalScore(BigDecimal.valueOf(score));
        entity.setScoringVersion(version);
        entity.setCalculatedAt(now);
        entity.setCreatedAt(now);
        ObjectNode components = objectMapper.createObjectNode();
        components.put("version", version);
        components.put("total", score);
        entity.setScoreComponents(components);
        hotScoreMapper.insert(entity);
    }
}
