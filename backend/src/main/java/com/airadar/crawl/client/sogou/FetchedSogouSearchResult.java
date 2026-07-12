package com.airadar.crawl.client.sogou;

import java.time.Instant;

public record FetchedSogouSearchResult(
        String title,
        String url,
        String passage,
        String content,
        String site,
        double score,
        Instant publishedAt,
        int rank
) {
}
