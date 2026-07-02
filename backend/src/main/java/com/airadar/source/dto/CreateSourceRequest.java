package com.airadar.source.dto;

import com.airadar.source.model.SourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateSourceRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[a-z0-9-]+$")
        String sourceCode,
        @NotNull
        SourceType sourceType,
        @NotBlank
        @Size(max = 100)
        String displayName,
        @NotNull
        Boolean enabled,
        @Min(1)
        Integer crawlIntervalMinutes,
        @NotNull
        Map<String, Object> config
) {
}
