# AI Radar Decision Log

This file captures short accepted decisions. Larger decisions with context, options, and consequences belong in `docs/adr/`.

## Accepted Decisions

1. AI Radar is an event-level AI intelligence product, not a generic news aggregator.
2. The MVP starts from arXiv, Hacker News, and GitHub.
3. The first priority is the smallest real closed loop before LLM analysis, alerts, reports, or evaluation.
4. Backend foundation uses Java 17, Spring Boot 3.5.15, MyBatis-Plus, and Flyway.
5. PostgreSQL is the primary database and `pgvector` is deferred.
6. Immutable `raw_item` snapshots are retained for traceability, reruns, and evaluation.
7. The first clustering baseline is deterministic and rule-based.
8. The first hot-scoring baseline is explainable and rule-based.
9. The first crawl path is manual before introducing scheduler infrastructure.
10. The API contract baseline uses OpenAPI 3.1.
11. The frontend MVP uses Vue 3, TypeScript, Vite, Vue Router, Ant Design Vue, and Axios.
12. The final Phase 3 frontend runs as direct Vue routes instead of `iframe`-wrapped static demo pages.
13. Local text query and minimum-score filtering are explicit frontend display filters, not backend business fields.
14. The first Phase 5 structured analysis path is synchronous, evidence-grounded, and stored in `cluster_analysis` with every success or failure recorded.
15. The first Phase 6 alert path is manual, synchronous, and stored in `subscription_rule` plus `alert_record` before introducing scheduler infrastructure or external delivery channels.
16. The first Phase 7 daily report path is manual, synchronous, and generated from persisted `hot_cluster`, `hot_score`, evidence, and the latest stored `cluster_analysis` before introducing scheduler infrastructure or new model calls.
17. The first Phase 8 evaluation loop is manual, rule-based, and runs labeled cases against persisted data (`raw_item`, `hot_item`, `hot_cluster`, `hot_score`, `cluster_analysis`, `alert_record`) without introducing LLM judges, schedulers, queues, vector stores, or external evaluation platforms.
18. The first fourth-source expansion after Phase 8 is Hugging Face Models, while Hugging Face datasets and Spaces stay out of scope for Phase 9A.
19. The frontend uses a unified `AppLayout` shell component for sidebar/topbar/breadcrumb, with menu items and breadcrumbs derived from route `meta`. UI language is unified to Chinese with ant-design-vue `zh_CN` locale configured via `a-config-provider`. The independent layouts and English copy previously implemented per page (Phase 6/7/8) are consolidated into this shell, and `shared/utils/datetime.ts` provides unified Chinese time formatting.
20. Phase 9A closes with Hugging Face Models integrated into the existing manual crawl-to-cluster path, frontend-compatible source labels, and a dedicated acceptance script plus source connector checklist for future source additions.

## Pending Decisions

1. When to introduce `pgvector` or other embedding-based retrieval.
2. Whether future scheduling needs exceed Spring Scheduler.
3. Which real LLM provider or framework should replace the Phase 5 fake model client.
4. When global frontend state is large enough to justify Pinia.
5. When aggregate analytics APIs justify charts or dashboards.
6. Which delivery channel should be introduced first after the manual Phase 6 alert baseline.
7. When daily report generation should move from manual runs to scheduled delivery.

## ADR Index

- [ADR-001: Use PostgreSQL as the Primary Database](adr/ADR-001-primary-database.md)
- [ADR-002: Retain Immutable Raw Item Snapshots](adr/ADR-002-raw-item-retention.md)
- [ADR-003: Start with a Rule-Based Clustering Baseline](adr/ADR-003-rule-based-clustering-baseline.md)
- [ADR-004: Start with Explainable Rule-Based Hot Scoring](adr/ADR-004-rule-based-hot-scoring-baseline.md)
- [ADR-005: Use Vue 3 for the Frontend MVP](adr/ADR-005-frontend-mvp-stack.md)
