package com.airadar.crawl.client.twitter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.collector.twitter")
public record TwitterProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts
) {

    public TwitterProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Twitter maxAttempts must be between 1 and 3.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
