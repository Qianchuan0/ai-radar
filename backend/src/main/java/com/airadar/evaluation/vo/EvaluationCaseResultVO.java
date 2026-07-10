package com.airadar.evaluation.vo;

import com.airadar.evaluation.model.EvaluationCaseStatus;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record EvaluationCaseResultVO(
        Long id,
        Long caseId,
        String caseCode,
        EvaluationCaseType caseType,
        EvaluationCaseStatus status,
        JsonNode actualPayload,
        String failureReason,
        Instant evaluatedAt
) {
}
