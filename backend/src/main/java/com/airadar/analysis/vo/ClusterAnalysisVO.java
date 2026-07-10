package com.airadar.analysis.vo;

import com.airadar.analysis.model.AnalysisRunStatus;
import com.airadar.analysis.model.AnalysisType;

import java.time.Instant;

public record ClusterAnalysisVO(
        Long id,
        Long hotClusterId,
        AnalysisType analysisType,
        AnalysisRunStatus status,
        String schemaVersion,
        String promptVersion,
        String modelProvider,
        String modelName,
        String inputHash,
        StructuredAnalysisResultVO result,
        String failureCode,
        String failureMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
}
