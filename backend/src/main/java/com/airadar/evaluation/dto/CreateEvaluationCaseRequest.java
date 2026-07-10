package com.airadar.evaluation.dto;

import com.airadar.evaluation.model.EvaluationCaseType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEvaluationCaseRequest(
        @NotBlank
        @Size(max = 120)
        String caseCode,
        @NotNull
        EvaluationCaseType caseType,
        @NotNull
        JsonNode targetPayload,
        @NotNull
        JsonNode expectedPayload,
        @Size(max = 500)
        String notes,
        @NotNull
        Boolean enabled
) {
}
