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

**Status:** Completed

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
- frontend source management page at `/sources` for viewing source configs and toggling enable/disable status
- backend integration coverage for scheduled crawl triggering, skip paths, idempotency buckets, task-list API visibility, and disable-via-status exclusion
- Phase 11A acceptance script
- Phase 11A acceptance note
- documentation sync: roadmap, decision log, ADR, project context, OpenAPI

## Phase 11B: Scheduled Daily Report Generation

**Status:** Completed

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

**Status:** Completed

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

## Phase 12B-1: Low-Risk JSON/API Sources (Weibo, HN Search, Twitter)

**Status:** Completed

### Goals

- integrate three low-risk, structured JSON/API sources without introducing heavy infrastructure
- validate that the existing crawl-to-cluster closed loop supports heterogeneous API sources with different request/response patterns
- keep application startup stable even when optional API keys are missing

### Deliverables

- `WEIBO_HOT_SEARCH` source type with Weibo hot list API integration
- `HACKER_NEWS_SEARCH` source type with Hacker News Algolia Search API integration
- `TWITTER` source type with `twitterapi.io` API integration
- `CRAWL_PROVIDER_NOT_CONFIGURED` error code for missing Twitter API key
- source-specific config validation, retry logic, and quality filtering
- frontend source labels "微博热搜", "Hacker News 搜索", "Twitter/X"
- raw-to-hot integration tests for all three sources
- Phase 12B-1 acceptance script
- documentation sync: roadmap, decision log, project context, README

## Phase 12B-2: HTML Search Sources (Bing + DuckDuckGo)

**Status:** Completed

### Goals

- integrate HTML search sources using lightweight jsoup parsing
- validate that AI Radar can safely handle page structure changes, anti-crawl failures, empty results, and rate limiting
- keep single-source isolation without introducing browser automation or proxy pools

### Deliverables

- jsoup 1.18.3 dependency for HTML parsing
- HTML search common support components (htmlsearch package) with headers, URL sanitization, block detection, and parse exceptions
- `BING_SEARCH` source type with Bing HTML parsing, safe User-Agent, rate limiting, and failure isolation
- `DUCKDUCKGO_SEARCH` source type with DuckDuckGo HTML parsing, redirect URL decoding, and block detection
- config validation for query, limit, freshnessDays, market/safeSearch (Bing) and region (DuckDuckGo)
- raw-to-hot normalizers for both sources with WEB_PAGE item_type, rank-based points, and host/market tags
- frontend source labels "Bing 搜索", "DuckDuckGo 搜索"
- unit tests for request validation, URL sanitization, and block detection
- raw-to-hot integration tests for both sources
- Phase 12B-2 acceptance script and optional live verification script
- documentation sync: roadmap, decision log, project context, README

### Notes

- HTML sources default to `maxAttempts=1` to avoid consecutive failed requests
- Recommended `crawlIntervalMinutes` >= 180 for HTML sources
- Parse failures, 403/429, and CAPTCHA pages map to clear `CRAWL_UPSTREAM_ERROR` with explicit messages
- Google Search was not included in 12B-2A due to higher anti-crawl risk; reserved for optional 12B-2B if local live probes succeed

## Phase 13A: V1 Baseline Freeze

**Status:** Completed

### Goals

- close the Phase 11/12 engineering baseline before V2 scoring and clustering work
- make current V1 clustering and V1 scoring behavior replayable
- keep this phase focused on verification and documentation, not algorithm changes

### Deliverables

- `V1BaselineReplayIntegrationTest` for deterministic V1 cluster and score replay
- `docs/v1-baseline-behavior.md` baseline behavior guide
- Phase 13A acceptance script
- Phase 13A acceptance note
- documentation sync: README, roadmap, decision log

## Phase 13B: Source Roles and Normalized Signal Adapters

**Status:** Completed

### Goals

- separate source semantics from V1 compatibility metrics
- introduce a minimal normalized signal model for future growth tracking and Score V2
- keep V1 scoring unchanged

### Deliverables

- `SourceRole`
- `NormalizedSignal`
- `SourceSignalAdapter`
- `SourceSignalAdapterRegistry`
- first adapters for Hacker News, GitHub, Hugging Face, Bing Search, and DuckDuckGo Search
- adapter/model/registry unit tests
- Phase 13B acceptance script
- Phase 13B acceptance note
- `docs/signal-layer-guide.md`

## Phase 14: Signal Snapshot and Growth Trend Calculation

**Status:** In Progress

### Goals

- enable time-series signal tracking for growth trend calculation
- provide 24h growth metrics without changing V1 scoring
- create the foundation for future Score V2 momentum signals

### Deliverables

- `hot_item_signal_snapshot` table with migration V7
- `SignalSnapshotEntity`, `SignalSnapshotMapper`, `SignalSnapshotService`
- `GrowthCalculationService` with 24h window support
- `GrowthMetrics`, `GrowthConfidence` models
- `HotItemSignalController` with `/signals` and `/trend` endpoints
- `SignalSnapshotVO`, `GrowthMetricsVO` for API responses
- Pipeline integration: snapshot creation after `hotItemService.upsert()`
- Unit tests for signal services
- Controller integration tests
- Phase 14 acceptance script
- Documentation updates (decision log, signal layer guide)

### Scope

**Included in Phase 14:**
- Signal snapshot storage for each crawl
- 24h growth trend calculation
- API endpoints for querying snapshots and trends
- `observed_at = raw_item.fetched_at` (crawl-centric time tracking)

**Explicitly NOT in Phase 14:**
- No changes to `RuleBasedScoringService` V1 sorting
- No frontend trend visualization
- No multi-window support (1h/6h/24h - 24h only)
- No complex cluster time-series aggregation
- No new infrastructure or dependencies

## Phase 15: Cross-Source Score V2 Shadow

**Status:** In Progress

### Goals

- introduce a semantic cross-source score (`cross-source-score-v2`) alongside `hn-score-v1`
- prove V2 separates cumulative scale from current growth velocity
- keep V1 as the authoritative ranking; V2 is shadow-only

### Deliverables

- `ClusterScoringStrategy` interface
- `HnScoreV1Strategy` transparent wrapper over unchanged `RuleBasedScoringService`
- `CrossSourceScoreV2Strategy` with 7 weighted dimensions
- `ScoringOrchestrator` running V2 in shadow (failure does not block V1)
- 7 score calculators: momentum, adoption, discussion, authority, relevance, evidenceDiversity, freshness
- `HotScoreMapper` query methods for version comparison
- `GET /api/v1/hot-clusters/{id}/scores` comparison API
- Unit tests: calculator coverage, V1/V2 comparison, shadow failure handling
- Phase 15 acceptance script

### V2 Score Dimensions (total 100)

| Dimension | Weight | Source |
| --- | ---: | --- |
| momentum | 0.25 | primary item 24h `GrowthMetrics.momentumScore` with confidence attenuation |
| adoption | 0.15 | primary `NormalizedSignal.adoption` + cluster boost |
| relevance | 0.15 | primary `NormalizedSignal.relevance` (search rank) or neutral baseline |
| freshness | 0.15 | primary item age (72h decay, matches V1) |
| discussion | 0.10 | primary `NormalizedSignal.discussion` + cluster boost |
| authority | 0.10 | highest-authority `SourceRole` present |
| evidenceDiversity | 0.10 | distinct `SourceRole` count with search-URL dedup |

### Scope

**Included in Phase 15:**
- V2 score persisted in `hot_score` with `scoring_version = cross-source-score-v2`
- `score_components` JSON with full per-dimension explainability
- Search-source deduplication (Bing/DuckDuckGo/Sogou same URL = one DISCOVERY)
- V2 runs in `REQUIRES_NEW` transaction; failures are logged and swallowed

**Explicitly NOT in Phase 15:**
- No replacement of V1 ranking (default sort unchanged)
- No LLM scoring or learned weights
- No multi-window momentum beyond 24h
- No `RuleBasedScoringService` code changes (zero V1 behavior change)
- No Flyway migration (hot_score already supports multiple versions)
