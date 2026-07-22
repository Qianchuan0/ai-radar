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

## Phase 18A: Multi-Window Cluster Trend Model

Phase 18A upgrades Phase 14's single-window 24h delta into a multi-window, source-aware, cluster-level trend layer.

### Multi-Window Support

`com.airadar.signal.model.TrendWindow` defines four canonical windows:

| Window | Code | Target | Max Deviation | HIGH Confidence | MEDIUM Confidence |
| --- | --- | --- | --- | --- | --- |
| `H1` | `1h` | 1 hour | 15 minutes | 5 minutes | 10 minutes |
| `H6` | `6h` | 6 hours | 60 minutes | 20 minutes | 40 minutes |
| `H24` | `24h` | 24 hours | 3 hours | 30 minutes | 90 minutes |
| `D3` | `3d` | 72 hours | 12 hours | 3 hours | 6 hours |

The `24h` wire form is preserved verbatim from Phase 14 rather than collapsing to `1d`, so persisted `window` fields and existing API callers keep matching. Windows strictly longer than 24h collapse to a day-count form (`72h -> "3d"`).

### Source-Specific Metric Semantics

`com.airadar.signal.model.MetricSemantics` classifies how each raw metric field is allowed to move:

- **MONOTONIC_CUMULATIVE** — GitHub stars, forks, watchers; Hugging Face downloads, likes; arXiv author/category counts. A drop is a real anomaly (measurement or pipeline regression) and flags `METRIC_RESET`.
- **RANK_LIKE_REVERSIBLE** — Search rank (Bing, DuckDuckGo, Sogou), Weibo hot-search rank. Movement is informational and never triggers a reset on its own.
- **VOLATILE_SOCIAL** — Hacker News points/comments, Twitter engagement, Weibo hot score, GitHub open-issues. Free to move in either direction with attention cycles.
- **RELEVANCE_SCORE** — Search `totalCount`, Sogou provider score. Informational only; excluded from the weighted growth rate.

Each adapter declares its semantics through `SourceSignalAdapter.metricSemantics()`, which returns an empty map by default so pre-Phase 18A adapters continue to behave exactly as before.

### RawMetricDelta

`com.airadar.signal.model.RawMetricDelta` carries the previous/current/delta/growthRate/anomaly for each raw metric field, preserving the original field name (e.g. `stargazersCount`, `rank`) so downstream consumers can interpret movement using the correct semantics.

### TrendMetrics

`com.airadar.signal.model.TrendMetrics` extends Phase 14 `GrowthMetrics` with:

- `rawMetricDeltas` — per-field source-aware deltas
- `growthRate` — weighted relative growth across raw deltas, using semantics as weights (`MONOTONIC_CUMULATIVE` weight 1.0, `VOLATILE_SOCIAL` 0.6, `RANK_LIKE_REVERSIBLE` 0.3, `RELEVANCE_SCORE` excluded)
- `velocity` — first derivative proxy, expressed as the normalized momentum delta itself (signed)
- `acceleration` — second derivative proxy: difference between the current window's momentum and the previous equal-sized window's momentum. Requires a third snapshot at `current - 2*window`; returns `null` when unavailable so callers cannot mistake absence for zero.

The original 24h growth endpoint keeps returning `GrowthMetrics` so Phase 15 V2 momentum consumers continue to work unchanged.

### Cluster Trend Aggregation

`com.airadar.cluster.service.ClusterTrendService` aggregates per-item `TrendMetrics` into a single `ClusterTrend` per window:

1. Loads active cluster items via `HotClusterItemMapper`.
2. De-duplicates DISCOVERY sources that resolve to the same canonical URL via `UrlCanonicalizer`. The discovery item with the lowest rank wins; non-discovery items are always kept.
3. Computes `TrendMetrics` for each surviving item.
4. Skips items whose confidence is `UNKNOWN` or `DATA_ANOMALY` (collected in `skippedItems`).
5. Aggregates: average `momentumScore`, weakest `confidence`, summed raw deltas per metric, summed normalized deltas per component, weighted average `growthRate`, average `acceleration`.

### Cluster Trend State

`com.airadar.cluster.model.ClusterTrendState` is derived from the signed sum of normalized deltas plus acceleration (not from `momentumScore`, which is always 0..100 and cannot represent cooling):

| State | Condition |
| --- | --- |
| `NEW` | All items skipped but at least one active item exists (historical baseline missing) |
| `RISING` | Signed delta > 5 AND acceleration >= 0 |
| `PEAKING` | Signed delta > 5 AND acceleration < 0 |
| `STABLE` | Signed delta in [-5, +5] |
| `COOLING` | Signed delta < -5 |
| `UNKNOWN` | Confidence is `UNKNOWN` or `DATA_ANOMALY`, or cluster has no active items |

### Phase 18A API Endpoints

- `GET /api/v1/hot-items/{id}/trends?window=6h` — returns `TrendMetricsVO` with raw deltas, growth rate, velocity, acceleration
- `GET /api/v1/hot-clusters/{id}/trends?windows=1h,6h,24h,3d` — returns `List<ClusterTrendVO>`, one per requested window

### Phase 18A Scope Boundaries

- No Score V2 ranking change (Phase 15 shadow scoring continues to read Phase 14 `GrowthMetrics`)
- No frontend trend visualization (reserved for Phase 19A)
- No time-series database, streaming compute, or full historical backfill
- No cluster trend cache table — the first version computes trends live; a cache table is reserved for a later phase if query cost forces it

## Verification (Phase 18A)

```bash
cd backend
./mvnw.cmd -Dtest=GrowthCalculationServiceTest,ClusterTrendServiceTest,SourceSignalAdapterTest test
```

For the Testcontainers-backed integration tests (requires Docker):

```bash
cd backend
./mvnw.cmd -Dtest=ClusterTrendControllerIntegrationTest,HotItemSignalControllerIntegrationTest test
```

Or run the acceptance script from the repo root:

```powershell
.\scripts\accept-phase-18a.ps1
```

## Phase 18B: Score V2 Online Ranking Adoption

Phase 18B rewires the V2 calculators to consume cluster-level signal inputs instead of primary-item data, so events with multiple evidence items are no longer undercounted when none of them happens to be the primary item. The Phase 18A `ClusterTrend` (24h window) drives momentum; `ADOPTION` / `COMMUNITY` / `DISCOVERY` source-role signal groups drive adoption / discussion / relevance; the earliest credible (non-DISCOVERY) `published_at` drives freshness; and precomputed deduplicated DISCOVERY canonical URLs drive evidence diversity.

### Online Version Switch

`ScoringStrategyProperties` (`ai-radar.scoring.online-version`) selects which scoring version drives the list / detail / alert / report ranking:

- `hn-score-v1` (default): V1 ranking, V2 stays shadow
- `cross-source-score-v2`: V2 ranking with V1 fallback when a V2 row is missing

The `/api/v1/hot-clusters` list and `/api/v1/hot-clusters/{id}` detail endpoints also accept an optional `?scoringVersion=` query parameter for V1/V2 side-by-side comparison without changing the global config. `GET /api/v1/scoring-strategy/status` returns the live online version, shadow version, v2-online flag, and rollout stage.

### Verification

Phase 18B unit tests (no Docker):

```bash
cd backend
./mvnw.cmd -Dtest=MomentumScoreCalculatorTest,AdoptionScoreCalculatorTest,DiscussionScoreCalculatorTest,RelevanceScoreCalculatorTest,FreshnessScoreCalculatorTest,EvidenceDiversityCalculatorTest,ScoringStrategyPropertiesTest,V1V2ComparisonTest,ScoringOrchestratorShadowTest test
```

Phase 18B integration tests (requires Docker):

```bash
cd backend
./mvnw.cmd -Dtest=ScoreV2OnlineRankingIntegrationTest test
```

Or run the acceptance script from the repo root:

```powershell
.\scripts\accept-phase-18b.ps1
```
`
