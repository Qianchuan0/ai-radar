package com.airadar.crawl.client.arxiv;

import com.airadar.common.exception.BusinessException;
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

class ArxivClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendExpectedQueryAndParseAtomEntries() throws IOException {
        AtomicReference<String> requestQuery = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/query", exchange -> {
            requestQuery.set(exchange.getRequestURI().getQuery());
            writeXml(exchange, 200, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <feed xmlns="http://www.w3.org/2005/Atom"
                          xmlns:arxiv="http://arxiv.org/schemas/atom">
                      <entry>
                        <id>http://arxiv.org/abs/2501.01234v2</id>
                        <updated>2026-07-01T12:00:00Z</updated>
                        <published>2026-06-30T08:30:00Z</published>
                        <title>
                          Agentic Systems for Reliable Tool Use
                        </title>
                        <summary>
                          This paper studies reliable tool use in multi-agent systems.
                        </summary>
                        <author><name>Alice Zhang</name></author>
                        <author><name>Bob Li</name></author>
                        <link rel="alternate" type="text/html" href="http://arxiv.org/abs/2501.01234v2"/>
                        <link title="pdf" rel="related" type="application/pdf" href="http://arxiv.org/pdf/2501.01234v2"/>
                        <arxiv:id>2501.01234v2</arxiv:id>
                        <category term="cs.AI"/>
                        <category term="cs.LG"/>
                      </entry>
                    </feed>
                    """);
        });
        server.start();

        ArxivClient client = new ArxivClient(
                RestClient.builder(),
                new ArxivProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        Duration.ZERO
                )
        );

        List<FetchedArxivPaper> papers = client.search(new ArxivSearchRequest(
                "all:agentic systems",
                5,
                10,
                ArxivSortBy.SUBMITTED_DATE,
                ArxivSortOrder.DESCENDING
        ));

        assertThat(requestQuery.get()).isEqualTo(
                "search_query=all:agentic systems&start=5&max_results=10&sortBy=submittedDate&sortOrder=descending"
        );
        assertThat(papers).hasSize(1);
        FetchedArxivPaper paper = papers.get(0);
        assertThat(paper.arxivId()).isEqualTo("2501.01234v2");
        assertThat(paper.title()).isEqualTo("Agentic Systems for Reliable Tool Use");
        assertThat(paper.summary()).isEqualTo("This paper studies reliable tool use in multi-agent systems.");
        assertThat(paper.authors()).containsExactly("Alice Zhang", "Bob Li");
        assertThat(paper.categories()).containsExactly("cs.AI", "cs.LG");
        assertThat(paper.publishedAt()).isEqualTo(Instant.parse("2026-06-30T08:30:00Z"));
        assertThat(paper.pdfUrl()).isEqualTo("http://arxiv.org/pdf/2501.01234v2");
        assertThat(paper.sourceUrl()).isEqualTo("http://arxiv.org/abs/2501.01234v2");
    }

    @Test
    void shouldFailOnInvalidXml() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/query", exchange -> writeXml(exchange, 200, "<feed><entry>"));
        server.start();

        ArxivClient client = new ArxivClient(
                RestClient.builder(),
                new ArxivProperties(
                        "http://localhost:" + server.getAddress().getPort(),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        1,
                        Duration.ZERO
                )
        );

        assertThatThrownBy(() -> client.search(new ArxivSearchRequest(
                "all:llm",
                0,
                5,
                ArxivSortBy.RELEVANCE,
                ArxivSortOrder.DESCENDING
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid arXiv Atom XML response.");
    }

    private void writeXml(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/atom+xml; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBody.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBody);
        }
    }
}
