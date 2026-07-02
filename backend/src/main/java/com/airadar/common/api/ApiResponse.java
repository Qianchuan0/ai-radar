package com.airadar.common.api;

import com.airadar.common.exception.ErrorCode;

import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "Success", data, Instant.now());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, Instant.now());
    }
}
