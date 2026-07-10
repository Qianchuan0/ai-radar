package com.airadar.crawl.collector.github;

import com.airadar.crawl.client.github.FetchedGitHubRepository;
import com.airadar.crawl.client.github.GitHubClient;
import com.airadar.crawl.client.github.GitHubSearchRequest;
import com.airadar.crawl.collector.CollectedItem;
import com.airadar.crawl.collector.CollectionBatch;
import com.airadar.crawl.collector.SourceCollector;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class GitHubCollector implements SourceCollector {

    private static final int DEFAULT_PER_PAGE = 10;

    private final GitHubClient gitHubClient;
    private final ObjectMapper objectMapper;

    public GitHubCollector(GitHubClient gitHubClient, ObjectMapper objectMapper) {
        this.gitHubClient = gitHubClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.GITHUB;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        List<FetchedGitHubRepository> repositories = gitHubClient.searchRepositories(new GitHubSearchRequest(
                config.path("query").asText(),
                resolveSort(config.path("sort").asText(null)),
                resolveOrder(config.path("order").asText(null)),
                config.path("perPage").asInt(DEFAULT_PER_PAGE),
                config.path("page").asInt(1)
        ));
        return new CollectionBatch(repositories.stream().map(this::toCollectedItem).toList(), List.of());
    }

    private CollectedItem toCollectedItem(FetchedGitHubRepository repository) {
        return new CollectedItem(
                String.valueOf(repository.repoId()),
                repository.htmlUrl(),
                toRawPayload(repository),
                repository.updatedAt(),
                Instant.now()
        );
    }

    private JsonNode toRawPayload(FetchedGitHubRepository repository) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("repoId", repository.repoId());
        payload.put("name", repository.name());
        payload.put("fullName", repository.fullName());
        payload.put("description", repository.description());
        payload.put("htmlUrl", repository.htmlUrl());
        payload.put("ownerLogin", repository.ownerLogin());
        payload.put("language", repository.language());
        payload.put("stargazersCount", repository.stargazersCount());
        payload.put("forksCount", repository.forksCount());
        payload.put("watchersCount", repository.watchersCount());
        payload.put("openIssuesCount", repository.openIssuesCount());
        payload.put("updatedAt", repository.updatedAt() == null ? null : repository.updatedAt().toString());

        ArrayNode topics = objectMapper.createArrayNode();
        repository.topics().forEach(topics::add);
        payload.set("topics", topics);
        return payload;
    }

    private String resolveSort(String value) {
        if (value == null || value.isBlank()) {
            return "updated";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "stars", "forks", "updated" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported GitHub sort: " + value);
        };
    }

    private String resolveOrder(String value) {
        if (value == null || value.isBlank()) {
            return "desc";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "asc", "desc" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported GitHub order: " + value);
        };
    }
}
