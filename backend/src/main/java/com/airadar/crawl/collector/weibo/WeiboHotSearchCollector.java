package com.airadar.crawl.collector.weibo;

import com.airadar.crawl.client.weibo.FetchedWeiboHotTopic;
import com.airadar.crawl.client.weibo.WeiboHotSearchClient;
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
import java.util.function.Predicate;

@Component
public class WeiboHotSearchCollector implements SourceCollector {

    private final WeiboHotSearchClient weiboHotSearchClient;
    private final ObjectMapper objectMapper;

    public WeiboHotSearchCollector(WeiboHotSearchClient weiboHotSearchClient, ObjectMapper objectMapper) {
        this.weiboHotSearchClient = weiboHotSearchClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.WEIBO_HOT_SEARCH;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        String query = config.path("query").asText("").trim().toLowerCase();
        boolean includeTopWhenNoMatch = config.path("includeTopWhenNoMatch").asBoolean(false);

        List<FetchedWeiboHotTopic> allTopics = weiboHotSearchClient.fetchHotSearch();

        // Filter by query matching
        List<FetchedWeiboHotTopic> matchedTopics = allTopics.stream()
                .filter(topicMatches(query))
                .toList();

        // If no matches and includeTopWhenNoMatch is true, include top 5 topics
        List<FetchedWeiboHotTopic> topicsToCollect = matchedTopics;
        if (matchedTopics.isEmpty() && includeTopWhenNoMatch && !allTopics.isEmpty()) {
            topicsToCollect = allTopics.stream()
                    .limit(5)
                    .toList();
        }

        List<CollectedItem> items = new ArrayList<>(topicsToCollect.size());
        for (FetchedWeiboHotTopic topic : topicsToCollect) {
            String externalId = topic.mid() != null && !topic.mid().isBlank()
                    ? topic.mid()
                    : sha256Hex(topic.word());
            items.add(new CollectedItem(
                    externalId,
                    "https://s.weibo.com/weibo?q=" + java.net.URLEncoder.encode(topic.word(), java.nio.charset.StandardCharsets.UTF_8),
                    toRawPayload(topic, query),
                    Instant.now(),
                    Instant.now()
            ));
        }

        return new CollectionBatch(items, List.of());
    }

    private Predicate<FetchedWeiboHotTopic> topicMatches(String query) {
        if (query.isBlank()) {
            return topic -> true;
        }
        return topic -> {
            String word = topic.word() != null ? topic.word().toLowerCase() : "";
            String note = topic.note() != null ? topic.note().toLowerCase() : "";
            String category = topic.category() != null ? topic.category().toLowerCase() : "";
            return word.contains(query) || note.contains(query) || category.contains(query);
        };
    }

    private JsonNode toRawPayload(FetchedWeiboHotTopic topic, String query) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("word", topic.word());
        payload.put("note", topic.note());
        payload.put("num", topic.num());
        payload.put("category", topic.category());
        payload.put("mid", topic.mid());
        payload.put("rawHot", topic.rawHot());
        payload.put("rank", topic.rank());
        payload.put("query", query);
        return payload;
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
