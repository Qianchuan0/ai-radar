package com.airadar.crawl.client.github;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GitHubClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final String token;

    public GitHubClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            GitHubProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();
        this.objectMapper = objectMapper;
        this.maxAttempts = properties.maxAttempts();
        this.token = properties.token();
    }

    public List<FetchedGitHubRepository> searchRepositories(GitHubSearchRequest request) {
        String responseBody = executeWithRetry(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/repositories")
                        .queryParam("q", request.query())
                        .queryParam("sort", request.sort())
                        .queryParam("order", request.order())
                        .queryParam("per_page", request.perPage())
                        .queryParam("page", request.page())
                        .build())
                .headers(headers -> applyAuth(headers))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                    throw new BusinessException(
                            ErrorCode.CRAWL_UPSTREAM_ERROR,
                            "GitHub returned " + response.getStatusCode()
                    );
                })
                .body(String.class));
        return parseResponse(responseBody);
    }

    private void applyAuth(HttpHeaders headers) {
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token.trim());
        }
    }

    private List<FetchedGitHubRepository> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "GitHub search response is missing items.");
            }
            List<FetchedGitHubRepository> repositories = new ArrayList<>(items.size());
            for (JsonNode item : items) {
                repositories.add(new FetchedGitHubRepository(
                        requiredLong(item, "id"),
                        requiredText(item, "name"),
                        requiredText(item, "full_name"),
                        nullableText(item, "description"),
                        requiredText(item, "html_url"),
                        requiredText(item.path("owner"), "login"),
                        nullableText(item, "language"),
                        extractTopics(item.path("topics")),
                        item.path("stargazers_count").asInt(0),
                        item.path("forks_count").asInt(0),
                        item.path("watchers_count").asInt(0),
                        item.path("open_issues_count").asInt(0),
                        parseInstant(item.path("updated_at").asText(null))
                ));
            }
            return List.copyOf(repositories);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid GitHub search JSON response.");
        }
    }

    private List<String> extractTopics(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> topics = new ArrayList<>(node.size());
        node.forEach(item -> {
            String topic = item.asText("").trim();
            if (!topic.isBlank()) {
                topics.add(topic);
            }
        });
        return List.copyOf(topics);
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private long requiredLong(JsonNode node, String fieldName) {
        if (!node.hasNonNull(fieldName)) {
            throw new BusinessException(
                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                    "GitHub repository is missing required field: " + fieldName
            );
        }
        return node.path(fieldName).asLong();
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = nullableText(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                    "GitHub repository is missing required field: " + fieldName
            );
        }
        return value;
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null ? null : text.trim();
    }

    private <T> T executeWithRetry(UpstreamCall<T> call) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.execute();
            } catch (ResourceAccessException exception) {
                lastFailure = exception;
            } catch (RestClientResponseException exception) {
                if (!exception.getStatusCode().is5xxServerError()) {
                    throw exception;
                }
                lastFailure = exception;
            } catch (BusinessException exception) {
                throw exception;
            }
            if (attempt < maxAttempts) {
                pauseBeforeRetry(attempt);
            }
        }
        throw new BusinessException(
                ErrorCode.CRAWL_UPSTREAM_ERROR,
                lastFailure == null ? ErrorCode.CRAWL_UPSTREAM_ERROR.getDefaultMessage() : lastFailure.getMessage()
        );
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "GitHub request was interrupted.");
        }
    }

    @FunctionalInterface
    private interface UpstreamCall<T> {
        T execute();
    }
}
