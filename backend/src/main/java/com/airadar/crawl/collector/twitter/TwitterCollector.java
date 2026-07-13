package com.airadar.crawl.collector.twitter;

import com.airadar.crawl.client.twitter.FetchedTweet;
import com.airadar.crawl.client.twitter.TwitterClient;
import com.airadar.crawl.client.twitter.TwitterSearchRequest;
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

@Component
public class TwitterCollector implements SourceCollector {

    private final TwitterClient twitterClient;
    private final ObjectMapper objectMapper;

    public TwitterCollector(TwitterClient twitterClient, ObjectMapper objectMapper) {
        this.twitterClient = twitterClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceType supportedType() {
        return SourceType.TWITTER;
    }

    @Override
    public CollectionBatch collect(SourceConfigEntity sourceConfig) {
        JsonNode config = sourceConfig.getConfigPayload();
        String query = config.path("query").asText("").trim();
        int limit = config.path("limit").asInt(20);
        int topDays = config.path("topDays").asInt(7);
        int latestDays = config.path("latestDays").asInt(3);
        int minLikes = config.path("minLikes").asInt(10);
        int minRetweets = config.path("minRetweets").asInt(5);
        int minViews = config.path("minViews").asInt(500);
        int minFollowers = config.path("minFollowers").asInt(100);
        boolean onlyOriginalTweets = config.path("onlyOriginalTweets").asBoolean(true);

        Instant topSince = Instant.now().minus(java.time.Duration.ofDays(topDays));
        Instant latestSince = Instant.now().minus(java.time.Duration.ofDays(latestDays));

        List<FetchedTweet> tweets = twitterClient.search(new TwitterSearchRequest(
                query,
                limit,
                topSince,
                latestSince,
                minLikes,
                minRetweets,
                minViews,
                minFollowers,
                onlyOriginalTweets
        ));

        List<CollectedItem> items = new ArrayList<>(tweets.size());
        for (FetchedTweet tweet : tweets) {
            items.add(new CollectedItem(
                    tweet.tweetId(),
                    buildTweetUrl(tweet.tweetId()),
                    toRawPayload(tweet, query),
                    parseCreatedAt(tweet.createdAt()),
                    Instant.now()
            ));
        }

        return new CollectionBatch(items, List.of());
    }

    private String buildTweetUrl(String tweetId) {
        return "https://twitter.com/i/web/status/" + tweetId;
    }

    private JsonNode toRawPayload(FetchedTweet tweet, String query) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("tweetId", tweet.tweetId());
        payload.put("text", tweet.text());
        payload.put("authorName", tweet.authorName());
        payload.put("authorUsername", tweet.authorUsername());
        payload.put("authorFollowers", tweet.authorFollowers());
        payload.put("authorVerified", tweet.authorVerified());
        payload.put("likeCount", tweet.likeCount());
        payload.put("retweetCount", tweet.retweetCount());
        payload.put("replyCount", tweet.replyCount());
        payload.put("quoteCount", tweet.quoteCount());
        payload.put("viewCount", tweet.viewCount());
        payload.put("createdAt", tweet.createdAt());
        payload.put("query", query);
        return payload;
    }

    private Instant parseCreatedAt(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(createdAt);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
