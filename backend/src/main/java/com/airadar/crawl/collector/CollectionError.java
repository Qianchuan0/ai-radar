package com.airadar.crawl.collector;

public record CollectionError(
        String externalId,
        String errorCode,
        String errorMessage,
        boolean retryable
) {
}
