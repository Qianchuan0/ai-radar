package com.airadar.crawl.client.twitter;

import java.time.Instant;

public record TwitterSearchRequest(
        String query,
        int limit,
        Instant topSince,
        Instant latestSince,
        int minLikes,
        int minRetweets,
        int minViews,
        int minFollowers,
        boolean onlyOriginalTweets
) {
}
