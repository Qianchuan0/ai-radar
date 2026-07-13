package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.hackernewssearch.FetchedHackerNewsSearchHit;
import com.airadar.crawl.client.hackernewssearch.HackerNewsSearchClient;
import com.airadar.crawl.client.hackernewssearch.HackerNewsSearchRequest;
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
class HackerNewsSearchRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private HackerNewsSearchClient hackerNewsSearchClient;

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
    void shouldNormalizeHackerNewsSearchResultsIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-ai-search",
                SourceType.HACKER_NEWS_SEARCH,
                "Hacker News AI Search",
                true,
                null,
                Map.of(
                        "query", "AI",
                        "limit", 20,
                        "freshnessHours", 24
                )
        ));
        Instant since = Instant.now().minusSeconds(86400);
        when(hackerNewsSearchClient.search(any(HackerNewsSearchRequest.class))).thenReturn(List.of(
                new FetchedHackerNewsSearchHit(
                        "123456",
                        "AI大模型最新进展",
                        "https://example.com/ai-models",
                        "详细介绍AI大模型的技术突破",
                        "techuser",
                        150,
                        42,
                        Instant.parse("2026-07-12T10:00:00Z"),
                        1720473600L
                ),
                new FetchedHackerNewsSearchHit(
                        "234567",
                        "机器学习框架对比",
                        "https://example.com/ml-frameworks",
                        "深度分析主流机器学习框架",
                        "mldev",
                        89,
                        25,
                        Instant.parse("2026-07-12T09:00:00Z"),
                        1720387200L
                )
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-hn-search-raw-1");

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
                "123456"
        )).isEqualTo("HACKER_NEWS_SEARCH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'title' FROM raw_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("AI大模型最新进展");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("ARTICLE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("techuser");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'points' FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("150");

        PageResponse<?> page = hotClusterQueryService.list(
                1, 20, HotClusterSort.SCORE_DESC, SourceType.HACKER_NEWS_SEARCH, null, null
        );
        assertThat(page.totalElements()).isEqualTo(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.HACKER_NEWS_SEARCH);

        verify(hackerNewsSearchClient).search(any(HackerNewsSearchRequest.class));
    }

    @Test
    void shouldUseFallbackUrlWhenOriginalUrlMissing() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-search-fallback",
                SourceType.HACKER_NEWS_SEARCH,
                "Hacker News Search Fallback",
                true,
                null,
                Map.of(
                        "query", "HN",
                        "limit", 20,
                        "freshnessHours", 24
                )
        ));
        when(hackerNewsSearchClient.search(any(HackerNewsSearchRequest.class))).thenReturn(List.of(
                new FetchedHackerNewsSearchHit(
                        "345678",
                        "HN Discussion Only",
                        null,
                        "No external link",
                        "hacker",
                        50,
                        10,
                        Instant.parse("2026-07-12T10:00:00Z"),
                        1720300800L
                )
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-hn-fallback-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(1);
        assertThat(task.persistedCount()).isEqualTo(1);
        assertThat(count("raw_item")).isEqualTo(1);
        assertThat(count("hot_item")).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_url FROM hot_item WHERE external_id = ?",
                String.class,
                "345678"
        )).isEqualTo("https://news.ycombinator.com/item?id=345678");
    }

    @Test
    void shouldHandleEmptyResults() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-search-empty",
                SourceType.HACKER_NEWS_SEARCH,
                "Hacker News Search Empty",
                true,
                null,
                Map.of(
                        "query", "nonexistent",
                        "limit", 20,
                        "freshnessHours", 24
                )
        ));
        when(hackerNewsSearchClient.search(any(HackerNewsSearchRequest.class))).thenReturn(List.of());

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-hn-empty-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isZero();
        assertThat(task.persistedCount()).isZero();
        assertThat(count("raw_item")).isZero();
        assertThat(count("hot_item")).isZero();
        assertThat(count("hot_cluster")).isZero();
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
