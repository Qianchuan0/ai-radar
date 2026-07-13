package com.airadar.crawl.client.hackernewssearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.collector.hacker-news-search")
public record HackerNewsSearchProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts
) {

    public HackerNewsSearchProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Hacker News Search maxAttempts must be between 1 and 3.");
        }
    }
}
