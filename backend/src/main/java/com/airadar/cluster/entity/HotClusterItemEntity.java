package com.airadar.cluster.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

@TableName(value = "hot_cluster_item", autoResultMap = true)
public class HotClusterItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hotClusterId;
    private Long hotItemId;
    private String matchMethod;
    private BigDecimal matchScore;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode matchReason;
    private String ruleVersion;
    private Boolean isPrimary;
    private Instant assignedAt;
    private Instant removedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHotClusterId() {
        return hotClusterId;
    }

    public void setHotClusterId(Long hotClusterId) {
        this.hotClusterId = hotClusterId;
    }

    public Long getHotItemId() {
        return hotItemId;
    }

    public void setHotItemId(Long hotItemId) {
        this.hotItemId = hotItemId;
    }

    public String getMatchMethod() {
        return matchMethod;
    }

    public void setMatchMethod(String matchMethod) {
        this.matchMethod = matchMethod;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(BigDecimal matchScore) {
        this.matchScore = matchScore;
    }

    public JsonNode getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(JsonNode matchReason) {
        this.matchReason = matchReason;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean primary) {
        isPrimary = primary;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }
}
