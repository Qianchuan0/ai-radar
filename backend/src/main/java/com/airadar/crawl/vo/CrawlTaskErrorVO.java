package com.airadar.crawl.vo;

import com.airadar.crawl.model.CrawlStage;

import java.time.Instant;

public record CrawlTaskErrorVO(
        CrawlStage stage,
        String externalId,
        String errorCode,
        String errorMessage,
        boolean retryable,
        Instant occurredAt
) {
}
