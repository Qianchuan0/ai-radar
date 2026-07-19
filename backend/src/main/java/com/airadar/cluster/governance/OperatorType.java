package com.airadar.cluster.governance;

/**
 * Who performed a governance action.
 *
 * <p>{@link #SYSTEM} is reserved for future automated governance flows
 * (e.g. a scheduler that auto-accepts high-confidence REVIEW_REQUIRED
 * decisions). {@link #MANUAL} is the only value the Phase 17B API uses.
 */
public enum OperatorType {
    SYSTEM,
    MANUAL
}
