package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.item.entity.HotItemEntity;

/**
 * Strategy interface for assigning a {@link HotItemEntity} to a
 * {@link HotClusterEntity}.
 *
 * <p>Each strategy implementation produces a versioned
 * {@link ClusterAssignmentResult} describing the cluster the item was assigned
 * to (or, when rejected, the candidate cluster that was considered) plus a
 * human-readable match reason.
 *
 * <p>Phase 16 ships two strategies running side by side:
 * <ul>
 *   <li>{@code hn-rule-v1} — the original canonical-URL baseline, wrapped by
 *       {@link CanonicalUrlClusterStrategy}</li>
 *   <li>{@code event-rule-v2} — the event-level clustering pipeline backed by
 *       item features, candidate retrieval, and layered match rules</li>
 * </ul>
 *
 * <p>Strategies are responsible for their own persistence: invoking
 * {@link #assign(HotItemEntity)} must leave the database in a state where the
 * returned cluster exists and the item has an active membership in it, except
 * when the result decision is {@link AssignmentDecision#REJECTED} or
 * {@link AssignmentDecision#REVIEW_REQUIRED} (in which case the strategy may
 * create a singleton cluster for the item and additionally persist the rejected
 * candidate for review).
 */
public interface ClusterAssignmentStrategy {

    /**
     * The clustering rule version tag, persisted as
     * {@code hot_cluster_item.rule_version}.
     *
     * @return stable version identifier (never {@code null})
     */
    String version();

    /**
     * Assigns the item to a cluster and returns the assignment outcome.
     *
     * <p>Implementations must be idempotent for a given item: invoking
     * {@code assign} twice with the same {@link HotItemEntity} must not create
     * a duplicate membership.
     *
     * @param item the hot item to assign (must already be persisted)
     * @return the assignment outcome describing the resulting cluster and the
     *         match decision (never {@code null})
     */
    ClusterAssignmentResult assign(HotItemEntity item);
}
