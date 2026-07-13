package com.airadar.crawl.client.duckduckgo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * DuckDuckGo 搜索客户端配置。
 */
@ConfigurationProperties(prefix = "ai-radar.collector.duckduckgo-search")
public record DuckDuckGoSearchProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration minRequestInterval
) {
    public DuckDuckGoSearchProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("DuckDuckGo Search maxAttempts must be between 1 and 3.");
        }
    }
}
