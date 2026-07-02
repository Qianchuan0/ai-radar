# Backend Foundation

## Scope

Phase 1 established the backend, database, migration, configuration, logging,
and API contract foundations. Phase 2 now implements the first Hacker News
closed loop on top of those boundaries.

## Runtime

- Java 17
- Spring Boot 3.5.15
- PostgreSQL 17 for local development
- MyBatis-Plus for persistence in later business modules
- Flyway with versioned SQL migrations

## Local Database

Start PostgreSQL from the repository root:

```powershell
docker compose up -d postgres
```

The local-only defaults are:

```text
database: ai_radar
username: ai_radar
password: ai_radar
port:     5432
```

These defaults are for local development only. Deployment environments must
override them and must not commit secrets.

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `AI_RADAR_DB_URL` | `jdbc:postgresql://localhost:5432/ai_radar` | JDBC URL |
| `AI_RADAR_DB_USERNAME` | `ai_radar` | Database user |
| `AI_RADAR_DB_PASSWORD` | `ai_radar` | Database password |
| `AI_RADAR_DB_MAX_POOL_SIZE` | `10` | Maximum Hikari connections |
| `AI_RADAR_DB_MIN_IDLE` | `2` | Minimum idle Hikari connections |
| `AI_RADAR_DB_CONNECTION_TIMEOUT_MS` | `30000` | Connection timeout |
| `AI_RADAR_LOG_LEVEL` | `INFO` | Application package log level |
| `SERVER_PORT` | `8080` | HTTP port |
| `AI_RADAR_POSTGRES_PORT` | `5432` | Local container host port |
| `AI_RADAR_HN_BASE_URL` | `https://hacker-news.firebaseio.com/v0` | Hacker News API base URL |
| `AI_RADAR_HN_CONNECT_TIMEOUT` | `3s` | Hacker News connection timeout |
| `AI_RADAR_HN_READ_TIMEOUT` | `5s` | Hacker News response timeout |
| `AI_RADAR_HN_MAX_ATTEMPTS` | `2` | Maximum attempts for retryable upstream failures |

Source API tokens and credentials must be injected through environment variables
or a future secret manager. They must not be stored in `source_config.config_payload`.

## Database Migrations

Migrations live in:

```text
backend/src/main/resources/db/migration/
```

Rules:

1. Never edit a migration after it has been shared or applied outside a disposable local database.
2. Add a new versioned migration for every schema change.
3. Keep production-like data fixes explicit and reviewable.
4. Do not use `flyway clean` against shared or production databases.
5. Verify indexes, uniqueness, foreign keys, and status checks together with each table change.

## Logging

- Every HTTP response carries `X-Request-Id`.
- A valid caller-provided request ID is preserved; otherwise the backend creates one.
- The request ID is placed in the logging MDC for the duration of the request.
- Do not log credentials, tokens, raw authentication headers, or complete raw payloads.
- Expected business failures should use stable error codes; unexpected failures are logged with stack traces.

## API Contract

The initial contract, now aligned with the Phase 2 implementation, is:

```text
docs/api/phase-one-openapi.yaml
```

The health, source configuration, synchronous manual crawl, crawl task query,
hot cluster list, and hot cluster detail paths are implemented. The manual crawl
response is terminal because Phase 2 deliberately keeps execution synchronous.

## Persistence Boundaries

- Controller handles protocol adaptation and validation.
- Service owns use-case orchestration and transaction boundaries.
- Mapper owns data access.
- Entity is a persistence model and is not returned by APIs.
- DTO is used for request and cross-layer input.
- VO is used for API responses.
- Complex deduplication, cluster ranking, and score queries may use explicit SQL.
