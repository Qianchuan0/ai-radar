package com.airadar.cluster.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/hot-clusters/{id}/merge}.
 *
 * <p>{@code winnerClusterId} comes from the path; the body only carries the
 * loser cluster id and a human-readable reason. Reason is required because
 * the Phase 17B plan explicitly forbids reason-less governance writes.
 */
public record ClusterMergeRequest(
        @NotNull Long loserClusterId,
        @NotBlank String reason,
        String operatorId
) {
}
