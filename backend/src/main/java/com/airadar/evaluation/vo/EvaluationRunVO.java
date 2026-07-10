package com.airadar.evaluation.vo;

import com.airadar.evaluation.model.EvaluationRunStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record EvaluationRunVO(
        Long id,
        Long datasetId,
        String datasetName,
        EvaluationRunStatus status,
        int totalCases,
        int passedCases,
        int failedCases,
        int errorCases,
        JsonNode metricsPayload,
        JsonNode errorAnalysisPayload,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        List<EvaluationCaseResultVO> caseResults
) {
}
