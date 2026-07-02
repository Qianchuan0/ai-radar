package com.airadar.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_ARGUMENT("COMMON.INVALID_ARGUMENT", "Invalid request parameters.", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("COMMON.BAD_REQUEST", "Invalid request body.", HttpStatus.BAD_REQUEST),
    SOURCE_NOT_FOUND("SOURCE.NOT_FOUND", "Source configuration not found.", HttpStatus.NOT_FOUND),
    SOURCE_ALREADY_EXISTS("SOURCE.ALREADY_EXISTS", "Source code already exists.", HttpStatus.CONFLICT),
    SOURCE_DISABLED("SOURCE.DISABLED", "Source configuration is disabled.", HttpStatus.CONFLICT),
    SOURCE_TYPE_UNSUPPORTED("SOURCE.TYPE_UNSUPPORTED", "Source type is not supported.", HttpStatus.BAD_REQUEST),
    CRAWL_TASK_NOT_FOUND("CRAWL.TASK_NOT_FOUND", "Crawl task not found.", HttpStatus.NOT_FOUND),
    CRAWL_UPSTREAM_ERROR("CRAWL.UPSTREAM_ERROR", "Upstream source request failed.", HttpStatus.BAD_GATEWAY),
    HOT_CLUSTER_NOT_FOUND("CLUSTER.NOT_FOUND", "Hot cluster not found.", HttpStatus.NOT_FOUND),
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
