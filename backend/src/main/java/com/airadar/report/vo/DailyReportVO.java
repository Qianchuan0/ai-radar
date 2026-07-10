package com.airadar.report.vo;

import com.airadar.report.model.ReportStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyReportVO(
        Long id,
        LocalDate reportDate,
        ReportStatus status,
        String title,
        String summary,
        int clusterCount,
        List<Long> topClusterIds,
        List<DailyReportClusterVO> clusters,
        Instant generatedAt,
        Instant createdAt
) {

    public DailyReportVO {
        topClusterIds = List.copyOf(topClusterIds);
        clusters = List.copyOf(clusters);
    }
}
