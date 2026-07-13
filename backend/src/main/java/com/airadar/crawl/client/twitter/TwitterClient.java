package com.airadar.crawl.client.twitter;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TwitterClient {

    private static final int TOP_MAX_PAGES = 2;
    private static final int LATEST_MAX_PAGES = 1;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final String apiKey;
    private final boolean configured;

    public TwitterClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            TwitterProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.maxAttempts = properties.maxAttempts();
        this.apiKey = properties.apiKey();
        this.configured = properties.isConfigured();
    }

    public List<FetchedTweet> search(TwitterSearchRequest request) {
        if (!configured) {
            throw new BusinessException(
                    ErrorCode.CRAWL_PROVIDER_NOT_CONFIGURED,
                    "Twitter API key is not configured."
            );
        }

        Set<String> seenIds = new HashSet<>();
        List<FetchedTweet> allResults = new ArrayList<>();

        // Fetch top tweets (up to 2 pages)
        for (int page = 0; page < TOP_MAX_PAGES; page++) {
            String searchQuery = buildSearchQuery(request.query(), request.topSince(), request.minLikes(), true);
            List<FetchedTweet> pageResults = fetchPage(searchQuery, page);
            for (FetchedTweet tweet : pageResults) {
                if (seenIds.add(tweet.tweetId())) {
                    allResults.add(tweet);
                }
            }
            if (pageResults.isEmpty()) {
                break;
            }
        }

        // Fetch latest tweets (up to 1 page)
        String latestQuery = buildSearchQuery(request.query(), request.latestSince(), 0, false);
        List<FetchedTweet> latestResults = fetchPage(latestQuery, 0);
        for (FetchedTweet tweet : latestResults) {
            if (seenIds.add(tweet.tweetId())) {
                allResults.add(tweet);
            }
        }

        // Apply quality filtering
        List<FetchedTweet> filtered = allResults.stream()
                .filter(tweet -> passesQualityFilter(tweet, request))
                .collect(Collectors.toList());

        // Sort by combined score (likes + retweets*3 + views/100) and limit
        return filtered.stream()
                .sorted(Comparator.comparingLong(this::calculateScore).reversed())
                .limit(request.limit())
                .toList();
    }

    private String buildSearchQuery(String query, Instant since, int minLikes, boolean isTop) {
        StringBuilder sb = new StringBuilder(query);

        // Filter out retweets and replies
        if (query != null && !query.contains("-filter:retweets")) {
            sb.append(" -filter:retweets -filter:replies");
        }

        // Add since date
        if (since != null) {
            String dateStr = since.atZone(java.time.ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            sb.append(" since:").append(dateStr);
        }

        // Add minimum faves for top query
        if (isTop && minLikes > 0) {
            sb.append(" min_faves:").append(minLikes);
        }

        return sb.toString();
    }

    private List<FetchedTweet> fetchPage(String searchQuery, int page) {
        String responseBody = executeWithRetry(searchQuery, page);
        return parseResponse(responseBody);
    }

    private String executeWithRetry(String searchQuery, int page) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/twitter/tweet/advanced_search")
                                .queryParam("query", searchQuery)
                                .queryParam("page", page)
                                .build())
                        .header("X-API-Key", apiKey)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (httpRequest, response) -> {
                            throw new BusinessException(
                                    ErrorCode.CRAWL_UPSTREAM_ERROR,
                                    "Twitter API returned " + response.getStatusCode()
                            );
                        })
                        .body(String.class);
            } catch (ResourceAccessException exception) {
                lastFailure = exception;
            } catch (RestClientResponseException exception) {
                if (!exception.getStatusCode().is5xxServerError()) {
                    throw exception;
                }
                lastFailure = exception;
            } catch (BusinessException exception) {
                throw exception;
            }
            if (attempt < maxAttempts) {
                pauseBeforeRetry(attempt);
            }
        }
        throw new BusinessException(
                ErrorCode.CRAWL_UPSTREAM_ERROR,
                lastFailure == null ? ErrorCode.CRAWL_UPSTREAM_ERROR.getDefaultMessage() : lastFailure.getMessage()
        );
    }

    private List<FetchedTweet> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode tweets = root.path("tweets");
            if (!tweets.isArray()) {
                tweets = root.path("data");
            }
            if (!tweets.isArray()) {
                return List.of();
            }

            List<FetchedTweet> results = new ArrayList<>(tweets.size());
            for (JsonNode tweetNode : tweets) {
                JsonNode authorNode = tweetNode.path("author");
                String tweetId = nullableText(tweetNode, "id", "tweet_id");
                String text = nullableText(tweetNode, "text");
                String authorName = firstNonBlank(nullableText(authorNode, "name"), nullableText(tweetNode, "author_name"));
                String authorUsername = firstNonBlank(nullableText(authorNode, "userName"), nullableText(tweetNode, "author_username"));
                long authorFollowers = firstLong(authorNode, "followers", -1L);
                if (authorFollowers < 0L) {
                    authorFollowers = firstLong(tweetNode, "author_followers", 0L);
                }
                boolean authorVerified = hasField(authorNode, "isBlueVerified")
                        ? authorNode.path("isBlueVerified").asBoolean(false)
                        : tweetNode.path("author_verified").asBoolean(false);
                int likeCount = firstInt(tweetNode, "likeCount", "like_count");
                int retweetCount = firstInt(tweetNode, "retweetCount", "retweet_count");
                int replyCount = firstInt(tweetNode, "replyCount", "reply_count");
                int quoteCount = firstInt(tweetNode, "quoteCount", "quote_count");
                long viewCount = firstLong(tweetNode, "viewCount", "view_count");
                String createdAt = nullableText(tweetNode, "createdAt", "created_at");

                if (tweetId == null || tweetId.isBlank()) {
                    continue;
                }

                results.add(new FetchedTweet(
                        tweetId,
                        text,
                        authorName,
                        authorUsername,
                        authorFollowers,
                        authorVerified,
                        likeCount,
                        retweetCount,
                        replyCount,
                        quoteCount,
                        viewCount,
                        createdAt
                ));
            }

            return List.copyOf(results);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Invalid JSON: Twitter API response could not be parsed.");
        }
    }

    private boolean passesQualityFilter(FetchedTweet tweet, TwitterSearchRequest request) {
        int minLikes = request.minLikes();
        int minRetweets = request.minRetweets();
        int minViews = request.minViews();
        int minFollowers = request.minFollowers();
        boolean authorVerified = tweet.authorVerified();

        // Blue V users get half thresholds
        if (authorVerified) {
            minLikes = Math.max(1, minLikes / 2);
            minRetweets = Math.max(1, minRetweets / 2);
            minViews = Math.max(1, minViews / 2);
            minFollowers = Math.max(1, minFollowers / 2);
        }

        return tweet.likeCount() >= minLikes
                && tweet.retweetCount() >= minRetweets
                && tweet.viewCount() >= minViews
                && tweet.authorFollowers() >= minFollowers;
    }

    private long calculateScore(FetchedTweet tweet) {
        return (long) tweet.likeCount()
                + (long) tweet.retweetCount() * 3
                + tweet.viewCount() / 100;
    }

    private String nullableText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            String text = value.asText(null);
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private int firstInt(JsonNode node, String firstFieldName, String secondFieldName) {
        if (hasField(node, firstFieldName)) {
            return node.path(firstFieldName).asInt(0);
        }
        return node.path(secondFieldName).asInt(0);
    }

    private long firstLong(JsonNode node, String firstFieldName, String secondFieldName) {
        if (hasField(node, firstFieldName)) {
            return node.path(firstFieldName).asLong(0L);
        }
        return node.path(secondFieldName).asLong(0L);
    }

    private long firstLong(JsonNode node, String fieldName, long defaultValue) {
        if (hasField(node, fieldName)) {
            return node.path(fieldName).asLong(defaultValue);
        }
        return defaultValue;
    }

    private boolean hasField(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return !value.isMissingNode() && !value.isNull();
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CRAWL_UPSTREAM_ERROR, "Twitter API request was interrupted.");
        }
    }
}
