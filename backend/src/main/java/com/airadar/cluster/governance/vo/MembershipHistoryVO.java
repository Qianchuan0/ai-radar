package com.airadar.cluster.governance.vo;

import java.time.Instant;

/**
 * Single {@code cluster_membership_history} row projected for the API.
 */
public record MembershipHistoryVO(
        Long id,
        Long hotClusterId,
        Long hotItemId,
        String action,
        Long fromClusterId,
        Long toClusterId,
        String reason,
        String operatorType,
        String operatorId,
        Long relatedDecisionId,
        Instant createdAt
) {
}
