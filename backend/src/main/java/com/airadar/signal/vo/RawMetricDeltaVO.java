package com.airadar.signal.vo;

import com.airadar.signal.model.MetricSemantics;

/**
 * API view of a single raw metric delta produced by the Phase 18A trend layer.
 *
 * <p>This VO mirrors {@link com.airadar.signal.model.RawMetricDelta} but lives
 * in the {@code vo} package so the wire contract is decoupled from the domain
 * record. The {@link MetricSemantics} enum is shared so callers can interpret
 * movement consistently.
 *
 * @param metric     raw metric field name as emitted by the source adapter
 * @param previous   historical value, or {@code null} when absent
 * @param current    current value, or {@code null} when absent
 * @param delta      {@code current - previous}; {@code null} when either side is missing
 * @param growthRate relative change {@code delta / max(previous, 1)} clamped to {@code [-1, +Inf)};
 *                   {@code null} when previous is missing or zero
 * @param semantics  metric semantics used to interpret the delta; never null
 * @param anomaly    true when the movement violates semantics (e.g. cumulative counter drop)
 */
public record RawMetricDeltaVO(
    String metric,
    Double previous,
    Double current,
    Double delta,
    Double growthRate,
    MetricSemantics semantics,
    boolean anomaly
) {
}
