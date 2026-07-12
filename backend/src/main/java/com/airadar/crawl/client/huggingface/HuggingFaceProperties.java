package com.airadar.crawl.client.huggingface;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.collector.hugging-face")
public record HuggingFaceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        String token
) {

    public HuggingFaceProperties {
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("Hugging Face maxAttempts must be between 1 and 3.");
        }
    }
}
