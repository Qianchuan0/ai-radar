package com.airadar.cluster.controller;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.raw.mapper.RawItemMapper;
import com.airadar.signal.entity.SignalSnapshotEntity;
import com.airadar.signal.mapper.SignalSnapshotMapper;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.mapper.SourceConfigMapper;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 18A integration test for {@code GET /api/v1/hot-clusters/{id}/trends}.
 *
 * <p>Requires Docker (Testcontainers). The test seeds a cluster with two
 * contributing GitHub items whose snapshots show positive growth, then asserts
 * the cluster trend resolves to {@code RISING} with both items listed under
 * {@code contributingItems}.
 */
@Testcontainers
@SpringBootTest(properties = {
    "ai-radar.operations.scheduled-crawl.enabled=false",
    "ai-radar.operations.scheduled-daily-report.enabled=false"
})
@AutoConfigureMockMvc
class ClusterTrendControllerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SourceConfigMapper sourceConfigMapper;

    @Autowired
    private CrawlTaskMapper crawlTaskMapper;

    @Autowired
    private RawItemMapper rawItemMapper;

    @Autowired
    private HotItemMapper hotItemMapper;

    @Autowired
    private HotClusterMapper hotClusterMapper;

    @Autowired
    private HotClusterItemMapper hotClusterItemMapper;

    @Autowired
    private SignalSnapshotMapper signalSnapshotMapper;

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
                hot_item_signal_snapshot,
                hot_item,
                raw_item,
                crawl_task,
                source_config
            RESTART IDENTITY CASCADE
            """);
    }

    @Test
    void shouldReturnRisingClusterTrendAcrossWindows() throws Exception {
        Instant base = Instant.parse("2026-07-17T00:00:00Z");
        Long crawlTaskId = createSucceededCrawlTask(base);

        HotClusterEntity cluster = new HotClusterEntity();
        cluster.setTitle("Rising AI project");
        cluster.setSummary("Multiple items growing");
        cluster.setStatus("ACTIVE");
        cluster.setFirstSeenAt(base.minusSeconds(72 * 3600));
        cluster.setLastSeenAt(base);
        cluster.setCreatedAt(base);
        cluster.setUpdatedAt(base);
        hotClusterMapper.insert(cluster);

        // Two GitHub items in the same cluster.
        HotItemEntity primary = githubHotItem(crawlTaskId, "gh-primary", base, "https://github.com/org/repo");
        hotItemMapper.insert(primary);
        HotItemEntity secondary = githubHotItem(crawlTaskId, "gh-secondary", base, "https://github.com/org/other");
        hotItemMapper.insert(secondary);

        linkItem(cluster.getId(), primary.getId(), true, base);
        linkItem(cluster.getId(), secondary.getId(), false, base);

        // Each item has a 6h-ago snapshot and a current snapshot showing growth.
        seedGithubSnapshots(primary, base, 3_800, 4_200);
        seedGithubSnapshots(secondary, base, 2_000, 2_500);

        mockMvc.perform(get("/api/v1/hot-clusters/{id}/trends", cluster.getId())
                .param("windows", "6h,24h"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].window").value("6h"))
            .andExpect(jsonPath("$.data[0].trendState").value("RISING"))
            .andExpect(jsonPath("$.data[0].contributingItems").value(org.hamcrest.Matchers.hasItem(primary.getId().intValue())))
            .andExpect(jsonPath("$.data[0].confidence").value(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.is("HIGH"),
                org.hamcrest.Matchers.is("MEDIUM")
            )));
    }

    @Test
    void shouldReturnUnknownForEmptyCluster() throws Exception {
        HotClusterEntity cluster = new HotClusterEntity();
        cluster.setTitle("Empty cluster");
        cluster.setStatus("ACTIVE");
        cluster.setFirstSeenAt(Instant.now());
        cluster.setLastSeenAt(Instant.now());
        cluster.setCreatedAt(Instant.now());
        cluster.setUpdatedAt(Instant.now());
        hotClusterMapper.insert(cluster);

        mockMvc.perform(get("/api/v1/hot-clusters/{id}/trends", cluster.getId())
                .param("windows", "1h"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].trendState").value("UNKNOWN"))
            .andExpect(jsonPath("$.data[0].contributingItems").isEmpty());
    }

    private Long createSucceededCrawlTask(Instant now) {
        SourceConfigEntity source = new SourceConfigEntity();
        source.setSourceCode("phase18a-cluster-test");
        source.setSourceType(SourceType.GITHUB);
        source.setDisplayName("Phase 18A Cluster Trend Test");
        source.setEnabled(true);
        source.setConfigPayload(objectMapper.createObjectNode().put("query", "ai"));
        source.setVersion(0);
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        sourceConfigMapper.insert(source);

        CrawlTaskEntity task = new CrawlTaskEntity();
        task.setSourceConfigId(source.getId());
        task.setTriggerType(CrawlTriggerType.MANUAL);
        task.setStatus(CrawlTaskStatus.SUCCEEDED);
        task.setIdempotencyKey("phase18a-cluster-test");
        task.setRequestedAt(now);
        task.setStartedAt(now);
        task.setFinishedAt(now);
        task.setFetchedCount(2);
        task.setPersistedCount(2);
        task.setMatchedCount(2);
        task.setFailedCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        crawlTaskMapper.insert(task);
        return task.getId();
    }

    private HotItemEntity githubHotItem(Long crawlTaskId, String externalId, Instant seenAt, String url) {
        RawItemEntity raw = new RawItemEntity();
        raw.setCrawlTaskId(crawlTaskId);
        raw.setSourceType(SourceType.GITHUB);
        raw.setExternalId(externalId);
        raw.setSourceUrl(url);
        raw.setRawPayload(objectMapper.createObjectNode().put("id", externalId));
        raw.setPayloadHash((externalId + "-raw").repeat(16).substring(0, 64));
        raw.setPublishedAt(seenAt.minusSeconds(3600));
        raw.setFetchedAt(seenAt);
        raw.setCreatedAt(seenAt);
        rawItemMapper.insert(raw);

        HotItemEntity entity = new HotItemEntity();
        entity.setLatestRawItemId(raw.getId());
        entity.setSourceType(SourceType.GITHUB);
        entity.setExternalId(externalId);
        entity.setItemType("REPOSITORY");
        entity.setTitle("Repo " + externalId);
        entity.setSummary("Repo " + externalId);
        entity.setSourceUrl(url);
        entity.setAuthor("org");
        entity.setTags(objectMapper.createArrayNode().add("ai"));
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("stargazersCount", 4_000);
        metrics.put("forksCount", 300);
        metrics.put("watchersCount", 60);
        metrics.put("openIssuesCount", 15);
        entity.setMetrics(metrics);
        entity.setContentHash((externalId + "-hot").repeat(16).substring(0, 64));
        entity.setPublishedAt(seenAt.minusSeconds(3600));
        entity.setFirstSeenAt(seenAt.minusSeconds(48 * 3600));
        entity.setLastSeenAt(seenAt);
        entity.setCreatedAt(seenAt);
        entity.setUpdatedAt(seenAt);
        return entity;
    }

    private void linkItem(Long clusterId, Long hotItemId, boolean primary, Instant at) {
        HotClusterItemEntity membership = new HotClusterItemEntity();
        membership.setHotClusterId(clusterId);
        membership.setHotItemId(hotItemId);
        membership.setMatchMethod("CANONICAL_URL");
        membership.setIsPrimary(primary);
        membership.setRuleVersion("hn-rule-v1");
        membership.setAssignedAt(at);
        hotClusterItemMapper.insert(membership);
    }

    private void seedGithubSnapshots(HotItemEntity item, Instant base, int previousStars, int currentStars) {
        signalSnapshotMapper.insert(githubSnapshot(
            item.getId(),
            item.getLatestRawItemId(),
            base.minusSeconds(6 * 3600),
            rawGithub(previousStars),
            normalized(50, 30, 45)
        ));
        signalSnapshotMapper.insert(githubSnapshot(
            item.getId(),
            item.getLatestRawItemId() + 100,
            base,
            rawGithub(currentStars),
            normalized(80, 50, 70)
        ));
    }

    private ObjectNode rawGithub(int stars) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("stargazersCount", stars);
        node.put("forksCount", 300);
        node.put("watchersCount", 60);
        node.put("openIssuesCount", 15);
        return node;
    }

    private ObjectNode normalized(double attention, double discussion, double adoption) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("attention", attention);
        node.put("discussion", discussion);
        node.put("adoption", adoption);
        node.put("authority", 0.0);
        node.put("relevance", 0.0);
        return node;
    }

    private SignalSnapshotEntity githubSnapshot(
        Long hotItemId, Long rawItemId, Instant observedAt,
        ObjectNode rawMetrics, ObjectNode normalizedSignal
    ) {
        SignalSnapshotEntity entity = new SignalSnapshotEntity();
        entity.setHotItemId(hotItemId);
        entity.setRawItemId(rawItemId);
        entity.setSourceType(SourceType.GITHUB);
        entity.setSourceRole(SourceRole.ADOPTION);
        entity.setObservedAt(observedAt);
        entity.setRawMetrics(rawMetrics);
        entity.setNormalizedSignal(normalizedSignal);
        entity.setCreatedAt(observedAt);
        return entity;
    }
}
