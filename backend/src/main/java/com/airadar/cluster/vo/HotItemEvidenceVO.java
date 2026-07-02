package com.airadar.cluster.vo;

import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record HotItemEvidenceVO(
        Long id,
        SourceType sourceType,
        String externalId,
        String title,
        String summary,
        String sourceUrl,
        String author,
        Instant publishedAt,
        String matchMethod,
        BigDecimal matchScore,
        JsonNode matchReason,
        String ruleVersion
) {
}
