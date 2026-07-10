package com.airadar.alert;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AlertFlowIntegrationTest {

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
    void shouldCreateMatchSuppressAndUpdateAlerts() throws Exception {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-alert-ai",
                SourceType.HACKER_NEWS,
                "Hacker News Alert AI",
                true,
                null,
                Map.of(
                        "feed", "TOP",
                        "fetchLimit", 2,
                        "keywords", List.of("AI", "agent")
                )
        ));
        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(201L, 202L));
        when(hackerNewsClient.fetchItem(201L)).thenReturn(Optional.of(item(
                201L,
                "OpenAI agent platform launches",
                "https://example.com/openai-agent",
                190,
                41
        )));
        when(hackerNewsClient.fetchItem(202L)).thenReturn(Optional.of(item(
                202L,
                "Agent platform walkthrough",
                "https://example.com/openai-agent?ref=hn",
                120,
                18
        )));

        crawlExecutionService.executeManual(source.id(), "phase6-alert-flow-1");

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "OpenAI Agent Watch",
                                  "enabled": true,
                                  "keywords": ["OpenAI", "agent"],
                                  "sourceTypes": ["HACKER_NEWS"],
                                  "minScore": 10,
                                  "suppressWindowHours": 24
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("OpenAI Agent Watch"))
                .andExpect(jsonPath("$.data.keywords[0]").value("openai"));

        mockMvc.perform(post("/api/v1/alerts/matching-runs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.scannedClusterCount").value(1))
                .andExpect(jsonPath("$.data.matchedRuleCount").value(1))
                .andExpect(jsonPath("$.data.createdAlertCount").value(1))
                .andExpect(jsonPath("$.data.suppressedAlertCount").value(0));

        mockMvc.perform(post("/api/v1/alerts/matching-runs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAlertCount").value(0))
                .andExpect(jsonPath("$.data.suppressedAlertCount").value(1));

        assertThat(count("alert_record")).isEqualTo(1);

        mockMvc.perform(get("/api/v1/alerts")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].subscriptionName").value("OpenAI Agent Watch"))
                .andExpect(jsonPath("$.data.items[0].status").value("NEW"))
                .andExpect(jsonPath("$.data.items[0].matchReason.matchedKeywords[0]").value("openai"))
                .andExpect(jsonPath("$.data.items[0].hotClusterTitle").value("OpenAI agent platform launches"));

        long alertId = jdbcTemplate.queryForObject("SELECT id FROM alert_record", Long.class);
        mockMvc.perform(patch("/api/v1/alerts/{alertId}/status", alertId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACKED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKED"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM alert_record", String.class)).isEqualTo("ACKED");
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
                "phase6-test",
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
