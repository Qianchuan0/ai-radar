# Signal Layer Documentation

**Date:** 2026-07-17
**Phase:** 14

## Overview

Phase 13B introduced the minimal signal layer, and Phase 14 extends it with persisted snapshots and 24h growth calculation. This layer prepares for Score V2 while keeping V1 scoring unchanged.

## Components

### SourceRole

`com.airadar.signal.model.SourceRole` defines the semantic role of a source:

- **PRIMARY**: Research artifacts and authoritative sources (future: arXiv)
- **ADOPTION**: Developer adoption signals (GitHub, HuggingFace)
- **COMMUNITY**: Community discussion (HackerNews, Weibo, Twitter, HN Search)
- **MEDIA**: Journalistic content (future: tech media outlets)
- **DISCOVERY**: Web search results (Bing, DuckDuckGo, Sogou)

### NormalizedSignal

`com.airadar.signal.model.NormalizedSignal` represents normalized signal components:

```java
public record NormalizedSignal(
    SourceType sourceType,
    SourceRole sourceRole,
    double attention,      // public attention (views, impressions)
    double discussion,     // community discussion (comments, replies)
    double adoption,       // adoption and usage (stars, downloads, forks)
    double authority,      // authority and credibility (future: citations)
    double relevance,      // query/content relevance (search ranking)
    Integer rank,          // original ranking position (search results)
    JsonNode rawMetrics    // preserved original metrics for traceability
)
```

Signal components are normalized to 0-100 range. Raw metrics are preserved for V1 compatibility and explainability.

### SourceSignalAdapter

`com.airadar.signal.adapter.SourceSignalAdapter` interface for converting hot items to normalized signals:

```java
public interface SourceSignalAdapter {
    SourceType supportedType();
    NormalizedSignal adapt(HotItemEntity hotItem);
}
```

### SourceSignalAdapterRegistry

Registry following the same pattern as `CollectorRegistry` and `HotItemNormalizerRegistry`:

- Uses `EnumMap<SourceType,>` for efficient type-based lookup
- Detects duplicate adapter registration at startup
- Throws `BusinessException(ErrorCode.SOURCE_TYPE_UNSUPPORTED)` for missing adapters
- Thread-safe immutable copy for runtime access

## Implemented Adapters

### HackerNewsSignalAdapter (COMMUNITY)

Maps HN metrics:
- `points` → attention
- `commentsCount` → discussion
- `adoption` → 0 (HN is not an adoption platform)

### GitHubSignalAdapter (ADOPTION)

Maps GitHub metrics:
- `stargazersCount`, `watchersCount` → attention (weak signal)
- `openIssuesCount` → discussion (community engagement)
- `stargazersCount`, `forksCount`, `watchersCount` → adoption (primary signal)

### HuggingFaceSignalAdapter (ADOPTION)

Maps HuggingFace metrics:
- `likes` → attention
- `downloads` → adoption (primary signal)
- `discussion` → 0 (no discussion for models)

### SearchSignalAdapter (DISCOVERY)

Maps search metrics for Bing, DuckDuckGo, and Sogou:
- `rank` → relevance (inverse rank: rank 1 = 100% relevance)
- `attention`, `discussion`, `adoption` → 0 (search sources don't contribute social heat)
- `rank` → preserved in `NormalizedSignal.rank`

### Additional Adapters

- `ArxivSignalAdapter` classifies arXiv as `PRIMARY`
- `HackerNewsSearchSignalAdapter` classifies HN Search as `COMMUNITY`
- `TwitterSignalAdapter` classifies Twitter as `COMMUNITY`
- `WeiboHotSearchSignalAdapter` classifies Weibo Hot Search as `COMMUNITY`

## V1 Compatibility

**Important:** The V1 metrics fields (`points`, `commentsCount`) are preserved unchanged.

V1 scoring (`RuleBasedScoringService`) continues to read:
- `metrics.points` → points score component
- `metrics.commentsCount` → comments score component

The signal layer runs in parallel and does not modify existing metrics. This ensures:
- V1 scoring results remain unchanged
- V1 evaluation fixtures stay valid
- Gradual migration path to V2 scoring

## Future Source Integration

**Rule:** All new sources must implement a `SourceSignalAdapter`.

### Steps for Adding a New Source:

1. **Implement Adapter**
   ```java
   @Component
   public class YourSourceSignalAdapter implements SourceSignalAdapter {
       @Override
       public SourceType supportedType() {
           return SourceType.YOUR_SOURCE;
       }

       @Override
       public NormalizedSignal adapt(HotItemEntity hotItem) {
           // Map your source-specific metrics to signal components
       }
   }
   ```

2. **Choose SourceRole**
   - Is it adoption? → `ADOPTION`
   - Is it discussion? → `COMMUNITY`
   - Is it search? → `DISCOVERY`
   - Is it primary research? → `PRIMARY`

3. **Map Metrics**
   - Identify which metrics represent attention, discussion, adoption
   - Normalize to 0-100 range (use logarithmic scaling for large ranges)
   - Preserve raw metrics in `NormalizedSignal.rawMetrics`

4. **Register Automatically**
   - Spring's `@Component` auto-registers the adapter
   - No manual registration needed

5. **Test Coverage**
   - Unit test for adapter metric mapping
   - Integration test for end-to-end data flow
   - Registry test to ensure no duplicate/missing adapters

## Signal Layer in V2

The signal layer enables Score V2 improvements:

1. **Semantic Separation**: Distinguish adoption (GitHub stars) from discussion (HN comments)
2. **Cross-Source Comparison**: Compare GitHub adoption directly with HuggingFace adoption
3. **Growth Trends**: Track signal changes over time (Phase 14)
4. **Score V2**: Rebalance scoring based on semantic signal roles (Phase 15)

## Current Limitations

- Not yet integrated into scoring (V2 will use these signals)
- Growth trend calculation limited to 24h window (Phase 14)
- All currently implemented source types now have adapters so snapshot persistence does not break existing crawls
- Authority signal not yet used (reserved for future citation metrics)

## Phase 14: Signal Snapshots and Growth Trends

Phase 14 introduces time-series signal tracking:

### Signal Snapshot Storage

- `hot_item_signal_snapshot` table stores normalized signal snapshots
- `observed_at = raw_item.fetched_at` (crawl-centric time tracking)
- Unique constraint on `raw_item_id` prevents duplicate snapshots
- Pipeline integration: snapshot created after `hotItemService.upsert()`

### Growth Trend Calculation

- `GrowthCalculationService` provides 24h growth metrics
- Finds historical snapshot within ±3h of target time (24h ago)
- Calculates deltas for attention, discussion, adoption, relevance
- Rank delta: `previousRank - currentRank` (positive = improvement)
- Momentum score: weighted sum of positive deltas
- Confidence levels: `HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`, `DATA_ANOMALY`, `METRIC_RESET`

### API Endpoints

- `GET /api/v1/hot-items/{id}/signals?limit=10` - recent snapshots
- `GET /api/v1/hot-items/{id}/trend?window=24h` - 24h growth metrics

## Verification

Run backend tests to verify signal layer integration:

```bash
cd backend
./mvnw.cmd -Dtest=com.airadar.signal.adapter.SourceSignalAdapterTest,com.airadar.signal.adapter.SourceSignalAdapterRegistryTest,com.airadar.signal.model.NormalizedSignalTest,com.airadar.signal.service.GrowthCalculationServiceTest,com.airadar.signal.controller.HotItemSignalControllerTest test
```

Expected:
- Focused Phase 14 tests pass without changing V1 scoring behavior
- SourceSignalAdapterRegistry successfully registers all current adapters
- No duplicate adapter exceptions at startup
