package com.airadar.cluster.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/v1/hot-clusters/{id}/items/{itemId}/move}.
 *
 * <p>{@code sourceClusterId} and {@code itemId} come from the path. The body
 * carries the destination cluster id and a reason.
 */
public record MoveItemRequest(
        @NotNull Long targetClusterId,
        @NotBlank String reason,
        String operatorId
) {
}
