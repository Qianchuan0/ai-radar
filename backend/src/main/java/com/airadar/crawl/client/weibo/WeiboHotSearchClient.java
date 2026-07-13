package com.airadar.crawl.client.weibo;

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

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class WeiboHotSearchClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public WeiboHotSearchClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            WeiboHotSearchProperties properties
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

    public List<FetchedWeiboHotTopic> fetchHotSearch() {
        String responseBody = executeWithRetry();
        return parseResponse(responseBody);
    }

    private String executeWithRetry() {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.get()
                        .uri("/ajax/side/hotSearch")
                        .header("Referer", "https://weibo.com/")
                        .header("Accept", "application/json")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            throw new BusinessException(
                                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                                    "Weibo Hot Search returned " + response.getStatusCode()
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

    private List<FetchedWeiboHotTopic> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode okNode = root.path("ok");
            if (okNode.isMissingNode() || okNode.isNull() || okNode.asInt() != 1) {
                throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Weibo Hot Search returned ok != 1.");
            }

            JsonNode data = root.path("data");
            JsonNode realtime = data.path("realtime");

            if (!realtime.isArray()) {
                return List.of();
            }

            List<FetchedWeiboHotTopic> results = new ArrayList<>(realtime.size());
            int rank = 1;
            for (JsonNode topicNode : realtime) {
                String word = nullableText(topicNode, "word");
                String note = nullableText(topicNode, "note");
                long num = topicNode.path("num").asLong(0L);
                String category = nullableText(topicNode, "category");
                String mid = nullableText(topicNode, "mid");
                long rawHot = topicNode.path("raw_hot").asLong(0L);

                if (word == null || word.isBlank()) {
                    rank++;
                    continue;
                }

                results.add(new FetchedWeiboHotTopic(
                        word,
                        note,
                        num,
                        category,
                        mid,
                        rawHot,
                        rank
                ));
                rank++;
            }

            return List.copyOf(results);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid JSON: Weibo Hot Search response could not be parsed.");
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
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Weibo Hot Search request was interrupted.");
        }
    }
}
