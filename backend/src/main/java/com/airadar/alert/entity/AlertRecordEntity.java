package com.airadar.alert.entity;

import com.airadar.alert.model.AlertStatus;
import com.airadar.common.persistence.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@TableName(value = "alert_record", autoResultMap = true)
public class AlertRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long subscriptionRuleId;
    private Long hotClusterId;
    private AlertStatus status;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode matchReason;
    private String suppressionKey;
    private Instant matchedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubscriptionRuleId() {
        return subscriptionRuleId;
    }

    public void setSubscriptionRuleId(Long subscriptionRuleId) {
        this.subscriptionRuleId = subscriptionRuleId;
    }

    public Long getHotClusterId() {
        return hotClusterId;
    }

    public void setHotClusterId(Long hotClusterId) {
        this.hotClusterId = hotClusterId;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public JsonNode getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(JsonNode matchReason) {
        this.matchReason = matchReason;
    }

    public String getSuppressionKey() {
        return suppressionKey;
    }

    public void setSuppressionKey(String suppressionKey) {
        this.suppressionKey = suppressionKey;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(Instant matchedAt) {
        this.matchedAt = matchedAt;
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
