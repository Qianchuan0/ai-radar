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

## Pending Decisions

1. When to introduce `pgvector` or other embedding-based retrieval.
2. Whether future scheduling needs exceed Spring Scheduler.
3. Which LLM integration framework to adopt later.
4. When global frontend state is large enough to justify Pinia.
5. When aggregate analytics APIs justify charts or dashboards.

## ADR Index

- [ADR-001: Use PostgreSQL as the Primary Database](adr/ADR-001-primary-database.md)
- [ADR-002: Retain Immutable Raw Item Snapshots](adr/ADR-002-raw-item-retention.md)
- [ADR-003: Start with a Rule-Based Clustering Baseline](adr/ADR-003-rule-based-clustering-baseline.md)
- [ADR-004: Start with Explainable Rule-Based Hot Scoring](adr/ADR-004-rule-based-hot-scoring-baseline.md)
- [ADR-005: Use Vue 3 for the Frontend MVP](adr/ADR-005-frontend-mvp-stack.md)
