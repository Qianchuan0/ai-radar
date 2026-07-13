package com.airadar.crawl.client.hackernewssearch;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class HackerNewsSearchClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public HackerNewsSearchClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            HackerNewsSearchProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.maxAttempts = properties.maxAttempts();
    }

    public List<FetchedHackerNewsSearchHit> search(HackerNewsSearchRequest request) {
        String responseBody = executeWithRetry(buildUrl(request));
        return parseResponse(responseBody);
    }

    private String buildUrl(HackerNewsSearchRequest request) {
        long sinceTimestamp = request.since().getEpochSecond();

        return UriComponentsBuilder.fromPath("/api/v1/search")
                .queryParam("query", request.query())
                .queryParam("tags", "story")
                .queryParam("hitsPerPage", request.limit())
                .queryParam("numericFilters", "created_at_i>" + sinceTimestamp)
                .build()
                .toUriString();
    }

    private String executeWithRetry(String uri) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.get()
                        .uri(uri)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            throw new BusinessException(
                                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                                    "Hacker News Search returned " + response.getStatusCode()
                            );
                        })
                        .body(String.class);
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

    private List<FetchedHackerNewsSearchHit> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode hits = root.path("hits");
            if (!hits.isArray()) {
                return List.of();
            }

            List<FetchedHackerNewsSearchHit> results = new ArrayList<>(hits.size());
            for (JsonNode hit : hits) {
                String objectId = nullableText(hit, "objectID");
                String title = nullableText(hit, "title");
                String url = nullableText(hit, "url");
                String storyText = nullableText(hit, "story_text");
                String author = nullableText(hit, "author");
                int points = hit.path("points").asInt(0);
                int numComments = hit.path("num_comments").asInt(0);
                long createdAtI = hit.path("created_at_i").asLong(0L);

                if (objectId == null || objectId.isBlank()) {
                    continue;
                }

                Instant createdAt = Instant.ofEpochSecond(createdAtI);

                // Fallback URL if original URL is missing
                if (url == null || url.isBlank()) {
                    url = "https://news.ycombinator.com/item?id=" + objectId;
                }

                results.add(new FetchedHackerNewsSearchHit(
                        objectId,
                        title,
                        url,
                        storyText,
                        author,
                        points,
                        numComments,
                        createdAt,
                        createdAtI
                ));
            }

            return List.copyOf(results);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid JSON: Hacker News Search response could not be parsed.");
        }
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null ? null : text.trim();
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Hacker News Search request was interrupted.");
        }
    }
}
