# Phase 11A Acceptance

**Status:** Accepted
**Date:** 2026-07-15

## Acceptance Status

Phase 11A has passed the acceptance script verification and is ready for maintainer review.

## Scope

This document covers the first lightweight scheduled operations step in `ai-radar`:

- add a configuration-gated Spring Scheduler crawl runner
- reuse the existing crawl execution flow to create `SCHEDULED` crawl tasks
- expose crawl-task list filters so scheduled runs can be reviewed through the API
- keep scheduled alerts, scheduled reports, external delivery, and scheduled evaluation out of scope

## Acceptance Command

Run the repo-level acceptance script:

```powershell
.\scripts\accept-phase-11a.ps1
```

## What The Script Verifies

1. `backend\mvnw.cmd -Dtest=ScheduledCrawlServiceIntegrationTest test`
   - verifies due-source triggering, in-flight skip handling, not-yet-due skip handling, bucketed idempotency keys, and task-list API visibility
2. `backend\mvnw.cmd test`
   - verifies the scheduled crawl additions do not regress the existing crawl, analysis, alert, report, and evaluation flows

## Completion Judgment

Phase 11A is ready for maintainer review when the repository has:

- Spring Scheduler enabled in the application but gated behind `ai-radar.operations.scheduled-crawl.enabled`
- scheduled crawl due checks based on source interval metadata and recent crawl-task history
- `SCHEDULED` crawl-task creation through the existing collector and item-processing pipeline
- API visibility for filtering crawl tasks by `sourceId`, `triggerType`, and `status`
- repeatable automated verification for scheduled crawl behavior

## Manual Recheck

For local recheck after tests pass:

1. Start the backend with `AI_RADAR_SCHEDULED_CRAWL_ENABLED=true`.
2. Create or inspect an enabled source with `crawlIntervalMinutes` set.
3. Wait at least one scheduler polling interval.
4. Call `GET /api/v1/crawl-tasks?triggerType=SCHEDULED` and verify that scheduled tasks appear.
