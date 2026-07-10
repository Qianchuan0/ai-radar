package com.airadar.report;

import com.airadar.crawl.client.hackernews.FetchedHackerNewsItem;
import com.airadar.crawl.client.hackernews.HackerNewsClient;
import com.airadar.crawl.client.hackernews.HackerNewsItemResponse;
import com.airadar.source.dto.CreateSourceRequest;
import com.airadar.source.model.SourceType;
import com.airadar.source.service.SourceConfigService;
import com.airadar.source.vo.SourceConfigVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DailyReportIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private HackerNewsClient hackerNewsClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceConfigService sourceConfigService;

    @Autowired
    private com.airadar.crawl.service.CrawlExecutionService crawlExecutionService;

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
    }

    @Test
    void shouldGenerateReadAndRefreshDailyReport() throws Exception {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-report-ai",
                SourceType.HACKER_NEWS,
                "Hacker News Report AI",
                true,
                null,
                Map.of(
                        "feed", "TOP",
                        "fetchLimit", 2,
                        "keywords", List.of("AI", "agent")
                )
        ));
        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(301L, 302L));
        when(hackerNewsClient.fetchItem(301L)).thenReturn(Optional.of(item(
                301L,
                "OpenAI agent launch notes",
                "https://example.com/openai-agent-launch",
                210,
                55
        )));
        when(hackerNewsClient.fetchItem(302L)).thenReturn(Optional.of(item(
                302L,
                "Agent launch discussion",
                "https://example.com/openai-agent-launch?ref=hn",
                140,
                21
        )));

        crawlExecutionService.executeManual(source.id(), "phase7-report-flow-1");
        long clusterId = jdbcTemplate.queryForObject("SELECT id FROM hot_cluster", Long.class);
        LocalDate reportDate = Instant.now().atOffset(ZoneOffset.UTC).toLocalDate();

        mockMvc.perform(post("/api/v1/hot-clusters/{clusterId}/analysis-runs", clusterId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        mockMvc.perform(post("/api/v1/reports/daily-runs")
                        .param("date", reportDate.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportDate").value(reportDate.toString()))
                .andExpect(jsonPath("$.data.clusterCount").value(1));

        mockMvc.perform(post("/api/v1/reports/daily-runs")
                        .param("date", reportDate.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clusterCount").value(1));

        assertThat(count("daily_report")).isEqualTo(1);

        mockMvc.perform(get("/api/v1/reports/daily/{reportDate}", reportDate)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportDate").value(reportDate.toString()))
                .andExpect(jsonPath("$.data.clusterCount").value(1))
                .andExpect(jsonPath("$.data.topClusterIds[0]").value(clusterId))
                .andExpect(jsonPath("$.data.clusters[0].title").value("OpenAI agent launch notes"))
                .andExpect(jsonPath("$.data.clusters[0].latestAnalysis.result.headline").value("OpenAI agent launch notes"));

        mockMvc.perform(get("/api/v1/reports/daily")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].reportDate").value(reportDate.toString()))
                .andExpect(jsonPath("$.data.items[0].clusterCount").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void shouldGenerateEmptyDailyReportForDateWithoutClusters() throws Exception {
        LocalDate reportDate = LocalDate.of(2026, 7, 1);

        mockMvc.perform(post("/api/v1/reports/daily-runs")
                        .param("date", reportDate.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clusterCount").value(0));

        mockMvc.perform(get("/api/v1/reports/daily/{reportDate}", reportDate)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clusterCount").value(0))
                .andExpect(jsonPath("$.data.clusters").isArray())
                .andExpect(jsonPath("$.data.clusters").isEmpty());
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
                "phase7-test",
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
