package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.weibo.FetchedWeiboHotTopic;
import com.airadar.crawl.client.weibo.WeiboHotSearchClient;
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
class WeiboHotSearchRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private WeiboHotSearchClient weiboHotSearchClient;

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
    void shouldNormalizeWeiboHotSearchResultsIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "weibo-ai-hot",
                SourceType.WEIBO_HOT_SEARCH,
                "Weibo AI Hot Search",
                true,
                null,
                Map.of(
                        "query", "AI",
                        "includeTopWhenNoMatch", false
                )
        ));
        when(weiboHotSearchClient.fetchHotSearch()).thenReturn(List.of(
                new FetchedWeiboHotTopic("AI大模型发展", "最新技术突破", 2500000L, "科技", "123456", 1200000L, 1),
                new FetchedWeiboHotTopic("AI智能助手应用", "AI用户体验提升", 1800000L, "科技", "234567", 900000L, 2)
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-weibo-raw-1");

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
        )).isEqualTo("WEIBO_HOT_SEARCH");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'word' FROM raw_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("AI大模型发展");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("TREND");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("微博热搜");

        PageResponse<?> page = hotClusterQueryService.list(
                1, 20, HotClusterSort.SCORE_DESC, SourceType.WEIBO_HOT_SEARCH, null, null
        );
        assertThat(page.totalElements()).isEqualTo(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.WEIBO_HOT_SEARCH);

        verify(weiboHotSearchClient).fetchHotSearch();
    }

    @Test
    void shouldFilterOutTopicsNotMatchingQuery() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "weibo-ai-hot-filter",
                SourceType.WEIBO_HOT_SEARCH,
                "Weibo AI Hot Search Filter",
                true,
                null,
                Map.of(
                        "query", "AI",
                        "includeTopWhenNoMatch", false
                )
        ));
        when(weiboHotSearchClient.fetchHotSearch()).thenReturn(List.of(
                new FetchedWeiboHotTopic("AI大模型发展", "最新技术突破", 2500000L, "科技", "123456", 1200000L, 1),
                new FetchedWeiboHotTopic("娱乐圈新闻", "明星动态", 3000000L, "娱乐", "345678", 1500000L, 2)
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-weibo-filter-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(1); // Only AI-related topic
        assertThat(task.persistedCount()).isEqualTo(1);
        assertThat(count("raw_item")).isEqualTo(1);
        assertThat(count("hot_item")).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'word' FROM raw_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("AI大模型发展");
    }

    @Test
    void shouldIncludeTopTopicsWhenNoMatchesAndConfigured() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "weibo-hot-fallback",
                SourceType.WEIBO_HOT_SEARCH,
                "Weibo Hot Search Fallback",
                true,
                null,
                Map.of(
                        "query", "nonexistent",
                        "includeTopWhenNoMatch", true
                )
        ));
        when(weiboHotSearchClient.fetchHotSearch()).thenReturn(List.of(
                new FetchedWeiboHotTopic("娱乐新闻", "明星动态", 3000000L, "娱乐", "345678", 1500000L, 1),
                new FetchedWeiboHotTopic("体育新闻", "比赛结果", 2000000L, "体育", "456789", 1000000L, 2),
                new FetchedWeiboHotTopic("科技新闻", "新产品发布", 1500000L, "科技", "567890", 750000L, 3)
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-weibo-fallback-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(3); // Top 3 topics included
        assertThat(task.persistedCount()).isEqualTo(3);
        assertThat(count("raw_item")).isEqualTo(3);
        assertThat(count("hot_item")).isEqualTo(3);
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
