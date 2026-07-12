package com.airadar.crawl.client.sogou;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.collector.sogou-search")
public record SogouSearchProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        String secretId,
        String secretKey
) {

    public SogouSearchProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Sogou Search maxAttempts must be between 1 and 3.");
        }
    }
}
