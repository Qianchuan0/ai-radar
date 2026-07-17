package com.airadar.signal.controller;

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

@Testcontainers
@SpringBootTest(properties = {
    "ai-radar.operations.scheduled-crawl.enabled=false",
    "ai-radar.operations.scheduled-daily-report.enabled=false"
})
@AutoConfigureMockMvc
class HotItemSignalControllerIntegrationTest {

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
    void shouldReturnSignalsAndTrendFromPersistedSnapshots() throws Exception {
        Instant base = Instant.parse("2026-07-17T00:00:00Z");
        Long crawlTaskId = createSucceededCrawlTask(base);
        RawItemEntity currentRawItem = rawItem(crawlTaskId, "hn-2", base);
        rawItemMapper.insert(currentRawItem);
        HotItemEntity hotItem = hotItem(currentRawItem.getId(), "hn-2", base);
        hotItemMapper.insert(hotItem);

        signalSnapshotMapper.insert(snapshot(
            hotItem.getId(),
            currentRawItem.getId(),
            base.minusSeconds(24 * 3600),
            signal(20, 10, 0, 0, null)
        ));
        signalSnapshotMapper.insert(snapshot(
            hotItem.getId(),
            currentRawItem.getId() + 1,
            base,
            signal(80, 50, 0, 0, null)
        ));

        mockMvc.perform(get("/api/v1/hot-items/{id}/signals", hotItem.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].hotItemId").value(hotItem.getId()));

        mockMvc.perform(get("/api/v1/hot-items/{id}/trend", hotItem.getId()).param("window", "24h"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.attentionDelta").value(60.0))
            .andExpect(jsonPath("$.data.discussionDelta").value(40.0))
            .andExpect(jsonPath("$.data.confidence").value("HIGH"));
    }

    private Long createSucceededCrawlTask(Instant now) {
        SourceConfigEntity source = new SourceConfigEntity();
        source.setSourceCode("phase14-controller-test");
        source.setSourceType(SourceType.HACKER_NEWS);
        source.setDisplayName("Phase 14 Controller Test");
        source.setEnabled(true);
        source.setConfigPayload(objectMapper.createObjectNode().put("feed", "TOP"));
        source.setVersion(0);
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        sourceConfigMapper.insert(source);

        CrawlTaskEntity task = new CrawlTaskEntity();
        task.setSourceConfigId(source.getId());
        task.setTriggerType(CrawlTriggerType.MANUAL);
        task.setStatus(CrawlTaskStatus.SUCCEEDED);
        task.setIdempotencyKey("phase14-controller-test");
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

    private RawItemEntity rawItem(Long crawlTaskId, String externalId, Instant fetchedAt) {
        RawItemEntity entity = new RawItemEntity();
        entity.setCrawlTaskId(crawlTaskId);
        entity.setSourceType(SourceType.HACKER_NEWS);
        entity.setExternalId(externalId);
        entity.setSourceUrl("https://example.com/" + externalId);
        entity.setRawPayload(objectMapper.createObjectNode().put("id", externalId));
        entity.setPayloadHash((externalId + "-raw").repeat(16).substring(0, 64));
        entity.setPublishedAt(fetchedAt.minusSeconds(3600));
        entity.setFetchedAt(fetchedAt);
        entity.setCreatedAt(fetchedAt);
        return entity;
    }

    private HotItemEntity hotItem(Long rawItemId, String externalId, Instant seenAt) {
        HotItemEntity entity = new HotItemEntity();
        entity.setLatestRawItemId(rawItemId);
        entity.setSourceType(SourceType.HACKER_NEWS);
        entity.setExternalId(externalId);
        entity.setItemType("STORY");
        entity.setTitle("Example " + externalId);
        entity.setSummary("Example " + externalId);
        entity.setSourceUrl("https://example.com/" + externalId);
        entity.setAuthor("tester");
        entity.setTags(objectMapper.createArrayNode().add("ai"));
        entity.setMetrics(objectMapper.createObjectNode().put("points", 100).put("commentsCount", 20));
        entity.setContentHash((externalId + "-hot").repeat(16).substring(0, 64));
        entity.setPublishedAt(seenAt.minusSeconds(3600));
        entity.setFirstSeenAt(seenAt.minusSeconds(24 * 3600));
        entity.setLastSeenAt(seenAt);
        entity.setCreatedAt(seenAt);
        entity.setUpdatedAt(seenAt);
        return entity;
    }

    private SignalSnapshotEntity snapshot(Long hotItemId, Long rawItemId, Instant observedAt, ObjectNode normalizedSignal) {
        SignalSnapshotEntity entity = new SignalSnapshotEntity();
        entity.setHotItemId(hotItemId);
        entity.setRawItemId(rawItemId);
        entity.setSourceType(SourceType.HACKER_NEWS);
        entity.setSourceRole(SourceRole.COMMUNITY);
        entity.setObservedAt(observedAt);
        entity.setRawMetrics(objectMapper.createObjectNode().put("points", 100).put("commentsCount", 20));
        entity.setNormalizedSignal(normalizedSignal);
        entity.setCreatedAt(observedAt);
        return entity;
    }

    private ObjectNode signal(double attention, double discussion, double adoption, double relevance, Integer rank) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("attention", attention);
        node.put("discussion", discussion);
        node.put("adoption", adoption);
        node.put("authority", 0.0);
        node.put("relevance", relevance);
        if (rank != null) {
            node.put("rank", rank);
        }
        return node;
    }
}
