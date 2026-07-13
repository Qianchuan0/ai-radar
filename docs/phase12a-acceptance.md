# Phase 12A Acceptance

**Date:** 2026-07-12
**Status:** Accepted

## Verified Capabilities

- `SOGOU_SEARCH` source type can be created via `SourceConfigService`
- Manual crawl task triggers `SogouSearchClient.search()` with TC3-HMAC-SHA256 signature
- `raw_item` persists with `source_type = SOGOU_SEARCH` and full search result payload
- `hot_item` normalizes to `item_type = SEARCH_RESULT` with rank-based points
- `hot_cluster` and `hot_score` are generated through the existing clustering and scoring pipeline
- Hot cluster API supports `sourceType=SOGOU_SEARCH` filtering
- Frontend displays "搜狗搜索" source label and filter option
- Application starts without `AI_RADAR_SOGOU_SEARCH_SECRET_ID` / `AI_RADAR_SOGOU_SEARCH_SECRET_KEY`
- `TencentCloudV3Signer` produces valid TC3-HMAC-SHA256 signatures
- `CRAWL_PROVIDER_NOT_CONFIGURED` error code covers missing credentials
- Optional Tencent Cloud request parameters such as `Cnt` and `Mode` are omitted unless configured, and `Response.Error` payloads fail the crawl explicitly

## Test Coverage

- `TencentCloudV3SignerTest`: signature structure, determinism, payload sensitivity, credential scope
- `SogouSearchClientTest`: signed POST, Pages parsing, empty response, upstream error, Tencent Cloud `Response.Error`, optional parameter omission, missing fields
- `SogouSearchRawDataFlowIntegrationTest`: full `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score` flow
- Frontend: `parseSourceType` includes `SOGOU_SEARCH`, production build passes

## Acceptance Script

```
cd D:\AiProgram\ai-radar
.\scripts\accept-phase-12a.ps1
```
