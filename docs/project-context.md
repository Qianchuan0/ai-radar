# AI Radar Project Context

## Project Name

AI Radar

## Product Positioning

AI Radar is an AI industry intelligence and trend analysis platform. It collects AI-related information from research feeds, developer communities, and open-source platforms, preserves the original evidence, and transforms scattered signals into event-level hot clusters.

## Target Users

- developers and technical leads tracking AI product and infrastructure changes
- product managers looking for actionable AI market signals
- researchers following model, paper, company, and open-source momentum

## Problems Solved

- AI information is fragmented across multiple sources
- the same event is repeated by different platforms
- platform-specific engagement signals are hard to compare directly
- pure recency ranking is too noisy for decision making
- summary-only workflows lose traceability and evaluation value

## Why It Is Not a Generic News Aggregator

The system does not stop at “crawl then summarize.” It keeps immutable raw snapshots, normalizes heterogeneous content, clusters related items into `hot_cluster`, and exposes explainable `hot_score` plus evidence pages.

## Core Flow

```text
multi-source collection
-> optional scheduled crawl trigger based on source interval metadata
-> raw data retention
-> normalization
-> deduplication and event clustering
-> explainable scoring
-> hot-cluster APIs
-> frontend list/detail pages
-> structured analysis (real OpenAI provider with Structured Outputs, fake fallback retained)
-> subscription matching and alert review
-> manual and optional scheduled daily report generation
-> manual evaluation of labeled cases against persisted data
```

## MVP Sources

### arXiv

Purpose: monitor AI papers and research directions.

Planned fields:

- `title`
- `abstract`
- `authors`
- `categories`
- `published_at`
- `arxiv_id`
- `pdf_url`
- `source_url`

### Hacker News

Purpose: monitor developer discussion around AI tools, launches, and workflows.

Planned fields:

- `title`
- `url`
- `hn_item_id`
- `points`
- `comments_count`
- `author`
- `published_at`
- raw payload

### GitHub

Purpose: monitor AI open-source project activity and adoption.

Current Phase 4 fields:

- `repo_full_name`
- `repo_name`
- `owner`
- `description`
- `url`
- `stars`
- `forks`
- `open_issues`
- `watchers`
- `topics`
- `language`
- `updated_at`

### Hugging Face Models

Purpose: monitor model ecosystem momentum and model-level adoption signals.

Phase 9A fields:

- `model_id`
- `url`
- `downloads`
- `likes`
- `pipeline_tag`
- `tags`
- `library_name`
- `created_at`
- `last_modified`
- `private`

## MVP Functional Modules

1. source configuration and enable/disable controls
2. manual crawl execution and crawl-task tracking
3. immutable raw-item retention
4. hot-item normalization
5. rule-based clustering
6. explainable hot scoring
7. hot-cluster list/detail APIs
8. frontend ranking page, detail page, and page states
9. manual subscription matching and alert review page
10. manual daily report generation, optional scheduled report generation, and report history page
11. manual evaluation loop with labeled datasets, rule-based case verifiers, persisted metrics, and error analysis
12. real OpenAI structured analysis provider with provider-not-configured fallback and persisted failure codes
13. lightweight scheduled crawl runner that reuses `crawl_interval_minutes` and the existing crawl-task pipeline
14. lightweight scheduled daily report runner that reuses the existing report generation service

## Phase 1 Minimal Closed Loop

```text
source_config
-> manual crawl
-> crawl_task
-> raw_item
-> hot_item
-> hot_cluster
-> hot_score
-> hot cluster APIs
-> frontend hot list
```

That closed loop is now implemented with the first real source path and a direct Vue frontend.

## Core Domain Models

### `source_config`

Stores source type, crawl configuration, keywords, enablement state, and interval metadata.

### `crawl_task`

Stores one crawl execution lifecycle, including state, counts, idempotency key, and error details.

### `raw_item`

Stores the immutable source snapshot for each crawl event. This is required for traceability, reruns, diagnostics, and evaluation.

### `hot_item`

Stores normalized content independent of the original source schema.

### `hot_cluster`

Stores the event-level topic that groups one or more related `hot_item` records.

### `hot_score`

Stores the explainable score history for a cluster, including total score, component breakdown, scoring version, and calculation time.

## Accepted Stack

- Backend: Java 17, Spring Boot 3.5.15, MyBatis-Plus, Flyway
- Database: PostgreSQL
- API contract: OpenAPI 3.1
- Frontend MVP: Vue 3, TypeScript, Vite, Vue Router, Ant Design Vue, Axios
- Local runtime: Docker Compose
- Clustering baseline: deterministic rules
- Scoring baseline: explainable rule-based scoring

## Design Principles

- event-centric instead of single-item-centric
- raw evidence retained separately from processed models
- strict backend layering: Controller / Service / Mapper / Entity / DTO / VO
- `hot_cluster` is the primary business object
- `raw_item` must stay available for traceability and evaluation
- LLMs cannot be the sole decision maker for clustering or scoring
- structured analysis must stay evidence-grounded and persist every run outcome
- subscription matching should stay traceable with persisted rules, match reasons, and suppression behavior
- important technical decisions must be reflected in the decision log or ADRs
- avoid heavy infrastructure before the product proves the need

## What Is Intentionally Out of Scope Right Now

- heavy scheduled orchestration and external job infrastructure beyond the lightweight Spring Scheduler operations loops
- embedding or LLM clustering
- external alert delivery channels, scheduler-driven matching, and scheduled report delivery
- automated scheduling of evaluation runs and LLM-as-judge evaluation; the Phase 8 baseline is manual and rule-based only
