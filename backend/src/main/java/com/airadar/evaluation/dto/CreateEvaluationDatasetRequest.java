package com.airadar.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEvaluationDatasetRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @Size(max = 500)
        String description,
        @NotNull
        Boolean enabled
) {
}
