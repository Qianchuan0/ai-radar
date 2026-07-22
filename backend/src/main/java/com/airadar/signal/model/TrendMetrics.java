package com.airadar.signal.model;

import java.time.Instant;
import java.util.List;

/**
 * Phase 18A item-level trend metrics.
 *
 * <p>Extends {@link GrowthMetrics} with multi-window support, source-aware raw
 * deltas, and derivative signals (growth rate, velocity, acceleration). The
 * original 24h endpoint keeps returning {@link GrowthMetrics} so existing V2
 * momentum consumers continue to work unchanged; new trend endpoints expose
 * this richer model.
 *
 * @param hotItemId         item id
 * @param window            canonical window code (e.g. {@code "6h"})
 * @param attentionDelta    normalized signal delta for attention
 * @param discussionDelta   normalized signal delta for discussion
 * @param adoptionDelta     normalized signal delta for adoption
 * @param relevanceDelta    normalized signal delta for relevance
 * @param rankDelta         rank movement; positive means the item improved
 * @param momentumScore     0..100 weighted momentum, same formula as Phase 14
 * @param confidence        confidence bucket, now driven by source semantics
 * @param rawMetricDeltas   per-field source-aware deltas, sorted by metric name
 * @param growthRate        weighted relative growth across raw deltas in
 *                          {@code [-1, +Inf)}; {@code null} when no delta is
 *                          available
 * @param velocity          first derivative of normalized momentum, expressed
 *                          as the normalized momentum delta itself
 * @param acceleration      second derivative proxy: difference between the
 *                          current window's momentum and the previous
 *                          equal-sized window's momentum; positive means
 *                          accelerating, negative means decelerating;
 *                          {@code null} when not computable
 * @param currentObservedAt observed_at of the current snapshot
 * @param previousObservedAt observed_at of the historical snapshot
 * @param calculatedAt      wall-clock time the metrics were computed
 */
public record TrendMetrics(
    Long hotItemId,
    String window,
    Double attentionDelta,
    Double discussionDelta,
    Double adoptionDelta,
    Double relevanceDelta,
    Integer rankDelta,
    Double momentumScore,
    GrowthConfidence confidence,
    List<RawMetricDelta> rawMetricDeltas,
    Double growthRate,
    Double velocity,
    Double acceleration,
    Instant currentObservedAt,
    Instant previousObservedAt,
    Instant calculatedAt
) {
}
