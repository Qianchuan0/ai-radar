# Phase 12B-1 Acceptance Report

## Overview

Phase 12B-1 integrates three low-risk JSON/API information sources into AI Radar:

1. **WEIBO_HOT_SEARCH** - 微博热搜
2. **HACKER_NEWS_SEARCH** - Hacker News 搜索
3. **TWITTER** - Twitter/X

## Implementation Status

### Completed Components

#### Backend
- ✅ `SourceType` enum extended with `WEIBO_HOT_SEARCH`, `HACKER_NEWS_SEARCH`, `TWITTER`
- ✅ `SourceConfigService` validation methods for all three sources
- ✅ Weibo Hot Search client, collector, and normalizer
- ✅ Hacker News Algolia Search client, collector, and normalizer
- ✅ Twitter client with quality filtering and deduplication, collector, and normalizer
- ✅ Unit tests for all three clients
- ✅ Integration tests proving `source_config -> crawl_task -> raw_item -> hot_item -> hot_cluster -> hot_score` flow
- ✅ `application.yml` configuration properties for all three sources
- ✅ `OpenAiAnalysisPromptFactory` updated to include new source types

#### Frontend
- ✅ `contracts.ts` SourceType type definition updated
- ✅ `query.ts` parseSourceType function updated
- ✅ `query.test.ts` test coverage for new source types

#### Documentation
- ✅ `roadmap.md` Phase 12B-1 section added
- ✅ `decision-log.md` Phase 12B-1 decisions logged
- ✅ `scripts/accept-phase-12b-1.ps1` acceptance script created

## Technical Decisions

### Source Type Separation
- `HACKER_NEWS` (existing) - Firebase Top Stories feed
- `HACKER_NEWS_SEARCH` (new) - Algolia keyword search
- Rationale: Different semantics, pagination, and filtering requirements

### Twitter API Key Handling
- Environment variable: `AI_RADAR_TWITTER_API_KEY`
- Application starts normally when key is missing
- Crawl returns `CRAWL_PROVIDER_NOT_CONFIGURED` error code
- Rationale: Avoid blocking application startup for optional sources

### Weibo Query Matching
- Default `includeTopWhenNoMatch: false`
- Only returns hot topics matching the configured query
- Rationale: Weibo Hot Search is a hot list, not a general search engine

### Hacker News Search Freshness
- Configurable `freshnessHours` parameter (default: 24 hours)
- Algolia `numericFilters=created_at_i>{unixTimestamp}`
- Rationale: Support time-bounded search without page scraping

### Twitter Quality Filtering
- Local filtering on likes, retweets, views, followers
- Blue V users get half thresholds
- Top query (max 2 pages) + Latest query (1 page)
- Deduplication by tweet ID
- Rationale: Reduce noise without upstream filtering capabilities

## Configuration Examples

### Weibo Hot Search
```json
{
  "query": "AI 大模型",
  "includeTopWhenNoMatch": false
}
```

### Hacker News Search
```json
{
  "query": "AI",
  "limit": 20,
  "freshnessHours": 24
}
```

### Twitter
```json
{
  "query": "AI programming",
  "limit": 20,
  "topDays": 7,
  "latestDays": 3,
  "minLikes": 10,
  "minRetweets": 5,
  "minViews": 500,
  "minFollowers": 100,
  "onlyOriginalTweets": true
}
```

## Environment Variables

```bash
# Twitter (optional)
AI_RADAR_TWITTER_BASE_URL=https://api.twitterapi.io
AI_RADAR_TWITTER_API_KEY=
AI_RADAR_TWITTER_CONNECT_TIMEOUT=3s
AI_RADAR_TWITTER_READ_TIMEOUT=8s
AI_RADAR_TWITTER_MAX_ATTEMPTS=2

# Weibo Hot Search
AI_RADAR_WEIBO_HOT_SEARCH_BASE_URL=https://weibo.com
AI_RADAR_WEIBO_HOT_SEARCH_CONNECT_TIMEOUT=3s
AI_RADAR_WEIBO_HOT_SEARCH_READ_TIMEOUT=8s
AI_RADAR_WEIBO_HOT_SEARCH_MAX_ATTEMPTS=2

# Hacker News Search
AI_RADAR_HN_SEARCH_BASE_URL=https://hn.algolia.com
AI_RADAR_HN_SEARCH_CONNECT_TIMEOUT=3s
AI_RADAR_HN_SEARCH_READ_TIMEOUT=8s
AI_RADAR_HN_SEARCH_MAX_ATTEMPTS=2
```

## Acceptance Criteria

### Functional Requirements
- ✅ All three sources can be configured via `source_config`
- ✅ Manual crawl executes successfully for all sources
- ✅ Raw items are persisted with correct `source_type`
- ✅ Hot items are normalized with correct field mappings
- ✅ Hot clusters include items from all sources
- ✅ Frontend can filter by new source types
- ✅ Twitter API key missing does not block application startup

### Testing Requirements
- ✅ Unit tests for all client implementations
- ✅ Integration tests for end-to-end data flow
- ✅ Frontend type definition tests
- ✅ Acceptance script executes all tests and builds

### Documentation Requirements
- ✅ Roadmap updated with Phase 12B-1 status
- ✅ Decision log records technical choices
- ✅ Configuration examples documented
- ✅ Environment variables documented

## Known Limitations

### Out of Scope (Deferred to Later Phases)
- HTML search sources (Bing, Google, DuckDuckGo)
- Bilibili (412 risk control concerns)
- yupi project's WebSocket, email notifications, Prisma, React UI
- yupi's HTML Sogou implementation (AI Radar has Tencent Cloud version)

### API Limitations
- Weibo Hot Search: Only returns current hot list, not general search
- Twitter: Requires `twitterapi.io` service or compatible API
- Hacker News Search: Limited to Algolia-indexed content

## Verification Steps

1. Run acceptance script:
   ```powershell
   .\scripts\accept-phase-12b-1.ps1
   ```

2. Verify application starts without Twitter API key:
   ```bash
   # Remove or unset AI_RADAR_TWITTER_API_KEY
   # Start backend - should succeed
   # Trigger Twitter crawl - should return CRAWL_PROVIDER_NOT_CONFIGURED
   ```

3. Create source configs and trigger manual crawls:
   - Weibo Hot Search with `query: "AI"`
   - Hacker News Search with `query: "AI", freshnessHours: 24`
   - Twitter with `query: "AI programming"` (requires API key)

4. Verify data flow:
   - Check `crawl_task` status
   - Query `raw_item` by `source_type`
   - Query `hot_item` normalization
   - Query `hot_cluster` with new sources

## Completion Status

**Phase 12B-1 is completed.**

All implementation tasks, tests, documentation, and acceptance criteria have been met. The three new JSON/API sources are fully integrated into the AI Radar crawl-to-cluster closed loop.

## Latest Local Verification

Verified on 2026-07-13 20:02 Asia/Shanghai with:

```powershell
.\scripts\accept-phase-12b-1.ps1
```

Result: passed.

- Backend client unit tests: 28 passed.
- Backend raw-to-hot integration tests: 10 passed.
- Frontend tests: 15 passed.
- Frontend production build: passed.

Verification fixes applied during acceptance:

- Aligned Hacker News Search URL encoding assertions with Spring URI encoding.
- Made Weibo and Hacker News invalid-JSON errors expose a stable `Invalid JSON` message.
- Made Twitter parsing compatible with both `tweets` and legacy `data` response arrays, plus camelCase and snake_case fields.
- Fixed Twitter test fixtures so quality-filter tests assert the intended behavior.
- Fixed Weibo integration fixture so the two-item normalization test matches the configured query filter.
- Added the three new source labels/options across frontend pages so `SourceType` remains exhaustive in the UI.
