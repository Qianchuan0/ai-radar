package com.airadar.crawl.client.duckduckgo;

import com.airadar.crawl.client.htmlsearch.HtmlSearchParseException;
import com.airadar.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock 测试类，用于测试 DuckDuckGoSearchClient 的行为。
 * 注意：这些测试使用 mock HTML，不发送真实网络请求。
 */
class DuckDuckGoSearchClientMockTest {

    @Test
    void testClientCreation() {
        RestClient.Builder restClientBuilder = RestClient.builder();

        assertDoesNotThrow(() -> {
            new DuckDuckGoSearchClient(
                    restClientBuilder,
                    new DuckDuckGoSearchProperties(
                            "https://html.duckduckgo.com",
                            Duration.ofSeconds(3),
                            Duration.ofSeconds(10),
                            1,
                            Duration.ofSeconds(10)
                    )
            );
        });
    }

    @Test
    void testPropertiesValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchProperties(
                    "https://html.duckduckgo.com",
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(10),
                    0,  // invalid maxAttempts
                    Duration.ofSeconds(10)
            );
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchProperties(
                    "https://html.duckduckgo.com",
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(10),
                    4,  // invalid maxAttempts
                    Duration.ofSeconds(10)
            );
        });
    }

    @Test
    void testRequestValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest("", 10, "wt-wt", 7);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest("test", 0, "wt-wt", 7);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest("test", 21, "wt-wt", 7);
        });
    }

    @Test
    void testResultRecord() {
        FetchedDuckDuckGoSearchResult result = new FetchedDuckDuckGoSearchResult(
                "Test Title",
                "https://example.com/redirect",
                "https://example.com",
                "Test snippet",
                1
        );

        assertEquals("Test Title", result.title());
        assertEquals("https://example.com/redirect", result.rawUrl());
        assertEquals("https://example.com", result.url());
        assertEquals("Test snippet", result.snippet());
        assertEquals(1, result.rank());
    }

    @Test
    void testHtmlSearchParseExceptionFactory() {
        BusinessException ex1 = HtmlSearchParseException.blocked("DuckDuckGo");
        assertTrue(ex1.getMessage().contains("blocked"));

        BusinessException ex2 = HtmlSearchParseException.parseFailed("DuckDuckGo", "test reason");
        assertTrue(ex2.getMessage().contains("test reason"));

        BusinessException ex3 = HtmlSearchParseException.emptyResult("DuckDuckGo");
        assertTrue(ex3.getMessage().contains("no results"));
    }
}
