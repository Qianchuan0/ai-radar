package com.airadar.cluster.governance.vo;

import java.time.Instant;

/**
 * Result of {@code POST /api/v1/hot-clusters/{id}/items/{itemId}/move}.
 */
public record MoveItemResultVO(
        Long hotItemId,
        Long fromClusterId,
        Long toClusterId,
        Instant movedAt,
        Long historyId
) {
}
