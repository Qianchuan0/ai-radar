package com.airadar.crawl.client.twitter;

public record FetchedTweet(
        String tweetId,
        String text,
        String authorName,
        String authorUsername,
        long authorFollowers,
        boolean authorVerified,
        int likeCount,
        int retweetCount,
        int replyCount,
        int quoteCount,
        long viewCount,
        String createdAt
) {
}
