package com.airadar.cluster.model;

/**
 * Phase 18A cluster-level trend state, derived from the aggregated momentum,
 * acceleration, and data completeness of all active items in a cluster.
 *
 * <p>States are intentionally coarse so that the frontend can explain them in
 * a single label without exposing raw score thresholds. The mapping from
 * momentum/acceleration to state lives in {@code ClusterTrendService} and is
 * documented for reviewer traceability.
 */
public enum ClusterTrendState {
    /** Historical signal insufficient or item first appeared inside the window. */
    NEW,
    /** Momentum positive and acceleration non-negative — actively climbing. */
    RISING,
    /** Momentum positive but acceleration negative — likely near a peak. */
    PEAKING,
    /** Momentum close to zero across contributing items. */
    STABLE,
    /** Momentum negative — interest is cooling. */
    COOLING,
    /** Snapshots missing or confidence is {@code UNKNOWN}/{@code DATA_ANOMALY}. */
    UNKNOWN
}
