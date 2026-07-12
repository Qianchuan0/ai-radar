package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.huggingface.FetchedHuggingFaceModel;
import com.airadar.crawl.client.huggingface.HuggingFaceClient;
import com.airadar.crawl.client.huggingface.HuggingFaceModelsRequest;
import com.airadar.crawl.service.CrawlExecutionService;
import com.airadar.crawl.vo.CrawlTaskVO;
import com.airadar.source.dto.CreateSourceRequest;
import com.airadar.source.model.SourceType;
import com.airadar.source.service.SourceConfigService;
import com.airadar.source.vo.SourceConfigVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
class HuggingFaceRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private HuggingFaceClient huggingFaceClient;

    @Autowired
    private SourceConfigService sourceConfigService;

    @Autowired
    private CrawlExecutionService crawlExecutionService;

    @Autowired
    private HotClusterQueryService hotClusterQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void shouldNormalizeHuggingFaceModelsIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "huggingface-text-generation",
                SourceType.HUGGING_FACE,
                "Hugging Face Text Generation",
                true,
                null,
                Map.of(
                        "search", "text-generation",
                        "sort", "downloads",
                        "direction", "desc",
                        "limit", 2,
                        "pipelineTag", "text-generation"
                )
        ));
        when(huggingFaceClient.searchModels(any(HuggingFaceModelsRequest.class))).thenReturn(List.of(
                model("meta-llama/Llama-3.1-8B-Instruct", 987654, 3210),
                model("Qwen/Qwen2.5-Coder-7B-Instruct", 456789, 2100)
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase9a-huggingface-raw-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(2);
        assertThat(task.persistedCount()).isEqualTo(2);
        assertThat(task.matchedCount()).isEqualTo(2);
        assertThat(task.failedCount()).isZero();
        assertThat(count("raw_item")).isEqualTo(2);
        assertThat(count("hot_item")).isEqualTo(2);
        assertThat(count("hot_cluster")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_type FROM raw_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).isEqualTo("HUGGING_FACE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'pipelineTag' FROM raw_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).isEqualTo("text-generation");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).isEqualTo("MODEL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_url FROM hot_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).isEqualTo("https://huggingface.co/meta-llama/Llama-3.1-8B-Instruct");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).isEqualTo("meta-llama");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary FROM hot_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).contains("Pipeline: text-generation");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT tags::text FROM hot_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).contains("text-generation").contains("transformers");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'downloads' FROM hot_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).isEqualTo("987654");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'likes' FROM hot_item WHERE external_id = ?",
                String.class,
                "meta-llama/Llama-3.1-8B-Instruct"
        )).isEqualTo("3210");

        PageResponse<?> page = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                SourceType.HUGGING_FACE,
                null,
                null
        );
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.HUGGING_FACE);

        verify(huggingFaceClient).searchModels(any(HuggingFaceModelsRequest.class));
    }

    private FetchedHuggingFaceModel model(String modelId, int downloads, int likes) {
        return new FetchedHuggingFaceModel(
                modelId,
                modelId,
                downloads,
                likes,
                List.of("transformers", "text-generation", "safetensors"),
                "text-generation",
                modelId.substring(0, modelId.indexOf('/')),
                "transformers",
                Instant.parse("2026-07-08T10:15:30Z"),
                Instant.parse("2026-07-09T09:00:00Z"),
                false
        );
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
