package com.airadar.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSubscriptionStatusRequest(
        @NotNull
        Boolean enabled
) {
}
