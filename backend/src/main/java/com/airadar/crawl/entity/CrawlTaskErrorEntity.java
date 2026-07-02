package com.airadar.crawl.entity;

import com.airadar.crawl.model.CrawlStage;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("crawl_task_error")
public class CrawlTaskErrorEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long crawlTaskId;
    private CrawlStage stage;
    private String externalId;
    private String errorCode;
    private String errorMessage;
    private Boolean retryable;
    private Instant occurredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCrawlTaskId() {
        return crawlTaskId;
    }

    public void setCrawlTaskId(Long crawlTaskId) {
        this.crawlTaskId = crawlTaskId;
    }

    public CrawlStage getStage() {
        return stage;
    }

    public void setStage(CrawlStage stage) {
        this.stage = stage;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Boolean getRetryable() {
        return retryable;
    }

    public void setRetryable(Boolean retryable) {
        this.retryable = retryable;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
