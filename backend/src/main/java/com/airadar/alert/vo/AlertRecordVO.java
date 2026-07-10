package com.airadar.alert.vo;

import com.airadar.alert.model.AlertStatus;
import com.airadar.source.model.SourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AlertRecordVO(
        Long id,
        Long subscriptionRuleId,
        String subscriptionName,
        Long hotClusterId,
        String hotClusterTitle,
        List<SourceType> sourceTypes,
        BigDecimal hotScore,
        AlertStatus status,
        Map<String, Object> matchReason,
        Instant matchedAt,
        Instant createdAt
) {
}
