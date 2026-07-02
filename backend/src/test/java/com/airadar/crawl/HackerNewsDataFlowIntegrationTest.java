package com.airadar.crawl;

import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.hackernews.FetchedHackerNewsItem;
import com.airadar.crawl.client.hackernews.HackerNewsClient;
import com.airadar.crawl.client.hackernews.HackerNewsItemResponse;
import com.airadar.crawl.service.CrawlExecutionService;
import com.airadar.crawl.vo.CrawlTaskVO;
import com.airadar.source.dto.CreateSourceRequest;
import com.airadar.source.model.SourceType;
import com.airadar.source.service.SourceConfigService;
import com.airadar.source.vo.SourceConfigVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
class HackerNewsDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private HackerNewsClient hackerNewsClient;

    @Autowired
    private SourceConfigService sourceConfigService;

    @Autowired
    private CrawlExecutionService crawlExecutionService;

    @Autowired
    private HotClusterQueryService hotClusterQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
    void shouldCloseHackerNewsDataFlowAndReuseIdempotencyKey() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "hn-top-ai",
                SourceType.HACKER_NEWS,
                "Hacker News Top AI",
                true,
                null,
                Map.of(
                        "feed", "TOP",
                        "fetchLimit", 3,
                        "keywords", List.of("AI", "agent")
                )
        ));
        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(101L, 102L, 103L));
        when(hackerNewsClient.fetchItem(101L)).thenReturn(Optional.of(item(
                101L,
                "OpenAI launches an agent framework",
                "https://example.com/agent?utm_source=hn",
                180,
                42
        )));
        when(hackerNewsClient.fetchItem(102L)).thenReturn(Optional.of(item(
                102L,
                "A guide to growing tomatoes",
                "https://example.com/garden",
                80,
                12
        )));
        when(hackerNewsClient.fetchItem(103L)).thenReturn(Optional.of(item(
                103L,
                "AI agent framework discussion",
                "https://example.com/agent?utm_medium=social",
                120,
                30
        )));

        CrawlTaskVO first = crawlExecutionService.executeManual(source.id(), "phase2-flow-1");
        CrawlTaskVO duplicate = crawlExecutionService.executeManual(source.id(), "phase2-flow-1");

        assertThat(first.status().name()).isEqualTo("SUCCEEDED");
        assertThat(first.fetchedCount()).isEqualTo(3);
        assertThat(first.persistedCount()).isEqualTo(3);
        assertThat(first.matchedCount()).isEqualTo(2);
        assertThat(first.failedCount()).isZero();
        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(count("raw_item")).isEqualTo(3);
        assertThat(count("hot_item")).isEqualTo(2);
        assertThat(count("hot_cluster")).isEqualTo(1);
        assertThat(count("hot_cluster_item")).isEqualTo(2);
        assertThat(count("hot_score")).isEqualTo(2);
        assertThat(count("crawl_task")).isEqualTo(1);

        PageResponse<?> page = hotClusterQueryService.list(1, 20, SourceType.HACKER_NEWS, null, null);
        assertThat(page.totalElements()).isEqualTo(1);
        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster", Long.class)
        );
        assertThat(detail.itemCount()).isEqualTo(2);
        assertThat(detail.items()).hasSize(2);
        assertThat(detail.score()).isNotNull();
        assertThat(detail.score().components()).isNotNull();
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
                "phase2-test",
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
