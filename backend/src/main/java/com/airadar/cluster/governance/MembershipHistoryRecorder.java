package com.airadar.cluster.governance;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Helper that inserts a single {@code cluster_membership_history} row.
 *
 * <p>Governance services delegate here instead of touching the mapper
 * directly so the recorded shape stays consistent. The recorder assumes the
 * caller has already validated the operation and is inside a transaction.
 */
@Component
public class MembershipHistoryRecorder {

    private final ClusterMembershipHistoryMapper mapper;

    public MembershipHistoryRecorder(ClusterMembershipHistoryMapper mapper) {
        this.mapper = mapper;
    }

    public ClusterMembershipHistoryEntity record(
            Long subjectClusterId,
            Long hotItemId,
            MembershipAction action,
            Long fromClusterId,
            Long toClusterId,
            String reason,
            OperatorType operatorType,
            String operatorId,
            Long relatedDecisionId
    ) {
        ClusterMembershipHistoryEntity row = new ClusterMembershipHistoryEntity();
        row.setHotClusterId(subjectClusterId);
        row.setHotItemId(hotItemId);
        row.setAction(action.name());
        row.setFromClusterId(fromClusterId);
        row.setToClusterId(toClusterId);
        row.setReason(reason == null ? "" : reason);
        row.setOperatorType(operatorType.name());
        row.setOperatorId(operatorId);
        row.setRelatedDecisionId(relatedDecisionId);
        row.setCreatedAt(Instant.now());
        mapper.insert(row);
        return row;
    }
}
