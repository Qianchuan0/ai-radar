package com.airadar.operations;

import java.time.Instant;
import java.time.LocalDate;

public record ScheduledDailyReportResult(
        LocalDate targetDate,
        boolean generated,
        boolean skipped,
        String skipReason,
        Integer clusterCount,
        Instant generatedAt,
        Instant startedAt,
        Instant finishedAt
) {
}
