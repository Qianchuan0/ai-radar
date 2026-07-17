package com.airadar.signal.adapter;

import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import org.springframework.stereotype.Component;

/**
 * Signal adapter for web search results.
 *
 * <p>This adapter supports both Bing and DuckDuckGo search sources.
 * Search sources contribute relevance signals but not social heat.
 *
 * <p>Maps search metrics to signal components:
 * <ul>
 *   <li>rank → relevance (higher rank = lower relevance)</li>
 *   <li>attention, discussion, adoption → 0 (search sources don't contribute social heat)</li>
 * </ul>
 *
 * <p>Relevance is calculated as inverse rank: rank 1 = 100% relevance, rank 10+ = low relevance.
 * This matches the rank-based points calculation used in V1 scoring.
 */
@Component
public class SearchSignalAdapter implements SourceSignalAdapter {

    private static final int MAX_RANK = 20;

    @Override
    public SourceType supportedType() {
        // This adapter supports both search sources
        // The registry pattern requires a single supportedType,
        // so we'll create separate instances for each
        return SourceType.BING_SEARCH;
    }

    @Override
    public NormalizedSignal adapt(HotItemEntity hotItem) {
        if (hotItem.getMetrics() == null) {
            return zeroSignal(hotItem.getSourceType());
        }

        int rank = hotItem.getMetrics().path("rank").asInt(1);
        int totalCount = hotItem.getMetrics().path("totalCount").asInt(rank);

        // Relevance: inverse rank, normalized to 0-100
        // Rank 1 = 100% relevance, higher ranks = lower relevance
        double relevance = normalizeRank(rank, totalCount);

        return NormalizedSignal.ofSearchResult(
            hotItem.getSourceType(),
            SourceRole.DISCOVERY,
            relevance,
            rank,
            hotItem.getMetrics()
        );
    }

    /**
     * Normalizes rank to relevance score (0-100).
     *
     * <p>Uses the same formula as V1 rank-based points:
     * points = max(1, totalCount - rank + 1)
     * relevance = points / totalCount * 100
     *
     * @param rank       the search result position (1-based)
     * @param totalCount total number of results
     * @return relevance score in 0-100 range
     */
    private double normalizeRank(int rank, int totalCount) {
        if (totalCount <= 0) {
            return 0.0;
        }
        // Clamp rank to 1..MAX_RANK
        int clampedRank = Math.max(1, Math.min(rank, MAX_RANK));
        // Calculate points using V1 formula
        int points = Math.max(1, totalCount - clampedRank + 1);
        // Normalize to 0-100
        return (double) points / totalCount * 100.0;
    }

    private NormalizedSignal zeroSignal(SourceType sourceType) {
        return NormalizedSignal.ofSearchResult(
            sourceType,
            SourceRole.DISCOVERY,
            0.0,   // relevance
            0,     // rank
            null   // rawMetrics
        );
    }
}

/**
 * DuckDuckGo-specific search adapter.
 *
 * <p>Uses the same logic as BingSearchSignalAdapter but declares DUCKDUCKGO_SEARCH as the supported type.
 */
@Component
class DuckDuckGoSearchSignalAdapter extends SearchSignalAdapter {
    @Override
    public SourceType supportedType() {
        return SourceType.DUCKDUCKGO_SEARCH;
    }
}

@Component
class SogouSearchSignalAdapter extends SearchSignalAdapter {
    @Override
    public SourceType supportedType() {
        return SourceType.SOGOU_SEARCH;
    }
}
