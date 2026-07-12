# Phase 12A: Sogou Search Source Design

**Date:** 2026-07-12
**Status:** Approved
**Phase:** 12A
**Source Type:** `SOGOU_SEARCH`

## Overview

Phase 12A adds the first Chinese platform source to AI Radar: Sogou Search via the Tencent Cloud Web Search API (wsa). The goal is to integrate Chinese-language public web search results into the existing `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score -> APIs -> frontend` closed loop without introducing new infrastructure.

## External API

**Tencent Cloud Web Search API (联网搜索API, wsa)**

- Endpoint: `POST https://wsa.tencentcloudapi.com`
- Action: `SearchPro`
- Version: `2025-05-08`
- Region: not required for this API
- Authentication: TC3-HMAC-SHA256 signature (requires SecretId + SecretKey)
- Content-Type: `application/json`

### Request Parameters

| Parameter | Required | Type | Description |
|-----------|----------|------|-------------|
| Query | Yes | String | Search query |
| Cnt | No | Integer | Result count, allowed: 10/20/30/40/50, default 10 |
| Mode | No | Integer | 0=natural (default), 1=multimodal VR, 2=mixed |
| Site | No | String | Site filter (single domain) |
| Freshness | No | String | Time range: d1/d7/m3/y2/d/m/y or empty |
| FromTime | No | Integer | Start timestamp (seconds) |
| ToTime | No | Integer | End timestamp (seconds) |

### Response

| Field | Type | Description |
|-------|------|-------------|
| Query | String | Original query |
| Pages | Array of String | JSON strings, each containing search result fields |
| Version | String | standard/premium/lite/flagship |
| Msg | String | Message |
| RequestId | String | Request ID |

Each `Pages` element is a JSON string with: `title`, `url`, `passage`, `content` (premium only), `site`, `score` (0-1), `date` (format "yyyy/MM/dd HH:mm:ss"), `pics`, `favicon`.

## Design Decisions

### 1. Manual TC3-HMAC-SHA256 Signature (No SDK)

Implement the Tencent Cloud v3 signature manually in a reusable `TencentCloudV3Signer` utility class. This avoids introducing the `tencentcloud-sdk-java` dependency, keeps the implementation consistent with existing clients (all use Spring RestClient directly), and produces a signer reusable for future Tencent Cloud sources (Phase 12B-12E).

### 2. Config Fields Aligned with API Parameters

The `source_config.config_payload` uses actual API parameter names (`query`, `cnt`, `mode`, `site`, `freshness`) rather than the phase.md draft names (`count`, `page`, `siteFilter`, `freshness=DAY`). This eliminates mapping logic and keeps the config transparent.

### 3. CRAWL_PROVIDER_NOT_CONFIGURED Error Code

New `ErrorCode.CRAWL_PROVIDER_NOT_CONFIGURED` for when SecretId/SecretKey are missing or invalid. Mirrors the existing `ANALYSIS_PROVIDER_NOT_CONFIGURED` pattern. Reusable for future sources requiring credentials.

## New Backend Classes

### `crawl.client.support.TencentCloudV3Signer`

Pure utility class, no Spring dependencies.

- **Input:** secretId, secretKey, service ("wsa"), host, action ("SearchPro"), version ("2025-05-08"), payload (JSON string), timestamp (Instant)
- **Output:** Authorization header value + X-TC-Timestamp value
- **Algorithm:**
  1. Build canonical request: `POST\n/\n\ncontent-type:application/json\nhost:{host}\nx-tc-action:{action}\n\ncontent-type;host;x-tc-action\n{hashedPayload}`
  2. Build string to sign: `TC3-HMAC-SHA256\n{timestamp}\n{date}/{service}/tc3_request\n{hashedCanonicalRequest}`
  3. Derive signature: HMAC-SHA256 layered derivation (`secretKey -> date -> service -> "tc3_request" -> stringToSign`)
  4. Build Authorization: `TC3-HMAC-SHA256 Credential={secretId}/{date}/{service}/tc3_request, SignedHeaders=content-type;host;x-tc-action, Signature={signature}`

### `crawl.client.sogou.SogouSearchProperties`

```java
@ConfigurationProperties(prefix = "ai-radar.collector.sogou-search")
public record SogouSearchProperties(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    int maxAttempts,
    String secretId,
    String secretKey
) {
    // maxAttempts validation: 1-3
}
```

### `crawl.client.sogou.SogouSearchRequest`

```java
public record SogouSearchRequest(
    String query,
    int cnt,
    int mode,
    String site,
    String freshness,
    Long fromTime,
    Long toTime
) {}
```

### `crawl.client.sogou.FetchedSogouSearchResult`

```java
public record FetchedSogouSearchResult(
    String title,
    String url,
    String passage,
    String content,
    String site,
    double score,
    Instant publishedAt,
    int rank
) {}
```

### `crawl.client.sogou.SogouSearchClient`

- Uses `RestClient` with `JdkClientHttpRequestFactory` (same pattern as HuggingFaceClient)
- Builds POST request to `/` with JSON body containing Query, Cnt, Mode, Site, Freshness, FromTime, ToTime
- Applies TC3-HMAC-SHA256 signature headers: `Authorization`, `X-TC-Action`, `X-TC-Version`, `X-TC-Timestamp`, `X-TC-Region` (omitted)
- Parses `Pages` array of JSON strings, each parsed into `FetchedSogouSearchResult` with `rank` = index + 1
- Retry logic identical to HuggingFaceClient: retry on `ResourceAccessException` and HTTP 5xx, max 2 attempts
- Error mapping: `UnauthorizedOperation`/`ResourceNotFound` -> `CRAWL_PROVIDER_NOT_CONFIGURED`; other API errors -> `CRAWL_UPSTREAM_ERROR`

### `crawl.collector.sogou.SogouSearchCollector`

- `@Component`, `implements SourceCollector`, `supportedType() = SOGOU_SEARCH`
- Reads config_payload: query, cnt (default 10), mode (default 0), site (default ""), freshness (default "")
- Checks SecretId/SecretKey in `collect()`: if blank, throws `BusinessException(CRAWL_PROVIDER_NOT_CONFIGURED)`
- Calls `SogouSearchClient.search(request)`
- Converts each `FetchedSogouSearchResult` to `CollectedItem`:
  - `externalId` = SHA-256(url)
  - `sourceUrl` = url
  - `rawPayload` = JSON with title, url, passage, content, site, score, date, rank, query
  - `publishedAt` = parsed from date field, null if parse fails
  - `fetchedAt` = Instant.now()
- `CollectionError` for items missing title or url

### `item.normalizer.SogouSearchHotItemNormalizer`

- `@Component`, `implements HotItemNormalizer`, `supportedType() = SOGOU_SEARCH`
- Reads `raw_item.rawPayload`
- `item_type` = `"SEARCH_RESULT"`
- `title` = payload.title
- `summary` = passage (fallback to content), truncated to 2000 chars
- `source_url` = payload.url, canonicalized via `UrlCanonicalizer`
- `author` = payload.site
- `tags` = query keywords split by " OR " or whitespace + site name
- `metrics`:
  - `points` = `max(1, totalCount - rank + 1)` (rank 1 gets highest points)
  - `commentsCount` = 0
  - `rank` = payload.rank
  - `score` = payload.score
  - `site` = payload.site
- `contentHash` = SHA-256(title.toLowerCase() + "\n" + source_url)

## Source Config Validation

`SourceConfigService.validateSogouSearchConfig`:
- `query`: required, non-blank
- `cnt`: optional, default 10, allowed: 10/20/30/40/50
- `mode`: optional, default 0, allowed: 0/1/2
- `site`: optional, default ""
- `freshness`: optional, default "", pattern: `^[dmy]\d{0,2}$` (d1-d30, m1-m12, y1-y5, or bare d/m/y) or empty

## Error Code

New `ErrorCode`:
```
CRAWL_PROVIDER_NOT_CONFIGURED("CRAWL.PROVIDER_NOT_CONFIGURED", "Crawl provider credentials are not configured.", HttpStatus.BAD_GATEWAY)
```

## Frontend Changes

### `contracts.ts`
Add `"SOGOU_SEARCH"` to the `SourceType` union.

### `query.ts`
Add `"SOGOU_SEARCH"` to `parseSourceType`.

### Page-level source labels (4 files)
Add to each `sourceLabel`/`sourceTypeLabel` function:
```typescript
if (source === "SOGOU_SEARCH") return "搜狗搜索";
```

Files:
- `HotClusterListPage.vue`
- `HotClusterDetailPage.vue`
- `AlertsPage.vue` (also add to `sourceOptions` array)
- `DailyReportsPage.vue`

### `query.test.ts`
Add `SOGOU_SEARCH` test case for `parseSourceType`.

## Testing Plan

### Backend Tests

| Test Class | Coverage |
|-----------|----------|
| `TencentCloudV3SignerTest` | Canonical request, string to sign, HMAC derivation, Authorization header format |
| `SogouSearchClientTest` | Request building, signature headers, Pages JSON string array parsing, empty response, 4xx/5xx errors, retry, missing credentials |
| `SogouSearchRawDataFlowIntegrationTest` | `raw_item -> hot_item -> hot_cluster -> hot_score` full flow with mock client |
| `SourceConfigServiceTest` (extend) | SOGOU_SEARCH config validation: blank query, invalid cnt/mode/freshness |

### Frontend Tests
- `query.test.ts`: `SOGOU_SEARCH` parseSourceType
- `npm run build`: SourceType union compatibility

### Acceptance Script
`scripts/accept-phase-12a.ps1` (modeled on `accept-phase-9a.ps1`):
1. Backend tests: `SogouSearchClientTest`, `SogouSearchRawDataFlowIntegrationTest`, `TencentCloudV3SignerTest`
2. Frontend tests
3. Frontend build

## Documentation Sync

| Document | Change |
|----------|--------|
| `docs/roadmap.md` | Add Phase 12A, status In Progress |
| `docs/decision-log.md` | Record: Chinese platform sources start with Sogou Search API, no web scraping |
| `docs/project-context.md` | Add Chinese platform source direction |
| `README.md` | Update after implementation and verification |
| `docs/phase12a-acceptance.md` | New: acceptance record |
| ADR | Not added (no new infrastructure) |

## Acceptance Criteria

- Can create `SOGOU_SEARCH` source config
- Can manually trigger crawl task
- Can persist `raw_item`
- Can generate `hot_item` (item_type=SEARCH_RESULT)
- Can enter `hot_cluster`
- Can generate `hot_score`
- API can filter by `sourceType=SOGOU_SEARCH`
- Frontend displays "搜狗搜索" source label
- Missing API key produces clear failure code, does not block app startup
- Documentation synced
- No auto-commit, no auto-push

## Out of Scope

- WeChat, Bilibili, Weibo, Chinese RSS sources (Phase 12B-12E)
- Web scraping (Sogou weixin.sogou.com HTML pages)
- Tencent Cloud Java SDK dependency
- Pagination support (API has no page parameter)
- Image/video/multimodal result extraction
- Scheduled crawl specific to Sogou (existing scheduler handles all sources)
