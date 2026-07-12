package com.airadar.crawl.collector.huggingface;

import com.airadar.crawl.client.huggingface.FetchedHuggingFaceModel;
import com.airadar.crawl.client.huggingface.HuggingFaceClient;
import com.airadar.crawl.client.huggingface.HuggingFaceModelsRequest;
import com.airadar.crawl.collector.CollectedItem;
import com.airadar.crawl.collector.CollectionBatch;
import com.airadar.crawl.collector.CollectionError;
import com.airadar.crawl.collector.SourceCollector;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class HuggingFaceCollector implements SourceCollector {

    private static final int DEFAULT_LIMIT = 20;

    private final HuggingFaceClient huggingFaceClient;
    private final ObjectMapper objectMapper;

    public HuggingFaceCollector(HuggingFaceClient huggingFaceClient, ObjectMapper objectMapper) {
        this.huggingFaceClient = huggingFaceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.HUGGING_FACE;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        List<FetchedHuggingFaceModel> models = huggingFaceClient.searchModels(new HuggingFaceModelsRequest(
                config.path("search").asText(),
                resolveSort(config.path("sort").asText(null)),
                resolveDirection(config.path("direction").asText(null)),
                config.path("limit").asInt(DEFAULT_LIMIT),
                cleanNullable(config.path("pipelineTag").asText(null))
        ));

        List<CollectedItem> items = new ArrayList<>(models.size());
        List<CollectionError> errors = new ArrayList<>();
        for (FetchedHuggingFaceModel model : models) {
            String modelId = primaryModelId(model);
            if (modelId == null || modelId.isBlank()) {
                errors.add(new CollectionError(
                        null,
                        "CRAWL.HUGGING_FACE_MODEL_INVALID",
                        "Hugging Face model is missing modelId or id.",
                        false
                ));
                continue;
            }
            items.add(new CollectedItem(
                    modelId,
                    "https://huggingface.co/" + modelId,
                    toRawPayload(model),
                    model.createdAt(),
                    Instant.now()
            ));
        }
        return new CollectionBatch(items, errors);
    }

    private JsonNode toRawPayload(FetchedHuggingFaceModel model) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("modelId", model.modelId());
        payload.put("id", model.id());
        payload.put("downloads", model.downloads());
        payload.put("likes", model.likes());
        payload.put("pipelineTag", model.pipelineTag());
        payload.put("author", model.author());
        payload.put("libraryName", model.libraryName());
        payload.put("createdAt", model.createdAt() == null ? null : model.createdAt().toString());
        payload.put("lastModified", model.lastModified() == null ? null : model.lastModified().toString());
        payload.put("private", model.privateModel());

        ArrayNode tags = objectMapper.createArrayNode();
        model.tags().forEach(tags::add);
        payload.set("tags", tags);
        return payload;
    }

    private String primaryModelId(FetchedHuggingFaceModel model) {
        if (model.modelId() != null && !model.modelId().isBlank()) {
            return model.modelId();
        }
        if (model.id() != null && !model.id().isBlank()) {
            return model.id();
        }
        return null;
    }

    private String cleanNullable(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String resolveSort(String value) {
        if (value == null || value.isBlank()) {
            return "downloads";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "downloads", "likes" -> normalized;
            case "createdat" -> "createdAt";
            case "lastmodified" -> "lastModified";
            default -> throw new IllegalArgumentException("Unsupported Hugging Face sort: " + value);
        };
    }

    private String resolveDirection(String value) {
        if (value == null || value.isBlank()) {
            return "desc";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "asc", "desc" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported Hugging Face direction: " + value);
        };
    }
}
