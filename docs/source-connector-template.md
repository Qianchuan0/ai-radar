# Source Connector Template

Use this checklist when adding a new source into the existing AI Radar closed loop.

## Required implementation areas

1. Add the new value to `SourceType`.
2. Add `source_config` validation in `SourceConfigService`.
3. Add source properties in `application.yml` and a typed `@ConfigurationProperties` record.
4. Add a client with request parameters, timeout handling, retry behavior, optional auth, and upstream error mapping.
5. Define the fetched source model returned by the client.
6. Add a `SourceCollector` implementation that converts upstream records into `CollectedItem`.
7. Define the stable `raw_payload` snapshot fields written into `raw_item`.
8. Add a `HotItemNormalizer` implementation for the new source.
9. Map source-specific metrics into unified metrics like `points` and `commentsCount`, while preserving source-native metrics.
10. Add a raw-to-hot integration test that proves `raw_item -> hot_item -> hot_cluster -> hot_score`.
11. Add client tests for query building, response parsing, invalid JSON, and upstream failures.
12. Update OpenAPI, roadmap, project context, decision log, and frontend source labels when the new source is user-visible.

## Guardrails

- Reuse the existing `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score` path.
- Keep the first version narrow. Add one source subtype before expanding into adjacent APIs.
- Do not put secrets into `source_config.config_payload`.
- Preserve stable raw fields even if the upstream API returns many more fields.
- Prefer visible proof through integration tests and frontend-compatible enum updates.
