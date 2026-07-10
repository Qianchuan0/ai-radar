package com.airadar.crawl.client.arxiv;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.collector.arxiv")
public record ArxivProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration minRequestInterval
) {

    public ArxivProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("arXiv maxAttempts must be between 1 and 3.");
        }
        if (minRequestInterval == null || minRequestInterval.isNegative()) {
            throw new IllegalArgumentException("arXiv minRequestInterval must be zero or positive.");
        }
    }
}
