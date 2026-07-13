package com.airadar.crawl.client.bing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BingSearchRequestTest {

    @Test
    void testValidRequest() {
        BingSearchRequest request = new BingSearchRequest(
                "AI agent",
                10,
                "en-US",
                7,
                "moderate"
        );

        assertEquals("AI agent", request.query());
        assertEquals(10, request.limit());
        assertEquals("en-US", request.market());
        assertEquals(7, request.freshnessDays());
        assertEquals("moderate", request.safeSearch());
    }

    @Test
    void testBlankQueryThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchRequest("  ", 10, "en-US", 7, "moderate");
        });
    }

    @Test
    void testNullQueryThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchRequest(null, 10, "en-US", 7, "moderate");
        });
    }

    @Test
    void testLimitTooLowThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchRequest("test", 0, "en-US", 7, "moderate");
        });
    }

    @Test
    void testLimitTooHighThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchRequest("test", 21, "en-US", 7, "moderate");
        });
    }

    @Test
    void testFreshnessDaysTooLowThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchRequest("test", 10, "en-US", 0, "moderate");
        });
    }

    @Test
    void testFreshnessDaysTooHighThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BingSearchRequest("test", 10, "en-US", 31, "moderate");
        });
    }

    @Test
    void testEdgeCaseValidValues() {
        assertDoesNotThrow(() -> {
            new BingSearchRequest("test", 1, "en-US", 1, "off");
        });

        assertDoesNotThrow(() -> {
            new BingSearchRequest("test", 20, "en-US", 30, "strict");
        });
    }
}
