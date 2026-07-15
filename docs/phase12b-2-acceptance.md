# Phase 12B-2 Acceptance: HTML Search Sources (Bing + DuckDuckGo)

**Date:** 2026-07-15
**Status:** Accepted

## Overview

Phase 12B-2 integrates HTML-based web search sources into AI Radar using lightweight jsoup parsing:

1. **BING_SEARCH** - Bing 搜索
2. **DUCKDUCKGO_SEARCH** - DuckDuckGo 搜索

## Verified Capabilities

### Backend
- ✅ `SourceType` enum extended with `BING_SEARCH`, `DUCKDUCKGO_SEARCH`
- ✅ `SourceConfigService` validation methods for both sources
- ✅ jsoup 1.18.3 dependency added
- ✅ HTML search common support components (htmlsearch package):
  - `HtmlSearchHeaders`: User-Agent management
  - `HtmlSearchUrlSanitizer`: tracking parameter removal
  - `HtmlSearchBlockDetector`: CAPTCHA/403/429 detection
  - `HtmlSearchParseException`: structured parse failures
- ✅ `BingSearchClient` with HTML parsing, safe headers, rate limiting
- ✅ `DuckDuckGoSearchClient` with HTML parsing, redirect URL decoding, block detection
- ✅ `BingSearchCollector` and `DuckDuckGoSearchCollector`
- ✅ `BingSearchHotItemNormalizer` and `DuckDuckGoSearchHotItemNormalizer`
- ✅ Unit tests for request validation, URL sanitization, block detection
- ✅ Raw-to-hot integration tests for both sources
- ✅ `application.yml` configuration properties

### Frontend
- ✅ `contracts.ts` SourceType type definition updated
- ✅ `query.ts` parseSourceType function updated
- ✅ `query.test.ts` test coverage for new source types
- ✅ Production build passes

### Documentation
- ✅ `roadmap.md` Phase 12B-2 section added
- ✅ `decision-log.md` Phase 12B-2 decisions logged
- ✅ `scripts/accept-phase-12b-2.ps1` acceptance script created
- ✅ `scripts/live-verify-phase-12b-2-html-search.ps1` optional live verification script

## Technical Decisions

### HTML Parsing Approach
- **Choice:** jsoup for lightweight parsing, no browser automation
- **Rationale:** Avoid Playwright/Puppeteer/Selenium overhead and proxy pools
- **Safety:** Conservative rate limiting (maxAttempts=1, minRequestInterval=10s)

### Failure Isolation
- **Blocked Page Detection:** Keyword matching for CAPTCHA, 403, 429, challenge pages
- **Error Mapping:** Explicit `CRAWL_UPSTREAM_ERROR` with clear messages
- **Single-Source Isolation:** Failures in one source don't affect others

### Points Calculation
- **Formula:** `max(1, totalCount - rank + 1)`
- **Rationale:** Higher rank = higher points, but minimum 1 point
- **Normalization:** Comments count is 0 for search results

### Google Search Exclusion
- **Decision:** Google Search excluded from Phase 12B-2A
- **Reason:** Higher anti-crawl risk compared to Bing/DuckDuckGo
- **Future:** Reserved for optional Phase 12B-2B if local live probes succeed

## Configuration Examples

### Bing Search
```json
{
  "query": "AI 大模型",
  "limit": 10,
  "freshnessDays": 7,
  "market": "zh-CN",
  "safeSearch": "Moderate"
}
```

### DuckDuckGo Search
```json
{
  "query": "artificial intelligence",
  "limit": 15,
  "freshnessDays": 30,
  "region": "cn"
}
```

## Environment Variables

```bash
# Bing Search
AI_RADAR_BING_SEARCH_BASE_URL=https://www.bing.com
AI_RADAR_BING_SEARCH_CONNECT_TIMEOUT=5s
AI_RADAR_BING_SEARCH_READ_TIMEOUT=10s
AI_RADAR_BING_SEARCH_MAX_ATTEMPTS=1
AI_RADAR_BING_SEARCH_MIN_REQUEST_INTERVAL=10s

# DuckDuckGo Search
AI_RADAR_DUCKDUCKGO_SEARCH_BASE_URL=https://html.duckduckgo.com
AI_RADAR_DUCKDUCKGO_SEARCH_CONNECT_TIMEOUT=5s
AI_RADAR_DUCKDUCKGO_SEARCH_READ_TIMEOUT=10s
AI_RADAR_DUCKDUCKGO_SEARCH_MAX_ATTEMPTS=1
AI_RADAR_DUCKDUCKGO_SEARCH_MIN_REQUEST_INTERVAL=10s
```

## Known Limitations

### Anti-Crawl Risk
- HTML sources may be blocked by anti-crawl measures
- Blocked pages result in `CRAWL_UPSTREAM_ERROR` with explicit messages
- No CAPTCHA handling or proxy pool rotation

### Rate Limiting
- Default `maxAttempts=1` to avoid consecutive failed requests
- Recommended `crawlIntervalMinutes` >= 180 (3 hours)
- Aggressive crawling may trigger blocks

### Content Scope
- Only search result snippets, not full page content
- Relies on search engine result page structure
- Page structure changes may require parser updates

## Acceptance Criteria

### Functional Requirements
- ✅ Both sources can be configured via `source_config`
- ✅ Manual crawl executes successfully for both sources
- ✅ Raw items persisted with correct `source_type`
- ✅ Hot items normalized with `item_type = WEB_PAGE`
- ✅ Rank-based points calculation works correctly
- ✅ Blocked pages detected and reported explicitly
- ✅ URL sanitization removes tracking parameters
- ✅ DuckDuckGo redirect URLs decoded properly
- ✅ Frontend can filter by new source types

### Testing Requirements
- ✅ Unit tests for request validation
- ✅ Unit tests for URL sanitization
- ✅ Unit tests for block detection
- ✅ Raw-to-hot integration tests for both sources
- ✅ Frontend type definition tests
- ✅ Acceptance script executes all tests and builds

### Documentation Requirements
- ✅ Roadmap updated with Phase 12B-2 status
- ✅ Decision log records technical choices
- ✅ Configuration examples documented
- ✅ Environment variables documented
- ✅ Known limitations documented

## Verification Steps

1. Run acceptance script:
   ```powershell
   .\scripts\accept-phase-12b-2.ps1
   ```

2. Optional live verification (requires real network access):
   ```powershell
   .\scripts\live-verify-phase-12b-2-html-search.ps1
   ```

3. Create source configs and trigger manual crawls:
   - Bing Search with `query: "AI 大模型"`
   - DuckDuckGo Search with `query: "artificial intelligence"`

4. Verify data flow:
   - Check `crawl_task` status for upstream errors
   - Query `raw_item` by `source_type`
   - Query `hot_item` normalization (WEB_PAGE item type)
   - Query `hot_cluster` with new sources

## Completion Status

**Phase 12B-2 is completed.**

All implementation tasks, tests, documentation, and acceptance criteria have been met. HTML search sources are fully integrated into the AI Radar crawl-to-cluster closed loop with conservative failure handling and explicit upstream error reporting.

## Latest Local Verification

Verified on 2026-07-15 with:

```powershell
.\scripts\accept-phase-12b-2.ps1
```

Result: passed.

- Backend unit tests: All passed
- Backend integration tests: All passed
- Frontend tests: All passed
- Frontend production build: Passed
