package com.airadar.cluster.governance.vo;

import java.time.Instant;
import java.util.List;

/**
 * Result of {@code POST /api/v1/hot-clusters/{id}/merge}.
 */
public record ClusterMergeResultVO(
        Long winnerClusterId,
        Long loserClusterId,
        Instant mergedAt,
        int movedMembershipCount,
        List<Long> historyIds
) {
}
