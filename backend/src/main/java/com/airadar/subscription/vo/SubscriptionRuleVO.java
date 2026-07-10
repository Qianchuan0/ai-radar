package com.airadar.subscription.vo;

import com.airadar.source.model.SourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SubscriptionRuleVO(
        Long id,
        String name,
        boolean enabled,
        List<String> keywords,
        List<SourceType> sourceTypes,
        BigDecimal minScore,
        int suppressWindowHours,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
