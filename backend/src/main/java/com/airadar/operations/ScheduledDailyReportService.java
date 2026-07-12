package com.airadar.operations;

import com.airadar.report.service.DailyReportService;
import com.airadar.report.vo.DailyReportGenerationVO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class ScheduledDailyReportService {

    static final String SKIP_REASON_REPORT_ALREADY_EXISTS = "REPORT_ALREADY_EXISTS";

    private final DailyReportService dailyReportService;
    private final ScheduledOperationsProperties properties;

    public ScheduledDailyReportService(
            DailyReportService dailyReportService,
            ScheduledOperationsProperties properties
    ) {
        this.dailyReportService = dailyReportService;
        this.properties = properties;
    }

    public ScheduledDailyReportResult runOnce() {
        Instant startedAt = Instant.now();
        ScheduledOperationsProperties.ScheduledDailyReport config = properties.getScheduledDailyReport();
        LocalDate targetDate = LocalDate.now(ZoneOffset.UTC).minusDays(config.getReportDateOffsetDays());

        if (!config.isRefreshExisting() && dailyReportService.existsByReportDate(targetDate)) {
            return new ScheduledDailyReportResult(
                    targetDate,
                    false,
                    true,
                    SKIP_REASON_REPORT_ALREADY_EXISTS,
                    null,
                    null,
                    startedAt,
                    Instant.now()
            );
        }

        DailyReportGenerationVO generation = dailyReportService.generate(targetDate);
        return new ScheduledDailyReportResult(
                targetDate,
                true,
                false,
                null,
                generation.clusterCount(),
                generation.generatedAt(),
                startedAt,
                Instant.now()
        );
    }
}
