package com.airadar.crawl.client.twitter;

import com.airadar.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwitterClientTest {

    private HttpServer server;
    private int requestCount;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        requestCount = 0;
    }

    @Test
    void shouldFetchAndParseTweets() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            String query = extractQueryParam(exchange, "query");
            if (query.contains("min_faves")) {
                // Top query page 0
                writeJson(exchange, 200, """
                        {
                          "data": [
                            {
                              "tweet_id": "123456",
                              "text": "AI大模型最新突破",
                              "author_name": "Tech User",
                              "author_username": "techuser",
                              "author_followers": 5000,
                              "author_verified": true,
                              "like_count": 150,
                              "retweet_count": 25,
                              "reply_count": 10,
                              "quote_count": 5,
                              "view_count": 15000,
                              "created_at": "2026-07-12T10:00:00Z"
                            }
                          ]
                        }
                        """);
            } else {
                // Latest query
                writeJson(exchange, 200, """
                        {
                          "data": [
                            {
                              "tweet_id": "234567",
                              "text": "机器学习新框架发布",
                              "author_name": "ML Dev",
                              "author_username": "mldev",
                              "author_followers": 3000,
                              "author_verified": false,
                              "like_count": 80,
                              "retweet_count": 15,
                              "reply_count": 5,
                              "quote_count": 2,
                              "view_count": 8000,
                              "created_at": "2026-07-12T09:00:00Z"
                            }
                          ]
                        }
                        """);
            }
        });
        server.start();

        TwitterClient client = createClient();

        Instant topSince = Instant.now().minusSeconds(86400 * 7);
        Instant latestSince = Instant.now().minusSeconds(86400 * 3);

        List<FetchedTweet> results = client.search(new TwitterSearchRequest(
                "AI programming",
                20,
                topSince,
                latestSince,
                10,
                5,
                500,
                100,
                true
        ));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).tweetId()).isEqualTo("123456");
        assertThat(results.get(0).text()).isEqualTo("AI大模型最新突破");
        assertThat(results.get(0).authorName()).isEqualTo("Tech User");
        assertThat(results.get(0).authorVerified()).isTrue();
        assertThat(requestCount).isGreaterThanOrEqualTo(2); // At least top + latest
    }

    @Test
    void shouldThrowWhenApiKeyNotConfigured() {
        TwitterClient client = new TwitterClient(
                RestClient.builder(),
                new ObjectMapper(),
                new TwitterProperties(
                        "http://localhost:0",
                        "",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1
                )
        );

        Instant since = Instant.now().minusSeconds(86400);

        assertThatThrownBy(() -> client.search(new TwitterSearchRequest(
                "test",
                10,
                since,
                since,
                10,
                5,
                500,
                100,
                true
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void shouldDeduplicateTweetsById() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            writeJson(exchange, 200, """
                    {
                      "data": [
                        {
                          "tweet_id": "123456",
                          "text": "Same tweet",
                          "author_name": "User",
                          "author_username": "user",
                          "author_followers": 1000,
                          "author_verified": false,
                          "like_count": 50,
                          "retweet_count": 10,
                          "reply_count": 5,
                          "quote_count": 2,
                          "view_count": 5000,
                          "created_at": "2026-07-12T10:00:00Z"
                        }
                      ]
                    }
                    """);
        });
        server.start();

        TwitterClient client = createClient();

        Instant since = Instant.now().minusSeconds(86400);

        List<FetchedTweet> results = client.search(new TwitterSearchRequest(
                "test",
                20,
                since,
                since,
                10,
                5,
                500,
                100,
                true
        ));

        // Should only have one tweet even though same ID appears in both top and latest
        assertThat(results).hasSize(1);
        assertThat(results.get(0).tweetId()).isEqualTo("123456");
    }

    @Test
    void shouldApplyQualityFiltering() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            writeJson(exchange, 200, """
                    {
                      "data": [
                        {
                          "tweet_id": "111111",
                          "text": "High quality tweet",
                          "author_name": "Popular User",
                          "author_username": "popuser",
                          "author_followers": 10000,
                          "author_verified": false,
                          "like_count": 200,
                          "retweet_count": 50,
                          "reply_count": 20,
                          "quote_count": 10,
                          "view_count": 20000,
                          "created_at": "2026-07-12T10:00:00Z"
                        },
                        {
                          "tweet_id": "222222",
                          "text": "Low quality tweet",
                          "author_name": "Small User",
                          "author_username": "smalluser",
                          "author_followers": 10,
                          "author_verified": false,
                          "like_count": 1,
                          "retweet_count": 0,
                          "reply_count": 0,
                          "quote_count": 0,
                          "view_count": 50,
                          "created_at": "2026-07-12T10:00:00Z"
                        }
                      ]
                    }
                    """);
        });
        server.start();

        TwitterClient client = createClient();

        Instant since = Instant.now().minusSeconds(86400);

        List<FetchedTweet> results = client.search(new TwitterSearchRequest(
                "test",
                20,
                since,
                since,
                50,  // High min likes
                10,  // High min retweets
                1000, // High min views
                500,  // High min followers
                true
        ));

        // Only high quality tweet should pass
        assertThat(results).hasSize(1);
        assertThat(results.get(0).tweetId()).isEqualTo("111111");
    }

    @Test
    void shouldApplyHalfThresholdsForBlueVUsers() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            writeJson(exchange, 200, """
                    {
                      "data": [
                        {
                          "tweet_id": "333333",
                          "text": "Blue V with lower metrics",
                          "author_name": "Verified User",
                          "author_username": "verified",
                          "author_followers": 250,
                          "author_verified": true,
                          "like_count": 25,
                          "retweet_count": 5,
                          "reply_count": 2,
                          "quote_count": 1,
                          "view_count": 250,
                          "created_at": "2026-07-12T10:00:00Z"
                        }
                      ]
                    }
                    """);
        });
        server.start();

        TwitterClient client = createClient();

        Instant since = Instant.now().minusSeconds(86400);

        List<FetchedTweet> results = client.search(new TwitterSearchRequest(
                "test",
                20,
                since,
                since,
                50,  // Blue V needs only 25
                10,  // Blue V needs only 5
                500,  // Blue V needs only 250
                500,  // Blue V needs only 250
                true
        ));

        // Blue V user should pass with half thresholds
        assertThat(results).hasSize(1);
        assertThat(results.get(0).tweetId()).isEqualTo("333333");
    }

    @Test
    void shouldSortByScoreAndLimit() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            writeJson(exchange, 200, """
                    {
                      "data": [
                        {
                          "tweet_id": "444444",
                          "text": "Low score",
                          "author_name": "User1",
                          "author_username": "user1",
                          "author_followers": 1000,
                          "author_verified": false,
                          "like_count": 10,
                          "retweet_count": 5,
                          "reply_count": 0,
                          "quote_count": 0,
                          "view_count": 1000,
                          "created_at": "2026-07-12T10:00:00Z"
                        },
                        {
                          "tweet_id": "555555",
                          "text": "High score",
                          "author_name": "User2",
                          "author_username": "user2",
                          "author_followers": 10000,
                          "author_verified": false,
                          "like_count": 1000,
                          "retweet_count": 500,
                          "reply_count": 100,
                          "quote_count": 50,
                          "view_count": 100000,
                          "created_at": "2026-07-12T10:00:00Z"
                        },
                        {
                          "tweet_id": "666666",
                          "text": "Medium score",
                          "author_name": "User3",
                          "author_username": "user3",
                          "author_followers": 5000,
                          "author_verified": false,
                          "like_count": 100,
                          "retweet_count": 50,
                          "reply_count": 10,
                          "quote_count": 5,
                          "view_count": 10000,
                          "created_at": "2026-07-12T10:00:00Z"
                        }
                      ]
                    }
                    """);
        });
        server.start();

        TwitterClient client = createClient();

        Instant since = Instant.now().minusSeconds(86400);

        List<FetchedTweet> results = client.search(new TwitterSearchRequest(
                "test",
                2,  // Limit to 2
                since,
                since,
                10,
                1,
                100,
                100,
                true
        ));

        // Should return only top 2 by score
        assertThat(results).hasSize(2);
        assertThat(results.get(0).tweetId()).isEqualTo("555555"); // Highest score
        assertThat(results.get(1).tweetId()).isEqualTo("666666"); // Second highest
    }

    @Test
    void shouldReturnEmptyListWhenNoResults() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            writeJson(exchange, 200, """
                    {"data": []}
                    """);
        });
        server.start();

        TwitterClient client = createClient();

        Instant since = Instant.now().minusSeconds(86400);

        List<FetchedTweet> results = client.search(new TwitterSearchRequest(
                "test",
                10,
                since,
                since,
                10,
                5,
                500,
                100,
                true
        ));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldSkipTweetsWithMissingId() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            writeJson(exchange, 200, """
                    {
                      "data": [
                        {
                          "text": "No ID tweet",
                          "author_name": "User",
                          "author_username": "user",
                          "author_followers": 1000,
                          "author_verified": false,
                          "like_count": 10,
                          "retweet_count": 5,
                          "reply_count": 0,
                          "quote_count": 0,
                          "view_count": 1000,
                          "created_at": "2026-07-12T10:00:00Z"
                        },
                        {
                          "tweet_id": "777777",
                          "text": "Valid tweet",
                          "author_name": "User2",
                          "author_username": "user2",
                          "author_followers": 1000,
                          "author_verified": false,
                          "like_count": 10,
                          "retweet_count": 5,
                          "reply_count": 0,
                          "quote_count": 0,
                          "view_count": 1000,
                          "created_at": "2026-07-12T10:00:00Z"
                        }
                      ]
                    }
                    """);
        });
        server.start();

        TwitterClient client = createClient();

        Instant since = Instant.now().minusSeconds(86400);

        List<FetchedTweet> results = client.search(new TwitterSearchRequest(
                "test",
                10,
                since,
                since,
                10,
                5,
                500,
                100,
                true
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).tweetId()).isEqualTo("777777");
    }

    @Test
    void shouldThrowOnUpstreamError() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 500, "{}"));
        server.start();

        TwitterClient client = createClient();

        Instant since = Instant.now().minusSeconds(86400);

        assertThatThrownBy(() -> client.search(new TwitterSearchRequest(
                "test",
                10,
                since,
                since,
                10,
                5,
                500,
                100,
                true
        )))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldVerifyRequestHeaders() throws IOException {
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            writeJson(exchange, 200, """
                    {"data": []}
                    """);
        });
        server.start();

        TwitterClient client = createClient();

        Instant since = Instant.now().minusSeconds(86400);

        client.search(new TwitterSearchRequest(
                "test",
                10,
                since,
                since,
                10,
                5,
                500,
                100,
                true
        ));

        assertThat(apiKeyHeader.get()).isEqualTo("test-api-key");
    }

    private TwitterClient createClient() {
        return new TwitterClient(
                RestClient.builder(),
                new ObjectMapper(),
                new TwitterProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        "test-api-key",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1
                )
        );
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBody.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBody);
        }
    }

    private String extractQueryParam(HttpExchange exchange, String paramName) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return "";
        }
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(paramName)) {
                return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
