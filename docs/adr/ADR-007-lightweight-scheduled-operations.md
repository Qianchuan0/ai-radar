# ADR-007: Use Spring Scheduler for the First Lightweight Scheduled Operations Loop

## Status

Accepted

## Context

After Phase 10, AI Radar has four real sources, manual crawl execution, alerts, reports, evaluation, and a real OpenAI-backed analysis provider. The remaining gap is operational continuity: `source_config` already stores `crawl_interval_minutes`, and `crawl_task.trigger_type` already supports `SCHEDULED`, but the application still depends on manual crawl triggers.

The next step should make source collection repeatable without pulling the project into heavy orchestration before the product proves the need.

## Decision

Use Spring Scheduler for the first scheduled operations loop in Phase 11A.

The initial scheduled scope is intentionally narrow:

- schedule source crawling only
- reuse the existing `CrawlExecutionService` and `crawl_task` persistence path
- determine due work from `source_config.enabled`, `crawl_interval_minutes`, and recent `crawl_task` history
- create `SCHEDULED` crawl tasks with bucketed idempotency keys
- keep alert matching, report generation, evaluation scheduling, and external delivery channels manual

The scheduled runner must be configuration-gated and disabled by default in local environments.

## Alternatives Considered

### 1. Quartz or another in-process job framework now

- stronger job features and richer scheduling metadata
- adds a second operational model before the first lightweight loop proves necessary
- increases schema, configuration, and failure-recovery complexity too early

### 2. External scheduler or queue infrastructure now

- scales further in multi-instance deployments
- requires infrastructure the current MVP does not otherwise need
- conflicts with the project's preference to validate the smallest operational loop first

### 3. Keep all crawling manual

- preserves the current simplicity
- leaves `crawl_interval_minutes` unused and blocks the first repeatable operations workflow
- does not close the operational gap after the multi-source and analysis phases

## Consequences

### Positive

- delivers the smallest useful scheduled loop with minimal new infrastructure
- keeps crawl behavior traceable through the existing `crawl_task` and `crawl_task_error` tables
- preserves a clear upgrade path if future scheduling needs exceed Spring Scheduler
- keeps scheduled and manual crawls on the same collector, persistence, clustering, and scoring path

### Negative

- this baseline is best suited to a single application instance
- due checks are application-level and depend on recent task history rather than a dedicated scheduler state model
- future phases may still need stronger coordination primitives if scheduling expands materially

## Follow-up Notes

- Re-evaluate the scheduler choice when scheduled alerts, scheduled reports, or multi-instance coordination become real requirements.
- Do not introduce external delivery or report scheduling under this ADR; those remain separate decisions.
