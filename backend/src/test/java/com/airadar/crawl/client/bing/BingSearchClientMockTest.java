package com.airadar.crawl.client.bing;

import com.airadar.crawl.client.htmlsearch.HtmlSearchParseException;
import com.airadar.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock 测试类，用于测试 BingSearchClient 的行为。
 * 注意：这些测试使用 mock HTML，不发送真实网络请求。
 */
class BingSearchClientMockTest {

    @Test
    void testClientCreation() {
        RestClient.Builder restClientBuilder = RestClient.builder();

        assertDoesNotThrow(() -> {
            new BingSearchClient(
                    restClientBuilder,
                    new BingSearchProperties(
                            "https://www.bing.com",
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
            new BingSearchProperties(
                    "https://www.bing.com",
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(10),
                    0,  // invalid maxAttempts
                    Duration.ofSeconds(10)
            );
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchProperties(
                    "https://www.bing.com",
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
            new BingSearchRequest("", 10, "en-US", 7, "moderate");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchRequest("test", 0, "en-US", 7, "moderate");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchRequest("test", 21, "en-US", 7, "moderate");
        });
    }

    @Test
    void testResultRecord() {
        FetchedBingSearchResult result = new FetchedBingSearchResult(
                "Test Title",
                "https://example.com",
                "Test snippet",
                "example.com",
                1
        );

        assertEquals("Test Title", result.title());
        assertEquals("https://example.com", result.url());
        assertEquals("Test snippet", result.snippet());
        assertEquals("example.com", result.displayUrl());
        assertEquals(1, result.rank());
    }

    @Test
    void testHtmlSearchParseExceptionFactory() {
        BusinessException ex1 = HtmlSearchParseException.blocked("Bing");
        assertTrue(ex1.getMessage().contains("blocked"));

        BusinessException ex2 = HtmlSearchParseException.parseFailed("Bing", "test reason");
        assertTrue(ex2.getMessage().contains("test reason"));

        BusinessException ex3 = HtmlSearchParseException.emptyResult("Bing");
        assertTrue(ex3.getMessage().contains("no results"));
    }
}
