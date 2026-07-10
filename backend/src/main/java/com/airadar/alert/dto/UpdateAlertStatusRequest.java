package com.airadar.alert.dto;

import com.airadar.alert.model.AlertStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAlertStatusRequest(
        @NotNull
        AlertStatus status
) {
}
