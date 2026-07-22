package com.airadar.signal.model;

/**
 * Source-aware semantics for raw metrics, used by Phase 18A to distinguish
 * expected movement from data anomalies when computing deltas.
 *
 * <p>Phase 14 used a single coarse rule: any drop in a normalized signal
 * component triggered {@link GrowthConfidence#METRIC_RESET}. Phase 18A keeps
 * that legacy behavior for the {@code 24h} growth endpoint while introducing
 * source-specific semantics for the trend layer so that:
 *
 * <ul>
 *   <li>cumulative counters (stars, downloads, forks) dropping is a real
 *       anomaly and should be flagged</li>
 *   <li>search rank changing is normal and must not be treated as a reset</li>
 *   <li>volatile social metrics (points, comments) are free to move either
 *       direction</li>
 *   <li>relevance scores can drift without being flagged</li>
 * </ul>
 */
public enum MetricSemantics {
    /**
     * Cumulative, monotonically non-decreasing counters such as GitHub stars,
     * downloads, or forks. A drop usually indicates a measurement or pipeline
     * anomaly and should be flagged as {@link GrowthConfidence#METRIC_RESET}.
     */
    MONOTONIC_CUMULATIVE,

    /**
     * Ranking-style metrics where movement in either direction is expected
     * (e.g. search result rank). Changes are informational and never trigger
     * a reset on their own.
     */
    RANK_LIKE_REVERSIBLE,

    /**
     * Volatile social metrics such as HackerNews points, comments, or view
     * counts. They may rise or fall naturally with attention cycles.
     */
    VOLATILE_SOCIAL,

    /**
     * Relevance or matching scores emitted by discovery sources. They are
     * recomputed per crawl and can drift without indicating an anomaly.
     */
    RELEVANCE_SCORE
}
