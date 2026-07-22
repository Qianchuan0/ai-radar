package com.airadar.cluster.vo;

import com.airadar.cluster.model.ClusterTrendState;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.vo.RawMetricDeltaVO;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * API view of cluster-level trend aggregation for a single window.
 *
 * <p>Aggregates per-item {@link com.airadar.signal.vo.TrendMetricsVO} results
 * into a single cluster trend snapshot. Discovery sources (Bing/DuckDuckGo/Sogou)
 * that resolve to the same canonical URL are de-duplicated at the service layer
 * so a repeated search hit cannot inflate cluster momentum.
 *
 * @param hotClusterId     cluster id
 * @param window           canonical window code (e.g. {@code "6h"})
 * @param trendState       aggregated trend state
 * @param momentumScore    aggregated 0..100 cluster momentum
 * @param confidence       weakest confidence across contributing items; never null
 * @param rawMetricDeltas  aggregated raw deltas per metric (sum), sorted by key
 * @param normalizedDeltas aggregated normalized deltas per component
 * @param growthRate       weighted relative growth across contributing items
 * @param acceleration     aggregated acceleration proxy
 * @param contributingItems item ids that produced a usable TrendMetrics for this window
 * @param skippedItems     item ids skipped due to UNKNOWN/DATA_ANOMALY confidence
 * @param calculatedAt     wall-clock time the cluster trend was computed
 */
public record ClusterTrendVO(
    Long hotClusterId,
    String window,
    ClusterTrendState trendState,
    Double momentumScore,
    GrowthConfidence confidence,
    List<RawMetricDeltaVO> rawMetricDeltas,
    Map<String, Double> normalizedDeltas,
    Double growthRate,
    Double acceleration,
    List<Long> contributingItems,
    List<Long> skippedItems,
    Instant calculatedAt
) {
}
