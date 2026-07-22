package com.airadar.cluster.service;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.model.ClusterTrend;
import com.airadar.cluster.model.ClusterTrendState;
import com.airadar.cluster.support.UrlCanonicalizer;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.MetricSemantics;
import com.airadar.signal.model.RawMetricDelta;
import com.airadar.signal.model.SourceRole;
import com.airadar.signal.model.TrendMetrics;
import com.airadar.signal.model.TrendWindow;
import com.airadar.signal.service.GrowthCalculationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Phase 18A cluster-level trend aggregation.
 *
 * <p>Loads active items of a cluster, computes {@link TrendMetrics} per item via
 * {@link GrowthCalculationService#calculateTrend(long, TrendWindow)}, deduplicates
 * DISCOVERY sources that resolve to the same canonical URL, and aggregates the
 * per-item results into a single {@link ClusterTrend}.
 *
 * <p>Aggregation rules:
 * <ul>
 *   <li>{@code momentumScore}: average across contributing items, capped at 100</li>
 *   <li>{@code confidence}: weakest confidence across contributing items</li>
 *   <li>{@code rawMetricDeltas}: sum per metric key across contributing items</li>
 *   <li>{@code normalizedDeltas}: sum per component across contributing items</li>
 *   <li>{@code growthRate}: weighted average across contributing items (null if no item contributes)</li>
 *   <li>{@code acceleration}: average across items that produced a non-null value</li>
 *   <li>{@code trendState}: derived from aggregated momentum, acceleration, and data completeness</li>
 * </ul>
 *
 * <p>Items whose confidence is {@link GrowthConfidence#UNKNOWN} or
 * {@link GrowthConfidence#DATA_ANOMALY} are collected in {@code skippedItems}
 * and do not contribute to the aggregation, but they still influence the
 * {@link ClusterTrendState} decision: if every active item was skipped and at
 * least one exists, the cluster is classified as {@link ClusterTrendState#NEW}
 * rather than {@code UNKNOWN} (signal exists but historical baseline does not).
 */
@Service
public class ClusterTrendService {

    private final HotClusterMapper hotClusterMapper;
    private final HotClusterItemMapper hotClusterItemMapper;
    private final HotItemMapper hotItemMapper;
    private final GrowthCalculationService growthCalculationService;
    private final UrlCanonicalizer urlCanonicalizer;

    public ClusterTrendService(
        HotClusterMapper hotClusterMapper,
        HotClusterItemMapper hotClusterItemMapper,
        HotItemMapper hotItemMapper,
        GrowthCalculationService growthCalculationService,
        UrlCanonicalizer urlCanonicalizer
    ) {
        this.hotClusterMapper = hotClusterMapper;
        this.hotClusterItemMapper = hotClusterItemMapper;
        this.hotItemMapper = hotItemMapper;
        this.growthCalculationService = growthCalculationService;
        this.urlCanonicalizer = urlCanonicalizer;
    }

    @Transactional(readOnly = true)
    public ClusterTrend aggregate(long hotClusterId, TrendWindow window) {
        HotClusterEntity cluster = hotClusterMapper.selectById(hotClusterId);
        if (cluster == null) {
            throw new BusinessException(ErrorCode.HOT_CLUSTER_NOT_FOUND);
        }

        List<HotClusterItemEntity> memberships = activeMemberships(hotClusterId);
        if (memberships.isEmpty()) {
            return emptyTrend(hotClusterId, window);
        }

        List<Long> hotItemIds = memberships.stream()
            .map(HotClusterItemEntity::getHotItemId)
            .toList();
        Map<Long, HotItemEntity> itemById = hotItemMapper.selectBatchIds(hotItemIds).stream()
            .collect(Collectors.toMap(HotItemEntity::getId, item -> item));

        // Preserve membership order (primary first) so cluster-level decisions remain stable.
        List<HotItemEntity> orderedItems = memberships.stream()
            .map(HotClusterItemEntity::getHotItemId)
            .map(itemById::get)
            .filter(Objects::nonNull)
            .toList();

        List<HotItemEntity> dedupedItems = dedupDiscoverySources(orderedItems);

        List<Long> contributingItemIds = new ArrayList<>();
        List<Long> skippedItemIds = new ArrayList<>();
        List<TrendMetrics> contributingMetrics = new ArrayList<>();
        for (HotItemEntity item : dedupedItems) {
            TrendMetrics metrics = growthCalculationService.calculateTrend(item.getId(), window);
            if (isSkippable(metrics)) {
                skippedItemIds.add(item.getId());
                continue;
            }
            contributingItemIds.add(item.getId());
            contributingMetrics.add(metrics);
        }

        if (contributingMetrics.isEmpty()) {
            // No item had usable historical signal; if the cluster has active items at all,
            // treat the trend as NEW instead of UNKNOWN so the UI can say "just appeared".
            ClusterTrendState state = dedupedItems.isEmpty()
                ? ClusterTrendState.UNKNOWN
                : ClusterTrendState.NEW;
            return new ClusterTrend(
                hotClusterId,
                window.code(),
                state,
                null,
                GrowthConfidence.UNKNOWN,
                List.of(),
                Map.of(),
                null,
                null,
                List.of(),
                skippedItemIds,
                Instant.now()
            );
        }

        double momentum = aggregateMomentum(contributingMetrics);
        Double growthRate = aggregateGrowthRate(contributingMetrics);
        Double acceleration = aggregateAcceleration(contributingMetrics);
        GrowthConfidence confidence = aggregateConfidence(contributingMetrics);
        List<RawMetricDelta> rawDeltas = aggregateRawDeltas(contributingMetrics);
        Map<String, Double> normalizedDeltas = aggregateNormalizedDeltas(contributingMetrics);
        double signedDelta = sumNormalizedDeltas(normalizedDeltas);
        ClusterTrendState state = decideState(signedDelta, acceleration, confidence);

        return new ClusterTrend(
            hotClusterId,
            window.code(),
            state,
            momentum,
            confidence,
            rawDeltas,
            normalizedDeltas,
            growthRate,
            acceleration,
            contributingItemIds,
            skippedItemIds,
            Instant.now()
        );
    }

    private List<HotClusterItemEntity> activeMemberships(long clusterId) {
        return hotClusterItemMapper.selectList(
            new LambdaQueryWrapper<HotClusterItemEntity>()
                .eq(HotClusterItemEntity::getHotClusterId, clusterId)
                .isNull(HotClusterItemEntity::getRemovedAt)
                .orderByDesc(HotClusterItemEntity::getIsPrimary)
                .orderByAsc(HotClusterItemEntity::getAssignedAt)
        );
    }

    private boolean isSkippable(TrendMetrics metrics) {
        GrowthConfidence confidence = metrics.confidence();
        return confidence == GrowthConfidence.UNKNOWN
            || confidence == GrowthConfidence.DATA_ANOMALY;
    }

    /**
     * De-duplicates DISCOVERY sources that point at the same canonical URL.
     *
     * <p>Non-DISCOVERY items are always kept. For DISCOVERY items, the one with
     * the lowest rank (highest relevance) wins per canonical URL; remaining
     * duplicates are dropped so they cannot inflate cluster momentum.
     */
    private List<HotItemEntity> dedupDiscoverySources(List<HotItemEntity> items) {
        List<HotItemEntity> result = new ArrayList<>(items.size());
        Map<String, HotItemEntity> bestDiscoveryPerUrl = new HashMap<>();
        for (HotItemEntity item : items) {
            if (item.getSourceType() == null) {
                result.add(item);
                continue;
            }
            SourceRole role = SourceRole.fromSourceType(item.getSourceType());
            if (role != SourceRole.DISCOVERY) {
                result.add(item);
                continue;
            }
            String canonical = urlCanonicalizer.canonicalize(item.getSourceUrl());
            if (canonical == null || canonical.isBlank()) {
                // Without a canonical URL we cannot dedup; keep the item rather than
                // silently dropping evidence.
                result.add(item);
                continue;
            }
            HotItemEntity existing = bestDiscoveryPerUrl.get(canonical);
            if (existing == null) {
                bestDiscoveryPerUrl.put(canonical, item);
            } else {
                HotItemEntity winner = chooseBetterDiscovery(existing, item);
                bestDiscoveryPerUrl.put(canonical, winner);
            }
        }
        result.addAll(bestDiscoveryPerUrl.values());
        // Keep primary-first ordering stable: re-sort by discovery (non-discovery first)
        // is unnecessary because non-discovery items are already in `result` in order,
        // and discovery dedup winners are appended afterwards. The cluster-level
        // aggregation does not depend on intra-list order.
        return result;
    }

    private HotItemEntity chooseBetterDiscovery(HotItemEntity left, HotItemEntity right) {
        Integer leftRank = readRank(left);
        Integer rightRank = readRank(right);
        if (leftRank == null && rightRank == null) {
            return left.getId() <= right.getId() ? left : right;
        }
        if (leftRank == null) {
            return right;
        }
        if (rightRank == null) {
            return left;
        }
        // Lower rank number = higher relevance = winner.
        if (leftRank.equals(rightRank)) {
            return left.getId() <= right.getId() ? left : right;
        }
        return leftRank < rightRank ? left : right;
    }

    private Integer readRank(HotItemEntity item) {
        if (item.getMetrics() == null) {
            return null;
        }
        var node = item.getMetrics().get("rank");
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private double aggregateMomentum(List<TrendMetrics> metrics) {
        double sum = 0.0;
        for (TrendMetrics metric : metrics) {
            if (metric.momentumScore() != null) {
                sum += metric.momentumScore();
            }
        }
        double average = sum / metrics.size();
        return Math.max(0.0, Math.min(100.0, average));
    }

    private Double aggregateGrowthRate(List<TrendMetrics> metrics) {
        double sum = 0.0;
        int count = 0;
        for (TrendMetrics metric : metrics) {
            if (metric.growthRate() != null) {
                sum += metric.growthRate();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        double average = sum / count;
        if (average < -1.0) {
            return -1.0;
        }
        return average;
    }

    private Double aggregateAcceleration(List<TrendMetrics> metrics) {
        double sum = 0.0;
        int count = 0;
        for (TrendMetrics metric : metrics) {
            if (metric.acceleration() != null) {
                sum += metric.acceleration();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum / count;
    }

    private GrowthConfidence aggregateConfidence(List<TrendMetrics> metrics) {
        GrowthConfidence weakest = GrowthConfidence.HIGH;
        for (TrendMetrics metric : metrics) {
            GrowthConfidence confidence = metric.confidence();
            if (confidence == null) {
                continue;
            }
            if (ordinal(confidence) > ordinal(weakest)) {
                weakest = confidence;
            }
        }
        return weakest;
    }

    /**
     * Custom ordering for confidence aggregation. Lower ordinal = stronger.
     *
     * <p>HIGH {@literal <} MEDIUM {@literal <} LOW {@literal <} METRIC_RESET
     * {@literal <} DATA_ANOMALY {@literal <} UNKNOWN. METRIC_RESET sits below LOW
     * because it flags a specific anomaly rather than overall unknown data.
     */
    private int ordinal(GrowthConfidence confidence) {
        return switch (confidence) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            case METRIC_RESET -> 3;
            case DATA_ANOMALY -> 4;
            case UNKNOWN -> 5;
        };
    }

    private List<RawMetricDelta> aggregateRawDeltas(List<TrendMetrics> metrics) {
        Map<String, MetricAccumulator> accumulators = new LinkedHashMap<>();
        for (TrendMetrics metric : metrics) {
            if (metric.rawMetricDeltas() == null) {
                continue;
            }
            for (RawMetricDelta delta : metric.rawMetricDeltas()) {
                MetricAccumulator acc = accumulators.computeIfAbsent(
                    delta.metric(),
                    key -> new MetricAccumulator(delta.metric(), delta.semantics())
                );
                acc.accumulate(delta);
            }
        }
        List<RawMetricDelta> aggregated = new ArrayList<>(accumulators.size());
        for (MetricAccumulator acc : accumulators.values()) {
            aggregated.add(acc.toDelta());
        }
        return aggregated;
    }

    private Map<String, Double> aggregateNormalizedDeltas(List<TrendMetrics> metrics) {
        Map<String, Double> deltas = new LinkedHashMap<>();
        accumulateNormalized(deltas, "attention", TrendMetrics::attentionDelta, metrics);
        accumulateNormalized(deltas, "discussion", TrendMetrics::discussionDelta, metrics);
        accumulateNormalized(deltas, "adoption", TrendMetrics::adoptionDelta, metrics);
        accumulateNormalized(deltas, "relevance", TrendMetrics::relevanceDelta, metrics);
        return deltas;
    }

    private void accumulateNormalized(
        Map<String, Double> target,
        String key,
        java.util.function.Function<TrendMetrics, Double> extractor,
        List<TrendMetrics> metrics
    ) {
        double sum = 0.0;
        for (TrendMetrics metric : metrics) {
            Double value = extractor.apply(metric);
            if (value != null) {
                sum += value;
            }
        }
        target.put(key, sum);
    }

    /**
     * Decides the cluster trend state from the aggregated signed normalized
     * delta, acceleration, and confidence.
     *
     * <p>The signed delta (sum of attention/discussion/adoption/relevance deltas
     * across contributing items) is the primary directional signal because it
     * can be negative, unlike {@code momentumScore} which is always 0..100.
     * Acceleration is secondary: positive momentum with negative acceleration
     * suggests the cluster is approaching a peak.
     */
    private ClusterTrendState decideState(
        double signedDelta,
        Double acceleration,
        GrowthConfidence confidence
    ) {
        if (confidence == GrowthConfidence.DATA_ANOMALY
            || confidence == GrowthConfidence.UNKNOWN) {
            return ClusterTrendState.UNKNOWN;
        }
        // Use signed thresholds so that small noise around zero resolves to STABLE.
        if (signedDelta > 5.0) {
            if (acceleration != null && acceleration < 0.0) {
                return ClusterTrendState.PEAKING;
            }
            return ClusterTrendState.RISING;
        }
        if (signedDelta < -5.0) {
            return ClusterTrendState.COOLING;
        }
        return ClusterTrendState.STABLE;
    }

    private double sumNormalizedDeltas(Map<String, Double> normalizedDeltas) {
        double sum = 0.0;
        for (Double value : normalizedDeltas.values()) {
            if (value != null) {
                sum += value;
            }
        }
        return sum;
    }

    private ClusterTrend emptyTrend(long hotClusterId, TrendWindow window) {
        return new ClusterTrend(
            hotClusterId,
            window.code(),
            ClusterTrendState.UNKNOWN,
            null,
            GrowthConfidence.UNKNOWN,
            List.of(),
            Map.of(),
            null,
            null,
            List.of(),
            List.of(),
            Instant.now()
        );
    }

    /**
     * Helper accumulator that preserves semantics across per-item deltas and
     * rolls up previous/current values using the last-seen non-null pair, so
     * the aggregated {@link RawMetricDelta} still exposes traceable raw values.
     */
    private static final class MetricAccumulator {
        private final String metric;
        private final MetricSemantics semantics;
        private double deltaSum;
        private double growthRateSum;
        int growthRateCount;
        int deltaCount;
        private Double previous;
        private Double current;
        private boolean anyAnomaly;

        MetricAccumulator(String metric, MetricSemantics semantics) {
            this.metric = metric;
            this.semantics = semantics;
        }

        void accumulate(RawMetricDelta delta) {
            if (delta.delta() != null) {
                deltaSum += delta.delta();
                deltaCount++;
            }
            if (delta.growthRate() != null) {
                growthRateSum += delta.growthRate();
                growthRateCount++;
            }
            // Keep the most recent non-null previous/current pair for traceability.
            if (delta.previous() != null) {
                previous = delta.previous();
            }
            if (delta.current() != null) {
                current = delta.current();
            }
            if (delta.anomaly()) {
                anyAnomaly = true;
            }
        }

        RawMetricDelta toDelta() {
            Double delta = deltaCount > 0 ? deltaSum : null;
            Double growthRate = growthRateCount > 0 ? growthRateSum / growthRateCount : null;
            if (growthRate != null && growthRate < -1.0) {
                growthRate = -1.0;
            }
            return new RawMetricDelta(
                metric,
                previous,
                current,
                delta,
                growthRate,
                semantics,
                anyAnomaly
            );
        }
    }
}
