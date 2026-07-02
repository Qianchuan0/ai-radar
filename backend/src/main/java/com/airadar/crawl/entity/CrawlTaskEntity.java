package com.airadar.crawl.entity;

import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("crawl_task")
public class CrawlTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceConfigId;
    private CrawlTriggerType triggerType;
    private CrawlTaskStatus status;
    private String idempotencyKey;
    private Long retryOfTaskId;
    private Instant requestedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer fetchedCount;
    private Integer persistedCount;
    private Integer matchedCount;
    private Integer failedCount;
    private String failureCode;
    private String failureMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceConfigId() {
        return sourceConfigId;
    }

    public void setSourceConfigId(Long sourceConfigId) {
        this.sourceConfigId = sourceConfigId;
    }

    public CrawlTriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(CrawlTriggerType triggerType) {
        this.triggerType = triggerType;
    }

    public CrawlTaskStatus getStatus() {
        return status;
    }

    public void setStatus(CrawlTaskStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getRetryOfTaskId() {
        return retryOfTaskId;
    }

    public void setRetryOfTaskId(Long retryOfTaskId) {
        this.retryOfTaskId = retryOfTaskId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getFetchedCount() {
        return fetchedCount;
    }

    public void setFetchedCount(Integer fetchedCount) {
        this.fetchedCount = fetchedCount;
    }

    public Integer getPersistedCount() {
        return persistedCount;
    }

    public void setPersistedCount(Integer persistedCount) {
        this.persistedCount = persistedCount;
    }

    public Integer getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(Integer matchedCount) {
        this.matchedCount = matchedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
