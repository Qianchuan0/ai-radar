package com.airadar.cluster.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/hot-clusters/{id}/split}.
 *
 * <p>{@code itemIds} are the hot items that should be moved out of the
 * source cluster. {@code targetClusterId} is optional: when omitted, a new
 * ACTIVE cluster is created from the items' shared metadata.
 */
public record ClusterSplitRequest(
        @NotEmpty List<Long> itemIds,
        Long targetClusterId,
        @NotBlank String reason,
        String operatorId
) {
}
