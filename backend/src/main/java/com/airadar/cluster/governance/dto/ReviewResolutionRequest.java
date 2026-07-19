package com.airadar.cluster.governance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/cluster-review/tasks/{id}/accept|reject|skip}.
 *
 * <p>Reason is required so every resolution is auditable.
 */
public record ReviewResolutionRequest(
        @NotBlank String reason,
        String operatorId
) {
}
