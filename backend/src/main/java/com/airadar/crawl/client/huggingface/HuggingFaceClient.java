package com.airadar.crawl.client.huggingface;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class HuggingFaceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final String token;

    public HuggingFaceClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            HuggingFaceProperties properties
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
        this.token = properties.token();
    }

    public List<FetchedHuggingFaceModel> searchModels(HuggingFaceModelsRequest request) {
        String responseBody = executeWithRetry(() -> restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/models")
                            .queryParam("search", request.search())
                            .queryParam("sort", request.sort())
                            .queryParam("direction", resolveDirection(request.direction()))
                            .queryParam("limit", request.limit());
                    if (request.pipelineTag() != null && !request.pipelineTag().isBlank()) {
                        builder.queryParam("pipeline_tag", request.pipelineTag().trim());
                    }
                    return builder.build();
                })
                .headers(this::applyAuth)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                    throw new BusinessException(
                            ErrorCode.CRAWL_UPSTREAM_ERROR,
                            "Hugging Face returned " + response.getStatusCode()
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

    private List<FetchedHuggingFaceModel> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.isArray()) {
                throw new BusinessException(
                        ErrorCode.CRAWL_UPSTREAM_ERROR,
                        "Hugging Face models response must be an array."
                );
            }
            List<FetchedHuggingFaceModel> models = new ArrayList<>(root.size());
            for (JsonNode item : root) {
                String modelId = firstNonBlank(nullableText(item, "modelId"), nullableText(item, "id"));
                if (modelId == null) {
                    throw new BusinessException(
                            ErrorCode.CRAWL_UPSTREAM_ERROR,
                            "Hugging Face model is missing required field: modelId"
                    );
                }
                models.add(new FetchedHuggingFaceModel(
                        modelId,
                        nullableText(item, "id"),
                        item.path("downloads").asInt(0),
                        item.path("likes").asInt(0),
                        extractTags(item.path("tags")),
                        nullableText(item, "pipeline_tag"),
                        extractAuthor(modelId),
                        nullableText(item, "library_name"),
                        parseInstant(item.path("createdAt").asText(null)),
                        parseInstant(item.path("lastModified").asText(null)),
                        item.path("private").asBoolean(false)
                ));
            }
            return List.copyOf(models);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid Hugging Face models JSON response.");
        }
    }

    private List<String> extractTags(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>(node.size());
        node.forEach(item -> {
            String tag = item.asText("").trim();
            if (!tag.isBlank()) {
                tags.add(tag);
            }
        });
        return List.copyOf(tags);
    }

    private String extractAuthor(String modelId) {
        int slashIndex = modelId.indexOf('/');
        if (slashIndex <= 0) {
            return null;
        }
        return modelId.substring(0, slashIndex);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null ? null : text.trim();
    }

    private String resolveDirection(String value) {
        if (value == null || value.isBlank()) {
            return "-1";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "asc" -> "1";
            case "desc" -> "-1";
            default -> throw new IllegalArgumentException("Unsupported Hugging Face direction: " + value);
        };
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
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Hugging Face request was interrupted.");
        }
    }

    @FunctionalInterface
    private interface UpstreamCall<T> {
        T execute();
    }
}
