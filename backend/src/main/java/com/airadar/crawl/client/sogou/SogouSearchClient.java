package com.airadar.crawl.client.sogou;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.crawl.client.support.SignedRequest;
import com.airadar.crawl.client.support.TencentCloudV3Signer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class SogouSearchClient {

    private static final String SERVICE = "wsa";
    private static final String ACTION = "SearchPro";
    private static final String VERSION = "2025-05-08";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final String secretId;
    private final String secretKey;
    private final String host;

    public SogouSearchClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            SogouSearchProperties properties
    ) {
        this.host = extractHost(properties.baseUrl());
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
        this.secretId = properties.secretId();
        this.secretKey = properties.secretKey();
    }

    public List<FetchedSogouSearchResult> search(SogouSearchRequest request) {
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new BusinessException(
                    ErrorCode.CRAWL_PROVIDER_NOT_CONFIGURED,
                    "Sogou Search secret-id or secret-key is not configured."
            );
        }
        String payload = buildPayload(request);
        String responseBody = executeWithRetry(payload);
        return parseResponse(responseBody);
    }

    private String buildPayload(SogouSearchRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("Query", request.query());
        if (request.cnt() != null) {
            payload.put("Cnt", request.cnt());
        }
        if (request.mode() != null) {
            payload.put("Mode", request.mode());
        }
        if (request.site() != null && !request.site().isBlank()) {
            payload.put("Site", request.site());
        }
        if (request.freshness() != null && !request.freshness().isBlank()) {
            payload.put("Freshness", request.freshness());
        }
        if (request.fromTime() != null) {
            payload.put("FromTime", request.fromTime());
        }
        if (request.toTime() != null) {
            payload.put("ToTime", request.toTime());
        }
        return payload.toString();
    }

    private String executeWithRetry(String payload) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Instant now = Instant.now();
                SignedRequest signed = TencentCloudV3Signer.sign(
                        secretId, secretKey, SERVICE, host, ACTION, VERSION, payload, now
                );
                return restClient.post()
                        .uri("/")
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("X-TC-Action", ACTION)
                        .header("X-TC-Version", VERSION)
                        .header("X-TC-Timestamp", signed.timestamp())
                        .header("Authorization", signed.authorization())
                        .body(payload)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            throw new BusinessException(
                                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                                    "Sogou Search returned " + response.getStatusCode()
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

    private List<FetchedSogouSearchResult> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode response = root.path("Response");
            JsonNode error = response.path("Error");
            if (!error.isMissingNode() && !error.isNull()) {
                String code = nullableText(error, "Code");
                String message = nullableText(error, "Message");
                throw new BusinessException(
                        ErrorCode.CRAWL_UPSTREAM_ERROR,
                        message == null || message.isBlank()
                                ? "Sogou Search returned upstream error: " + code
                                : "Sogou Search returned upstream error: " + code + " - " + message
                );
            }
            JsonNode pages = response.path("Pages");
            if (!pages.isArray()) {
                return List.of();
            }
            List<FetchedSogouSearchResult> results = new ArrayList<>(pages.size());
            int rank = 1;
            for (JsonNode pageNode : pages) {
                String pageJson = pageNode.asText("");
                if (pageJson.isBlank()) {
                    rank++;
                    continue;
                }
                try {
                    JsonNode page = objectMapper.readTree(pageJson);
                    String title = nullableText(page, "title");
                    String url = nullableText(page, "url");
                    if (title == null || title.isBlank() || url == null || url.isBlank()) {
                        rank++;
                        continue;
                    }
                    results.add(new FetchedSogouSearchResult(
                            title,
                            url,
                            nullableText(page, "passage"),
                            nullableText(page, "content"),
                            nullableText(page, "site"),
                            page.path("score").asDouble(0.0),
                            parseDate(nullableText(page, "date")),
                            rank
                    ));
                } catch (Exception ignored) {
                    // Skip unparseable page entries
                }
                rank++;
            }
            return List.copyOf(results);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid Sogou Search JSON response.");
        }
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.from(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                    .withZone(ZoneOffset.UTC)
                    .parse(value));
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored2) {
                return null;
            }
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

    private String extractHost(String baseUrl) {
        String withoutScheme = baseUrl.replaceFirst("^https?://", "");
        return withoutScheme.split("/")[0];
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Sogou Search request was interrupted.");
        }
    }
}
