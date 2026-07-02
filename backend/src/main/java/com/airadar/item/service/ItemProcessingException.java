package com.airadar.item.service;

import com.airadar.crawl.model.CrawlStage;

public class ItemProcessingException extends RuntimeException {

    private final CrawlStage stage;

    public ItemProcessingException(CrawlStage stage, Throwable cause) {
        super(cause.getMessage(), cause);
        this.stage = stage;
    }

    public CrawlStage getStage() {
        return stage;
    }
}
