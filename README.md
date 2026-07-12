# AI Radar

## Positioning

AI Radar is an AI industry intelligence and trend analysis platform. It is not a generic AI news aggregator. The product centers on event-level hot clusters, source evidence, and explainable hot scoring.

## Why This Project Exists

AI information is fragmented across research feeds, developer communities, and open-source platforms. The same event appears repeatedly in different forms, which makes manual tracking noisy and unreliable. AI Radar turns those scattered signals into traceable event-level clusters.

## Why It Is Not a Normal Aggregator

- The system retains immutable `raw_item` snapshots instead of only keeping summaries.
- The system normalizes heterogeneous content into `hot_item`, then groups related items into `hot_cluster`.
- Ranking is based on explainable `hot_score`, not only recency.
- Detail pages always point back to source evidence.

## MVP Scope

- Start from `arXiv`, `Hacker News`, `GitHub`, and `Hugging Face Models`.
- Prove the closed loop from source configuration to event-level hot-cluster display.
- Current verified path: `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score -> cluster_analysis (real OpenAI provider or fake fallback) -> subscription_rule -> alert_record -> daily_report -> evaluation_run -> APIs -> frontend list/detail/alerts/reports/evaluation`.
- Current verified source expansion: `Hacker News + arXiv + GitHub + Hugging Face Models`, with `HUGGING_FACE` covered through client parsing, raw retention, normalization, clustering, scoring, and frontend-compatible source filtering.

## Core Concepts

- `raw_item`: immutable snapshot captured for each crawl event
- `hot_item`: normalized content record derived from source data
- `hot_cluster`: event-level hot topic made of one or more related hot items
- `hot_score`: explainable score with total, components, version, and calculation time

## Planned Stack

- Backend: Java 17, Spring Boot 3.5.15, MyBatis-Plus, Flyway
- Database: PostgreSQL
- API contract: OpenAPI 3.1
- Frontend MVP: Vue 3, TypeScript, Vite, Vue Router, Ant Design Vue, Axios
- Local runtime: Docker Compose for PostgreSQL

## Current Status

**Phase 1 completed / Phase 2 completed / Phase 3 completed / Phase 4 completed / Phase 5 completed / Phase 6 completed / Phase 7 completed / Phase 8 completed / Phase 9A completed / Phase 10 completed / Phase 11A in progress / Phase 11B in progress / Phase 12A in progress**

Phase 10 replaces the Phase 5 fake structured analysis with a real OpenAI-compatible provider backed by the official `openai-java` SDK and the Chat Completions API. Chat Completions is chosen over the Responses API so the same code path works against both OpenAI itself and OpenAI-compatible gateways (e.g. DeepSeek). The wire schema mode is `response_format=json_object` plus an in-prompt schema description, because the stricter `json_schema` mode is not implemented by every compatible gateway. The application starts even when the API key is missing; in that case analysis runs are persisted with `ANALYSIS_PROVIDER_NOT_CONFIGURED` until `AI_RADAR_OPENAI_API_KEY` is provided. Default tests stay offline, `.\scripts\accept-phase-10.ps1` is the repeatable acceptance path, and an optional `scripts/live-verify-openai.ps1` path verified the real provider end-to-end against DeepSeek on 2026-07-12.

Phase 11A and Phase 11B extend operations with lightweight, configuration-gated Spring Scheduler loops. Scheduled crawl reuses the existing crawl pipeline, while scheduled daily report generation reuses the existing `DailyReportService.generate(date)` path and skips existing reports by default. Both loops are disabled by default and do not introduce Quartz, external queues, alert delivery, or scheduled evaluation.

Phase 12A adds the first Chinese platform source via the Tencent Cloud Web Search API (wsa, sourced from Sogou Search). The integration uses a manually implemented TC3-HMAC-SHA256 signer (`TencentCloudV3Signer`) rather than the Tencent Cloud SDK, keeping the dependency footprint unchanged. The application starts even when `AI_RADAR_SOGOU_SEARCH_SECRET_ID` or `AI_RADAR_SOGOU_SEARCH_SECRET_KEY` is missing; in that case crawl tasks fail with `CRAWL.PROVIDER_NOT_CONFIGURED`.
