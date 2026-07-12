# AI Radar Development Roadmap

## Phase 0: Project Initialization

**Status:** Completed

### Goals

- establish the repository structure and collaboration rules
- document project context and decision logging conventions
- prepare a clean entry point for later implementation

### Deliverables

- root project docs and engineering rules
- initial `backend/`, `frontend/`, and `evaluation/` directories

## Phase 1: Backend Foundation

**Status:** Completed

### Goals

- set up the backend foundation and migration path
- define response, exception, and health-check conventions
- prepare the schema and contract baseline for the first data flow

### Deliverables

- Spring Boot backend skeleton
- PostgreSQL + Flyway foundation
- Phase 1 API contract
- accepted architecture decisions and ADRs

## Phase 2: First Data Flow Closed Loop

**Status:** Completed

### Goals

- prove one real data source end to end
- validate crawl, raw retention, normalization, clustering, and scoring boundaries
- keep the flow traceable and explainable

### Deliverables

- Hacker News source configuration and manual crawl path
- crawl-task lifecycle and error recording
- `raw_item`, `hot_item`, `hot_cluster`, and `hot_score`
- hot-cluster list/detail APIs
- automated and manual verification path

## Phase 3: Frontend MVP

**Status:** Completed

### Goals

- render event-level hot clusters using the real backend
- provide direct Vue list/detail/state pages
- keep only real backend fields plus clearly local display filters

### Deliverables

- Vue 3 + TypeScript + Vite frontend
- hot-cluster ranking page
- hot-cluster detail page
- loading, empty, and error state page
- score breakdown and evidence views
- build/test verification
- docs aligned with the direct frontend entry

## Phase 4: Additional Data Sources

**Status:** Completed

### Goals

- extend the MVP from single-source Hacker News to real multi-source collection
- validate cross-source normalization and multi-source clustering without introducing new infrastructure

### Deliverables

- arXiv client closed loop with Atom XML parsing and rate-limit-aware request settings
- arXiv raw-item closed loop through manual crawl, `crawl_task`, and `raw_item`
- arXiv hot-item normalization closed loop with stable field mapping
- GitHub client closed loop with optional token auth and repository-search parsing
- GitHub raw-item closed loop through manual crawl, `crawl_task`, and `raw_item`
- GitHub hot-item normalization closed loop with repository metadata mapped into the unified schema
- GitHub hot-cluster ranking closed loop through existing rule-based clustering and scoring
- cross-source clustering proof using Hacker News plus arXiv canonical URL evidence
- cross-source clustering proof using Hacker News plus GitHub canonical URL evidence
- source-specific retry and rate-limit-aware request settings
- cross-source normalization tests

## Phase 5: LLM Structured Analysis

**Status:** Completed

### Goals

- add constrained structured analysis based on evidence
- record model calls, outputs, and failure states

### Deliverables

- LLM integration decision
- structured output schema
- analysis service and observability hooks
- `cluster_analysis` persistence for run metadata, payloads, and failures
- `POST /api/v1/hot-clusters/{clusterId}/analysis-runs` trigger API
- `GET /api/v1/hot-clusters/{clusterId}/analysis` latest-analysis API
- detail-page structured analysis card backed by the real API

## Phase 6: Subscription and Alerts

**Status:** Completed

### Goals

- support subscriptions around user interests
- generate traceable alerts without noisy duplication

### Deliverables

- subscription rules
- alert matching logic
- alert records and suppression rules
- manual matching API and frontend alert review page

## Phase 7: Daily Report

**Status:** Completed

### Goals

- produce evidence-backed daily summaries from cluster data
- expose historical report views

### Deliverables

- daily report model
- manual generation flow from persisted cluster snapshots
- report API and page
- historical report list view

## Phase 8: Evaluation

**Status:** Completed

### Goals

- evaluate crawl, clustering, scoring, analysis, and alert quality
- feed error cases back into iteration

### Deliverables

- labeled evaluation dataset
- quality metrics
- evaluation report and error analysis

## Phase 9A: Hugging Face Models Source + Source Connector Template

**Status:** Completed

### Goals

- add Hugging Face Models as the fourth heterogeneous source
- keep the integration inside the existing crawl-to-cluster closed loop
- document a reusable connector checklist for future sources

### Deliverables

- Hugging Face models client, collector, and hot-item normalizer
- `HUGGING_FACE` source configuration and manual crawl support
- raw-item to hot-score integration coverage for the fourth source
- source connector implementation template and documentation updates
- Phase 9A acceptance script and acceptance note

## Phase 10: Real LLM Structured Analysis Provider

**Status:** Completed

### Goals

- replace the Phase 5 fake structured analysis with a real, replayable OpenAI provider
- keep the existing `cluster_analysis` persistence, API surface, and frontend card unchanged
- preserve application startup when the API key is missing and record a stable failure code instead

### Deliverables

- `openai-java` SDK dependency and `ai-radar.analysis.openai` configuration
- `OpenAiStructuredAnalysisClient` using the Chat Completions API with `response_format=json_object` and an in-prompt schema description
- prompt factory, response mapper, and structured output schema derived from the existing VO
- provider selector backed by `@ConditionalOnProperty`, with `openai` as the default
- expanded `ErrorCode` set covering provider-not-configured, upstream, timeout, schema, and parse failures
- `AnalysisProviderException` propagation into persisted `cluster_analysis.failureCode`
- backend unit, mock-client, and integration tests without real OpenAI calls
- optional live verification script gated on `OPENAI_API_KEY`
- Phase 10 acceptance script
- Phase 10 acceptance note
- documentation sync: roadmap, decision log, ADR, project context, README

## Phase 11A: Lightweight Scheduled Operations

**Status:** In Progress

### Goals

- add the first lightweight scheduled operations loop without introducing heavy job infrastructure
- reuse existing `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score` boundaries
- keep alert delivery, scheduled reports, and scheduled evaluation out of scope for this step

### Deliverables

- Spring Scheduler based scheduled crawl runner gated by configuration
- scheduled due-source selection based on `source_config.enabled`, `crawl_interval_minutes`, and recent `crawl_task` history
- `SCHEDULED` crawl-task creation through the existing crawl execution path with bucketed idempotency keys
- skip handling for in-flight sources and not-yet-due sources
- crawl-task list API filters for `sourceId`, `triggerType`, and `status`
- backend integration coverage for scheduled crawl triggering, skip paths, idempotency buckets, and task-list API visibility
- Phase 11A acceptance script
- Phase 11A acceptance note
- documentation sync: roadmap, decision log, ADR, project context, OpenAPI

## Phase 11B: Scheduled Daily Report Generation

**Status:** In Progress

### Goals

- add a second lightweight scheduled operations loop for daily report generation
- reuse the existing `DailyReportService.generate(LocalDate)` and `daily_report.report_date` uniqueness
- default to generating the UTC previous day report without external delivery or alert scheduling
- keep the runner configuration-gated and disabled by default

### Deliverables

- Spring Scheduler based scheduled daily report runner gated by configuration
- target report date calculation based on UTC date plus configurable offset
- default skip behavior when the target date already has a report
- explicit `refresh-existing` option for regenerating an existing report
- backend integration coverage for generation, skip, refresh, empty-report, and default-disabled behavior
- Phase 11B acceptance script
- Phase 11B acceptance note
- documentation sync: README, roadmap, decision log, ADR, project context

## Phase 12A: Sogou Search Source

**Status:** In Progress

### Goals

- add the first Chinese platform source via the Tencent Cloud Web Search API (wsa)
- reuse the existing `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score` closed loop
- keep the integration offline-testable without real API credentials

### Deliverables

- `SOGOU_SEARCH` source type with Tencent Cloud v3 signature support
- `TencentCloudV3Signer` reusable utility for TC3-HMAC-SHA256 signature
- `SogouSearchClient`, `SogouSearchCollector`, and `SogouSearchHotItemNormalizer`
- source config validation for query, cnt, mode, and freshness
- raw-to-hot integration test proving the full closed loop
- `CRAWL_PROVIDER_NOT_CONFIGURED` error code for missing credentials
- frontend source label "搜狗搜索" and filter option
- Phase 12A acceptance script and acceptance note
- documentation sync: roadmap, decision log, project context, README
