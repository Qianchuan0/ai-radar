package com.airadar.item.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record NormalizedHotItem(
        String itemType,
        String title,
        String summary,
        String sourceUrl,
        String author,
        JsonNode tags,
        JsonNode metrics,
        String contentHash,
        Instant publishedAt
) {
}
