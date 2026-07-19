package com.airadar.cluster.governance.vo;

import java.time.Instant;

/**
 * Result of resolving a review task (accept / reject / skip).
 */
public record ReviewResolutionVO(
        Long taskId,
        Long clusterMatchDecisionId,
        String status,
        String resolutionReason,
        Instant resolvedAt,
        Long membershipHistoryId
) {
}
