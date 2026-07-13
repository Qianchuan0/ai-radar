package com.airadar.crawl.client.bing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bing 搜索客户端配置。
 */
@ConfigurationProperties(prefix = "ai-radar.collector.bing-search")
public record BingSearchProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration minRequestInterval
) {
    public BingSearchProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Bing Search maxAttempts must be between 1 and 3.");
        }
    }
}
