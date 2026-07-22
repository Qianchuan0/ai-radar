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
- Current verified path: `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score -> cluster_analysis (real OpenAI provider or fake fallback) -> subscription_rule -> alert_record -> daily_report -> evaluation_run -> APIs -> frontend list/detail/alerts/reports/evaluation/sources`.
- Current verified source expansion: `Hacker News + arXiv + GitHub + Hugging Face Models + Sogou Search + Weibo Hot Search + Hacker News Search + Twitter + Bing Search + DuckDuckGo Search`, with each added source covered through client parsing, raw retention, normalization, clustering, scoring, and frontend-compatible source filtering.

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

**Phase 1 completed / Phase 2 completed / Phase 3 completed / Phase 4 completed / Phase 5 completed / Phase 6 completed / Phase 7 completed / Phase 8 completed / Phase 9A completed / Phase 10 completed / Phase 11A completed / Phase 11B completed / Phase 12A completed / Phase 12B-1 completed / Phase 12B-2 completed / Phase 13A completed / Phase 13B completed / Phase 16A completed / Phase 16 completed / Phase 17B completed / Phase 17C completed / Phase 18A completed / Phase 18B completed**

Phase 10 replaces the Phase 5 fake structured analysis with a real OpenAI-compatible provider backed by the official `openai-java` SDK and the Chat Completions API. Chat Completions is chosen over the Responses API so the same code path works against both OpenAI itself and OpenAI-compatible gateways (e.g. DeepSeek). The wire schema mode is `response_format=json_object` plus an in-prompt schema description, because the stricter `json_schema` mode is not implemented by every compatible gateway. The application starts even when the API key is missing; in that case analysis runs are persisted with `ANALYSIS_PROVIDER_NOT_CONFIGURED` until `AI_RADAR_OPENAI_API_KEY` is provided. Default tests stay offline, `.\scripts\accept-phase-10.ps1` is the repeatable acceptance path, and an optional `scripts/live-verify-openai.ps1` path verified the real provider end-to-end against DeepSeek on 2026-07-12.

Phase 11A and Phase 11B extend operations with lightweight, configuration-gated Spring Scheduler loops. Scheduled crawl reuses the existing crawl pipeline, while scheduled daily report generation reuses the existing `DailyReportService.generate(date)` path and skips existing reports by default. Both loops are disabled by default and do not introduce Quartz, external queues, alert delivery, or scheduled evaluation.

Phase 12A adds the first Chinese platform source via the Tencent Cloud Web Search API (wsa, sourced from Sogou Search). The integration uses a manually implemented TC3-HMAC-SHA256 signer (`TencentCloudV3Signer`) rather than the Tencent Cloud SDK, keeping the dependency footprint unchanged. The application starts even when `AI_RADAR_SOGOU_SEARCH_SECRET_ID` or `AI_RADAR_SOGOU_SEARCH_SECRET_KEY` is missing; in that case crawl tasks fail with `CRAWL.PROVIDER_NOT_CONFIGURED`.

Phase 12B-1 adds three structured JSON/API sources: Weibo Hot Search, Hacker News Algolia Search, and Twitter/X via `twitterapi.io`. Weibo and Hacker News Search run without credentials; Twitter is optional and returns `CRAWL.PROVIDER_NOT_CONFIGURED` during crawl when `AI_RADAR_TWITTER_API_KEY` is missing, without blocking application startup. The repeatable acceptance path is `.\scripts\accept-phase-12b-1.ps1`.

Phase 12B-2 adds HTML search sources (Bing and DuckDuckGo) using lightweight jsoup parsing. Each source has independent client, config validation, collector, and normalizer to keep failures isolated. Blocked pages (403/429/CAPTCHA/challenge) are detected via keyword matching and map to explicit `CRAWL_UPSTREAM_ERROR` rather than being treated as empty results. Default settings use `maxAttempts=1` and `minRequestInterval=10s` to avoid aggressive retries. Google Search is excluded from 12B-2A due to higher anti-crawl risk and reserved for optional 12B-2B only if local live probes succeed. The repeatable acceptance path is `.\scripts\accept-phase-12b-2.ps1`.

Phase 13A freezes the V1 baseline before Score V2 and clustering V2 work. `V1BaselineReplayIntegrationTest` creates a fixed two-item replay proving current `hn-rule-v1` clustering and `hn-score-v1` scoring remain deterministic, while `.\scripts\accept-phase-13a.ps1` ties that replay to the previously accepted Phase 11/12 scripts.

Phase 13B adds a minimal signal layer with `SourceRole`, `NormalizedSignal`, `SourceSignalAdapter`, and `SourceSignalAdapterRegistry`. The first adapters cover Hacker News, GitHub, Hugging Face, Bing Search, and DuckDuckGo Search. Search sources contribute rank/relevance but zero social heat. The repeatable acceptance path is `.\scripts\accept-phase-13b.ps1`.

Phase 16A introduces a minimal, replayable clustering evaluation baseline. `ClusterBaselineFixtures.defaultFixture()` ships 13 frozen items covering 5 must-merge groups (same event reported via different URLs/sources/phrasings) and 5 must-not-merge pairs (same entity, different action). `ClusterEvaluationService` replays any `ClusterAssignmentStrategy` against the fixture and emits precision, recall, false-merge, and false-split metrics. The runner is strategy-agnostic, so V1 (`hn-rule-v1`) and V2 (`event-rule-v2`) can be evaluated against identical inputs. The repeatable acceptance path is `.\scripts\accept-phase-16a.ps1`.

Phase 16 upgrades URL-level merging to event-level clustering V2 while keeping V1 (`hn-rule-v1`) authoritative. The V2 pipeline is deterministic: `ItemFeatureExtractor` (title, external ids, entities, keywords, event time, publisher, event type) -> `ClusterCandidateRetriever` (bounded to 72h / 50 candidates) -> `LayeredMatcher` (L1 identifiers, L2 entity + org + event type + time, L3 weighted similarity with 0.82/0.60 thresholds) -> `cluster_match_decision` persistence for ACCEPTED/REJECTED/REVIEW_REQUIRED/NO_CANDIDATE. `ClusterAssignmentOrchestrator` mirrors the Phase 15 shadow pattern: V1 still controls online cluster membership; V2 runs in evaluate-only mode when `ai-radar.cluster.shadow-strategy=event-rule-v2` is set, persisting only `cluster_match_decision` rows for offline comparison and future governance. V2 never uses pgvector, embeddings, or LLM as a decision maker. The repeatable acceptance path is `.\scripts\accept-phase-16.ps1`.

Phase 17B adds a manual cluster governance layer between V2 evaluate-only shadow runs and any future V2 online membership writes. Governance services (`ClusterMergeService`, `ClusterSplitService`, `MoveItemService`, `ReclusterService`, `ClusterReviewService`) operate on the existing `hot_cluster` / `hot_cluster_item` tables and record every mutation in `cluster_membership_history`. `REVIEW_REQUIRED` decisions are materialized lazily into `cluster_review_task` rows so the V2 strategy itself stays read-only on the wire. Phase 17B does not flip the online strategy; the repeatable acceptance path is `.\scripts\accept-phase-17b.ps1`.

Phase 17C promotes V2 from evaluate-only shadow to a gated, staged online writer. V1 (`hn-rule-v1`) remains authoritative and still creates the initial singleton cluster for every crawled item. When `ai-radar.cluster.v2-online.enabled=true` plus a non-zero `traffic-percent` and an explicit `allowed-match-levels` set, `V2OnlineAssignmentService` runs the V2 pipeline, picks the best candidate, and relocates the item from its V1 singleton into the V2 target cluster via `MoveItemService` with `OperatorType.SYSTEM`. Every relocation writes a `cluster_membership_history` row through the same governance path as manual ops, and V2 online runs inside a nested savepoint so any V2-side failure rolls back only the V2 work — V1's singleton stays intact. `REVIEW_REQUIRED` decisions proactively materialize into `cluster_review_task` rows. `GET /api/v1/cluster-strategy/status` returns the live configuration and a derived rollout stage (`V1_ONLY`, `SHADOW_ONLY`, `STAGE_2_L1`, `STAGE_3_L2`, `STAGE_4_L3`). Rollback is one property flip: `AI_RADAR_CLUSTER_V2_ONLINE_ENABLED=false`. The repeatable acceptance path is `.\scripts\accept-phase-17c.ps1`.

Phase 18A upgrades the Phase 14 item-level 24h delta into a cluster-level multi-window trend model. Four windows (`1h`, `6h`, `24h`, `3d`) are supported with per-window confidence tolerances. Each `SourceSignalAdapter` declares source-specific `MetricSemantics` (`MONOTONIC_CUMULATIVE`, `RANK_LIKE_REVERSIBLE`, `VOLATILE_SOCIAL`, `RELEVANCE_SCORE`) so only cumulative-counter drops (GitHub stars, Hugging Face downloads) flag `METRIC_RESET`; search-rank drift and social fluctuation are treated as informational. `ClusterTrendService` aggregates per-item `TrendMetrics` into a single `ClusterTrend` per window, de-duplicating DISCOVERY sources by canonical URL so Bing + DuckDuckGo + Sogou hits on the same URL cannot inflate cluster momentum. The Phase 14 `/trend?window=24h` endpoint and `GrowthMetrics` shape stay unchanged; new endpoints `GET /api/v1/hot-items/{id}/trends?window=6h` and `GET /api/v1/hot-clusters/{id}/trends?windows=1h,6h,24h,3d` expose the richer model. No Score V2 ranking change, no frontend visualization, and no cache table are introduced in this phase. The repeatable acceptance path is `.\scripts\accept-phase-18a.ps1`.

Phase 18B promotes Score V2 from shadow score to a controllable online ranking capability. `ScoringContext` now carries the Phase 18A `ClusterTrend` (24h window), source-role groupings, deduplicated DISCOVERY canonical URLs, and the earliest credible event time. The five V2 calculators that previously read primary-item data (`MomentumScoreCalculator`, `AdoptionScoreCalculator`, `DiscussionScoreCalculator`, `RelevanceScoreCalculator`, `FreshnessScoreCalculator`) are refactored to consume cluster-level inputs so events with multiple evidence items are no longer undercounted when none of them happens to be the primary item. `ScoringStrategyProperties` exposes `ai-radar.scoring.online-version` (default `hn-score-v1`, supports `cross-source-score-v2`); the list / detail endpoints accept an optional `scoringVersion` query parameter for V1/V2 side-by-side comparison, `AlertService` and `DailyReportService` read the configured online version for ranking, and `GET /api/v1/scoring-strategy/status` returns the live configuration + rollout stage. V1 remains the conservative default; V2 failures still fall back to V1 through the unchanged `ScoringOrchestrator` shadow-swallow contract. The repeatable acceptance path is `.\scripts\accept-phase-18b.ps1`.
