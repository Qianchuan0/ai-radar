package com.airadar.crawl;

import com.airadar.cluster.model.HotClusterSort;
import com.airadar.cluster.service.HotClusterQueryService;
import com.airadar.cluster.vo.HotClusterDetailVO;
import com.airadar.common.api.PageResponse;
import com.airadar.crawl.client.github.FetchedGitHubRepository;
import com.airadar.crawl.client.github.GitHubClient;
import com.airadar.crawl.client.github.GitHubSearchRequest;
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
class GitHubRawDataFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

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
    void shouldNormalizeGitHubRepositoriesIntoHotItems() {
        SourceConfigVO source = sourceConfigService.create(new CreateSourceRequest(
                "github-agents",
                SourceType.GITHUB,
                "GitHub Agents",
                true,
                null,
                Map.of(
                        "query", "ai agent stars:>1000",
                        "sort", "updated",
                        "order", "desc",
                        "perPage", 2,
                        "page", 1
                )
        ));
        when(gitHubClient.searchRepositories(any(GitHubSearchRequest.class))).thenReturn(List.of(
                repository(912345678L, "mannaandpoem/OpenManus", "https://github.com/mannaandpoem/OpenManus/"),
                repository(998877665L, "significant-gravitas/AutoGPT", "https://github.com/significant-gravitas/AutoGPT")
        ));

        CrawlTaskVO task = crawlExecutionService.executeManual(source.id(), "phase4-github-raw-1");

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
                "912345678"
        )).isEqualTo("GITHUB");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload ->> 'fullName' FROM raw_item WHERE external_id = ?",
                String.class,
                "912345678"
        )).isEqualTo("mannaandpoem/OpenManus");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_type FROM hot_item WHERE external_id = ?",
                String.class,
                "912345678"
        )).isEqualTo("REPOSITORY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_url FROM hot_item WHERE external_id = ?",
                String.class,
                "912345678"
        )).isEqualTo("https://github.com/mannaandpoem/OpenManus");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT author FROM hot_item WHERE external_id = ?",
                String.class,
                "912345678"
        )).isEqualTo("mannaandpoem");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT summary FROM hot_item WHERE external_id = ?",
                String.class,
                "912345678"
        )).isEqualTo("An open source generalist AI agent.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT tags::text FROM hot_item WHERE external_id = ?",
                String.class,
                "912345678"
        )).contains("ai-agent").contains("Python");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'points' FROM hot_item WHERE external_id = ?",
                String.class,
                "912345678"
        )).isEqualTo("42000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT metrics ->> 'forksCount' FROM hot_item WHERE external_id = ?",
                String.class,
                "912345678"
        )).isEqualTo("5100");

        PageResponse<?> page = hotClusterQueryService.list(
                1,
                20,
                HotClusterSort.SCORE_DESC,
                SourceType.GITHUB,
                null,
                null
        );
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);

        HotClusterDetailVO detail = hotClusterQueryService.get(
                jdbcTemplate.queryForObject("SELECT id FROM hot_cluster ORDER BY id LIMIT 1", Long.class)
        );
        assertThat(detail.items()).isNotEmpty();
        assertThat(detail.items().get(0).sourceType()).isEqualTo(SourceType.GITHUB);

        verify(gitHubClient).searchRepositories(any(GitHubSearchRequest.class));
    }

    private FetchedGitHubRepository repository(long repoId, String fullName, String htmlUrl) {
        String ownerLogin = fullName.substring(0, fullName.indexOf('/'));
        String name = fullName.substring(fullName.indexOf('/') + 1);
        return new FetchedGitHubRepository(
                repoId,
                name,
                fullName,
                "An open source generalist AI agent.",
                htmlUrl,
                ownerLogin,
                "Python",
                List.of("ai-agent", "llm", "automation"),
                42000,
                5100,
                42000,
                128,
                Instant.parse("2026-07-07T09:15:00Z")
        );
    }

    private long count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
