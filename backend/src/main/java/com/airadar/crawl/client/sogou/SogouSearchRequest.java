package com.airadar.crawl.client.sogou;

public record SogouSearchRequest(
        String query,
        int cnt,
        int mode,
        String site,
        String freshness,
        Long fromTime,
        Long toTime
) {
}
