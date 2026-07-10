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
public class GitHubHotItemNormalizer implements HotItemNormalizer {

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public GitHubHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.GITHUB;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        String fullName = cleanText(payload.path("fullName").asText(""));
        String sourceUrl = urlCanonicalizer.canonicalize(normalizeGitHubUrl(
                payload.path("htmlUrl").asText(rawItem.getSourceUrl())
        ));
        if (fullName.isBlank() || sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        String description = cleanText(payload.path("description").asText(""));
        ArrayNode tags = objectMapper.createArrayNode();
        Set<String> seenTags = new HashSet<>();
        JsonNode topics = payload.path("topics");
        if (topics.isArray()) {
            topics.forEach(node -> addTag(tags, seenTags, node.asText("")));
        }
        addTag(tags, seenTags, payload.path("language").asText(""));

        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", payload.path("stargazersCount").asInt(0));
        metrics.put("commentsCount", payload.path("forksCount").asInt(0));
        metrics.put("stargazersCount", payload.path("stargazersCount").asInt(0));
        metrics.put("forksCount", payload.path("forksCount").asInt(0));
        metrics.put("watchersCount", payload.path("watchersCount").asInt(0));
        metrics.put("openIssuesCount", payload.path("openIssuesCount").asInt(0));
        String language = cleanText(payload.path("language").asText(""));
        if (!language.isBlank()) {
            metrics.put("language", language);
        }
        String updatedAt = payload.path("updatedAt").asText("");
        if (!updatedAt.isBlank()) {
            metrics.put("updatedAt", updatedAt);
        }

        return Optional.of(new NormalizedHotItem(
                "REPOSITORY",
                fullName,
                description.isBlank() ? null : truncate(description, 2000),
                sourceUrl,
                cleanNullable(payload.path("ownerLogin").asText(null)),
                tags,
                metrics,
                sha256(fullName.toLowerCase() + "\n" + sourceUrl),
                rawItem.getPublishedAt()
        ));
    }

    private void addTag(ArrayNode tags, Set<String> seenTags, String value) {
        String normalized = cleanText(value);
        if (!normalized.isBlank() && seenTags.add(normalized.toLowerCase())) {
            tags.add(normalized);
        }
    }

    private String cleanNullable(String value) {
        String cleaned = cleanText(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeGitHubUrl(String value) {
        String cleaned = cleanText(value);
        if (cleaned.endsWith("/") && cleaned.startsWith("http")) {
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
