package com.airadar.operations;

import java.time.Instant;
import java.util.List;

public record ScheduledCrawlResult(
        Instant startedAt,
        Instant finishedAt,
        int scannedSources,
        int triggeredTasks,
        int skippedSources,
        int failedSources,
        List<ScheduledSourceResult> sources
) {

    public ScheduledCrawlResult {
        sources = List.copyOf(sources);
    }
}
