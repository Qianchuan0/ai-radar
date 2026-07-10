package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.arxiv.ArxivClient;
import com.airadar.crawl.client.arxiv.ArxivSearchRequest;
import com.airadar.crawl.client.arxiv.FetchedArxivPaper;
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
class ArxivRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private ArxivClient arxivClient;

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
    void shouldNormalizeArxivRawItemsIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "arxiv-agents",
                SourceType.ARXIV,
                "arXiv Agents",
                true,
                null,
                Map.of(
                        "searchQuery", "cat:cs.AI AND all:agent",
                        "start", 0,
                        "maxResults", 2,
                        "sortBy", "SUBMITTED_DATE",
                        "sortOrder", "DESCENDING"
                )
        ));
        when(arxivClient.search(any(ArxivSearchRequest.class))).thenReturn(List.of(
                paper("2501.01234v2", "Agentic Systems for Reliable Tool Use", "http://arxiv.org/abs/2501.01234v2?utm_source=test"),
                paper("2501.05678v1", "Structured Reasoning for Tool-Using LLMs")
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase4-arxiv-raw-1");

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
                "2501.01234v2"
        )).isEqualTo("ARXIV");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'title' FROM raw_item WHERE external_id = ?",
                String.class,
                "2501.01234v2"
        )).isEqualTo("Agentic Systems for Reliable Tool Use");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                "2501.01234v2"
        )).isEqualTo("PAPER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_url FROM hot_item WHERE external_id = ?",
                String.class,
                "2501.01234v2"
        )).isEqualTo("https://arxiv.org/abs/2501.01234v2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                "2501.01234v2"
        )).isEqualTo("Alice Zhang");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary FROM hot_item WHERE external_id = ?",
                String.class,
                "2501.01234v2"
        )).isEqualTo("A paper about agent systems.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT tags::text FROM hot_item WHERE external_id = ?",
                String.class,
                "2501.01234v2"
        )).contains("cs.AI").contains("cs.LG");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'authorsCount' FROM hot_item WHERE external_id = ?",
                String.class,
                "2501.01234v2"
        )).isEqualTo("2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'pdfUrl' FROM hot_item WHERE external_id = ?",
                String.class,
                "2501.01234v2"
        )).isEqualTo("https://arxiv.org/pdf/2501.01234v2");

        PageResponse<?> page = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                SourceType.ARXIV,
                null,
                null
        );
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.ARXIV);

        verify(arxivClient).search(any(ArxivSearchRequest.class));
    }

    private FetchedArxivPaper paper(String arxivId, String title) {
        return paper(arxivId, title, "http://arxiv.org/abs/" + arxivId);
    }

    private FetchedArxivPaper paper(String arxivId, String title, String sourceUrl) {
        return new FetchedArxivPaper(
                arxivId,
                title,
                "A paper about agent systems.",
                List.of("Alice Zhang", "Bob Li"),
                List.of("cs.AI", "cs.LG"),
                Instant.parse("2026-07-01T08:30:00Z"),
                "http://arxiv.org/pdf/" + arxivId,
                sourceUrl
        );
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
