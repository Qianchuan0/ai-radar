package com.airadar.signal.model;

/**
 * Source-aware delta for a single raw metric field, produced by the Phase 18A
 * trend layer.
 *
 * <p>Unlike the normalized-signal deltas exposed by {@link GrowthMetrics}, this
 * record keeps the original metric name (e.g. {@code stargazersCount},
 * {@code rank}) so that downstream consumers can interpret movement using the
 * correct {@link MetricSemantics} instead of relying on a single coarse
 * {@code METRIC_RESET} flag.
 *
 * @param metric     raw metric field name as emitted by the source adapter
 * @param previous   historical value, or {@code null} when the field was
 *                   absent from the historical snapshot
 * @param current    current value, or {@code null} when the field is absent
 *                   from the latest snapshot
 * @param delta      {@code current - previous}; {@code null} when either side
 *                   is missing so callers cannot accidentally treat the
 *                   absence as zero movement
 * @param growthRate relative change {@code delta / max(previous, 1)} clamped
 *                   to {@code [-1, +Inf)}; {@code null} when previous is
 *                   missing or zero (avoid divide-by-zero)
 * @param semantics  metric semantics used to interpret the delta; never null
 * @param anomaly    true when the movement violates semantics (e.g. a
 *                   {@link MetricSemantics#MONOTONIC_CUMULATIVE} field drops)
 */
public record RawMetricDelta(
    String metric,
    Double previous,
    Double current,
    Double delta,
    Double growthRate,
    MetricSemantics semantics,
    boolean anomaly
) {
}
