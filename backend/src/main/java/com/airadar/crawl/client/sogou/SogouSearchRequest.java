package com.airadar.crawl.client.sogou;

public record SogouSearchRequest(
        String query,
        Integer cnt,
        Integer mode,
        String site,
        String freshness,
        Long fromTime,
        Long toTime
) {
}
