package com.airadar.crawl.collector.sogou;

import com.airadar.crawl.client.sogou.FetchedSogouSearchResult;
import com.airadar.crawl.client.sogou.SogouSearchClient;
import com.airadar.crawl.client.sogou.SogouSearchRequest;
import com.airadar.crawl.collector.CollectedItem;
import com.airadar.crawl.collector.CollectionBatch;
import com.airadar.crawl.collector.SourceCollector;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class SogouSearchCollector implements SourceCollector {

    private final SogouSearchClient sogouSearchClient;
    private final ObjectMapper objectMapper;

    public SogouSearchCollector(SogouSearchClient sogouSearchClient, ObjectMapper objectMapper) {
        this.sogouSearchClient = sogouSearchClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.SOGOU_SEARCH;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        String query = config.path("query").asText("").trim();
        Integer cnt = config.hasNonNull("cnt") ? config.path("cnt").asInt() : null;
        Integer mode = config.hasNonNull("mode") ? config.path("mode").asInt() : null;
        String site = config.path("site").asText("");
        String freshness = config.path("freshness").asText("");

        List<FetchedSogouSearchResult> results = sogouSearchClient.search(new SogouSearchRequest(
                query, cnt, mode, site, freshness, null, null
        ));

        int totalCount = results.size();
        List<CollectedItem> items = new ArrayList<>(totalCount);
        for (FetchedSogouSearchResult result : results) {
            String externalId = sha256Hex(result.url());
            items.add(new CollectedItem(
                    externalId,
                    result.url(),
                    toRawPayload(result, query, totalCount),
                    result.publishedAt(),
                    Instant.now()
            ));
        }
        return new CollectionBatch(items, List.of());
    }

    private JsonNode toRawPayload(FetchedSogouSearchResult result, String query, int totalCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", result.title());
        payload.put("url", result.url());
        payload.put("passage", result.passage());
        payload.put("content", result.content());
        payload.put("site", result.site());
        payload.put("score", result.score());
        payload.put("date", result.publishedAt() == null ? null : result.publishedAt().toString());
        payload.put("rank", result.rank());
        payload.put("totalCount", totalCount);
        payload.put("query", query);
        return payload;
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
