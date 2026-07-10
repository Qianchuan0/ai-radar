package com.airadar.evaluation.vo;

import java.time.Instant;

public record EvaluationDatasetVO(
        Long id,
        String name,
        String description,
        int version,
        boolean enabled,
        int caseCount,
        Instant createdAt,
        Instant updatedAt
) {
}
