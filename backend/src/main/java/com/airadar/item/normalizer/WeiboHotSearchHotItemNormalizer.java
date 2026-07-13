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
public class WeiboHotSearchHotItemNormalizer implements HotItemNormalizer {

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public WeiboHotSearchHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.WEIBO_HOT_SEARCH;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        String word = cleanText(payload.path("word").asText(""));
        if (word.isBlank()) {
            return Optional.empty();
        }

        String note = cleanText(payload.path("note").asText(""));
        long num = payload.path("num").asLong(0L);
        String category = cleanText(payload.path("category").asText(""));
        String mid = cleanText(payload.path("mid").asText(""));
        int rank = payload.path("rank").asInt(1);
        long rawHot = payload.path("rawHot").asLong(0L);
        String query = cleanText(payload.path("query").asText(""));

        // Build summary: note + hot value
        String summary;
        if (!note.isBlank()) {
            summary = note + " (热度: " + num + ")";
        } else {
            summary = "热度: " + num;
        }
        if (summary.length() > 500) {
            summary = summary.substring(0, 500);
        }

        // Build source URL
        String sourceUrl = "https://s.weibo.com/weibo?q=" + java.net.URLEncoder.encode(word, StandardCharsets.UTF_8);
        sourceUrl = urlCanonicalizer.canonicalize(sourceUrl);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        // Build tags
        ArrayNode tags = objectMapper.createArrayNode();
        Set<String> seenTags = new HashSet<>();
        addTag(tags, seenTags, "weibo-hot-search");
        if (!query.isBlank()) {
            addTag(tags, seenTags, query);
        }
        if (!category.isBlank()) {
            addTag(tags, seenTags, category);
        }

        // Build metrics
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", num);
        metrics.put("commentsCount", 0);
        metrics.put("rank", rank);
        metrics.put("rawHot", rawHot);
        if (!category.isBlank()) {
            metrics.put("category", category);
        }

        // Build external ID
        String externalId;
        if (mid != null && !mid.isBlank()) {
            externalId = mid;
        } else {
            externalId = sha256Hex(word.toLowerCase());
        }

        return Optional.of(new NormalizedHotItem(
                "TREND",
                word,
                summary,
                sourceUrl,
                "微博热搜",
                tags,
                metrics,
                sha256Hex(word.toLowerCase() + "\n" + sourceUrl),
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
