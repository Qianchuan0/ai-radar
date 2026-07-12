package com.airadar.operations;

import java.time.Instant;

public record ScheduledSourceResult(
        long sourceId,
        String sourceCode,
        boolean triggered,
        Long taskId,
        String status,
        String skipReason,
        Instant evaluatedAt
) {
}
