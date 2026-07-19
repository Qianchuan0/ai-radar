package com.airadar.evaluation.realtimedata;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.strategy.ClusterMatchDecisionEntity;
import com.airadar.cluster.strategy.ClusterMatchDecisionMapper;
import com.airadar.cluster.strategy.EventRuleClusterStrategy;
import com.airadar.cluster.service.RuleBasedClusterService;
import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.evaluation.entity.EvaluationCaseEntity;
import com.airadar.evaluation.entity.EvaluationDatasetEntity;
import com.airadar.evaluation.mapper.EvaluationCaseMapper;
import com.airadar.evaluation.mapper.EvaluationDatasetMapper;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.raw.mapper.RawItemMapper;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.mapper.HotScoreMapper;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.mapper.SourceConfigMapper;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the Phase 17A real-data evaluation closed
 * loop on real PostgreSQL via Testcontainers.
 *
 * <p>Sets up a small but realistic dataset (4 hot_items in 2 hot_clusters,
 * 4 cluster_pair cases, 3 ranking_relevance cases, V1 + V2 scores and V2
 * match decisions), then runs the cluster/ranking evaluators for both V1 and
 * V2 and asserts the reports land with the expected fields.
 *
 * <p>Requires a Docker daemon. Run via
 * {@code mvn -Dtest=RealDataEvaluationIntegrationTest test}.
 */
@Testcontainers
@SpringBootTest(properties = {
        "ai-radar.operations.scheduled-crawl.enabled=false",
        "ai-radar.operations.scheduled-daily-report.enabled=false"
})
class RealDataEvaluationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @TempDir
    Path tempDir;

    @Autowired private SourceConfigMapper sourceConfigMapper;
    @Autowired private CrawlTaskMapper crawlTaskMapper;
    @Autowired private RawItemMapper rawItemMapper;
    @Autowired private HotItemMapper hotItemMapper;
    @Autowired private HotClusterMapper hotClusterMapper;
    @Autowired private HotClusterItemMapper hotClusterItemMapper;
    @Autowired private HotScoreMapper hotScoreMapper;
    @Autowired private ClusterMatchDecisionMapper decisionMapper;
    @Autowired private EvaluationDatasetMapper datasetMapper;
    @Autowired private EvaluationCaseMapper caseMapper;
    @Autowired private RealDataClusterEvaluationService clusterEvalService;
    @Autowired private RankingEvaluationService rankingEvalService;
    @Autowired private EvaluationReportWriter reportWriter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Instant evaluatedAt;
    private long datasetId;

    @BeforeEach
    void setUp() throws Exception {
        evaluatedAt = Instant.parse("2026-07-19T10:00:00Z");
        cleanEvaluationFixtureTables();
        long crawlTaskId = seedCrawlPipeline();
        long[] hotItemIds = seedHotItems(crawlTaskId);
        long clusterA = seedCluster("Cluster A");
        long clusterB = seedCluster("Cluster B");
        seedMembership(hotItemIds[0], clusterA, RuleBasedClusterService.RULE_VERSION);
        seedMembership(hotItemIds[1], clusterA, RuleBasedClusterService.RULE_VERSION);
        seedMembership(hotItemIds[2], clusterB, RuleBasedClusterService.RULE_VERSION);
        seedMembership(hotItemIds[3], clusterB, RuleBasedClusterService.RULE_VERSION);
        // V2 mirrors V1 membership via ACCEPTED match decisions
        seedAcceptedDecision(hotItemIds[0], clusterA);
        seedAcceptedDecision(hotItemIds[1], clusterA);
        seedAcceptedDecision(hotItemIds[2], clusterB);
        seedAcceptedDecision(hotItemIds[3], clusterB);
        // Scores for ranking eval
        seedScore(clusterA, "hn-score-v1", "95");
        seedScore(clusterB, "hn-score-v1", "70");
        seedScore(clusterA, "cross-source-score-v2", "92");
        seedScore(clusterB, "cross-source-score-v2", "90");
        this.datasetId = seedDataset();
        // 4 cluster-pair cases: 2 must-merge, 2 must-not-merge, all satisfied by V1
        seedPairCase("pair-1-2", hotItemIds[0], hotItemIds[1], "MUST_MERGE", "MODEL_RELEASE");
        seedPairCase("pair-3-4", hotItemIds[2], hotItemIds[3], "MUST_MERGE", "OPEN_SOURCE_LAUNCH");
        seedPairCase("pair-1-3", hotItemIds[0], hotItemIds[2], "MUST_NOT_MERGE", "SAME_NAME_DIFFERENT_EVENT");
        seedPairCase("pair-2-4", hotItemIds[1], hotItemIds[3], "MUST_NOT_MERGE", "SAME_COMPANY_DIFFERENT_EVENT");
        // 2 ranking cases in window 2026-07-18
        seedRankingCase(clusterA, "HIGHLY_RELEVANT", true);
        seedRankingCase(clusterB, "RELEVANT", false);
    }

    @Test
    void runsFullV1V2ClusterEvaluation() {
        ClusterPairEvaluationReport v1 = clusterEvalService.evaluate(
                datasetId, RuleBasedClusterService.RULE_VERSION, evaluatedAt);
        ClusterPairEvaluationReport v2 = clusterEvalService.evaluate(
                datasetId, EventRuleClusterStrategy.RULE_VERSION, evaluatedAt);

        assertThat(v1.getTotalPairs()).isEqualTo(4);
        assertThat(v1.getTruePositives()).isEqualTo(2);
        assertThat(v1.getTrueNegatives()).isEqualTo(2);
        assertThat(v1.getFalsePositives()).isZero();
        assertThat(v1.getFalseNegatives()).isZero();
        assertThat(v1.getPairwisePrecision()).isEqualTo(1.0);
        assertThat(v1.getPairwiseRecall()).isEqualTo(1.0);

        // V2 mirrors V1 by construction in this fixture.
        assertThat(v2.getStrategyVersion()).isEqualTo(EventRuleClusterStrategy.RULE_VERSION);
        assertThat(v2.getTruePositives()).isEqualTo(2);
        assertThat(v2.getTrueNegatives()).isEqualTo(2);
        assertThat(v2.getPairwisePrecision()).isEqualTo(1.0);
        assertThat(v2.getPairwiseRecall()).isEqualTo(1.0);

        assertThat(v1.getSlicesByCategory()).containsKeys("MODEL_RELEASE", "OPEN_SOURCE_LAUNCH",
                "SAME_NAME_DIFFERENT_EVENT", "SAME_COMPANY_DIFFERENT_EVENT");
    }

    @Test
    void runsFullV1V2RankingEvaluation() {
        RankingEvaluationReport v1 = rankingEvalService.evaluate(
                datasetId, RankingEvaluationService.V1_SCORING_VERSION, 10, evaluatedAt);
        RankingEvaluationReport v2 = rankingEvalService.evaluate(
                datasetId, RankingEvaluationService.V2_SCORING_VERSION, 10, evaluatedAt);

        assertThat(v1.getLabeledClusterCount()).isEqualTo(2);
        assertThat(v1.getMissingScoreCount()).isZero();
        assertThat(v1.getMajorEventTotal()).isEqualTo(1);
        // 2 labeled clusters, top-10 includes both; major event (clusterA) is in top-N -> 0 missed
        assertThat(v1.getMajorEventMissed()).isZero();
        assertThat(v2.getRankingDiffVsV1TopN())
                .as("V1 ranks A(95) > B(70); V2 ranks A(92) > B(90); both top-2 = {A,B} -> diff 0")
                .isZero();
    }

    @Test
    void writesReportsToEndToEnd() {
        ClusterPairEvaluationReport v1Cluster = clusterEvalService.evaluate(
                datasetId, RuleBasedClusterService.RULE_VERSION, evaluatedAt);
        ClusterPairEvaluationReport v2Cluster = clusterEvalService.evaluate(
                datasetId, EventRuleClusterStrategy.RULE_VERSION, evaluatedAt);
        RankingEvaluationReport v1Ranking = rankingEvalService.evaluate(
                datasetId, RankingEvaluationService.V1_SCORING_VERSION, 10, evaluatedAt);
        RankingEvaluationReport v2Ranking = rankingEvalService.evaluate(
                datasetId, RankingEvaluationService.V2_SCORING_VERSION, 10, evaluatedAt);

        EvaluationReportWriter.WrittenReports written = reportWriter.write(
                v1Cluster, v2Cluster, v1Ranking, v2Ranking, tempDir);

        assertThat(written.clusterV1Json()).exists();
        assertThat(written.clusterV2Json()).exists();
        assertThat(written.rankingV1Json()).exists();
        assertThat(written.rankingV2Json()).exists();
        assertThat(written.summaryMarkdown()).exists();
    }

    // ----- seed helpers -----

    private void cleanEvaluationFixtureTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    evaluation_case_result,
                    evaluation_run,
                    evaluation_case,
                    evaluation_dataset,
                    hot_score,
                    cluster_match_decision,
                    hot_cluster_item,
                    hot_item_feature,
                    hot_item_signal_snapshot,
                    hot_cluster,
                    hot_item,
                    raw_item,
                    crawl_task,
                    source_config
                RESTART IDENTITY CASCADE
                """);
    }

    private long seedCrawlPipeline() {
        SourceConfigEntity source = new SourceConfigEntity();
        source.setSourceCode("phase17a-it");
        source.setSourceType(SourceType.HACKER_NEWS);
        source.setDisplayName("Phase 17A IT");
        source.setEnabled(false);
        source.setConfigPayload(objectMapper.createObjectNode().put("purpose", "phase17a-it"));
        source.setVersion(0);
        source.setCreatedAt(evaluatedAt);
        source.setUpdatedAt(evaluatedAt);
        sourceConfigMapper.insert(source);

        CrawlTaskEntity task = new CrawlTaskEntity();
        task.setSourceConfigId(source.getId());
        task.setTriggerType(CrawlTriggerType.MANUAL);
        task.setStatus(CrawlTaskStatus.SUCCEEDED);
        task.setIdempotencyKey("phase17a-it");
        task.setRequestedAt(evaluatedAt);
        task.setStartedAt(evaluatedAt);
        task.setFinishedAt(evaluatedAt);
        task.setFetchedCount(0);
        task.setPersistedCount(0);
        task.setMatchedCount(0);
        task.setFailedCount(0);
        task.setCreatedAt(evaluatedAt);
        task.setUpdatedAt(evaluatedAt);
        crawlTaskMapper.insert(task);
        return task.getId();
    }

    private long[] seedHotItems(long crawlTaskId) {
        long[] ids = new long[4];
        for (int i = 0; i < 4; i++) {
            RawItemEntity raw = new RawItemEntity();
            raw.setCrawlTaskId(crawlTaskId);
            raw.setSourceType(SourceType.HACKER_NEWS);
            raw.setExternalId("ext-" + i);
            raw.setSourceUrl("https://example.com/" + i);
            raw.setRawPayload(objectMapper.createObjectNode().put("externalId", "ext-" + i));
            raw.setPayloadHash("hash-" + i);
            raw.setPublishedAt(evaluatedAt);
            raw.setFetchedAt(evaluatedAt);
            raw.setCreatedAt(evaluatedAt);
            rawItemMapper.insert(raw);

            HotItemEntity hot = new HotItemEntity();
            hot.setLatestRawItemId(raw.getId());
            hot.setSourceType(SourceType.HACKER_NEWS);
            hot.setExternalId("ext-" + i);
            hot.setItemType("STORY");
            hot.setTitle("hot item " + i);
            hot.setSummary("summary " + i);
            hot.setSourceUrl("https://example.com/" + i);
            hot.setTags(objectMapper.createArrayNode());
            hot.setMetrics(objectMapper.createObjectNode());
            hot.setContentHash("hot-hash-" + i);
            hot.setPublishedAt(evaluatedAt);
            hot.setFirstSeenAt(evaluatedAt);
            hot.setLastSeenAt(evaluatedAt);
            hot.setCreatedAt(evaluatedAt);
            hot.setUpdatedAt(evaluatedAt);
            hotItemMapper.insert(hot);
            ids[i] = hot.getId();
        }
        return ids;
    }

    private long seedCluster(String title) {
        HotClusterEntity entity = new HotClusterEntity();
        entity.setTitle(title);
        entity.setSummary(title);
        entity.setStatus("ACTIVE");
        entity.setPrimaryItemId(null);
        entity.setFirstSeenAt(evaluatedAt);
        entity.setLastSeenAt(evaluatedAt);
        entity.setVersion(0);
        entity.setCreatedAt(evaluatedAt);
        entity.setUpdatedAt(evaluatedAt);
        hotClusterMapper.insert(entity);
        return entity.getId();
    }

    private void seedMembership(long hotItemId, long clusterId, String ruleVersion) {
        HotClusterItemEntity entity = new HotClusterItemEntity();
        entity.setHotClusterId(clusterId);
        entity.setHotItemId(hotItemId);
        entity.setMatchMethod("FIXTURE");
        entity.setMatchScore(BigDecimal.ONE);
        entity.setMatchReason(objectMapper.createObjectNode().put("reason", "phase17a-it"));
        entity.setRuleVersion(ruleVersion);
        entity.setIsPrimary(false);
        entity.setAssignedAt(evaluatedAt);
        hotClusterItemMapper.insert(entity);
    }

    private void seedAcceptedDecision(long hotItemId, long clusterId) {
        ClusterMatchDecisionEntity entity = new ClusterMatchDecisionEntity();
        entity.setHotItemId(hotItemId);
        entity.setCandidateClusterId(clusterId);
        entity.setDecision("ACCEPTED");
        entity.setMatchScore(BigDecimal.ONE);
        entity.setMatchMethod("FIXTURE_ACCEPTED");
        entity.setMatchReason(objectMapper.createObjectNode().put("reason", "phase17a-it"));
        entity.setRuleVersion(EventRuleClusterStrategy.RULE_VERSION);
        entity.setDecidedAt(evaluatedAt);
        decisionMapper.insert(entity);
    }

    private void seedScore(long clusterId, String version, String value) {
        HotScoreEntity entity = new HotScoreEntity();
        entity.setHotClusterId(clusterId);
        entity.setScoringVersion(version);
        entity.setTotalScore(new BigDecimal(value));
        entity.setScoreComponents(objectMapper.createObjectNode());
        entity.setCalculatedAt(evaluatedAt);
        entity.setCreatedAt(evaluatedAt);
        hotScoreMapper.insert(entity);
    }

    private long seedDataset() {
        EvaluationDatasetEntity dataset = new EvaluationDatasetEntity();
        dataset.setName("phase17a-it");
        dataset.setDescription("Phase 17A integration");
        dataset.setVersion(1);
        dataset.setEnabled(true);
        dataset.setCreatedAt(evaluatedAt);
        dataset.setUpdatedAt(evaluatedAt);
        datasetMapper.insert(dataset);
        return dataset.getId();
    }

    private void seedPairCase(String pairKey, long itemA, long itemB, String expectation, String category)
            throws Exception {
        EvaluationCaseEntity entity = new EvaluationCaseEntity();
        entity.setDatasetId(datasetId);
        entity.setCaseCode(pairKey);
        entity.setCaseType(EvaluationCaseType.CLUSTER_PAIR_EXPECTATION);
        JsonNode target = objectMapper.readTree(String.format(
                "{\"pairKey\":\"%s\",\"itemA\":{\"hotItemId\":%d,\"title\":\"a\"},"
                        + "\"itemB\":{\"hotItemId\":%d,\"title\":\"b\"}}",
                pairKey, itemA, itemB));
        JsonNode expected = objectMapper.readTree(String.format(
                "{\"expectation\":\"%s\",\"category\":\"%s\"}", expectation, category));
        entity.setTargetPayload(target);
        entity.setExpectedPayload(expected);
        entity.setEnabled(true);
        entity.setCreatedAt(evaluatedAt);
        entity.setUpdatedAt(evaluatedAt);
        caseMapper.insert(entity);
    }

    private void seedRankingCase(long clusterId, String relevance, boolean isMajorEvent)
            throws Exception {
        EvaluationCaseEntity entity = new EvaluationCaseEntity();
        entity.setDatasetId(datasetId);
        entity.setCaseCode("2026-07-18T00:00:00Z|" + clusterId);
        entity.setCaseType(EvaluationCaseType.RANKING_RELEVANCE_EXPECTATION);
        JsonNode target = objectMapper.readTree(String.format(
                "{\"windowStart\":\"2026-07-18T00:00:00Z\",\"windowEnd\":\"2026-07-18T23:59:59Z\","
                        + "\"clusterId\":%d}", clusterId));
        JsonNode expected = objectMapper.readTree(String.format(
                "{\"relevance\":\"%s\",\"isMajorEvent\":%s}", relevance, isMajorEvent));
        entity.setTargetPayload(target);
        entity.setExpectedPayload(expected);
        entity.setEnabled(true);
        entity.setCreatedAt(evaluatedAt);
        entity.setUpdatedAt(evaluatedAt);
        caseMapper.insert(entity);
    }
}
