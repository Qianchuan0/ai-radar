package com.airadar.item.normalizer;

import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.model.NormalizedHotItem;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

@Component
public class HuggingFaceHotItemNormalizer implements HotItemNormalizer {

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public HuggingFaceHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.HUGGING_FACE;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        String modelId = cleanText(firstNonBlank(
                payload.path("modelId").asText(""),
                payload.path("id").asText("")
        ));
        String sourceUrl = urlCanonicalizer.canonicalize(normalizeSourceUrl(
                rawItem.getSourceUrl() == null || rawItem.getSourceUrl().isBlank()
                        ? "https://huggingface.co/" + modelId
                        : rawItem.getSourceUrl()
        ));
        if (modelId.isBlank() || sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        ArrayNode tags = objectMapper.createArrayNode();
        Set<String> seenTags = new HashSet<>();
        JsonNode rawTags = payload.path("tags");
        if (rawTags.isArray()) {
            rawTags.forEach(node -> addTag(tags, seenTags, node.asText("")));
        }
        String pipelineTag = cleanText(payload.path("pipelineTag").asText(""));
        addTag(tags, seenTags, pipelineTag);

        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", payload.path("downloads").asInt(0));
        metrics.put("commentsCount", payload.path("likes").asInt(0));
        metrics.put("downloads", payload.path("downloads").asInt(0));
        metrics.put("likes", payload.path("likes").asInt(0));
        if (!pipelineTag.isBlank()) {
            metrics.put("pipelineTag", pipelineTag);
        }
        String libraryName = cleanText(payload.path("libraryName").asText(""));
        if (!libraryName.isBlank()) {
            metrics.put("libraryName", libraryName);
        }
        String lastModified = cleanText(payload.path("lastModified").asText(""));
        if (!lastModified.isBlank()) {
            metrics.put("lastModified", lastModified);
        }
        metrics.put("private", payload.path("private").asBoolean(false));

        return Optional.of(new NormalizedHotItem(
                "MODEL",
                modelId,
                buildSummary(pipelineTag, tags),
                sourceUrl,
                extractAuthor(payload, modelId),
                tags,
                metrics,
                sha256(modelId.toLowerCase() + "\n" + sourceUrl),
                rawItem.getPublishedAt()
        ));
    }

    private String extractAuthor(JsonNode payload, String modelId) {
        String author = cleanText(payload.path("author").asText(""));
        if (!author.isBlank()) {
            return author;
        }
        int slashIndex = modelId.indexOf('/');
        if (slashIndex <= 0) {
            return null;
        }
        return modelId.substring(0, slashIndex);
    }

    private String buildSummary(String pipelineTag, ArrayNode tags) {
        StringBuilder builder = new StringBuilder();
        if (!pipelineTag.isBlank()) {
            builder.append("Pipeline: ").append(pipelineTag);
        }
        String joinedTags = joinTags(tags, 6);
        if (!joinedTags.isBlank()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("Tags: ").append(joinedTags);
        }
        return builder.length() == 0 ? null : truncate(builder.toString(), 2000);
    }

    private String joinTags(ArrayNode tags, int maxCount) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (JsonNode tag : tags) {
            String value = cleanText(tag.asText(""));
            if (value.isBlank()) {
                continue;
            }
            if (count > 0) {
                builder.append(", ");
            }
            builder.append(value);
            count++;
            if (count >= maxCount) {
                break;
            }
        }
        return builder.toString();
    }

    private void addTag(ArrayNode tags, Set<String> seenTags, String value) {
        String normalized = cleanText(value);
        if (!normalized.isBlank() && seenTags.add(normalized.toLowerCase())) {
            tags.add(normalized);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeSourceUrl(String value) {
        String cleaned = cleanText(value);
        if (cleaned.endsWith("/")) {
            return cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
