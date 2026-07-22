package com.airadar.signal.vo;

import com.airadar.signal.model.GrowthConfidence;

import java.time.Instant;
import java.util.List;

/**
 * API view of item-level trend metrics across a single window.
 *
 * <p>Phase 18A extends Phase 14's {@link GrowthMetricsVO} with source-aware
 * raw deltas, growth rate, velocity, and acceleration. The original
 * {@code /hot-items/{id}/trend?window=24h} endpoint continues to return
 * {@link GrowthMetricsVO} for backward compatibility; the new
 * {@code /hot-items/{id}/trends?window=X} endpoint returns this richer VO.
 *
 * @param hotItemId         item id
 * @param window            canonical window code (e.g. {@code "6h"})
 * @param attentionDelta    normalized attention delta
 * @param discussionDelta   normalized discussion delta
 * @param adoptionDelta     normalized adoption delta
 * @param relevanceDelta    normalized relevance delta
 * @param rankDelta         rank movement; positive means the item improved
 * @param momentumScore     0..100 weighted momentum
 * @param confidence        confidence bucket, driven by source semantics
 * @param rawMetricDeltas   per-field source-aware deltas
 * @param growthRate        weighted relative growth; {@code null} when unavailable
 * @param velocity          normalized momentum delta (velocity proxy)
 * @param acceleration      second derivative proxy; {@code null} when not computable
 * @param currentObservedAt observed_at of the current snapshot
 * @param previousObservedAt observed_at of the historical snapshot
 * @param calculatedAt      wall-clock time the metrics were computed
 */
public record TrendMetricsVO(
    Long hotItemId,
    String window,
    Double attentionDelta,
    Double discussionDelta,
    Double adoptionDelta,
    Double relevanceDelta,
    Integer rankDelta,
    Double momentumScore,
    GrowthConfidence confidence,
    List<RawMetricDeltaVO> rawMetricDeltas,
    Double growthRate,
    Double velocity,
    Double acceleration,
    Instant currentObservedAt,
    Instant previousObservedAt,
    Instant calculatedAt
) {
}
