package com.airadar.analysis;

import com.airadar.analysis.client.ClusterEvidencePack;
import com.airadar.analysis.client.StructuredAnalysisModelClient;
import com.airadar.analysis.client.openai.OpenAiStructuredAnalysisClient;
import com.airadar.analysis.vo.AnalysisEvidenceRefVO;
import com.airadar.analysis.vo.StructuredAnalysisResultVO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the default {@code provider=openai} path without making real
 * OpenAI calls. Covers two cases required by Phase 10:
 *
 * <ul>
 *   <li>API key missing → FAILED with ANALYSIS_PROVIDER_NOT_CONFIGURED persisted.</li>
 *   <li>Mocked provider returns a structured result → SUCCEEDED persisted and
 *       readable via GET /analysis.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OpenAiAnalysisProviderIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private HackerNewsClient hackerNewsClient;

    @MockitoBean
    private OpenAiStructuredAnalysisClient openAiStructuredAnalysisClient;

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
    void shouldRecordFailedRunWhenOpenAiProviderNotConfigured() throws Exception {
        // openAiStructuredAnalysisClient is not stubbed here, so it defaults to
        // Mockito's "smart-return" for the real bean: the real bean has a null
        // OpenAIClient because no API key is set, which means invoking analyze()
        // would normally throw ANALYSIS_PROVIDER_NOT_CONFIGURED. Because
        // @MockitoBean replaces the bean with a Mockito mock that returns null
        // by default, we stub it explicitly to throw the same exception the
        // real unconfigured bean would throw.
        when(openAiStructuredAnalysisClient.analyze(any(ClusterEvidencePack.class)))
                .thenThrow(new com.airadar.analysis.client.AnalysisProviderException(
                        com.airadar.common.exception.ErrorCode.ANALYSIS_PROVIDER_NOT_CONFIGURED,
                        "OpenAI analysis provider is not configured. Set AI_RADAR_OPENAI_API_KEY to enable it."
                ));

        long clusterId = prepareCluster();

        mockMvc.perform(post("/api/v1/hot-clusters/{clusterId}/analysis-runs", clusterId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hotClusterId").value(clusterId))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.modelProvider").value("openai"))
                .andExpect(jsonPath("$.data.failureCode").value("ANALYSIS_PROVIDER_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.failureMessage").value(
                        org.hamcrest.Matchers.containsString("AI_RADAR_OPENAI_API_KEY")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT failure_code FROM cluster_analysis", String.class
        )).isEqualTo("ANALYSIS_PROVIDER_NOT_CONFIGURED");
    }

    @Test
    void shouldPersistAndReadStructuredResultFromOpenAiProvider() throws Exception {
        StructuredAnalysisResultVO stubResult = new StructuredAnalysisResultVO(
                "Stub headline from OpenAI provider",
                "Stub brief backed by evidence.",
                "Why this cluster matters: cross-source signal.",
                List.of("Signal one.", "Signal two."),
                List.of(new AnalysisEvidenceRefVO(
                        101L,
                        SourceType.HACKER_NEWS,
                        "OpenAI launches an agent framework",
                        "https://example.com/agent"
                )),
                List.of("Single-day window may shift."),
                List.of("Compare next crawl."),
                "HIGH"
        );
        when(openAiStructuredAnalysisClient.analyze(any(ClusterEvidencePack.class)))
                .thenReturn(stubResult);

        long clusterId = prepareCluster();

        mockMvc.perform(post("/api/v1/hot-clusters/{clusterId}/analysis-runs", clusterId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.modelProvider").value("openai"))
                .andExpect(jsonPath("$.data.modelName").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$.data.result.headline").value("Stub headline from OpenAI provider"))
                .andExpect(jsonPath("$.data.result.confidence").value("HIGH"));

        mockMvc.perform(get("/api/v1/hot-clusters/{clusterId}/analysis", clusterId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.modelProvider").value("openai"))
                .andExpect(jsonPath("$.data.result.evidenceRefs[0].sourceType").value("HACKER_NEWS"));
    }

    private long prepareCluster() throws Exception {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-openai-provider-test",
                SourceType.HACKER_NEWS,
                "Hacker News OpenAI Provider Test",
                true,
                null,
                Map.of(
                        "feed", "TOP",
                        "fetchLimit", 1,
                        "keywords", List.of("AI")
                )
        ));
        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(101L));
        when(hackerNewsClient.fetchItem(101L)).thenReturn(Optional.of(item(
                101L,
                "OpenAI launches an agent framework",
                "https://example.com/agent",
                180,
                42
        )));

        crawlExecutionService.executeManual(source.id(), "phase10-openai-provider-" + System.nanoTime());
        return jdbcTemplate.queryForObject("SELECT id FROM hot_cluster", Long.class);
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
                "phase10-test",
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
}
