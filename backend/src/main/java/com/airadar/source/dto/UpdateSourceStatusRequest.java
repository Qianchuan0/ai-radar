package com.airadar.source.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSourceStatusRequest(
        @NotNull Boolean enabled
) {
}
