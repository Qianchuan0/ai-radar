package com.airadar.analysis.client.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.analysis.openai")
public record OpenAiAnalysisProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts
) {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    public OpenAiAnalysisProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(30);
        }
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("ai-radar.analysis.openai.max-attempts must be between 1 and 3.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
