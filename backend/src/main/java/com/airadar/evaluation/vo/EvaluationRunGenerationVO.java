package com.airadar.evaluation.vo;

import com.airadar.evaluation.model.EvaluationRunStatus;

import java.time.Instant;

public record EvaluationRunGenerationVO(
        Long runId,
        Long datasetId,
        EvaluationRunStatus status,
        int totalCases,
        int passedCases,
        int failedCases,
        int errorCases,
        Instant startedAt,
        Instant finishedAt
) {
}
