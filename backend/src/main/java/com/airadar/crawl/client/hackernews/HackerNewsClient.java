package com.airadar.crawl.client.hackernews;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class HackerNewsClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public HackerNewsClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            HackerNewsProperties properties
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

    public List<Long> fetchTopStoryIds() {
        Long[] ids = executeWithRetry(
                () -> restClient.get().uri("/topstories.json").retrieve().body(Long[].class)
        );
        if (ids == null) {
            return List.of();
        }
        return Arrays.asList(ids);
    }

    public Optional<FetchedHackerNewsItem> fetchItem(long itemId) {
        JsonNode rawPayload = executeWithRetry(
                () -> restClient.get()
                        .uri("/item/{itemId}.json", itemId)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                            if (response.getStatusCode().value() != 404) {
                                throw new BusinessException(
                                        ErrorCode.CRAWL_UPSTREAM_ERROR,
                                        "Hacker News returned " + response.getStatusCode()
                                );
                            }
                        })
                        .body(JsonNode.class)
        );
        if (rawPayload == null || rawPayload.isNull()) {
            return Optional.empty();
        }
        try {
            HackerNewsItemResponse item = objectMapper.treeToValue(rawPayload, HackerNewsItemResponse.class);
            return Optional.of(new FetchedHackerNewsItem(item, rawPayload));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid Hacker News item response.");
        }
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
            Thread.sleep(200L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Hacker News request was interrupted.");
        }
    }

    @FunctionalInterface
    private interface UpstreamCall<T> {
        T execute();
    }
}
