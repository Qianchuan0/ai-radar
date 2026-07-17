package com.airadar.scoring.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.GrowthMetrics;
import com.airadar.signal.model.NormalizedSignal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Shared context for all V2 score calculators.
 *
 * <p>Built once per cluster scoring invocation, this record avoids redundant
 * database lookups across the seven {@code ScoreCalculator} implementations.
 * Each calculator reads only the fields it needs.
 *
 * @param cluster        the cluster being scored
 * @param activeItems    active cluster members ({@code removed_at IS NULL})
 * @param primaryItem    primary item used for cumulative-scale signals; falls back to the first active item
 * @param signals        normalized signals keyed by hot item id
 * @param growthByItem   24h growth metrics keyed by hot item id; missing entries mean no trend data
 * @param now            reference timestamp for freshness calculations
 */
public record ScoringContext(
        HotClusterEntity cluster,
        List<HotItemEntity> activeItems,
        HotItemEntity primaryItem,
        Map<Long, NormalizedSignal> signals,
        Map<Long, GrowthMetrics> growthByItem,
        Instant now
) {

    /**
     * Returns the growth metrics for the primary item, or {@code null} if unavailable.
     *
     * @return primary item growth metrics, or {@code null}
     */
    public GrowthMetrics primaryGrowth() {
        if (primaryItem == null) {
            return null;
        }
        return growthByItem.get(primaryItem.getId());
    }

    /**
     * Returns the normalized signal for the primary item, or {@code null} if unavailable.
     *
     * @return primary item signal, or {@code null}
     */
    public NormalizedSignal primarySignal() {
        if (primaryItem == null) {
            return null;
        }
        return signals.get(primaryItem.getId());
    }
}
