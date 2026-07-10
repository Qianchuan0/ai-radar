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

- Start from `arXiv`, `Hacker News`, and `GitHub`.
- Prove the closed loop from source configuration to event-level hot-cluster display.
- Current verified path: `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score -> cluster_analysis -> subscription_rule -> alert_record -> daily_report -> APIs -> frontend list/detail/alerts/reports`.

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

**Phase 1 completed / Phase 2 completed / Phase 3 completed / Phase 4 completed / Phase 5 completed / Phase 6 in progress / Phase 7 in progress**
