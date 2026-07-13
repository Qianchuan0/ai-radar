package com.airadar.crawl.client.weibo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.collector.weibo-hot-search")
public record WeiboHotSearchProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts
) {

    public WeiboHotSearchProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Weibo Hot Search maxAttempts must be between 1 and 3.");
        }
    }
}
