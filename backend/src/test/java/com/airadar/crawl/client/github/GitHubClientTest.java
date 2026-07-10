package com.airadar.crawl.client.github;

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

class GitHubClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendExpectedQueryAndParseRepositories() throws IOException {
        AtomicReference<String> requestQuery = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search/repositories", exchange -> {
            requestQuery.set(exchange.getRequestURI().getQuery());
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200, """
                    {
                      "items": [
                        {
                          "id": 912345678,
                          "name": "openmanus",
                          "full_name": "mannaandpoem/OpenManus",
                          "description": "An open source generalist AI agent.",
                          "html_url": "https://github.com/mannaandpoem/OpenManus",
                          "owner": { "login": "mannaandpoem" },
                          "language": "Python",
                          "topics": ["ai-agent", "llm", "automation"],
                          "stargazers_count": 42000,
                          "forks_count": 5100,
                          "watchers_count": 42000,
                          "open_issues_count": 128,
                          "updated_at": "2026-07-07T09:15:00Z"
                        }
                      ]
                    }
                    """);
        });
        server.start();

        GitHubClient client = new GitHubClient(
                RestClient.builder(),
                new ObjectMapper(),
                new GitHubProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        "test-token"
                )
        );

        List<FetchedGitHubRepository> repositories = client.searchRepositories(new GitHubSearchRequest(
                "ai agent stars:>1000",
                "updated",
                "desc",
                5,
                2
        ));

        assertThat(requestQuery.get()).isEqualTo("q=ai agent stars:>1000&sort=updated&order=desc&per_page=5&page=2");
        assertThat(authorizationHeader.get()).isEqualTo("Bearer test-token");
        assertThat(repositories).hasSize(1);
        FetchedGitHubRepository repository = repositories.get(0);
        assertThat(repository.repoId()).isEqualTo(912345678L);
        assertThat(repository.fullName()).isEqualTo("mannaandpoem/OpenManus");
        assertThat(repository.description()).isEqualTo("An open source generalist AI agent.");
        assertThat(repository.ownerLogin()).isEqualTo("mannaandpoem");
        assertThat(repository.language()).isEqualTo("Python");
        assertThat(repository.topics()).containsExactly("ai-agent", "llm", "automation");
        assertThat(repository.stargazersCount()).isEqualTo(42000);
        assertThat(repository.forksCount()).isEqualTo(5100);
        assertThat(repository.updatedAt()).isEqualTo(Instant.parse("2026-07-07T09:15:00Z"));
    }

    @Test
    void shouldFailOnInvalidJson() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search/repositories", exchange -> writeJson(exchange, 200, "{\"items\":"));
        server.start();

        GitHubClient client = new GitHubClient(
                RestClient.builder(),
                new ObjectMapper(),
                new GitHubProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        ""
                )
        );

        assertThatThrownBy(() -> client.searchRepositories(new GitHubSearchRequest(
                "llm",
                "stars",
                "desc",
                10,
                1
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid GitHub search JSON response.");
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
