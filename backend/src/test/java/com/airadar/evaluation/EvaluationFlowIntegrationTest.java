package com.airadar.evaluation;

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
import org.springframework.test.web.servlet.MvcResult;
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
class EvaluationFlowIntegrationTest {

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
                    evaluation_case_result,
                    evaluation_run,
                    evaluation_case,
                    evaluation_dataset,
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
    void shouldRunEvaluationWithPassedFailedAndErrorCases() throws Exception {
        long clusterId = seedCrawledCluster();
        String externalId = jdbcTemplate.queryForObject(
                "SELECT external_id FROM hot_item WHERE source_type = 'HACKER_NEWS' ORDER BY id LIMIT 1",
                String.class
        );

        long datasetId = createDataset("phase8-dataset");
        createCase(datasetId, "crawl-present-true", "CRAWL_ITEM_PRESENT",
                target("sourceType", "HACKER_NEWS", "externalId", externalId),
                expected("present", true));
        createCase(datasetId, "crawl-present-missing", "CRAWL_ITEM_PRESENT",
                target("sourceType", "HACKER_NEWS", "externalId", "does-not-exist-9999"),
                expected("present", true));
        createCase(datasetId, "crawl-absent-true", "CRAWL_ITEM_PRESENT",
                target("sourceType", "HACKER_NEWS", "externalId", "does-not-exist-9999"),
                expected("present", false));
        createCase(datasetId, "score-floor-pass", "SCORE_THRESHOLD",
                target("hotClusterId", clusterId),
                expected("minTotalScore", 0));
        createCase(datasetId, "score-ceiling-fail", "SCORE_THRESHOLD",
                target("hotClusterId", clusterId),
                expected("minTotalScore", 999999));
        createCase(datasetId, "analysis-fields-error", "ANALYSIS_REQUIRED_FIELDS",
                target("hotClusterId", clusterId),
                expectedFields("headline", "brief"));

        mockMvc.perform(post("/api/v1/evaluation/runs").param("datasetId", String.valueOf(datasetId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasetId").value(datasetId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalCases").value(6))
                .andExpect(jsonPath("$.data.passedCases").value(3))
                .andExpect(jsonPath("$.data.failedCases").value(2))
                .andExpect(jsonPath("$.data.errorCases").value(1));

        Long runId = jdbcTemplate.queryForObject(
                "SELECT id FROM evaluation_run WHERE dataset_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                datasetId
        );

        MvcResult metricsResult = mockMvc.perform(get("/api/v1/evaluation/runs/{runId}", runId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode runDetail = objectMapper.readTree(metricsResult.getResponse().getContentAsString()).get("data");

        assertThat(runDetail.get("totalCases").asInt()).isEqualTo(6);
        assertThat(runDetail.get("passedCases").asInt()).isEqualTo(3);
        assertThat(runDetail.get("failedCases").asInt()).isEqualTo(2);
        assertThat(runDetail.get("errorCases").asInt()).isEqualTo(1);
        assertThat(runDetail.get("metricsPayload").get("passRate").asDouble()).isEqualTo(0.5);
        assertThat(runDetail.get("metricsPayload").get("byCaseType").get("CRAWL_ITEM_PRESENT").get("total").asInt())
                .isEqualTo(3);
        assertThat(runDetail.get("metricsPayload").get("byCaseType").get("SCORE_THRESHOLD").get("failed").asInt())
                .isEqualTo(1);
        assertThat(runDetail.get("errorAnalysisPayload").get("failedCases").isArray()).isTrue();
        assertThat(runDetail.get("errorAnalysisPayload").get("failedCases").size()).isEqualTo(2);
        assertThat(runDetail.get("errorAnalysisPayload").get("errorCases").size()).isEqualTo(1);
        assertThat(runDetail.get("caseResults").size()).isEqualTo(6);

        mockMvc.perform(get("/api/v1/evaluation/datasets")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].caseCount").value(6));

        mockMvc.perform(get("/api/v1/evaluation/datasets/{datasetId}/cases", datasetId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));

        mockMvc.perform(get("/api/v1/evaluation/runs")
                        .param("datasetId", String.valueOf(datasetId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));
    }

    @Test
    void shouldRejectRunOnEmptyDataset() throws Exception {
        long datasetId = createDataset("phase8-empty-dataset");

        mockMvc.perform(post("/api/v1/evaluation/runs").param("datasetId", String.valueOf(datasetId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundForMissingRun() throws Exception {
        mockMvc.perform(get("/api/v1/evaluation/runs/{runId}", 999999L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private long seedCrawledCluster() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-eval-source",
                SourceType.HACKER_NEWS,
                "Hacker News Evaluation Source",
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
                "OpenAI agent framework launch",
                "https://example.com/openai-agent-framework",
                220,
                60
        )));
        when(hackerNewsClient.fetchItem(402L)).thenReturn(Optional.of(item(
                402L,
                "Agent framework discussion thread",
                "https://example.com/openai-agent-framework?ref=hn",
                150,
                25
        )));
        crawlExecutionService.executeManual(source.id(), "phase8-eval-seed");
        return jdbcTemplate.queryForObject("SELECT id FROM hot_cluster", Long.class);
    }

    private long createDataset(String name) throws Exception {
        String body = """
                {"name":"%s","description":"phase8 dataset","enabled":true}
                """.formatted(name);
        MvcResult result = mockMvc.perform(post("/api/v1/evaluation/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private void createCase(
            long datasetId,
            String caseCode,
            String caseType,
            JsonNode targetPayload,
            JsonNode expectedPayload
    ) throws Exception {
        var requestNode = objectMapper.createObjectNode();
        requestNode.put("caseCode", caseCode);
        requestNode.put("caseType", caseType);
        requestNode.set("targetPayload", targetPayload);
        requestNode.set("expectedPayload", expectedPayload);
        requestNode.put("enabled", true);
        mockMvc.perform(post("/api/v1/evaluation/datasets/{datasetId}/cases", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestNode))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    private JsonNode target(Object... pairs) {
        return buildObject(pairs);
    }

    private JsonNode expected(Object... pairs) {
        return buildObject(pairs);
    }

    private JsonNode expectedFields(String... fields) {
        var node = objectMapper.createObjectNode();
        var array = objectMapper.createArrayNode();
        for (String field : fields) {
            array.add(field);
        }
        node.set("fields", array);
        return node;
    }

    private JsonNode buildObject(Object... pairs) {
        var node = objectMapper.createObjectNode();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof Number n) {
                node.put(key, n.doubleValue());
            } else if (value instanceof Boolean b) {
                node.put(key, b);
            } else {
                node.put(key, String.valueOf(value));
            }
        }
        return node;
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
                "phase8-test",
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
