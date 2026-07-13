package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.twitter.FetchedTweet;
import com.airadar.crawl.client.twitter.TwitterClient;
import com.airadar.crawl.client.twitter.TwitterSearchRequest;
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
class TwitterRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private TwitterClient twitterClient;

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
    void shouldNormalizeTwitterResultsIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "twitter-ai",
                SourceType.TWITTER,
                "Twitter AI Search",
                true,
                null,
                Map.of(
                        "query", "AI programming",
                        "limit", 20,
                        "topDays", 7,
                        "latestDays", 3,
                        "minLikes", 10,
                        "minRetweets", 5,
                        "minViews", 500,
                        "minFollowers", 100,
                        "onlyOriginalTweets", true
                )
        ));
        Instant topSince = Instant.now().minusSeconds(86400 * 7);
        Instant latestSince = Instant.now().minusSeconds(86400 * 3);
        when(twitterClient.search(any(TwitterSearchRequest.class))).thenReturn(List.of(
                new FetchedTweet(
                        "123456",
                        "AI大模型最新突破！这是一条很长的推文内容，用于测试标题截断功能，确保只保留前100个字符作为标题。",
                        "Tech User",
                        "techuser",
                        5000L,
                        true,
                        150,
                        25,
                        10,
                        5,
                        15000L,
                        "2026-07-12T10:00:00Z"
                ),
                new FetchedTweet(
                        "234567",
                        "机器学习新框架发布 #MachineLearning #AI",
                        "ML Dev",
                        "mldev",
                        3000L,
                        false,
                        80,
                        15,
                        5,
                        2,
                        8000L,
                        "2026-07-12T09:00:00Z"
                )
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-twitter-raw-1");

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
        )).isEqualTo("TWITTER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'text' FROM raw_item WHERE external_id = ?",
                String.class,
                "123456"
        )).contains("AI大模型最新突破");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("POST");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("Tech User");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'likeCount' FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("150");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'authorVerified' FROM hot_item WHERE external_id = ?",
                String.class,
                "123456"
        )).isEqualTo("true");

        PageResponse<?> page = hotClusterQueryService.list(
                1, 20, HotClusterSort.SCORE_DESC, SourceType.TWITTER, null, null
        );
        assertThat(page.totalElements()).isEqualTo(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.TWITTER);

        verify(twitterClient).search(any(TwitterSearchRequest.class));
    }

    @Test
    void shouldExtractHashtagsAsTags() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "twitter-hashtags",
                SourceType.TWITTER,
                "Twitter Hashtags Test",
                true,
                null,
                Map.of(
                        "query", "AI",
                        "limit", 20,
                        "topDays", 7,
                        "latestDays", 3,
                        "minLikes", 10,
                        "minRetweets", 5,
                        "minViews", 500,
                        "minFollowers", 100,
                        "onlyOriginalTweets", true
                )
        ));
        when(twitterClient.search(any(TwitterSearchRequest.class))).thenReturn(List.of(
                new FetchedTweet(
                        "345678",
                        "Check out this AI project! #MachineLearning #DeepLearning #AI",
                        "AI Researcher",
                        "airesearcher",
                        1000L,
                        false,
                        50,
                        10,
                        5,
                        3,
                        5000L,
                        "2026-07-12T10:00:00Z"
                )
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-twitter-tags-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(1);
        assertThat(task.persistedCount()).isEqualTo(1);
        assertThat(count("hot_item")).isEqualTo(1);

        // Check that hashtags are extracted as tags
        String tags = jdbcTemplate.queryForObject(
                "SELECT tags FROM hot_item WHERE external_id = ?",
                String.class,
                "345678"
        );
        assertThat(tags).contains("#MachineLearning");
        assertThat(tags).contains("#DeepLearning");
        assertThat(tags).contains("#AI");
    }

    @Test
    void shouldUseAuthorUsernameWhenAuthorNameMissing() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "twitter-username-fallback",
                SourceType.TWITTER,
                "Twitter Username Fallback",
                true,
                null,
                Map.of(
                        "query", "test",
                        "limit", 20,
                        "topDays", 7,
                        "latestDays", 3,
                        "minLikes", 10,
                        "minRetweets", 5,
                        "minViews", 500,
                        "minFollowers", 100,
                        "onlyOriginalTweets", true
                )
        ));
        when(twitterClient.search(any(TwitterSearchRequest.class))).thenReturn(List.of(
                new FetchedTweet(
                        "456789",
                        "Test tweet",
                        "",
                        "fallbackuser",
                        500L,
                        false,
                        20,
                        5,
                        2,
                        0,
                        1000L,
                        "2026-07-12T10:00:00Z"
                )
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-twitter-username-1");

        assertThat(task.status().name()).isEqualTo("SUCCEEDED");
        assertThat(task.fetchedCount()).isEqualTo(1);
        assertThat(task.persistedCount()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                "456789"
        )).isEqualTo("fallbackuser");
    }

    @Test
    void shouldHandleEmptyResults() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "twitter-empty",
                SourceType.TWITTER,
                "Twitter Empty Results",
                true,
                null,
                Map.of(
                        "query", "nonexistent",
                        "limit", 20,
                        "topDays", 7,
                        "latestDays", 3,
                        "minLikes", 10,
                        "minRetweets", 5,
                        "minViews", 500,
                        "minFollowers", 100,
                        "onlyOriginalTweets", true
                )
        ));
        when(twitterClient.search(any(TwitterSearchRequest.class))).thenReturn(List.of());

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase12b-twitter-empty-1");

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
