package com.airadar.crawl.collector.hackernews;

import com.airadar.common.exception.BusinessException;
import com.airadar.crawl.client.hackernews.FetchedHackerNewsItem;
import com.airadar.crawl.client.hackernews.HackerNewsClient;
import com.airadar.crawl.client.hackernews.HackerNewsItemResponse;
import com.airadar.crawl.collector.CollectedItem;
import com.airadar.crawl.collector.CollectionBatch;
import com.airadar.crawl.collector.CollectionError;
import com.airadar.crawl.collector.SourceCollector;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class HackerNewsCollector implements SourceCollector {

    private static final int DEFAULT_FETCH_LIMIT = 100;
    private static final int MAX_FETCH_LIMIT = 100;
    private static final String HN_DISCUSSION_URL = "https://news.ycombinator.com/item?id=";

    private final HackerNewsClient hackerNewsClient;

    public HackerNewsCollector(HackerNewsClient hackerNewsClient) {
        this.hackerNewsClient = hackerNewsClient;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.HACKER_NEWS;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        int fetchLimit = resolveFetchLimit(sourceConfig.getConfigPayload());
        List<Long> storyIds = hackerNewsClient.fetchTopStoryIds().stream().limit(fetchLimit).toList();
        List<CollectedItem> items = new ArrayList<>();
        List<CollectionError> errors = new ArrayList<>();

        for (Long storyId : storyIds) {
            try {
                hackerNewsClient.fetchItem(storyId).ifPresent(fetched -> items.add(toCollectedItem(fetched)));
            } catch (BusinessException exception) {
                errors.add(new CollectionError(
                        String.valueOf(storyId),
                        exception.getErrorCode().getCode(),
                        exception.getMessage(),
                        true
                ));
            } catch (RuntimeException exception) {
                errors.add(new CollectionError(
                        String.valueOf(storyId),
                        "CRAWL.HN_ITEM_FAILED",
                        exception.getMessage(),
                        false
                ));
            }
        }
        return new CollectionBatch(items, errors);
    }

    private CollectedItem toCollectedItem(FetchedHackerNewsItem fetched) {
        HackerNewsItemResponse item = fetched.item();
        String sourceUrl = item.url() == null || item.url().isBlank()
                ? HN_DISCUSSION_URL + item.id()
                : item.url();
        Instant publishedAt = item.time() == null ? null : Instant.ofEpochSecond(item.time());
        return new CollectedItem(
                String.valueOf(item.id()),
                sourceUrl,
                fetched.rawPayload(),
                publishedAt,
                Instant.now()
        );
    }

    private int resolveFetchLimit(JsonNode config) {
        if (config == null || !config.has("fetchLimit")) {
            return DEFAULT_FETCH_LIMIT;
        }
        return Math.max(1, Math.min(config.path("fetchLimit").asInt(DEFAULT_FETCH_LIMIT), MAX_FETCH_LIMIT));
    }
}
