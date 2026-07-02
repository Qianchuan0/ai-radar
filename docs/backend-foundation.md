# Backend Foundation

## Scope

Phase 1 establishes the backend, database, migration, configuration, logging,
and API contract foundations. It does not implement source management, crawling,
normalization, clustering, scoring, or hot cluster query use cases.

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

The Phase 1 contract is:

```text
docs/api/phase-one-openapi.yaml
```

Only `GET /api/health` is implemented in Phase 1. Other paths describe the
Phase 2 boundary and must not be reported as available until implemented and tested.

## Persistence Boundaries

- Controller handles protocol adaptation and validation.
- Service owns use-case orchestration and transaction boundaries.
- Mapper owns data access.
- Entity is a persistence model and is not returned by APIs.
- DTO is used for request and cross-layer input.
- VO is used for API responses.
- Complex deduplication, cluster ranking, and score queries may use explicit SQL.
