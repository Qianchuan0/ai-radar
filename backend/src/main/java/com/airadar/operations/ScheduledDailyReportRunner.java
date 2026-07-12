package com.airadar.operations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "ai-radar.operations.scheduled-daily-report",
        name = "enabled",
        havingValue = "true"
)
public class ScheduledDailyReportRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledDailyReportRunner.class);

    private final ScheduledDailyReportService scheduledDailyReportService;

    public ScheduledDailyReportRunner(ScheduledDailyReportService scheduledDailyReportService) {
        this.scheduledDailyReportService = scheduledDailyReportService;
    }

    @Scheduled(
            fixedDelayString = "${ai-radar.operations.scheduled-daily-report.fixed-delay:15m}",
            initialDelayString = "${ai-radar.operations.scheduled-daily-report.initial-delay:1m}"
    )
    public void runScheduledDailyReport() {
        try {
            ScheduledDailyReportResult result = scheduledDailyReportService.runOnce();
            log.info(
                    "Scheduled daily report run finished: targetDate={}, generated={}, skipped={}, skipReason={}, clusterCount={}, durationMs={}",
                    result.targetDate(),
                    result.generated(),
                    result.skipped(),
                    result.skipReason(),
                    result.clusterCount(),
                    java.time.Duration.between(result.startedAt(), result.finishedAt()).toMillis()
            );
        } catch (RuntimeException exception) {
            log.error("Scheduled daily report run failed unexpectedly.", exception);
        }
    }
}
