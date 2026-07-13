package com.airadar.crawl.client.htmlsearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlSearchUrlSanitizerTest {

    @Test
    void testValidHttpUrl() {
        String url = "http://example.com/path";
        String result = HtmlSearchUrlSanitizer.sanitize(url);
        assertEquals(url, result);
    }

    @Test
    void testValidHttpsUrl() {
        String url = "https://example.com/path";
        String result = HtmlSearchUrlSanitizer.sanitize(url);
        assertEquals(url, result);
    }

    @Test
    void testInvalidProtocol() {
        String url = "ftp://example.com/path";
        String result = HtmlSearchUrlSanitizer.sanitize(url);
        assertNull(result);
    }

    @Test
    void testNullUrl() {
        String result = HtmlSearchUrlSanitizer.sanitize(null);
        assertNull(result);
    }

    @Test
    void testBlankUrl() {
        String result = HtmlSearchUrlSanitizer.sanitize("  ");
        assertNull(result);
    }

    @Test
    void testUrlWithTrackingParams() {
        String url = "https://example.com/path?utm_source=test&utm_medium=link&id=123";
        String result = HtmlSearchUrlSanitizer.sanitize(url);
        assertEquals("https://example.com/path?id=123", result);
    }

    @Test
    void testUrlWithFragment() {
        String url = "https://example.com/path#section";
        String result = HtmlSearchUrlSanitizer.sanitize(url);
        assertEquals(url, result);
    }

    @Test
    void testMalformedUrl() {
        String url = "not-a-url";
        String result = HtmlSearchUrlSanitizer.sanitize(url);
        assertNull(result);
    }

    @Test
    void testUrlWithAllTrackingParams() {
        String url = "https://example.com/path?utm_source=test&fbclid=123&gclid=456&id=789";
        String result = HtmlSearchUrlSanitizer.sanitize(url);
        assertEquals("https://example.com/path?id=789", result);
    }

    @Test
    void testUrlWithOnlyTrackingParams() {
        String url = "https://example.com/path?utm_source=test&fbclid=123";
        String result = HtmlSearchUrlSanitizer.sanitize(url);
        assertEquals("https://example.com/path", result);
    }
}
