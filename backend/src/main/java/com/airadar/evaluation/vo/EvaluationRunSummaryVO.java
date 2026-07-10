package com.airadar.evaluation.vo;

import com.airadar.evaluation.model.EvaluationRunStatus;

import java.time.Instant;

public record EvaluationRunSummaryVO(
        Long id,
        Long datasetId,
        String datasetName,
        EvaluationRunStatus status,
        int totalCases,
        int passedCases,
        int failedCases,
        int errorCases,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
