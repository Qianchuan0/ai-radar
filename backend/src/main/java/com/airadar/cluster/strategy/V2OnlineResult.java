package com.airadar.cluster.strategy;

import java.util.List;

/**
 * Outcome of a Phase 17C V2 online attempt for one hot item.
 *
 * <p>{@link #movedToClusterId()} is non-null only when the orchestrator
 * actually relocated the item from its V1 singleton into a V2 target
 * cluster through {@code MoveItemService}. Every other path
 * (no candidates, best decision REJECTED / REVIEW_REQUIRED, level not
 * allowed, target equal to source, V2 write error) returns {@code null}
 * so the caller keeps the V1 result as the online assignment.
 *
 * @param decisions every persisted {@code cluster_match_decision} row
 *                  produced by the V2 evaluation
 * @param movedToClusterId the cluster the item was relocated into;
 *                         {@code null} when no move happened
 * @param membershipHistoryId id of the {@code cluster_membership_history}
 *                            row created by {@code MoveItemService};
 *                            {@code null} when no move happened
 * @param skipReason short tag explaining why no move happened; {@code "MOVED"}
 *                   when a move succeeded
 */
public record V2OnlineResult(
        List<ClusterMatchDecisionEntity> decisions,
        Long movedToClusterId,
        Long membershipHistoryId,
        String skipReason
) {

    public static V2OnlineResult moved(
            List<ClusterMatchDecisionEntity> decisions,
            Long movedToClusterId,
            Long membershipHistoryId
    ) {
        return new V2OnlineResult(decisions, movedToClusterId, membershipHistoryId, "MOVED");
    }

    public static V2OnlineResult skipped(
            List<ClusterMatchDecisionEntity> decisions,
            String skipReason
    ) {
        return new V2OnlineResult(decisions, null, null, skipReason);
    }
}
