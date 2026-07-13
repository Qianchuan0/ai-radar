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
public class DuckDuckGoSearchHotItemNormalizer implements HotItemNormalizer {

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public DuckDuckGoSearchHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.DUCKDUCKGO_SEARCH;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        String title = cleanText(payload.path("title").asText(""));
        String url = cleanText(payload.path("url").asText(""));

        if (title.isBlank() || url.isBlank()) {
            return Optional.empty();
        }

        String sourceUrl = urlCanonicalizer.canonicalize(url);
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        int rank = payload.path("rank").asInt(1);
        int totalCount = payload.path("totalCount").asInt(rank);
        int points = Math.max(1, totalCount - rank + 1);

        ArrayNode tags = objectMapper.createArrayNode();
        Set<String> seenTags = new HashSet<>();

        // 添加来源标签
        addTag(tags, seenTags, "duckduckgo-search");

        // 添加查询标签
        String query = cleanText(payload.path("query").asText(""));
        if (!query.isBlank()) {
            for (String keyword : query.split("\\s+")) {
                addTag(tags, seenTags, keyword);
            }
        }

        // 添加 region 标签
        String region = cleanText(payload.path("region").asText(""));
        addTag(tags, seenTags, region);

        // 添加 URL host 标签
        String host = extractHost(sourceUrl);
        addTag(tags, seenTags, host);

        // 构建 metrics
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", points);
        metrics.put("commentsCount", 0);
        metrics.put("rank", rank);
        metrics.put("sourceHost", host);
        if (!region.isBlank()) {
            metrics.put("region", region);
        }

        // 构建摘要
        String snippet = cleanText(payload.path("snippet").asText(""));
        String summary = snippet;
        if (summary.isBlank()) {
            summary = null;
        } else if (summary.length() > 2000) {
            summary = summary.substring(0, 2000);
        }

        // 构建作者
        String author = host.isBlank() ? "DuckDuckGo Search" : host;

        return Optional.of(new NormalizedHotItem(
                "WEB_PAGE",
                title,
                summary,
                sourceUrl,
                author,
                tags,
                metrics,
                sha256Hex(title.toLowerCase() + "\n" + sourceUrl),
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

    private String extractHost(String url) {
        try {
            String host = url.replaceFirst("^https?://", "");
            int pathStart = host.indexOf('/');
            if (pathStart > 0) {
                host = host.substring(0, pathStart);
            }
            int portStart = host.indexOf(':');
            if (portStart > 0) {
                host = host.substring(0, portStart);
            }
            return host;
        } catch (Exception e) {
            return "";
        }
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
