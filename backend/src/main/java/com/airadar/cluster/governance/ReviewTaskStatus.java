package com.airadar.cluster.governance;

/**
 * State of a {@code cluster_review_task}.
 *
 * <p>OPEN tasks are waiting for human resolution. ACCEPTED tasks resolved the
 * underlying REVIEW_REQUIRED decision by merging the item into the candidate
 * cluster. REJECTED and SKIPPED tasks leave the online membership untouched;
 * the only difference is intent: REJECTED means "this candidate is wrong",
 * SKIPPED means "defer — keep the item where it is for now".
 */
public enum ReviewTaskStatus {
    OPEN,
    ACCEPTED,
    REJECTED,
    SKIPPED
}
