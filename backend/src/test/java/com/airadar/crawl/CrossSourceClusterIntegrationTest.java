package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.cluster.vo.HotClusterSummaryVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.arxiv.ArxivClient;
import com.airadar.crawl.client.arxiv.ArxivSearchRequest;
import com.airadar.crawl.client.arxiv.FetchedArxivPaper;
import com.airadar.crawl.client.github.FetchedGitHubRepository;
import com.airadar.crawl.client.github.GitHubClient;
import com.airadar.crawl.client.github.GitHubSearchRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
class CrossSourceClusterIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean
    private HackerNewsClient hackerNewsClient;

    @MockitoBean
    private ArxivClient arxivClient;

    @MockitoBean
    private GitHubClient gitHubClient;

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
    void shouldClusterHackerNewsAndArxivEvidenceIntoSameHotCluster() {
        SourceConfigVO hnSource = sourceConfigService.create(new CreateSourceRequest(
                "hn-cross-source",
                SourceType.HACKER_NEWS,
                "HN Cross Source",
                true,
                null,
                Map.of(
                        "feed", "TOP",
                        "fetchLimit", 1,
                        "keywords", List.of("AI", "agent")
                )
        ));
        SourceConfigVO arxivSource = sourceConfigService.create(new CreateSourceRequest(
                "arxiv-cross-source",
                SourceType.ARXIV,
                "arXiv Cross Source",
                true,
                null,
                Map.of(
                        "searchQuery", "cat:cs.AI AND all:agent",
                        "start", 0,
                        "maxResults", 1,
                        "sortBy", "SUBMITTED_DATE",
                        "sortOrder", "DESCENDING"
                )
        ));

        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(101L));
        when(hackerNewsClient.fetchItem(101L)).thenReturn(Optional.of(hnItem(
                101L,
                "AI agent paper discussion",
                "https://arxiv.org/abs/2501.01234v2?utm_source=hn",
                180,
                42
        )));
        when(arxivClient.search(any(ArxivSearchRequest.class))).thenReturn(List.of(
                new FetchedArxivPaper(
                        "2501.01234v2",
                        "Agentic Systems for Reliable Tool Use",
                        "A paper about agent systems.",
                        List.of("Alice Zhang", "Bob Li"),
                        List.of("cs.AI", "cs.LG"),
                        Instant.parse("2026-07-01T08:30:00Z"),
                        "http://arxiv.org/pdf/2501.01234v2",
                        "http://arxiv.org/abs/2501.01234v2"
                )
        ));

        CrawlTaskVO hnTask = crawlExecutionService.executeManual(hnSource.id(), "phase4-cross-source-hn");
        CrawlTaskVO arxivTask = crawlExecutionService.executeManual(arxivSource.id(), "phase4-cross-source-arxiv");

        assertThat(hnTask.status().name()).isEqualTo("SUCCEEDED");
        assertThat(arxivTask.status().name()).isEqualTo("SUCCEEDED");
        assertThat(count("raw_item")).isEqualTo(2);
        assertThat(count("hot_item")).isEqualTo(2);
        assertThat(count("hot_cluster")).isEqualTo(1);
        assertThat(count("hot_cluster_item")).isEqualTo(2);

        PageResponse<HotClusterSummaryVO> allClusters = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                null,
                null,
                null
        );
        assertThat(allClusters.totalElements()).isEqualTo(1);
        HotClusterSummaryVO summary = allClusters.items().get(0);
        assertThat(summary.sourceTypes()).containsExactlyInAnyOrder(SourceType.HACKER_NEWS, SourceType.ARXIV);

        PageResponse<HotClusterSummaryVO> arxivClusters = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                SourceType.ARXIV,
                null,
                null
        );
        PageResponse<HotClusterSummaryVO> hnClusters = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                SourceType.HACKER_NEWS,
                null,
                null
        );
        assertThat(arxivClusters.totalElements()).isEqualTo(1);
        assertThat(hnClusters.totalElements()).isEqualTo(1);
        assertThat(arxivClusters.items().get(0).id()).isEqualTo(summary.id());
        assertThat(hnClusters.items().get(0).id()).isEqualTo(summary.id());

        HotClusterDetailVO detail = hotClusterQueryService.get(summary.id());
        assertThat(detail.itemCount()).isEqualTo(2);
        assertThat(detail.items()).hasSize(2);
        assertThat(detail.items().stream().map(item -> item.sourceType()).toList())
                .containsExactlyInAnyOrder(SourceType.HACKER_NEWS, SourceType.ARXIV);
        assertThat(detail.items().stream().map(item -> item.sourceUrl()).distinct())
                .containsExactly("https://arxiv.org/abs/2501.01234v2");

        verify(hackerNewsClient).fetchTopStoryIds();
        verify(arxivClient).search(any(ArxivSearchRequest.class));
    }

    @Test
    void shouldClusterHackerNewsAndGitHubEvidenceIntoSameHotCluster() {
        SourceConfigVO hnSource = sourceConfigService.create(new CreateSourceRequest(
                "hn-github-cross-source",
                SourceType.HACKER_NEWS,
                "HN GitHub Cross Source",
                true,
                null,
                Map.of(
                        "feed", "TOP",
                        "fetchLimit", 1,
                        "keywords", List.of("AI", "agent", "open source")
                )
        ));
        SourceConfigVO gitHubSource = sourceConfigService.create(new CreateSourceRequest(
                "github-cross-source",
                SourceType.GITHUB,
                "GitHub Cross Source",
                true,
                null,
                Map.of(
                        "query", "open source ai agent",
                        "sort", "updated",
                        "order", "desc",
                        "perPage", 1,
                        "page", 1
                )
        ));

        when(hackerNewsClient.fetchTopStoryIds()).thenReturn(List.of(202L));
        when(hackerNewsClient.fetchItem(202L)).thenReturn(Optional.of(hnItem(
                202L,
                "Open source AI agent repo discussion",
                "https://github.com/micro/go-micro?utm_source=hn",
                220,
                58
        )));
        when(gitHubClient.searchRepositories(any(GitHubSearchRequest.class))).thenReturn(List.of(
                repository(
                        29217054L,
                        "micro/go-micro",
                        "https://github.com/micro/go-micro/"
                )
        ));

        CrawlTaskVO hnTask = crawlExecutionService.executeManual(hnSource.id(), "phase4-cross-source-hn-github");
        CrawlTaskVO gitHubTask = crawlExecutionService.executeManual(gitHubSource.id(), "phase4-cross-source-github");

        assertThat(hnTask.status().name()).isEqualTo("SUCCEEDED");
        assertThat(gitHubTask.status().name()).isEqualTo("SUCCEEDED");
        assertThat(count("raw_item")).isEqualTo(2);
        assertThat(count("hot_item")).isEqualTo(2);
        assertThat(count("hot_cluster")).isEqualTo(1);
        assertThat(count("hot_cluster_item")).isEqualTo(2);

        PageResponse<HotClusterSummaryVO> allClusters = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                null,
                null,
                null
        );
        assertThat(allClusters.totalElements()).isEqualTo(1);
        HotClusterSummaryVO summary = allClusters.items().get(0);
        assertThat(summary.sourceTypes()).containsExactlyInAnyOrder(SourceType.HACKER_NEWS, SourceType.GITHUB);

        PageResponse<HotClusterSummaryVO> gitHubClusters = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                SourceType.GITHUB,
                null,
                null
        );
        PageResponse<HotClusterSummaryVO> hnClusters = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                SourceType.HACKER_NEWS,
                null,
                null
        );
        assertThat(gitHubClusters.totalElements()).isEqualTo(1);
        assertThat(hnClusters.totalElements()).isEqualTo(1);
        assertThat(gitHubClusters.items().get(0).id()).isEqualTo(summary.id());
        assertThat(hnClusters.items().get(0).id()).isEqualTo(summary.id());

        HotClusterDetailVO detail = hotClusterQueryService.get(summary.id());
        assertThat(detail.itemCount()).isEqualTo(2);
        assertThat(detail.items()).hasSize(2);
        assertThat(detail.items().stream().map(item -> item.sourceType()).toList())
                .containsExactlyInAnyOrder(SourceType.HACKER_NEWS, SourceType.GITHUB);
        assertThat(detail.items().stream().map(item -> item.sourceUrl()).distinct())
                .containsExactly("https://github.com/micro/go-micro");

        verify(hackerNewsClient).fetchTopStoryIds();
        verify(gitHubClient).searchRepositories(any(GitHubSearchRequest.class));
    }

    private FetchedHackerNewsItem hnItem(
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
                "phase4-cross-source",
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

    private FetchedGitHubRepository repository(long repoId, String fullName, String htmlUrl) {
        String ownerLogin = fullName.substring(0, fullName.indexOf('/'));
        String name = fullName.substring(fullName.indexOf('/') + 1);
        return new FetchedGitHubRepository(
                repoId,
                name,
                fullName,
                "A Go agent harness and service framework",
                htmlUrl,
                ownerLogin,
                "Go",
                List.of("ai-agent", "microservices", "open-source"),
                18000,
                2400,
                18000,
                64,
                Instant.parse("2026-07-07T09:15:00Z")
        );
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
