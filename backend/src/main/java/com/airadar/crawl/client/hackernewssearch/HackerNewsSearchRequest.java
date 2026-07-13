package com.airadar.crawl.client.hackernewssearch;

import java.time.Instant;

public record HackerNewsSearchRequest(
        String query,
        int limit,
        Instant since
) {
}
