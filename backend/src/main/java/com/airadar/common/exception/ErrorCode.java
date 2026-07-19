package com.airadar.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_ARGUMENT("COMMON.INVALID_ARGUMENT", "Invalid request parameters.", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("COMMON.BAD_REQUEST", "Invalid request body.", HttpStatus.BAD_REQUEST),
    SOURCE_NOT_FOUND("SOURCE.NOT_FOUND", "Source configuration not found.", HttpStatus.NOT_FOUND),
    SOURCE_ALREADY_EXISTS("SOURCE.ALREADY_EXISTS", "Source code already exists.", HttpStatus.CONFLICT),
    SOURCE_DISABLED("SOURCE.DISABLED", "Source configuration is disabled.", HttpStatus.CONFLICT),
    SOURCE_TYPE_UNSUPPORTED("SOURCE.TYPE_UNSUPPORTED", "Source type is not supported.", HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_NOT_FOUND("SUBSCRIPTION.NOT_FOUND", "Subscription rule not found.", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_EXISTS("SUBSCRIPTION.ALREADY_EXISTS", "Subscription rule already exists.", HttpStatus.CONFLICT),
    ALERT_NOT_FOUND("ALERT.NOT_FOUND", "Alert record not found.", HttpStatus.NOT_FOUND),
    REPORT_NOT_FOUND("REPORT.NOT_FOUND", "Daily report not found.", HttpStatus.NOT_FOUND),
    CRAWL_TASK_NOT_FOUND("CRAWL.TASK_NOT_FOUND", "Crawl task not found.", HttpStatus.NOT_FOUND),
    CRAWL_UPSTREAM_ERROR("CRAWL.UPSTREAM_ERROR", "Upstream source request failed.", HttpStatus.BAD_GATEWAY),
    CRAWL_PROVIDER_NOT_CONFIGURED("CRAWL.PROVIDER_NOT_CONFIGURED", "Crawl provider credentials are not configured.", HttpStatus.BAD_GATEWAY),
    HOT_ITEM_NOT_FOUND("ITEM.NOT_FOUND", "Hot item not found.", HttpStatus.NOT_FOUND),
    HOT_CLUSTER_NOT_FOUND("CLUSTER.NOT_FOUND", "Hot cluster not found.", HttpStatus.NOT_FOUND),
    CLUSTER_GOVERNANCE_INVALID_TARGET("CLUSTER.GOVERNANCE_INVALID_TARGET", "Cluster governance target is invalid.", HttpStatus.CONFLICT),
    CLUSTER_GOVERNANCE_INVALID_ARGUMENT("CLUSTER.GOVERNANCE_INVALID_ARGUMENT", "Cluster governance request is invalid.", HttpStatus.BAD_REQUEST),
    CLUSTER_GOVERNANCE_NO_MEMBERSHIP("CLUSTER.GOVERNANCE_NO_MEMBERSHIP", "Hot item has no active cluster membership.", HttpStatus.CONFLICT),
    CLUSTER_REVIEW_TASK_NOT_FOUND("CLUSTER.REVIEW_TASK_NOT_FOUND", "Cluster review task not found.", HttpStatus.NOT_FOUND),
    CLUSTER_ANALYSIS_NOT_FOUND("ANALYSIS.NOT_FOUND", "Cluster analysis not found.", HttpStatus.NOT_FOUND),
    ANALYSIS_PROVIDER_NOT_CONFIGURED("ANALYSIS.PROVIDER_NOT_CONFIGURED", "Analysis provider is not configured.", HttpStatus.CONFLICT),
    ANALYSIS_UPSTREAM_ERROR("ANALYSIS.UPSTREAM_ERROR", "Analysis upstream provider call failed.", HttpStatus.BAD_GATEWAY),
    ANALYSIS_TIMEOUT("ANALYSIS.TIMEOUT", "Analysis upstream provider call timed out.", HttpStatus.GATEWAY_TIMEOUT),
    ANALYSIS_SCHEMA_INVALID("ANALYSIS.SCHEMA_INVALID", "Analysis structured output schema is invalid.", HttpStatus.BAD_REQUEST),
    ANALYSIS_RESPONSE_PARSE_FAILED("ANALYSIS.RESPONSE_PARSE_FAILED", "Analysis provider response could not be parsed.", HttpStatus.BAD_GATEWAY),
    ANALYSIS_GENERATION_FAILED("ANALYSIS.GENERATION_FAILED", "Structured analysis generation failed.", HttpStatus.INTERNAL_SERVER_ERROR),
    EVALUATION_DATASET_NOT_FOUND("EVALUATION.DATASET_NOT_FOUND", "Evaluation dataset not found.", HttpStatus.NOT_FOUND),
    EVALUATION_CASE_NOT_FOUND("EVALUATION.CASE_NOT_FOUND", "Evaluation case not found.", HttpStatus.NOT_FOUND),
    EVALUATION_RUN_NOT_FOUND("EVALUATION.RUN_NOT_FOUND", "Evaluation run not found.", HttpStatus.NOT_FOUND),
    EVALUATION_EMPTY_DATASET("EVALUATION.EMPTY_DATASET", "Evaluation dataset has no enabled cases.", HttpStatus.CONFLICT),
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
