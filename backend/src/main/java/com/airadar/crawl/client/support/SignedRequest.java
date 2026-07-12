package com.airadar.crawl.client.support;

public record SignedRequest(String authorization, String timestamp) {
}
