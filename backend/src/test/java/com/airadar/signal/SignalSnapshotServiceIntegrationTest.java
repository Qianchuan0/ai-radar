package com.airadar.signal;

import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.raw.mapper.RawItemMapper;
import com.airadar.signal.entity.SignalSnapshotEntity;
import com.airadar.signal.service.SignalSnapshotService;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.mapper.SourceConfigMapper;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
    "ai-radar.operations.scheduled-crawl.enabled=false",
    "ai-radar.operations.scheduled-daily-report.enabled=false"
})
class SignalSnapshotServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

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
    private SignalSnapshotService signalSnapshotService;

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
    void shouldPersistSignalSnapshotIdempotentlyByRawItemId() {
        Long crawlTaskId = createSucceededCrawlTask();
        RawItemEntity rawItem = rawItem();
        rawItem.setCrawlTaskId(crawlTaskId);
        rawItemMapper.insert(rawItem);
        HotItemEntity hotItem = hotItem(rawItem.getId());
        hotItemMapper.insert(hotItem);

        NormalizedSignal signal = NormalizedSignal.of(
            SourceType.HACKER_NEWS,
            SourceRole.COMMUNITY,
            60.0,
            30.0,
            0.0,
            metrics(120, 35)
        );

        SignalSnapshotEntity first = signalSnapshotService.save(rawItem, hotItem, signal);
        SignalSnapshotEntity duplicate = signalSnapshotService.save(rawItem, hotItem, signal);

        assertThat(first.getId()).isNotNull();
        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM hot_item_signal_snapshot", Long.class)).isEqualTo(1L);
    }

    private Long createSucceededCrawlTask() {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        SourceConfigEntity source = new SourceConfigEntity();
        source.setSourceCode("phase14-snapshot-test");
        source.setSourceType(SourceType.HACKER_NEWS);
        source.setDisplayName("Phase 14 Snapshot Test");
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
        task.setIdempotencyKey("phase14-snapshot-test");
        task.setRequestedAt(now);
        task.setStartedAt(now);
        task.setFinishedAt(now);
        task.setFetchedCount(1);
        task.setPersistedCount(1);
        task.setMatchedCount(1);
        task.setFailedCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        crawlTaskMapper.insert(task);
        return task.getId();
    }

    private RawItemEntity rawItem() {
        RawItemEntity entity = new RawItemEntity();
        entity.setSourceType(SourceType.HACKER_NEWS);
        entity.setExternalId("hn-1");
        entity.setSourceUrl("https://example.com/hn");
        entity.setRawPayload(objectMapper.createObjectNode().put("id", "hn-1"));
        entity.setPayloadHash("a".repeat(64));
        entity.setPublishedAt(Instant.parse("2026-07-16T00:00:00Z"));
        entity.setFetchedAt(Instant.parse("2026-07-17T00:00:00Z"));
        entity.setCreatedAt(Instant.parse("2026-07-17T00:00:00Z"));
        return entity;
    }

    private HotItemEntity hotItem(Long rawItemId) {
        HotItemEntity entity = new HotItemEntity();
        entity.setLatestRawItemId(rawItemId);
        entity.setSourceType(SourceType.HACKER_NEWS);
        entity.setExternalId("hn-1");
        entity.setItemType("STORY");
        entity.setTitle("Example story");
        entity.setSummary("Example story");
        entity.setSourceUrl("https://example.com/hn");
        entity.setAuthor("tester");
        entity.setTags(objectMapper.createArrayNode().add("ai"));
        entity.setMetrics(metrics(120, 35));
        entity.setContentHash("b".repeat(64));
        entity.setPublishedAt(Instant.parse("2026-07-16T00:00:00Z"));
        entity.setFirstSeenAt(Instant.parse("2026-07-17T00:00:00Z"));
        entity.setLastSeenAt(Instant.parse("2026-07-17T00:00:00Z"));
        entity.setCreatedAt(Instant.parse("2026-07-17T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-07-17T00:00:00Z"));
        return entity;
    }

    private ObjectNode metrics(int points, int comments) {
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", points);
        metrics.put("commentsCount", comments);
        return metrics;
    }
}
