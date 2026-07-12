package com.airadar.crawl.client.huggingface;

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

class HuggingFaceClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendExpectedQueryAndParseModels() throws IOException {
        AtomicReference<String> requestQuery = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/models", exchange -> {
            requestQuery.set(exchange.getRequestURI().getQuery());
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200, """
                    [
                      {
                        "id": "meta-llama/Llama-3.1-8B-Instruct",
                        "downloads": 987654,
                        "likes": 3210,
                        "tags": ["transformers", "text-generation", "llama"],
                        "pipeline_tag": "text-generation",
                        "library_name": "transformers",
                        "createdAt": "2026-07-08T10:15:30.000Z",
                        "lastModified": "2026-07-09T09:00:00.000Z",
                        "private": false,
                        "modelId": "meta-llama/Llama-3.1-8B-Instruct"
                      }
                    ]
                    """);
        });
        server.start();

        HuggingFaceClient client = new HuggingFaceClient(
                RestClient.builder(),
                new ObjectMapper(),
                new HuggingFaceProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        "hf-test-token"
                )
        );

        List<FetchedHuggingFaceModel> models = client.searchModels(new HuggingFaceModelsRequest(
                "text-generation",
                "downloads",
                "desc",
                5,
                "text-generation"
        ));

        assertThat(requestQuery.get())
                .isEqualTo("search=text-generation&sort=downloads&direction=-1&limit=5&pipeline_tag=text-generation");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer hf-test-token");
        assertThat(models).hasSize(1);
        FetchedHuggingFaceModel model = models.get(0);
        assertThat(model.modelId()).isEqualTo("meta-llama/Llama-3.1-8B-Instruct");
        assertThat(model.id()).isEqualTo("meta-llama/Llama-3.1-8B-Instruct");
        assertThat(model.downloads()).isEqualTo(987654);
        assertThat(model.likes()).isEqualTo(3210);
        assertThat(model.tags()).containsExactly("transformers", "text-generation", "llama");
        assertThat(model.pipelineTag()).isEqualTo("text-generation");
        assertThat(model.author()).isEqualTo("meta-llama");
        assertThat(model.createdAt()).isEqualTo(Instant.parse("2026-07-08T10:15:30Z"));
        assertThat(model.lastModified()).isEqualTo(Instant.parse("2026-07-09T09:00:00Z"));
    }

    @Test
    void shouldFailOnInvalidJson() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/models", exchange -> writeJson(exchange, 200, "["));
        server.start();

        HuggingFaceClient client = new HuggingFaceClient(
                RestClient.builder(),
                new ObjectMapper(),
                new HuggingFaceProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        ""
                )
        );

        assertThatThrownBy(() -> client.searchModels(new HuggingFaceModelsRequest(
                "text-generation",
                "downloads",
                "desc",
                10,
                "text-generation"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid Hugging Face models JSON response.");
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
