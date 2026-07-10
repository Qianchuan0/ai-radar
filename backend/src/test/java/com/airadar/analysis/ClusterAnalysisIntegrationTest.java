package com.airadar.analysis;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClusterAnalysisIntegrationTest {

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
    void shouldGeneratePersistAndReadLatestClusterAnalysis() throws Exception {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-analysis-ai",
                SourceType.HACKER_NEWS,
                "Hacker News Analysis AI",
                true,
                null,
                Map.of(
                        "feed", "TOP",
                        "fetchLimit", 2,
                        "keywords", List.of("AI", "agent")
                )
        ));
        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(101L, 103L));
        when(hackerNewsClient.fetchItem(101L)).thenReturn(Optional.of(item(
                101L,
                "OpenAI launches an agent framework",
                "https://example.com/agent?utm_source=hn",
                180,
                42
        )));
        when(hackerNewsClient.fetchItem(103L)).thenReturn(Optional.of(item(
                103L,
                "AI agent framework discussion",
                "https://example.com/agent?utm_medium=social",
                120,
                30
        )));

        crawlExecutionService.executeManual(source.id(), "phase5-analysis-flow-1");
        long clusterId = jdbcTemplate.queryForObject("SELECT id FROM hot_cluster", Long.class);

        mockMvc.perform(post("/api/v1/hot-clusters/{clusterId}/analysis-runs", clusterId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.hotClusterId").value(clusterId))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.analysisType").value("CLUSTER_BRIEF"))
                .andExpect(jsonPath("$.data.result.headline").value("OpenAI launches an agent framework"))
                .andExpect(jsonPath("$.data.result.keySignals[0]").exists())
                .andExpect(jsonPath("$.data.result.evidenceRefs[0].sourceType").value("HACKER_NEWS"));

        assertThat(count("cluster_analysis")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM cluster_analysis", String.class)).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT result_payload ->> 'headline' FROM cluster_analysis",
                String.class
        )).isEqualTo("OpenAI launches an agent framework");

        mockMvc.perform(get("/api/v1/hot-clusters/{clusterId}/analysis", clusterId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.hotClusterId").value(clusterId))
                .andExpect(jsonPath("$.data.result.brief").exists())
                .andExpect(jsonPath("$.data.result.evidenceRefs[0].title").value("OpenAI launches an agent framework"));
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
                "phase5-test",
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
