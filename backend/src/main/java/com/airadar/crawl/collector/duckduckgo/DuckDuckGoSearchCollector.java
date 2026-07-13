package com.airadar.crawl.collector.duckduckgo;

import com.airadar.crawl.client.duckduckgo.DuckDuckGoSearchClient;
import com.airadar.crawl.client.duckduckgo.DuckDuckGoSearchRequest;
import com.airadar.crawl.client.duckduckgo.FetchedDuckDuckGoSearchResult;
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
public class DuckDuckGoSearchCollector implements SourceCollector {

    private final DuckDuckGoSearchClient duckDuckGoSearchClient;
    private final ObjectMapper objectMapper;

    public DuckDuckGoSearchCollector(DuckDuckGoSearchClient duckDuckGoSearchClient, ObjectMapper objectMapper) {
        this.duckDuckGoSearchClient = duckDuckGoSearchClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.DUCKDUCKGO_SEARCH;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        String query = config.path("query").asText("").trim();
        int limit = config.path("limit").asInt(10);
        String region = config.path("region").asText("wt-wt");
        int freshnessDays = config.path("freshnessDays").asInt(7);

        List<FetchedDuckDuckGoSearchResult> results = duckDuckGoSearchClient.search(new DuckDuckGoSearchRequest(
                query, limit, region, freshnessDays
        ));

        int totalCount = results.size();
        List<CollectedItem> items = new ArrayList<>(totalCount);

        for (FetchedDuckDuckGoSearchResult result : results) {
            String externalId = sha256Hex(result.url());
            items.add(new CollectedItem(
                    externalId,
                    result.url(),
                    toRawPayload(result, query, region, totalCount),
                    null, // publishedAt
                    Instant.now()
            ));
        }

        return new CollectionBatch(items, List.of());
    }

    private JsonNode toRawPayload(FetchedDuckDuckGoSearchResult result, String query, String region, int totalCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", result.title());
        payload.put("url", result.url());
        payload.put("rawUrl", result.rawUrl());
        payload.put("snippet", result.snippet());
        payload.put("rank", result.rank());
        payload.put("query", query);
        payload.put("region", region);
        payload.put("totalCount", totalCount);
        payload.put("fetchedFrom", "DuckDuckGo Search");
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
