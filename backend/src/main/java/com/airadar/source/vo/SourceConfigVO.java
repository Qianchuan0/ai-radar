package com.airadar.source.vo;

import com.airadar.source.model.SourceType;

import java.time.Instant;
import java.util.Map;

public record SourceConfigVO(
        Long id,
        String sourceCode,
        SourceType sourceType,
        String displayName,
        boolean enabled,
        Integer crawlIntervalMinutes,
        Map<String, Object> config,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
