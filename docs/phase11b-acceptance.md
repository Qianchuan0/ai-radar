# Phase 11B Acceptance: Scheduled Daily Report Generation

This document covers the second lightweight scheduled operations step in `ai-radar`:

- generate daily reports through an optional Spring Scheduler runner
- reuse the existing `DailyReportService.generate(LocalDate)` implementation
- target UTC yesterday by default with a configurable date offset
- skip existing reports by default and allow explicit refresh
- keep alert scheduling, external delivery, scheduled evaluation, Quartz, queues, and distributed locks out of scope

## Configuration

The runner is disabled by default.

```yaml
ai-radar:
  operations:
    scheduled-daily-report:
      enabled: false
      fixed-delay: 15m
      initial-delay: 1m
      report-date-offset-days: 1
      refresh-existing: false
```

Environment overrides:

- `AI_RADAR_SCHEDULED_DAILY_REPORT_ENABLED`
- `AI_RADAR_SCHEDULED_DAILY_REPORT_FIXED_DELAY`
- `AI_RADAR_SCHEDULED_DAILY_REPORT_INITIAL_DELAY`
- `AI_RADAR_SCHEDULED_DAILY_REPORT_OFFSET_DAYS`
- `AI_RADAR_SCHEDULED_DAILY_REPORT_REFRESH_EXISTING`

## Automated Acceptance

Run from the repository root:

```powershell
.\scripts\accept-phase-11b.ps1
```

The script runs:

1. `backend\mvnw.cmd -Dtest=ScheduledDailyReportServiceIntegrationTest,DailyReportIntegrationTest test`
2. `backend\mvnw.cmd test`

## Validated Behavior

- a target date with no existing report can be generated
- an existing target-date report is skipped by default
- `refresh-existing=true` regenerates the existing target-date report without inserting a duplicate row
- a date without clusters still generates an empty daily report
- the scheduled runner is not created unless explicitly enabled

## Manual Review Path

When the runner is enabled locally, review generated reports through the existing APIs:

- `GET /api/v1/reports/daily`
- `GET /api/v1/reports/daily/{reportDate}`

No new external API is introduced in Phase 11B.
