package com.airadar.baseline;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.service.RuleBasedClusterService;
import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.raw.mapper.RawItemMapper;
import com.airadar.scoring.entity.HotScoreEntity;
import com.airadar.scoring.service.RuleBasedScoringService;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.mapper.SourceConfigMapper;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
    "ai-radar.operations.scheduled-crawl.enabled=false",
    "ai-radar.operations.scheduled-daily-report.enabled=false"
})
class V1BaselineReplayIntegrationTest {

    private static final Instant FIXED_PUBLISHED_AT = Instant.parse("2099-01-01T00:00:00Z");
    private static final Instant FIXED_SEEN_AT = Instant.parse("2026-07-14T00:00:00Z");
    private static final String SHARED_SOURCE_URL = "https://example.com/ai-agent-framework";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SourceConfigMapper sourceConfigMapper;

    @Autowired
    private CrawlTaskMapper crawlTaskMapper;

    @Autowired
    private RawItemMapper rawItemMapper;

    @Autowired
    private HotItemMapper hotItemMapper;

    @Autowired
    private RuleBasedClusterService clusterService;

    @Autowired
    private RuleBasedScoringService scoringService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
            TRUNCATE TABLE
                crawl_task_error,
                hot_score,
                hot_cluster_item,
                hot_cluster,
                hot_item,
                raw_item,
                crawl_task,
                source_config
            RESTART IDENTITY CASCADE
            """);
    }

    @Test
    void shouldReplayV1ClusterAndScoreBaseline() {
        Long crawlTaskId = createSucceededCrawlTask();
        HotItemEntity first = createHotItem(
            crawlTaskId,
            "hn-1301",
            "OpenAI launches an agent framework",
            180,
            42,
            List.of("AI", "agent")
        );
        HotItemEntity second = createHotItem(
            crawlTaskId,
            "hn-1302",
            "AI agent framework discussion",
            120,
            30,
            List.of("AI", "agent")
        );

        HotClusterEntity firstCluster = clusterService.assign(first);
        HotClusterEntity secondCluster = clusterService.assign(second);
        HotScoreEntity score = scoringService.score(firstCluster);

        assertThat(secondCluster.getId()).isEqualTo(firstCluster.getId());
        assertThat(firstCluster.getTitle()).isEqualTo("OpenAI launches an agent framework");
        assertThat(firstCluster.getStatus()).isEqualTo("ACTIVE");
        assertThat(count("hot_cluster")).isEqualTo(1);
        assertThat(count("hot_cluster_item")).isEqualTo(2);

        assertThat(jdbcTemplate.queryForList(
            "SELECT match_method FROM hot_cluster_item ORDER BY id",
            String.class
        )).containsExactly("SINGLETON", "CANONICAL_URL");
        assertThat(jdbcTemplate.queryForList(
            "SELECT rule_version FROM hot_cluster_item ORDER BY id",
            String.class
        )).containsOnly(RuleBasedClusterService.RULE_VERSION);

        assertThat(score.getScoringVersion()).isEqualTo(RuleBasedScoringService.SCORING_VERSION);
        assertThat(score.getHotClusterId()).isEqualTo(firstCluster.getId());
        assertThat(score.getScoreComponents().get("points").asDouble()).isEqualTo(expectedCappedLogScore(180, 500, 35));
        assertThat(score.getScoreComponents().get("comments").asDouble()).isEqualTo(expectedCappedLogScore(42, 200, 20));
        assertThat(score.getScoreComponents().get("freshness").asDouble()).isEqualTo(30.0);
        assertThat(score.getScoreComponents().get("keyword").asDouble()).isEqualTo(4.0);
        assertThat(score.getScoreComponents().get("clusterEvidence").asDouble()).isEqualTo(2.5);
        assertThat(score.getTotalScore()).isEqualByComparingTo(expectedTotalScore(180, 42, 2, 2));
    }

    private Long createSucceededCrawlTask() {
        SourceConfigEntity source = new SourceConfigEntity();
        source.setSourceCode("phase13a-v1-baseline-hn");
        source.setSourceType(SourceType.HACKER_NEWS);
        source.setDisplayName("Phase 13A V1 Baseline HN");
        source.setEnabled(true);
        source.setConfigPayload(objectMapper.createObjectNode().put("feed", "TOP"));
        source.setVersion(0);
        source.setCreatedAt(FIXED_SEEN_AT);
        source.setUpdatedAt(FIXED_SEEN_AT);
        sourceConfigMapper.insert(source);

        CrawlTaskEntity task = new CrawlTaskEntity();
        task.setSourceConfigId(source.getId());
        task.setTriggerType(CrawlTriggerType.MANUAL);
        task.setStatus(CrawlTaskStatus.SUCCEEDED);
        task.setIdempotencyKey("phase13a-v1-baseline");
        task.setRequestedAt(FIXED_SEEN_AT);
        task.setStartedAt(FIXED_SEEN_AT);
        task.setFinishedAt(FIXED_SEEN_AT);
        task.setFetchedCount(2);
        task.setPersistedCount(2);
        task.setMatchedCount(2);
        task.setFailedCount(0);
        task.setCreatedAt(FIXED_SEEN_AT);
        task.setUpdatedAt(FIXED_SEEN_AT);
        crawlTaskMapper.insert(task);
        return task.getId();
    }

    private HotItemEntity createHotItem(
        Long crawlTaskId,
        String externalId,
        String title,
        int points,
        int commentsCount,
        List<String> tags
    ) {
        RawItemEntity rawItem = new RawItemEntity();
        rawItem.setCrawlTaskId(crawlTaskId);
        rawItem.setSourceType(SourceType.HACKER_NEWS);
        rawItem.setExternalId(externalId);
        rawItem.setSourceUrl(SHARED_SOURCE_URL);
        rawItem.setRawPayload(rawPayload(externalId, title, points, commentsCount));
        rawItem.setPayloadHash(hashFor("raw-" + externalId));
        rawItem.setPublishedAt(FIXED_PUBLISHED_AT);
        rawItem.setFetchedAt(FIXED_SEEN_AT);
        rawItem.setCreatedAt(FIXED_SEEN_AT);
        rawItemMapper.insert(rawItem);

        HotItemEntity hotItem = new HotItemEntity();
        hotItem.setLatestRawItemId(rawItem.getId());
        hotItem.setSourceType(SourceType.HACKER_NEWS);
        hotItem.setExternalId(externalId);
        hotItem.setItemType("STORY");
        hotItem.setTitle(title);
        hotItem.setSummary(title);
        hotItem.setSourceUrl(SHARED_SOURCE_URL);
        hotItem.setAuthor("phase13a");
        hotItem.setTags(tags(tags));
        hotItem.setMetrics(metrics(points, commentsCount));
        hotItem.setContentHash(hashFor("hot-" + externalId));
        hotItem.setPublishedAt(FIXED_PUBLISHED_AT);
        hotItem.setFirstSeenAt(FIXED_SEEN_AT);
        hotItem.setLastSeenAt(FIXED_SEEN_AT);
        hotItem.setCreatedAt(FIXED_SEEN_AT);
        hotItem.setUpdatedAt(FIXED_SEEN_AT);
        hotItemMapper.insert(hotItem);
        return hotItem;
    }

    private ObjectNode rawPayload(String externalId, String title, int points, int commentsCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", externalId);
        payload.put("title", title);
        payload.put("url", SHARED_SOURCE_URL);
        payload.put("score", points);
        payload.put("descendants", commentsCount);
        return payload;
    }

    private ObjectNode metrics(int points, int commentsCount) {
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", points);
        metrics.put("commentsCount", commentsCount);
        return metrics;
    }

    private ArrayNode tags(List<String> values) {
        ArrayNode tags = objectMapper.createArrayNode();
        values.forEach(tags::add);
        return tags;
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    private BigDecimal expectedTotalScore(int points, int commentsCount, int keywordCount, int itemCount) {
        double total = expectedCappedLogScore(points, 500, 35)
            + expectedCappedLogScore(commentsCount, 200, 20)
            + 30.0
            + Math.min(10, keywordCount * 2.0)
            + Math.min(5, Math.max(0, itemCount - 1) * 2.5);
        return BigDecimal.valueOf(total).setScale(4, RoundingMode.HALF_UP);
    }

    private double expectedCappedLogScore(int value, int cap, int maxScore) {
        int capped = Math.max(0, Math.min(value, cap));
        return Math.log1p(capped) / Math.log1p(cap) * maxScore;
    }

    private String hashFor(String value) {
        return Integer.toHexString(value.hashCode()).repeat(8).substring(0, 64);
    }
}
