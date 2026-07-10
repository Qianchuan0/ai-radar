package com.airadar.subscription.dto;

import com.airadar.source.model.SourceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateSubscriptionRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotNull
        Boolean enabled,
        @NotNull
        List<@NotBlank @Size(max = 80) String> keywords,
        @NotNull
        List<SourceType> sourceTypes,
        @DecimalMin("0.0")
        BigDecimal minScore,
        @NotNull
        @Min(1)
        @Max(720)
        Integer suppressWindowHours
) {
}
