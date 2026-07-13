package com.airadar.crawl.client.htmlsearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlSearchBlockDetectorTest {

    @Test
    void testCaptchaPage() {
        String content = "<html>Please complete the CAPTCHA to continue</html>";
        assertTrue(HtmlSearchBlockDetector.isBlocked("Test Page", content));
    }

    @Test
    void testCloudflareChallenge() {
        String content = "<html>cf-challenge-xyz</html>";
        assertTrue(HtmlSearchBlockDetector.isBlocked("Test Page", content));
    }

    @Test
    void testAccessDenied() {
        String content = "<html>Access denied - You have been blocked</html>";
        assertTrue(HtmlSearchBlockDetector.isBlocked("Test Page", content));
    }

    @Test
    void testNormalPage() {
        String content = "<html><div>Normal search results</div></html>";
        assertFalse(HtmlSearchBlockDetector.isBlocked("Search Results", content));
    }

    @Test
    void testEmptyResultPage() {
        String content = "<html>No results found for your query</html>";
        assertTrue(HtmlSearchBlockDetector.isEmptyResult(content));
    }

    @Test
    void testNonEmptyResultPage() {
        String content = "<html><div>Result 1</div><div>Result 2</div></html>";
        assertFalse(HtmlSearchBlockDetector.isEmptyResult(content));
    }

    @Test
    void testEmptyContentForEmptyResult() {
        String content = "";
        assertFalse(HtmlSearchBlockDetector.isEmptyResult(content));
    }

    @Test
    void testRequiresJavaScript() {
        String content = "<html><noscript>Please enable JavaScript to continue</noscript></html>";
        assertTrue(HtmlSearchBlockDetector.requiresJavaScript(content));
    }

    @Test
    void testNormalPageForJsCheck() {
        String content = "<html><div>Content without JS requirement</div></html>";
        assertFalse(HtmlSearchBlockDetector.requiresJavaScript(content));
    }

    @Test
    void testBlockedWithTitle() {
        String title = "Verify you are human";
        assertTrue(HtmlSearchBlockDetector.isBlocked(title, "content"));
    }
}
