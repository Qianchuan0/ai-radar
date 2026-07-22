package com.airadar.scoring.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.model.ClusterTrend;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.signal.model.GrowthMetrics;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.model.SourceRole;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared context for all V2 score calculators.
 *
 * <p>Built once per cluster scoring invocation, this record avoids redundant
 * database lookups across the seven {@code ScoreCalculator} implementations.
 * Each calculator reads only the fields it needs.
 *
 * <p><b>Phase 18B refactor:</b> calculators no longer read primary-item data
 * for momentum / adoption / discussion / relevance / freshness. Instead, the
 * context exposes a precomputed {@link ClusterTrend} (24h window), per-role
 * item / signal groupings, deduplicated DISCOVERY canonical URLs, and the
 * earliest credible event time. {@code primaryItem} / {@code primaryGrowth()}
 * / {@code primarySignal()} are retained only for traceability and for
 * calculators that still operate at the item level (Authority, EvidenceDiversity).
 *
 * @param cluster                   the cluster being scored
 * @param activeItems               active cluster members ({@code removed_at IS NULL})
 * @param primaryItem               primary item kept for traceability; not used by Phase 18B calculators
 * @param signals                   normalized signals keyed by hot item id
 * @param growthByItem              24h growth metrics keyed by hot item id; missing entries mean no trend data
 * @param clusterTrend              24h aggregated cluster trend; {@code null} when trend computation failed
 * @param itemsByRole               active items grouped by {@link SourceRole}; roled without items are absent
 * @param signalsByRole             normalized signals grouped by {@link SourceRole}
 * @param dedupedDiscoveryUrls      canonical URLs from DISCOVERY items after dedup
 * @param earliestCredibleEventAt   earliest {@code published_at} among non-DISCOVERY items;
 *                                  falls back to {@code cluster.firstSeenAt} when no credible item exists
 * @param now                       reference timestamp for freshness calculations
 */
public record ScoringContext(
        HotClusterEntity cluster,
        List<HotItemEntity> activeItems,
        HotItemEntity primaryItem,
        Map<Long, NormalizedSignal> signals,
        Map<Long, GrowthMetrics> growthByItem,
        ClusterTrend clusterTrend,
        Map<SourceRole, List<HotItemEntity>> itemsByRole,
        Map<SourceRole, List<NormalizedSignal>> signalsByRole,
        Set<String> dedupedDiscoveryUrls,
        Instant earliestCredibleEventAt,
        Instant now
) {

    /**
     * Returns the growth metrics for the primary item, or {@code null} if unavailable.
     *
     * <p>Deprecated for Phase 18B calculator use. New calculators should read
     * {@link #clusterTrend()} instead.
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
     * <p>Deprecated for Phase 18B calculator use. New calculators should read
     * role-grouped signals via {@link #signalsByRole()}.
     *
     * @return primary item signal, or {@code null}
     */
    public NormalizedSignal primarySignal() {
        if (primaryItem == null) {
            return null;
        }
        return signals.get(primaryItem.getId());
    }

    /**
     * Returns the items grouped under the given source role, or an empty list.
     *
     * @param role the source role to look up; {@code null} returns an empty list
     * @return items for the role, never {@code null}
     */
    public List<HotItemEntity> itemsForRole(SourceRole role) {
        if (role == null || itemsByRole == null) {
            return List.of();
        }
        return itemsByRole.getOrDefault(role, List.of());
    }

    /**
     * Returns the signals grouped under the given source role, or an empty list.
     *
     * @param role the source role to look up; {@code null} returns an empty list
     * @return signals for the role, never {@code null}
     */
    public List<NormalizedSignal> signalsForRole(SourceRole role) {
        if (role == null || signalsByRole == null) {
            return List.of();
        }
        return signalsByRole.getOrDefault(role, List.of());
    }
}
