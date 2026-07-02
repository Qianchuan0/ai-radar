# ADR-001: Use PostgreSQL as the Primary Database

## Status

Accepted

## Date

2026-07-02

## Context

AI Radar must persist source configuration, crawl task state, immutable source
records, normalized items, event-level cluster membership, and explainable score
history. Source payloads differ across arXiv, Hacker News, and GitHub, while the
processing chain also requires transactions, foreign keys, uniqueness, and
queryable relationships.

Vector similarity may become useful for clustering later, but the project does
not yet have an evaluation dataset proving that vector retrieval is required.

## Problem

Choose a primary database that supports the first real data flow without adding
an independent document or vector database prematurely.

## Options

1. **MySQL**
   - Mature relational database with broad operational support.
   - Supports JSON, but offers a less direct future path to keeping relational,
     JSON, and optional vector workloads in one selected platform.
2. **PostgreSQL**
   - Supports transactions, constraints, rich indexing, and `jsonb`.
   - Leaves open the option of adding pgvector later.
3. **PostgreSQL with pgvector enabled immediately**
   - Provides vector similarity from the start.
   - Introduces vector schema, index, model, and evaluation decisions before
     the need has been demonstrated.

## Decision

Use PostgreSQL as the primary database. Use `jsonb` where source-specific or
explainability payloads are genuinely variable. Do not enable pgvector in the
first data flow.

Use Flyway versioned SQL migrations as the database schema source of truth.

## Rationale

PostgreSQL meets both sides of the current data model: strongly constrained
relational processing state and heterogeneous source payloads. It supports the
MVP in one database while retaining a low-friction path to evaluate pgvector
later. Deferring pgvector prevents an unmeasured clustering hypothesis from
becoming an infrastructure dependency.

## Consequences

- The backend requires PostgreSQL to start with Flyway enabled.
- PostgreSQL-specific features such as `jsonb`, partial indexes, and GIN indexes
  may be used intentionally.
- Local development receives a PostgreSQL 17 Compose service.
- Database portability is not a primary Phase 1 goal.
- A future vector feature can be evaluated without deploying Milvus or another
  vector database immediately.

## Future Revisit Conditions

Revisit this decision if:

- operational constraints require a different managed database;
- measured scale or query patterns cannot be met reasonably by PostgreSQL;
- a labeled clustering evaluation set demonstrates a material benefit from
  vector similarity, at which point pgvector should be evaluated separately.
