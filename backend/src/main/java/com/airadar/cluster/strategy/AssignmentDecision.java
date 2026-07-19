package com.airadar.cluster.strategy;

/**
 * Outcome of a {@link ClusterAssignmentStrategy#assign} decision.
 *
 * <p>The four values cover every path the V2 pipeline can take:
 * <ul>
 *   <li>{@link #ACCEPTED} — the item matched an existing cluster and was added
 *       to it</li>
 *   <li>{@link #REJECTED} — a candidate was considered but the match score
 *       fell below the rejection threshold, so a new singleton cluster was
 *       created instead</li>
 *   <li>{@link #REVIEW_REQUIRED} — the candidate landed in the grey zone
 *       between acceptance and rejection; the item still gets a singleton
 *       cluster but the candidate is recorded for human review</li>
 *   <li>{@link #NO_CANDIDATE} — no candidate cluster was retrieved, so a new
 *       singleton cluster was created</li>
 * </ul>
 *
 * <p>{@link #ACCEPTED} and {@link #NO_CANDIDATE} together represent items that
 * were resolved deterministically without review.
 */
public enum AssignmentDecision {

    ACCEPTED,
    REJECTED,
    REVIEW_REQUIRED,
    NO_CANDIDATE
}
