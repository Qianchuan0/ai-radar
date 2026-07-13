package com.airadar.crawl.client.sogou;

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

class SogouSearchClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendSignedPostAndParsePages() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> actionHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            actionHeader.set(exchange.getRequestHeaders().getFirst("X-TC-Action"));
            writeJson(exchange, 200, """
                    {
                      "Response": {
                        "Query": "大模型",
                        "Pages": [
                          "{\\"title\\":\\"大模型发展报告\\",\\"url\\":\\"https://example.com/article1\\",\\"passage\\":\\"大模型最新进展\\",\\"site\\":\\"示例站\\",\\"score\\":0.95,\\"date\\":\\"2026/07/12 10:00:00\\"}",
                          "{\\"title\\":\\"AI智能体应用\\",\\"url\\":\\"https://example.com/article2\\",\\"passage\\":\\"智能体技术\\",\\"site\\":\\"科技网\\",\\"score\\":0.80,\\"date\\":\\"2026/07/11 15:30:00\\"}"
                        ],
                        "Version": "standard",
                        "RequestId": "req-123"
                      }
                    }
                    """);
        });
        server.start();

        SogouSearchClient client = new SogouSearchClient(
                RestClient.builder(),
                new ObjectMapper(),
                new SogouSearchProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        "test-secret-id",
                        "test-secret-key"
                )
        );

        List<FetchedSogouSearchResult> results = client.search(new SogouSearchRequest(
                "大模型", 20, 0, "", "", null, null
        ));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).title()).isEqualTo("大模型发展报告");
        assertThat(results.get(0).url()).isEqualTo("https://example.com/article1");
        assertThat(results.get(0).passage()).isEqualTo("大模型最新进展");
        assertThat(results.get(0).site()).isEqualTo("示例站");
        assertThat(results.get(0).score()).isEqualTo(0.95);
        assertThat(results.get(0).rank()).isEqualTo(1);
        assertThat(results.get(0).publishedAt()).isNotNull();
        assertThat(results.get(1).rank()).isEqualTo(2);
        assertThat(authorizationHeader.get()).startsWith("TC3-HMAC-SHA256 ");
        assertThat(actionHeader.get()).isEqualTo("SearchPro");
        assertThat(requestBody.get()).contains("\"Query\":\"大模型\"");
        assertThat(requestBody.get()).contains("\"Cnt\":20");
    }

    @Test
    void shouldReturnEmptyListWhenPagesIsEmpty() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {"Response":{"Query":"test","Pages":[],"Version":"standard","RequestId":"r"}}
                """));
        server.start();

        SogouSearchClient client = createClient();

        List<FetchedSogouSearchResult> results = client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        ));

        assertThat(results).isEmpty();
    }

    @Test
    void shouldOmitOptionalParametersWhenUnset() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, """
                    {"Response":{"Query":"test","Pages":[],"Version":"lite","RequestId":"r"}}
                    """);
        });
        server.start();

        SogouSearchClient client = createClient();

        List<FetchedSogouSearchResult> results = client.search(new SogouSearchRequest(
                "test", null, null, "", "", null, null
        ));

        assertThat(results).isEmpty();
        assertThat(requestBody.get()).contains("\"Query\":\"test\"");
        assertThat(requestBody.get()).doesNotContain("\"Cnt\"");
        assertThat(requestBody.get()).doesNotContain("\"Mode\"");
    }

    @Test
    void shouldThrowOnTencentCloudResponseError() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {"Response":{"Error":{"Code":"InvalidParameter","Message":"illegal Mode"},"RequestId":"r"}}
                """));
        server.start();

        SogouSearchClient client = createClient();

        assertThatThrownBy(() -> client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("InvalidParameter")
                .hasMessageContaining("illegal Mode");
    }

    @Test
    void shouldThrowOnUpstreamError() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 500, """
                {"Response":{"Error":{"Code":"InternalError","Message":"boom"},"RequestId":"r"}}
                """));
        server.start();

        SogouSearchClient client = createClient();

        assertThatThrownBy(() -> client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        )))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldThrowProviderNotConfiguredWhenCredentialsAreBlank() {
        SogouSearchClient client = new SogouSearchClient(
                RestClient.builder(),
                new ObjectMapper(),
                new SogouSearchProperties(
                        "http://localhost:0",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        "",
                        ""
                )
        );

        assertThatThrownBy(() -> client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void shouldSkipPagesWithMissingTitleOrUrl() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> writeJson(exchange, 200, """
                {"Response":{"Query":"test","Pages":["{\\"title\\":\\"\\",\\"url\\":\\"https://example.com/a\\",\\"passage\\":\\"p\\",\\"site\\":\\"s\\",\\"score\\":0.5}","{\\"title\\":\\"ok\\",\\"url\\":\\"https://example.com/b\\",\\"passage\\":\\"p\\",\\"site\\":\\"s\\",\\"score\\":0.5}"],"Version":"standard","RequestId":"r"}}
                """));
        server.start();

        SogouSearchClient client = createClient();

        List<FetchedSogouSearchResult> results = client.search(new SogouSearchRequest(
                "test", 10, 0, "", "", null, null
        ));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("ok");
    }

    private SogouSearchClient createClient() {
        return new SogouSearchClient(
                RestClient.builder(),
                new ObjectMapper(),
                new SogouSearchProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        "test-secret-id",
                        "test-secret-key"
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
