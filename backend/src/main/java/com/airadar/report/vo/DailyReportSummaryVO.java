package com.airadar.report.vo;

import com.airadar.report.model.ReportStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyReportSummaryVO(
        Long id,
        LocalDate reportDate,
        ReportStatus status,
        String title,
        String summary,
        int clusterCount,
        List<Long> topClusterIds,
        Instant generatedAt
) {

    public DailyReportSummaryVO {
        topClusterIds = List.copyOf(topClusterIds);
    }
}
