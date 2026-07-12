package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.sogou.FetchedSogouSearchResult;
import com.airadar.crawl.client.sogou.SogouSearchClient;
import com.airadar.crawl.client.sogou.SogouSearchRequest;
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
class SogouSearchRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private SogouSearchClient sogouSearchClient;

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
    void shouldNormalizeSogouSearchResultsIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "sogou-ai-search",
                SourceType.SOGOU_SEARCH,
                "Sogou AI Search",
                true,
                null,
                Map.of(
                        "query", "大模型 OR 智能体",
                        "cnt", 20,
                        "mode", 0,
                        "site", "",
                        "freshness", "d1"
                )
        ));
        when(sogouSearchClient.search(any(SogouSearchRequest.class))).thenReturn(List.of(
                result("大模型发展报告", "https://example.com/article1", "大模型最新进展", "示例站", 0.95, 1),
                result("AI智能体应用", "https://example.com/article2", "智能体技术", "科技网", 0.80, 2)
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12a-sogou-raw-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(2);
        assertThat(task.persistedCount()).isEqualTo(2);
        assertThat(task.matchedCount()).isEqualTo(2);
        assertThat(task.failedCount()).isZero();
        assertThat(count("raw_item")).isEqualTo(2);
        assertThat(count("hot_item")).isEqualTo(2);
        assertThat(count("hot_cluster")).isEqualTo(2);

        String externalId = externalIdFor("https://example.com/article1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_type FROM raw_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("SOGOU_SEARCH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'query' FROM raw_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("大模型 OR 智能体");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("SEARCH_RESULT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("示例站");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'rank' FROM hot_item WHERE external_id = ?",
                String.class,
                externalId
        )).isEqualTo("1");

        PageResponse<?> page = hotClusterQueryService.list(
                1, 20, HotClusterSort.SCORE_DESC, SourceType.SOGOU_SEARCH, null, null
        );
        assertThat(page.totalElements()).isEqualTo(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.SOGOU_SEARCH);

        verify(sogouSearchClient).search(any(SogouSearchRequest.class));
    }

    private FetchedSogouSearchResult result(String title, String url, String passage, String site, double score, int rank) {
        return new FetchedSogouSearchResult(
                title, url, passage, null, site, score, Instant.parse("2026-07-12T10:00:00Z"), rank
        );
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
