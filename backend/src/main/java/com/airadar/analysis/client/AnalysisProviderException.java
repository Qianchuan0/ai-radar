package com.airadar.analysis.client;

import com.airadar.common.exception.ErrorCode;

/**
 * Thrown by structured analysis providers when a failure has a stable,
 * persisted failure code that should be recorded in {@code cluster_analysis}.
 *
 * <p>The {@link ErrorCode} returned by {@link #errorCode()} is mapped to a
 * persisted {@code failureCode} string by {@code AnalysisService}.</p>
 */
public class AnalysisProviderException extends RuntimeException {

    private final ErrorCode errorCode;

    public AnalysisProviderException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AnalysisProviderException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
