package com.airadar.cluster.governance.vo;

import java.time.Instant;
import java.util.List;

/**
 * Result of {@code POST /api/v1/hot-clusters/{id}/split}.
 */
public record ClusterSplitResultVO(
        Long sourceClusterId,
        Long targetClusterId,
        boolean targetCreated,
        Instant splitAt,
        List<Long> movedItemIds,
        List<Long> historyIds
) {
}
