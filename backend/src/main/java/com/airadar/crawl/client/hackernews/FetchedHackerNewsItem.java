package com.airadar.crawl.client.hackernews;

import com.fasterxml.jackson.databind.JsonNode;

public record FetchedHackerNewsItem(
        HackerNewsItemResponse item,
        JsonNode rawPayload
) {
}
