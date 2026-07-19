package com.airadar.evaluation.cluster;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.strategy.ClusterAssignmentResult;
import com.airadar.cluster.strategy.ClusterAssignmentStrategy;
import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.raw.mapper.RawItemMapper;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.mapper.SourceConfigMapper;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replays a {@link ClusterBaselineFixture} through a
 * {@link ClusterAssignmentStrategy} and emits a
 * {@link ClusterEvaluationReport}.
 *
 * <p>The service is intentionally replayable: each call first truncates the
 * clustering-related tables (source_config, crawl_task, raw_item, hot_item,
 * hot_cluster, hot_cluster_item, hot_score) so the strategy always sees a
 * fresh schema regardless of previous test state. This matches the
 * {@code V1BaselineReplayIntegrationTest} pattern and is what makes the
 * Phase 16A baseline deterministic across runs and CI environments.
 *
 * <p><b>Side effect warning:</b> {@link #evaluate} truncates production-shaped
 * tables. It is intended for evaluation runs only and is not exposed through
 * any controller. Callers in production code paths must confirm they are not
 * pointing at a populated database.
 */
@Service
public class ClusterEvaluationService {

    private static final String TRUNCATE_SQL = """
            TRUNCATE TABLE
                hot_score,
                hot_cluster_item,
                hot_cluster,
                hot_item,
                raw_item,
                crawl_task,
                source_config
            RESTART IDENTITY CASCADE
            """;

    private final SourceConfigMapper sourceConfigMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final RawItemMapper rawItemMapper;
    private final HotItemMapper hotItemMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public ClusterEvaluationService(
            SourceConfigMapper sourceConfigMapper,
            CrawlTaskMapper crawlTaskMapper,
            RawItemMapper rawItemMapper,
            HotItemMapper hotItemMapper,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.sourceConfigMapper = sourceConfigMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.rawItemMapper = rawItemMapper;
        this.hotItemMapper = hotItemMapper;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Replays the fixture through the strategy and returns the evaluation
     * report.
     *
     * <p>This call truncates the schema before running. Callers must not
     * point this method at a database they care about.
     *
     * @param strategy    the strategy to evaluate
     * @param fixture     the frozen fixture to replay
     * @param evaluatedAt the timestamp to record on the report
     * @return the report describing must-merge / must-not-merge outcomes
     */
    public ClusterEvaluationReport evaluate(
            ClusterAssignmentStrategy strategy,
            ClusterBaselineFixture fixture,
            Instant evaluatedAt
    ) {
        jdbcTemplate.execute(TRUNCATE_SQL);

        Long crawlTaskId = createEvaluationCrawlTask(fixture.getVersion(), evaluatedAt);

        Map<String, Long> clusterIdByKey = new LinkedHashMap<>();
        for (FixtureInputItem input : fixture.getItems()) {
            HotItemEntity hotItem = persistHotItem(crawlTaskId, input, evaluatedAt);
            ClusterAssignmentResult result = strategy.assign(hotItem);
            HotClusterEntity cluster = result.getCluster();
            if (cluster == null || cluster.getId() == null) {
                throw new IllegalStateException(
                        "Strategy " + strategy.version()
                                + " returned null cluster for fixture item " + input.getKey());
            }
            clusterIdByKey.put(input.getKey(), cluster.getId());
        }

        return buildReport(strategy.version(), fixture, clusterIdByKey, evaluatedAt);
    }

    private Long createEvaluationCrawlTask(String fixtureVersion, Instant evaluatedAt) {
        String sourceCode = "phase16a-eval-" + fixtureVersion;
        SourceConfigEntity source = new SourceConfigEntity();
        source.setSourceCode(sourceCode);
        source.setSourceType(SourceType.HACKER_NEWS);
        source.setDisplayName("Phase 16A Evaluation Baseline");
        source.setEnabled(false);
        source.setConfigPayload(objectMapper.createObjectNode().put("purpose", "cluster-eval"));
        source.setVersion(0);
        source.setCreatedAt(evaluatedAt);
        source.setUpdatedAt(evaluatedAt);
        sourceConfigMapper.insert(source);

        CrawlTaskEntity task = new CrawlTaskEntity();
        task.setSourceConfigId(source.getId());
        task.setTriggerType(CrawlTriggerType.MANUAL);
        task.setStatus(CrawlTaskStatus.SUCCEEDED);
        task.setIdempotencyKey("phase16a-eval-" + fixtureVersion);
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

    private HotItemEntity persistHotItem(Long crawlTaskId, FixtureInputItem input, Instant evaluatedAt) {
        RawItemEntity rawItem = new RawItemEntity();
        rawItem.setCrawlTaskId(crawlTaskId);
        rawItem.setSourceType(input.getSourceType());
        rawItem.setExternalId(input.getExternalId());
        rawItem.setSourceUrl(input.getSourceUrl());
        rawItem.setRawPayload(rawPayload(input));
        rawItem.setPayloadHash(hashFor(input.getKey()));
        rawItem.setPublishedAt(input.getPublishedAt());
        rawItem.setFetchedAt(evaluatedAt);
        rawItem.setCreatedAt(evaluatedAt);
        rawItemMapper.insert(rawItem);

        HotItemEntity hotItem = new HotItemEntity();
        hotItem.setLatestRawItemId(rawItem.getId());
        hotItem.setSourceType(input.getSourceType());
        hotItem.setExternalId(input.getExternalId());
        hotItem.setItemType(input.getItemType());
        hotItem.setTitle(input.getTitle());
        hotItem.setSummary(input.getSummary() != null ? input.getSummary() : input.getTitle());
        hotItem.setSourceUrl(input.getSourceUrl());
        hotItem.setAuthor(input.getAuthor());
        hotItem.setTags(objectMapper.valueToTree(input.getTags()));
        hotItem.setMetrics(objectMapper.valueToTree(input.getMetrics()));
        hotItem.setContentHash(hashFor("hot-" + input.getKey()));
        hotItem.setPublishedAt(input.getPublishedAt());
        hotItem.setFirstSeenAt(evaluatedAt);
        hotItem.setLastSeenAt(evaluatedAt);
        hotItem.setCreatedAt(evaluatedAt);
        hotItem.setUpdatedAt(evaluatedAt);
        hotItemMapper.insert(hotItem);
        return hotItem;
    }

    private JsonNode rawPayload(FixtureInputItem input) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("externalId", input.getExternalId());
        payload.put("title", input.getTitle());
        payload.put("url", input.getSourceUrl());
        payload.set("metrics", objectMapper.valueToTree(input.getMetrics()));
        return payload;
    }

    private ClusterEvaluationReport buildReport(
            String strategyVersion,
            ClusterBaselineFixture fixture,
            Map<String, Long> clusterIdByKey,
            Instant evaluatedAt
    ) {
        Set<Long> distinctClusters = new HashSet<>(clusterIdByKey.values());
        List<ClusterEvaluationCaseResult> caseResults = new ArrayList<>();

        int mustMergeTotal = fixture.getMustMergeGroups().size();
        int mustMergeSatisfied = 0;
        for (Set<String> group : fixture.getMustMergeGroups()) {
            List<String> keys = new ArrayList<>(group);
            List<Long> clusterIds = keys.stream().map(clusterIdByKey::get).toList();
            long distinct = new HashSet<>(clusterIds).size();
            boolean satisfied = distinct == 1;
            if (satisfied) {
                mustMergeSatisfied++;
            }
            caseResults.add(new ClusterEvaluationCaseResult(
                    ClusterEvaluationCaseResult.ExpectationType.MUST_MERGE,
                    keys,
                    satisfied,
                    clusterIds,
                    satisfied
                            ? "merged into cluster " + clusterIds.get(0)
                            : "split across " + distinct + " clusters " + clusterIds
            ));
        }

        int mustNotMergeTotal = fixture.getMustNotMergePairs().size();
        int mustNotMergeSatisfied = 0;
        for (ClusterBaselineFixture.MustNotMergePair pair : fixture.getMustNotMergePairs()) {
            Long a = clusterIdByKey.get(pair.getKeyA());
            Long b = clusterIdByKey.get(pair.getKeyB());
            List<String> keys = List.of(pair.getKeyA(), pair.getKeyB());
            List<Long> clusterIds = List.of(a, b);
            boolean satisfied = a != null && b != null && !a.equals(b);
            if (satisfied) {
                mustNotMergeSatisfied++;
            }
            caseResults.add(new ClusterEvaluationCaseResult(
                    ClusterEvaluationCaseResult.ExpectationType.MUST_NOT_MERGE,
                    keys,
                    satisfied,
                    clusterIds,
                    satisfied
                            ? "kept apart (" + a + " vs " + b + ")"
                            : "wrongly merged into " + a
            ));
        }

        double precision = mustNotMergeTotal == 0
                ? Double.NaN
                : (double) mustNotMergeSatisfied / mustNotMergeTotal;
        double recall = mustMergeTotal == 0
                ? Double.NaN
                : (double) mustMergeSatisfied / mustMergeTotal;

        return new ClusterEvaluationReport(
                strategyVersion,
                fixture.getVersion(),
                evaluatedAt,
                clusterIdByKey.size(),
                distinctClusters.size(),
                mustMergeTotal,
                mustMergeSatisfied,
                mustNotMergeTotal,
                mustNotMergeSatisfied,
                precision,
                recall,
                mustNotMergeTotal - mustNotMergeSatisfied,
                mustMergeTotal - mustMergeSatisfied,
                caseResults,
                clusterIdByKey
        );
    }

    private static String hashFor(String value) {
        return Integer.toHexString(value.hashCode()).repeat(8).substring(0, 64);
    }
}
