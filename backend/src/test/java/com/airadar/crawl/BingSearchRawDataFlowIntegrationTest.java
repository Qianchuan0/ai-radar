package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.bing.BingSearchClient;
import com.airadar.crawl.client.bing.BingSearchRequest;
import com.airadar.crawl.client.bing.FetchedBingSearchResult;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
class BingSearchRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private BingSearchClient bingSearchClient;

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
    void shouldNormalizeBingSearchResultsIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "bing-ai-search",
                SourceType.BING_SEARCH,
                "Bing AI Search",
                true,
                null,
                Map.of(
                        "query", "AI agent",
                        "limit", 10,
                        "market", "en-US",
                        "freshnessDays", 7,
                        "safeSearch", "moderate"
                )
        ));

        when(bingSearchClient.search(any(BingSearchRequest.class))).thenReturn(List.of(
                result("AI Agent Development Guide", "https://example.com/ai-agent-guide", "Learn how to build AI agents", "example.com", 1),
                result("Advanced Agent Frameworks", "https://example.com/agent-frameworks", "Comparison of agent frameworks", "tech.io", 2)
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-2-bing-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(2);
        assertThat(task.persistedCount()).isEqualTo(2);
        assertThat(task.matchedCount()).isEqualTo(2);
        assertThat(task.failedCount()).isZero();
        assertThat(count("raw_item")).isEqualTo(2);
        assertThat(count("hot_item")).isEqualTo(2);
        assertThat(count("hot_cluster")).isEqualTo(2);

        String externalId = externalIdFor("https://example.com/ai-agent-guide");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_type FROM raw_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("BING_SEARCH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'query' FROM raw_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("AI agent");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("WEB_PAGE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'rank' FROM hot_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("1");

        PageResponse<?> page = hotClusterQueryService.list(
                1, 20, HotClusterSort.SCORE_DESC, SourceType.BING_SEARCH, null, null
        );
        assertThat(page.totalElements()).isEqualTo(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.BING_SEARCH);

        verify(bingSearchClient).search(any(BingSearchRequest.class));
    }

    private FetchedBingSearchResult result(String title, String url, String snippet, String displayUrl, int rank) {
        return new FetchedBingSearchResult(title, url, snippet, displayUrl, rank);
    }

    private String externalIdFor(String url) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
