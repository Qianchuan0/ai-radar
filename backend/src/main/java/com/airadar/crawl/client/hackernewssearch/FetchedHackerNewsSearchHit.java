package com.airadar.crawl.client.hackernewssearch;

import java.time.Instant;

public record FetchedHackerNewsSearchHit(
        String objectId,
        String title,
        String url,
        String storyText,
        String author,
        int points,
        int numComments,
        Instant createdAt,
        long createdAtI
) {
}
