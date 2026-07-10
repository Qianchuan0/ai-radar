package com.airadar.evaluation.vo;

import com.airadar.evaluation.model.EvaluationCaseType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record EvaluationCaseVO(
        Long id,
        Long datasetId,
        String caseCode,
        EvaluationCaseType caseType,
        JsonNode targetPayload,
        JsonNode expectedPayload,
        String notes,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
