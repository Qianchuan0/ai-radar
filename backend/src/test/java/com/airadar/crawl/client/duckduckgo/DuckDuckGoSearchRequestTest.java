package com.airadar.crawl.client.duckduckgo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuckDuckGoSearchRequestTest {

    @Test
    void testValidRequest() {
        DuckDuckGoSearchRequest request = new DuckDuckGoSearchRequest(
                "AI agent",
                10,
                "wt-wt",
                7
        );

        assertEquals("AI agent", request.query());
        assertEquals(10, request.limit());
        assertEquals("wt-wt", request.region());
        assertEquals(7, request.freshnessDays());
    }

    @Test
    void testBlankQueryThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest("  ", 10, "wt-wt", 7);
        });
    }

    @Test
    void testNullQueryThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest(null, 10, "wt-wt", 7);
        });
    }

    @Test
    void testLimitTooLowThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest("test", 0, "wt-wt", 7);
        });
    }

    @Test
    void testLimitTooHighThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest("test", 21, "wt-wt", 7);
        });
    }

    @Test
    void testFreshnessDaysTooLowThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest("test", 10, "wt-wt", 0);
        });
    }

    @Test
    void testFreshnessDaysTooHighThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DuckDuckGoSearchRequest("test", 10, "wt-wt", 31);
        });
    }

    @Test
    void testEdgeCaseValidValues() {
        assertDoesNotThrow(() -> {
            new DuckDuckGoSearchRequest("test", 1, "wt-wt", 1);
        });

        assertDoesNotThrow(() -> {
            new DuckDuckGoSearchRequest("test", 20, "wt-wt", 30);
        });
    }

    @Test
    void testDifferentRegion() {
        assertDoesNotThrow(() -> {
            new DuckDuckGoSearchRequest("test", 10, "zh-cn", 7);
        });
    }
}
