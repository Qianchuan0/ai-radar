package com.airadar.crawl.client.hackernewssearch;

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

class HackerNewsSearchClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldFetchAndParseSearchHits() throws IOException {
        Instant since = Instant.now().minusSeconds(86400); // 24 hours ago
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {
                  "hits": [
                    {
                      "objectID": "123456",
                      "title": "AI大模型最新进展",
                      "url": "https://example.com/ai-models",
                      "story_text": "详细介绍AI大模型的技术突破和应用场景",
                      "author": "techuser",
                      "points": 150,
                      "num_comments": 42,
                      "created_at_i": 1720473600
                    },
                    {
                      "objectID": "234567",
                      "title": "机器学习框架对比",
                      "url": "https://example.com/ml-frameworks",
                      "story_text": "深度分析主流机器学习框架",
                      "author": "mldev",
                      "points": 89,
                      "num_comments": 25,
                      "created_at_i": 1720387200
                    }
                  ],
                  "nbHits": 2,
                  "page": 1
                }
                """));
        server.start();

        HackerNewsSearchClient client = createClient();

        List<FetchedHackerNewsSearchHit> results = client.search(new HackerNewsSearchRequest(
                "AI",
                20,
                since
        ));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).objectId()).isEqualTo("123456");
        assertThat(results.get(0).title()).isEqualTo("AI大模型最新进展");
        assertThat(results.get(0).url()).isEqualTo("https://example.com/ai-models");
        assertThat(results.get(0).storyText()).isEqualTo("详细介绍AI大模型的技术突破和应用场景");
        assertThat(results.get(0).author()).isEqualTo("techuser");
        assertThat(results.get(0).points()).isEqualTo(150);
        assertThat(results.get(0).numComments()).isEqualTo(42);
        assertThat(results.get(0).createdAt()).isNotNull();
        assertThat(results.get(0).createdAtI()).isEqualTo(1720473600L);
    }

    @Test
    void shouldReturnEmptyListWhenHitsIsEmpty() throws IOException {
        Instant since = Instant.now().minusSeconds(86400);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {"hits": [], "nbHits": 0, "page": 1}
                """));
        server.start();

        HackerNewsSearchClient client = createClient();

        List<FetchedHackerNewsSearchHit> results = client.search(new HackerNewsSearchRequest(
                "test",
                10,
                since
        ));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldUseFallbackUrlWhenOriginalUrlIsMissing() throws IOException {
        Instant since = Instant.now().minusSeconds(86400);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {
                  "hits": [
                    {
                      "objectID": "345678",
                      "title": "HN Discussion Only",
                      "url": null,
                      "story_text": "No external link",
                      "author": "hacker",
                      "points": 50,
                      "num_comments": 10,
                      "created_at_i": 1720300800
                    }
                  ],
                  "nbHits": 1,
                  "page": 1
                }
                """));
        server.start();

        HackerNewsSearchClient client = createClient();

        List<FetchedHackerNewsSearchHit> results = client.search(new HackerNewsSearchRequest(
                "HN",
                10,
                since
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://news.ycombinator.com/item?id=345678");
    }

    @Test
    void shouldSkipHitsWithMissingObjectId() throws IOException {
        Instant since = Instant.now().minusSeconds(86400);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {
                  "hits": [
                    {
                      "title": "No ID",
                      "url": "https://example.com/no-id",
                      "points": 10,
                      "created_at_i": 1720300800
                    },
                    {
                      "objectID": "456789",
                      "title": "Valid Hit",
                      "url": "https://example.com/valid",
                      "points": 20,
                      "created_at_i": 1720300801
                    }
                  ],
                  "nbHits": 2,
                  "page": 1
                }
                """));
        server.start();

        HackerNewsSearchClient client = createClient();

        List<FetchedHackerNewsSearchHit> results = client.search(new HackerNewsSearchRequest(
                "test",
                10,
                since
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).objectId()).isEqualTo("456789");
    }

    @Test
    void shouldHandleMissingOptionalFields() throws IOException {
        Instant since = Instant.now().minusSeconds(86400);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {
                  "hits": [
                    {
                      "objectID": "567890",
                      "title": "Minimal Hit"
                    }
                  ],
                  "nbHits": 1,
                  "page": 1
                }
                """));
        server.start();

        HackerNewsSearchClient client = createClient();

        List<FetchedHackerNewsSearchHit> results = client.search(new HackerNewsSearchRequest(
                "minimal",
                10,
                since
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).objectId()).isEqualTo("567890");
        assertThat(results.get(0).title()).isEqualTo("Minimal Hit");
        assertThat(results.get(0).url()).isEqualTo("https://news.ycombinator.com/item?id=567890");
        assertThat(results.get(0).storyText()).isNull();
        assertThat(results.get(0).author()).isNull();
        assertThat(results.get(0).points()).isEqualTo(0);
        assertThat(results.get(0).numComments()).isEqualTo(0);
    }

    @Test
    void shouldThrowOnUpstreamError() throws IOException {
        Instant since = Instant.now().minusSeconds(86400);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 500, "{}"));
        server.start();

        HackerNewsSearchClient client = createClient();

        assertThatThrownBy(() -> client.search(new HackerNewsSearchRequest(
                "test",
                10,
                since
        )))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowOnInvalidJson() throws IOException {
        Instant since = Instant.now().minusSeconds(86400);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write("invalid json".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        HackerNewsSearchClient client = createClient();

        assertThatThrownBy(() -> client.search(new HackerNewsSearchRequest(
                "test",
                10,
                since
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void shouldHandleEmptyResponse() throws IOException {
        Instant since = Instant.now().minusSeconds(86400);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, ""));
        server.start();

        HackerNewsSearchClient client = createClient();

        List<FetchedHackerNewsSearchHit> results = client.search(new HackerNewsSearchRequest(
                "test",
                10,
                since
        ));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldVerifyRequestParameters() throws IOException {
        AtomicReference<String> requestUri = new AtomicReference<>();
        Instant since = Instant.now().minusSeconds(86400);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            writeJson(exchange, 200, "{\"hits\": [], \"nbHits\": 0, \"page\": 1}");
        });
        server.start();

        HackerNewsSearchClient client = createClient();
        client.search(new HackerNewsSearchRequest("AI programming", 15, since));

        assertThat(requestUri.get()).contains("query=AI%20programming");
        assertThat(requestUri.get()).contains("tags=story");
        assertThat(requestUri.get()).contains("hitsPerPage=15");
        assertThat(requestUri.get()).contains("numericFilters=created_at_i%3E");
    }

    private HackerNewsSearchClient createClient() {
        return new HackerNewsSearchClient(
                RestClient.builder(),
                new ObjectMapper(),
                new HackerNewsSearchProperties(
                        "http://localhost:" + server.getAddress().getPort(),
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
}
