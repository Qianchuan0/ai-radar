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
import java.util.HexFormat;
import java.util.Optional;

@Component
public class ArxivHotItemNormalizer implements HotItemNormalizer {

    private final ObjectMapper objectMapper;
    private final UrlCanonicalizer urlCanonicalizer;

    public ArxivHotItemNormalizer(ObjectMapper objectMapper, UrlCanonicalizer urlCanonicalizer) {
        this.objectMapper = objectMapper;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.ARXIV;
    }

    @Override
    public Optional<NormalizedHotItem> normalize(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        JsonNode payload = rawItem.getRawPayload();
        String title = cleanText(payload.path("title").asText(""));
        String summary = cleanText(payload.path("summary").asText(""));
        String arxivId = payload.path("arxivId").asText("").trim();
        String sourceUrl = urlCanonicalizer.canonicalize(normalizeArxivUrl(
                payload.path("sourceUrl").asText(rawItem.getSourceUrl())
        ));
        if (title.isBlank() || arxivId.isBlank() || sourceUrl == null || sourceUrl.isBlank()) {
            return Optional.empty();
        }

        ArrayNode tags = objectMapper.createArrayNode();
        JsonNode categories = payload.path("categories");
        if (categories.isArray()) {
            categories.forEach(node -> {
                String value = node.asText("").trim();
                if (!value.isBlank()) {
                    tags.add(value);
                }
            });
        }

        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("points", 0);
        metrics.put("commentsCount", 0);
        metrics.put("authorsCount", countArrayItems(payload.path("authors")));
        metrics.put("categoriesCount", tags.size());
        String pdfUrl = payload.path("pdfUrl").asText(null);
        if (pdfUrl != null && !pdfUrl.isBlank()) {
            metrics.put("pdfUrl", urlCanonicalizer.canonicalize(normalizeArxivUrl(pdfUrl)));
        }

        return Optional.of(new NormalizedHotItem(
                "PAPER",
                title,
                summary.isBlank() ? null : truncate(summary, 2000),
                sourceUrl,
                firstAuthor(payload.path("authors")),
                tags,
                metrics,
                sha256(arxivId + "\n" + title.toLowerCase()),
                rawItem.getPublishedAt()
        ));
    }

    private String firstAuthor(JsonNode authors) {
        if (!authors.isArray() || authors.isEmpty()) {
            return null;
        }
        String value = cleanText(authors.get(0).asText(""));
        return value.isBlank() ? null : value;
    }

    private int countArrayItems(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String normalizeArxivUrl(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("http://arxiv.org/", "https://arxiv.org/")
                .replace("http://www.arxiv.org/", "https://arxiv.org/");
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
