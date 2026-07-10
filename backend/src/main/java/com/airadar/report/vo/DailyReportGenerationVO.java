package com.airadar.report.vo;

import java.time.Instant;
import java.time.LocalDate;

public record DailyReportGenerationVO(
        LocalDate reportDate,
        int clusterCount,
        Instant generatedAt
) {
}
