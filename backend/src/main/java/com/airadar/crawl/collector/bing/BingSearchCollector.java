package com.airadar.crawl.collector.bing;

import com.airadar.crawl.client.bing.BingSearchClient;
import com.airadar.crawl.client.bing.BingSearchRequest;
import com.airadar.crawl.client.bing.FetchedBingSearchResult;
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
public class BingSearchCollector implements SourceCollector {

    private final BingSearchClient bingSearchClient;
    private final ObjectMapper objectMapper;

    public BingSearchCollector(BingSearchClient bingSearchClient, ObjectMapper objectMapper) {
        this.bingSearchClient = bingSearchClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.BING_SEARCH;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        String query = config.path("query").asText("").trim();
        int limit = config.path("limit").asInt(10);
        String market = config.path("market").asText("en-US");
        int freshnessDays = config.path("freshnessDays").asInt(7);
        String safeSearch = config.path("safeSearch").asText("moderate");

        List<FetchedBingSearchResult> results = bingSearchClient.search(new BingSearchRequest(
                query, limit, market, freshnessDays, safeSearch
        ));

        int totalCount = results.size();
        List<CollectedItem> items = new ArrayList<>(totalCount);

        for (FetchedBingSearchResult result : results) {
            String externalId = sha256Hex(result.url());
            items.add(new CollectedItem(
                    externalId,
                    result.url(),
                    toRawPayload(result, query, market, totalCount),
                    null, // publishedAt
                    Instant.now()
            ));
        }

        return new CollectionBatch(items, List.of());
    }

    private JsonNode toRawPayload(FetchedBingSearchResult result, String query, String market, int totalCount) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("title", result.title());
        payload.put("url", result.url());
        payload.put("snippet", result.snippet());
        payload.put("displayUrl", result.displayUrl());
        payload.put("rank", result.rank());
        payload.put("query", query);
        payload.put("market", market);
        payload.put("totalCount", totalCount);
        payload.put("fetchedFrom", "Bing Search");
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
