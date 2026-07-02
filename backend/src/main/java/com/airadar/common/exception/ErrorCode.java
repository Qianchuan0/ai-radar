package com.airadar.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_ARGUMENT("COMMON.INVALID_ARGUMENT", "Invalid request parameters.", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("COMMON.BAD_REQUEST", "Invalid request body.", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("COMMON.INTERNAL_ERROR", "Internal server error.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
