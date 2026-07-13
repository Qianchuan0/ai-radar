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
public class HackerNewsSearchHotItemNormalizer implements HotItemNormalizer {

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public HackerNewsSearchHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.HACKER_NEWS_SEARCH;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        String objectId = cleanText(payload.path("objectId").asText(""));
        String title = cleanText(payload.path("title").asText(""));
        String url = cleanText(payload.path("url").asText(""));
        String storyText = cleanText(payload.path("storyText").asText(""));
        String author = cleanText(payload.path("author").asText(""));
        int points = payload.path("points").asInt(0);
        int numComments = payload.path("numComments").asInt(0);
        String query = cleanText(payload.path("query").asText(""));

        if (objectId.isBlank() || title.isBlank()) {
            return Optional.empty();
        }

        // Use fallback URL if original is missing
        if (url.isBlank()) {
            url = "https://news.ycombinator.com/item?id=" + objectId;
        }

        String sourceUrl = urlCanonicalizer.canonicalize(url);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        // Use story_text as summary, truncate if too long
        String summary = storyText;
        if (summary.isBlank()) {
            summary = null;
        } else if (summary.length() > 2000) {
            summary = summary.substring(0, 2000);
        }

        // Build tags
        ArrayNode tags = objectMapper.createArrayNode();
        Set<String> seenTags = new HashSet<>();
        addTag(tags, seenTags, "hacker-news-search");
        if (!query.isBlank()) {
            addTag(tags, seenTags, query);
        }

        // Build metrics
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", points);
        metrics.put("commentsCount", numComments);

        return Optional.of(new NormalizedHotItem(
                "ARTICLE",
                title,
                summary,
                sourceUrl,
                author,
                tags,
                metrics,
                sha256Hex(objectId.toLowerCase()),
                rawItem.getPublishedAt()
        ));
    }

    private void addTag(ArrayNode tags, Set<String> seenTags, String value) {
        String normalized = cleanText(value);
        if (!normalized.isBlank() && seenTags.add(normalized.toLowerCase())) {
            tags.add(normalized);
        }
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
