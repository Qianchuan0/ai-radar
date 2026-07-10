package com.airadar.item.normalizer;

import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.item.model.NormalizedHotItem;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.source.entity.SourceConfigEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class HackerNewsHotItemNormalizer implements HotItemNormalizer {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public HackerNewsHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public com.airadar.source.model.SourceType supportedType() {
        return com.airadar.source.model.SourceType.HACKER_NEWS;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        if (!"story".equals(payload.path("type").asText())
                || payload.path("deleted").asBoolean(false)
                || payload.path("dead").asBoolean(false)) {
            return Optional.empty();
        }

        String title = cleanText(payload.path("title").asText(""));
        if (title.isBlank()) {
            return Optional.empty();
        }
        String text = cleanText(payload.path("text").asText(""));
        List<String> matchedKeywords = matchKeywords(title + " " + text, sourceConfig.getConfigPayload());
        if (matchedKeywords.isEmpty()) {
            return Optional.empty();
        }

        String sourceUrl = urlCanonicalizer.canonicalize(rawItem.getSourceUrl());
        ArrayNode tags = objectMapper.createArrayNode();
        matchedKeywords.forEach(tags::add);
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", payload.path("score").asInt(0));
        metrics.put("commentsCount", payload.path("descendants").asInt(0));

        return Optional.of(new NormalizedHotItem(
                "POST",
                title,
                text.isBlank() ? null : truncate(text, 1000),
                sourceUrl,
                payload.path("by").asText(null),
                tags,
                metrics,
                sha256(title.toLowerCase(Locale.ROOT) + "\n" + sourceUrl),
                rawItem.getPublishedAt()
        ));
    }

    private List<String> matchKeywords(String content, JsonNode config) {
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        JsonNode keywords = config == null ? null : config.path("keywords");
        if (keywords == null || !keywords.isArray()) {
            return matches;
        }
        for (JsonNode keywordNode : keywords) {
            String keyword = keywordNode.asText("").trim();
            if (!keyword.isBlank() && normalizedContent.contains(keyword.toLowerCase(Locale.ROOT))) {
                matches.add(keyword);
            }
        }
        return matches;
    }

    private String cleanText(String value) {
        String withoutTags = HTML_TAG.matcher(value).replaceAll(" ");
        return HtmlUtils.htmlUnescape(withoutTags).replaceAll("\\s+", " ").trim();
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
