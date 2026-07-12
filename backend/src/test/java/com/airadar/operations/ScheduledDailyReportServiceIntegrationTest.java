package com.airadar.operations;

import com.airadar.crawl.client.hackernews.FetchedHackerNewsItem;
import com.airadar.crawl.client.hackernews.HackerNewsClient;
import com.airadar.crawl.client.hackernews.HackerNewsItemResponse;
import com.airadar.crawl.service.CrawlExecutionService;
import com.airadar.report.service.DailyReportService;
import com.airadar.source.dto.CreateSourceRequest;
import com.airadar.source.model.SourceType;
import com.airadar.source.service.SourceConfigService;
import com.airadar.source.vo.SourceConfigVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "ai-radar.analysis.provider=fake",
        "ai-radar.operations.scheduled-crawl.enabled=false",
        "ai-radar.operations.scheduled-daily-report.enabled=false"
})
class ScheduledDailyReportServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private HackerNewsClient hackerNewsClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ScheduledDailyReportService scheduledDailyReportService;

    @Autowired
    private ScheduledOperationsProperties scheduledOperationsProperties;

    @Autowired
    private DailyReportService dailyReportService;

    @Autowired
    private SourceConfigService sourceConfigService;

    @Autowired
    private CrawlExecutionService crawlExecutionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    daily_report,
                    alert_record,
                    subscription_rule,
                    cluster_analysis,
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
        ScheduledOperationsProperties.ScheduledDailyReport config =
                scheduledOperationsProperties.getScheduledDailyReport();
        config.setReportDateOffsetDays(1);
        config.setRefreshExisting(false);
    }

    @Test
    void shouldGenerateReportForTargetDateWithoutExistingReport() {
        scheduledOperationsProperties.getScheduledDailyReport().setReportDateOffsetDays(0);
        seedCurrentDayCluster();

        ScheduledDailyReportResult result = scheduledDailyReportService.runOnce();

        LocalDate targetDate = LocalDate.now(ZoneOffset.UTC);
        assertThat(result.targetDate()).isEqualTo(targetDate);
        assertThat(result.generated()).isTrue();
        assertThat(result.skipped()).isFalse();
        assertThat(result.clusterCount()).isEqualTo(1);
        assertThat(result.generatedAt()).isNotNull();
        assertThat(count("daily_report")).isEqualTo(1);
        assertThat(dailyReportService.get(targetDate).clusterCount()).isEqualTo(1);
    }

    @Test
    void shouldSkipWhenTargetReportAlreadyExists() {
        LocalDate targetDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        dailyReportService.generate(targetDate);

        ScheduledDailyReportResult result = scheduledDailyReportService.runOnce();

        assertThat(result.targetDate()).isEqualTo(targetDate);
        assertThat(result.generated()).isFalse();
        assertThat(result.skipped()).isTrue();
        assertThat(result.skipReason()).isEqualTo(ScheduledDailyReportService.SKIP_REASON_REPORT_ALREADY_EXISTS);
        assertThat(result.clusterCount()).isNull();
        assertThat(count("daily_report")).isEqualTo(1);
    }

    @Test
    void shouldRefreshExistingReportWhenConfigured() {
        LocalDate targetDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        dailyReportService.generate(targetDate);
        scheduledOperationsProperties.getScheduledDailyReport().setRefreshExisting(true);

        ScheduledDailyReportResult result = scheduledDailyReportService.runOnce();

        assertThat(result.targetDate()).isEqualTo(targetDate);
        assertThat(result.generated()).isTrue();
        assertThat(result.skipped()).isFalse();
        assertThat(result.clusterCount()).isZero();
        assertThat(count("daily_report")).isEqualTo(1);
    }

    @Test
    void shouldGenerateEmptyReportForDateWithoutClusters() {
        LocalDate targetDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        ScheduledDailyReportResult result = scheduledDailyReportService.runOnce();

        assertThat(result.targetDate()).isEqualTo(targetDate);
        assertThat(result.generated()).isTrue();
        assertThat(result.clusterCount()).isZero();
        assertThat(dailyReportService.get(targetDate).clusters()).isEmpty();
    }

    @Test
    void scheduledDailyReportRunnerShouldStayDisabledByDefault() {
        assertThat(applicationContext.getBeanNamesForType(ScheduledDailyReportRunner.class)).isEmpty();
    }

    private void seedCurrentDayCluster() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-scheduled-report",
                SourceType.HACKER_NEWS,
                "Hacker News Scheduled Report",
                true,
                null,
                Map.of(
                        "feed", "TOP",
                        "fetchLimit", 2,
                        "keywords", List.of("AI", "agent")
                )
        ));
        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(401L, 402L));
        when(hackerNewsClient.fetchItem(401L)).thenReturn(Optional.of(item(
                401L,
                "AI daily report automation lands",
                "https://example.com/ai-daily-report-automation",
                220,
                58
        )));
        when(hackerNewsClient.fetchItem(402L)).thenReturn(Optional.of(item(
                402L,
                "Daily report automation discussion",
                "https://example.com/ai-daily-report-automation?ref=hn",
                150,
                27
        )));

        crawlExecutionService.executeManual(source.id(), "phase11b-scheduled-report-cluster");
    }

    private FetchedHackerNewsItem item(
            long id,
            String title,
            String url,
            int score,
            int descendants
    ) {
        long publishedAt = Instant.now().minusSeconds(3600).getEpochSecond();
        HackerNewsItemResponse response = new HackerNewsItemResponse(
                id,
                false,
                "story",
                "phase11b-test",
                publishedAt,
                null,
                false,
                url,
                score,
                title,
                descendants,
                List.of()
        );
        JsonNode payload = objectMapper.valueToTree(response);
        return new FetchedHackerNewsItem(response, payload);
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
