package com.airadar.crawl.client.weibo;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeiboHotSearchClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldFetchAndParseHotSearchTopics() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {
                  "ok": 1,
                  "data": {
                    "realtime": [
                      {
                        "word": "AI大模型发展",
                        "note": "最新技术突破",
                        "num": 2500000,
                        "category": "科技",
                        "mid": "123456",
                        "raw_hot": 1200000
                      },
                      {
                        "word": "智能助手应用",
                        "note": "用户体验提升",
                        "num": 1800000,
                        "category": "科技",
                        "mid": "234567",
                        "raw_hot": 900000
                      }
                    ]
                  }
                }
                """));
        server.start();

        WeiboHotSearchClient client = createClient();

        List<FetchedWeiboHotTopic> results = client.fetchHotSearch();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).word()).isEqualTo("AI大模型发展");
        assertThat(results.get(0).note()).isEqualTo("最新技术突破");
        assertThat(results.get(0).num()).isEqualTo(2500000L);
        assertThat(results.get(0).category()).isEqualTo("科技");
        assertThat(results.get(0).mid()).isEqualTo("123456");
        assertThat(results.get(0).rawHot()).isEqualTo(1200000L);
        assertThat(results.get(0).rank()).isEqualTo(1);

        assertThat(results.get(1).word()).isEqualTo("智能助手应用");
        assertThat(results.get(1).rank()).isEqualTo(2);
    }

    @Test
    void shouldReturnEmptyListWhenRealtimeIsEmpty() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {"ok": 1, "data": {"realtime": []}}
                """));
        server.start();

        WeiboHotSearchClient client = createClient();

        List<FetchedWeiboHotTopic> results = client.fetchHotSearch();

        assertThat(results).isEmpty();
    }

    @Test
    void shouldThrowWhenOkIsNot1() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {"ok": 0, "data": {"realtime": []}}
                """));
        server.start();

        WeiboHotSearchClient client = createClient();

        assertThatThrownBy(() -> client.fetchHotSearch())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ok != 1");
    }

    @Test
    void shouldThrowOnUpstreamError() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 500, "{}"));
        server.start();

        WeiboHotSearchClient client = createClient();

        assertThatThrownBy(() -> client.fetchHotSearch())
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowOnInvalidJson() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write("invalid json".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        WeiboHotSearchClient client = createClient();

        assertThatThrownBy(() -> client.fetchHotSearch())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    void shouldSkipTopicsWithBlankWord() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {
                  "ok": 1,
                  "data": {
                    "realtime": [
                      {
                        "word": "",
                        "note": "empty word",
                        "num": 1000,
                        "category": "test"
                      },
                      {
                        "word": "valid topic",
                        "note": "valid",
                        "num": 2000,
                        "category": "test"
                      }
                    ]
                  }
                }
                """));
        server.start();

        WeiboHotSearchClient client = createClient();

        List<FetchedWeiboHotTopic> results = client.fetchHotSearch();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).word()).isEqualTo("valid topic");
        assertThat(results.get(0).rank()).isEqualTo(2);
    }

    @Test
    void shouldHandleMissingOptionalFields() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {
                  "ok": 1,
                  "data": {
                    "realtime": [
                      {
                        "word": "minimal topic"
                      }
                    ]
                  }
                }
                """));
        server.start();

        WeiboHotSearchClient client = createClient();

        List<FetchedWeiboHotTopic> results = client.fetchHotSearch();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).word()).isEqualTo("minimal topic");
        assertThat(results.get(0).note()).isNull();
        assertThat(results.get(0).category()).isNull();
        assertThat(results.get(0).mid()).isNull();
        assertThat(results.get(0).num()).isEqualTo(0L);
        assertThat(results.get(0).rawHot()).isEqualTo(0L);
    }

    @Test
    void shouldHandleEmptyResponse() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, ""));
        server.start();

        WeiboHotSearchClient client = createClient();

        List<FetchedWeiboHotTopic> results = client.fetchHotSearch();

        assertThat(results).isEmpty();
    }

    @Test
    void shouldVerifyRequestHeaders() throws IOException {
        AtomicReference<String> refererHeader = new AtomicReference<>();
        AtomicReference<String> acceptHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            refererHeader.set(exchange.getRequestHeaders().getFirst("Referer"));
            acceptHeader.set(exchange.getRequestHeaders().getFirst("Accept"));
            writeJson(exchange, 200, "{\"ok\": 1, \"data\": {\"realtime\": []}}");
        });
        server.start();

        WeiboHotSearchClient client = createClient();
        client.fetchHotSearch();

        assertThat(refererHeader.get()).isEqualTo("https://weibo.com/");
        assertThat(acceptHeader.get()).isEqualTo("application/json");
    }

    private WeiboHotSearchClient createClient() {
        return new WeiboHotSearchClient(
                RestClient.builder(),
                new ObjectMapper(),
                new WeiboHotSearchProperties(
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
