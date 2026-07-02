package com.airadar.crawl.vo;

import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;

import java.time.Instant;
import java.util.List;

public record CrawlTaskVO(
        Long id,
        Long sourceId,
        CrawlTriggerType triggerType,
        CrawlTaskStatus status,
        Long retryOfTaskId,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt,
        int fetchedCount,
        int persistedCount,
        int matchedCount,
        int failedCount,
        String failureCode,
        String failureMessage,
        List<CrawlTaskErrorVO> errors
) {

    public CrawlTaskVO {
        errors = List.copyOf(errors);
    }
}
