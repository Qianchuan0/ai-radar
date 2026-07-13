package com.airadar.crawl.collector.hackernewssearch;

import com.airadar.crawl.client.hackernewssearch.FetchedHackerNewsSearchHit;
import com.airadar.crawl.client.hackernewssearch.HackerNewsSearchClient;
import com.airadar.crawl.client.hackernewssearch.HackerNewsSearchRequest;
import com.airadar.crawl.collector.CollectedItem;
import com.airadar.crawl.collector.CollectionBatch;
import com.airadar.crawl.collector.SourceCollector;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class HackerNewsSearchCollector implements SourceCollector {

    private final HackerNewsSearchClient hackerNewsSearchClient;
    private final ObjectMapper objectMapper;

    public HackerNewsSearchCollector(HackerNewsSearchClient hackerNewsSearchClient, ObjectMapper objectMapper) {
        this.hackerNewsSearchClient = hackerNewsSearchClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.HACKER_NEWS_SEARCH;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        String query = config.path("query").asText("").trim();
        int limit = config.path("limit").asInt(20);
        int freshnessHours = config.path("freshnessHours").asInt(24);

        Instant since = Instant.now().minus(java.time.Duration.ofHours(freshnessHours));

        List<FetchedHackerNewsSearchHit> hits = hackerNewsSearchClient.search(new HackerNewsSearchRequest(
                query,
                limit,
                since
        ));

        List<CollectedItem> items = new ArrayList<>(hits.size());
        for (FetchedHackerNewsSearchHit hit : hits) {
            items.add(new CollectedItem(
                    hit.objectId(),
                    hit.url() != null && !hit.url().isBlank() ? hit.url() : "https://news.ycombinator.com/item?id=" + hit.objectId(),
                    toRawPayload(hit, query, freshnessHours),
                    hit.createdAt(),
                    Instant.now()
            ));
        }

        return new CollectionBatch(items, List.of());
    }

    private JsonNode toRawPayload(FetchedHackerNewsSearchHit hit, String query, int freshnessHours) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("objectId", hit.objectId());
        payload.put("title", hit.title());
        payload.put("url", hit.url());
        payload.put("storyText", hit.storyText());
        payload.put("author", hit.author());
        payload.put("points", hit.points());
        payload.put("numComments", hit.numComments());
        payload.put("createdAt", hit.createdAt() != null ? hit.createdAt().toString() : null);
        payload.put("createdAtI", hit.createdAtI());
        payload.put("query", query);
        payload.put("freshnessHours", freshnessHours);
        return payload;
    }
}
