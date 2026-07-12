package com.airadar.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "ai-radar.operations.scheduled-crawl",
        name = "enabled",
        havingValue = "true"
)
public class ScheduledCrawlRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCrawlRunner.class);

    private final ScheduledCrawlService scheduledCrawlService;

    public ScheduledCrawlRunner(ScheduledCrawlService scheduledCrawlService) {
        this.scheduledCrawlService = scheduledCrawlService;
    }

    @Scheduled(
            fixedDelayString = "${ai-radar.operations.scheduled-crawl.fixed-delay:60s}",
            initialDelayString = "${ai-radar.operations.scheduled-crawl.initial-delay:30s}"
    )
    public void runScheduledCrawl() {
        try {
            ScheduledCrawlResult result = scheduledCrawlService.runOnce();
            log.info(
                    "Scheduled crawl run finished: scanned={}, triggered={}, skipped={}, failed={}, durationMs={}",
                    result.scannedSources(),
                    result.triggeredTasks(),
                    result.skippedSources(),
                    result.failedSources(),
                    java.time.Duration.between(result.startedAt(), result.finishedAt()).toMillis()
            );
        } catch (RuntimeException exception) {
            log.error("Scheduled crawl run failed unexpectedly.", exception);
        }
    }
}
