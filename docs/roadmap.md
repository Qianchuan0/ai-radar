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
