package com.airadar.operations;

import com.airadar.crawl.client.hackernews.FetchedHackerNewsItem;
import com.airadar.crawl.client.hackernews.HackerNewsClient;
import com.airadar.crawl.client.hackernews.HackerNewsItemResponse;
import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.crawl.service.CrawlExecutionService;
import com.airadar.crawl.vo.CrawlTaskVO;
import com.airadar.common.api.PageResponse;
import com.airadar.source.dto.CreateSourceRequest;
import com.airadar.source.model.SourceType;
import com.airadar.source.service.SourceConfigService;
import com.airadar.source.vo.SourceConfigVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = "ai-radar.operations.scheduled-crawl.enabled=false")
class ScheduledCrawlServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private HackerNewsClient hackerNewsClient;

    @Autowired
    private SourceConfigService sourceConfigService;

    @Autowired
    private ScheduledCrawlService scheduledCrawlService;

    @Autowired
    private CrawlTaskMapper crawlTaskMapper;

    @Autowired
    private com.airadar.crawl.service.CrawlTaskLifecycleService taskLifecycleService;

    @Autowired
    private CrawlExecutionService crawlExecutionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

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
    void shouldTriggerScheduledCrawlForDueSource() {
        SourceConfigVO source = createSource(60);
        mockHackerNewsItems();

        ScheduledCrawlResult result = scheduledCrawlService.runOnce();

        assertThat(result.scannedSources()).isEqualTo(1);
        assertThat(result.triggeredTasks()).isEqualTo(1);
        assertThat(result.skippedSources()).isZero();
        assertThat(result.sources()).hasSize(1);
        ScheduledSourceResult sourceResult = result.sources().get(0);
        assertThat(sourceResult.sourceId()).isEqualTo(source.id());
        assertThat(sourceResult.triggered()).isTrue();
        assertThat(sourceResult.skipReason()).isNull();

        CrawlTaskEntity task = crawlTaskMapper.selectList(null).get(0);
        assertThat(task.getTriggerType()).isEqualTo(CrawlTriggerType.SCHEDULED);
        assertThat(task.getStatus()).isEqualTo(CrawlTaskStatus.SUCCEEDED);
    }

    @Test
    void shouldSkipWhenIntervalNotElapsed() {
        SourceConfigVO source = createSource(60);
        mockHackerNewsItems();

        scheduledCrawlService.runOnce();
        ScheduledCrawlResult second = scheduledCrawlService.runOnce();

        assertThat(second.triggeredTasks()).isZero();
        assertThat(second.skippedSources()).isEqualTo(1);
        ScheduledSourceResult sourceResult = second.sources().get(0);
        assertThat(sourceResult.triggered()).isFalse();
        assertThat(sourceResult.skipReason()).isEqualTo(ScheduledCrawlService.SKIP_REASON_NOT_YET_DUE);
        assertThat(count("crawl_task")).isEqualTo(1);
    }

    @Test
    void shouldSkipWhenTaskInFlight() {
        SourceConfigVO source = createSource(60);
        CrawlTaskEntity inFlight = new CrawlTaskEntity();
        inFlight.setSourceConfigId(source.id());
        inFlight.setTriggerType(CrawlTriggerType.SCHEDULED);
        inFlight.setStatus(CrawlTaskStatus.RUNNING);
        inFlight.setIdempotencyKey("scheduled-crawl-" + source.id() + "-manual-seed");
        Instant now = Instant.now();
        inFlight.setRequestedAt(now.minusSeconds(120));
        inFlight.setStartedAt(now.minusSeconds(110));
        inFlight.setFetchedCount(0);
        inFlight.setPersistedCount(0);
        inFlight.setMatchedCount(0);
        inFlight.setFailedCount(0);
        inFlight.setCreatedAt(now.minusSeconds(120));
        inFlight.setUpdatedAt(now.minusSeconds(110));
        crawlTaskMapper.insert(inFlight);

        ScheduledCrawlResult result = scheduledCrawlService.runOnce();

        assertThat(result.triggeredTasks()).isZero();
        assertThat(result.skippedSources()).isEqualTo(1);
        assertThat(result.sources().get(0).skipReason()).isEqualTo(ScheduledCrawlService.SKIP_REASON_IN_FLIGHT);
        assertThat(count("crawl_task")).isEqualTo(1);
    }

    @Test
    void shouldNotSelectDisabledOrIntervallessSources() {
        createSource(60);
        sourceConfigService.create(new CreateSourceRequest(
                "hn-disabled",
                SourceType.HACKER_NEWS,
                "Hacker News Disabled",
                false,
                60,
                java.util.Map.of("feed", "TOP", "fetchLimit", 3, "keywords", List.of("AI"))
        ));

        ScheduledCrawlResult result = scheduledCrawlService.runOnce();

        assertThat(result.scannedSources()).isEqualTo(1);
    }

    @Test
    void shouldExcludeSourceAfterDisablingViaStatusAndReincludeAfterReenable() {
        SourceConfigVO source = createSource(60);
        mockHackerNewsItems();

        ScheduledCrawlResult firstRun = scheduledCrawlService.runOnce();
        assertThat(firstRun.scannedSources()).isEqualTo(1);
        assertThat(firstRun.triggeredTasks()).isEqualTo(1);

        sourceConfigService.updateStatus(source.id(), false);

        ScheduledCrawlResult afterDisable = scheduledCrawlService.runOnce();
        assertThat(afterDisable.scannedSources()).isZero();
        assertThat(afterDisable.triggeredTasks()).isZero();
        assertThat(afterDisable.sources()).isEmpty();

        sourceConfigService.updateStatus(source.id(), true);

        ScheduledCrawlResult afterReenable = scheduledCrawlService.runOnce();
        assertThat(afterReenable.scannedSources()).isEqualTo(1);
        assertThat(afterReenable.triggeredTasks()).isZero();
        ScheduledSourceResult reenableResult = afterReenable.sources().get(0);
        assertThat(reenableResult.sourceId()).isEqualTo(source.id());
        assertThat(reenableResult.skipReason()).isEqualTo(ScheduledCrawlService.SKIP_REASON_NOT_YET_DUE);

        assertThat(count("crawl_task")).isEqualTo(1);
    }

    @Test
    void shouldReuseIdempotencyKeyWithinSameBucket() {
        SourceConfigVO source = createSource(60);
        mockHackerNewsItems();

        String key1 = ScheduledCrawlService.buildIdempotencyKey(source.id(), 60, Instant.now());
        String key2 = ScheduledCrawlService.buildIdempotencyKey(source.id(), 60, Instant.now().plusSeconds(30));
        assertThat(key1).isEqualTo(key2);

        String keyNextHour = ScheduledCrawlService.buildIdempotencyKey(
                source.id(), 60, Instant.now().plusSeconds(3700));
        assertThat(keyNextHour).isNotEqualTo(key1);
    }

    @Test
    void listShouldFilterByTriggerTypeStatusAndSource() {
        SourceConfigVO source = createSource(60);
        mockHackerNewsItems();

        scheduledCrawlService.runOnce();
        crawlExecutionService.executeManual(source.id(), "manual-key-1");

        PageResponse<CrawlTaskVO> scheduledOnly = taskLifecycleService.list(
                1, 20, null, CrawlTriggerType.SCHEDULED, null);
        assertThat(scheduledOnly.totalElements()).isEqualTo(1);
        assertThat(scheduledOnly.items().get(0).triggerType()).isEqualTo(CrawlTriggerType.SCHEDULED);

        PageResponse<CrawlTaskVO> manualOnly = taskLifecycleService.list(
                1, 20, null, CrawlTriggerType.MANUAL, null);
        assertThat(manualOnly.totalElements()).isEqualTo(1);
        assertThat(manualOnly.items().get(0).triggerType()).isEqualTo(CrawlTriggerType.MANUAL);

        PageResponse<CrawlTaskVO> bySource = taskLifecycleService.list(
                1, 20, source.id(), null, null);
        assertThat(bySource.totalElements()).isEqualTo(2);

        PageResponse<CrawlTaskVO> succeeded = taskLifecycleService.list(
                1, 20, null, null, CrawlTaskStatus.SUCCEEDED);
        assertThat(succeeded.totalElements()).isEqualTo(2);
    }

    @Test
    void shouldListCrawlTasksViaApi() throws Exception {
        SourceConfigVO source = createSource(60);
        mockHackerNewsItems();

        scheduledCrawlService.runOnce();
        crawlExecutionService.executeManual(source.id(), "manual-key-api");

        mockMvc.perform(get("/api/v1/crawl-tasks")
                        .param("triggerType", "SCHEDULED")
                        .param("sourceId", String.valueOf(source.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].triggerType").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.items[0].sourceId").value(source.id()));
    }

    private SourceConfigVO createSource(int intervalMinutes) {
        return sourceConfigService.create(new CreateSourceRequest(
                "hn-scheduled",
                SourceType.HACKER_NEWS,
                "Hacker News Scheduled",
                true,
                intervalMinutes,
                java.util.Map.of("feed", "TOP", "fetchLimit", 3, "keywords", List.of("AI", "agent"))
        ));
    }

    private void mockHackerNewsItems() {
        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(101L));
        long publishedAt = Instant.now().minusSeconds(3600).getEpochSecond();
        HackerNewsItemResponse response = new HackerNewsItemResponse(
                101L,
                false,
                "story",
                "scheduled-test",
                publishedAt,
                null,
                false,
                "https://example.com/ai-agent",
                180,
                "AI agent framework launched",
                42,
                List.of()
        );
        JsonNode payload = objectMapper.valueToTree(response);
        when(hackerNewsClient.fetchItem(101L)).thenReturn(Optional.of(new FetchedHackerNewsItem(response, payload)));
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
