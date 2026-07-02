package com.airadar.crawl.client.hackernews;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.collector.hacker-news")
public record HackerNewsProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts
) {

    public HackerNewsProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Hacker News maxAttempts must be between 1 and 3.");
        }
    }
}
