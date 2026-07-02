package com.airadar.crawl.collector;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record CollectedItem(
        String externalId,
        String sourceUrl,
        JsonNode rawPayload,
        Instant publishedAt,
        Instant fetchedAt
) {
}
