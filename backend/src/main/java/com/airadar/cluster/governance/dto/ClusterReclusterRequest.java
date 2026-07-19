package com.airadar.cluster.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/hot-clusters/{id}/recluster}.
 *
 * <p>{@code itemIds} are re-evaluated through the V2 strategy. The V2 run
 * writes only {@code cluster_match_decision} rows; online membership is not
 * changed. Operators must use merge / split / move to act on the result.
 */
public record ClusterReclusterRequest(
        @NotEmpty List<Long> itemIds,
        @NotBlank String reason,
        String operatorId
) {
}
