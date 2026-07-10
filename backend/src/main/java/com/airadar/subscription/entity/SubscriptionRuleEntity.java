package com.airadar.subscription.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

@TableName(value = "subscription_rule", autoResultMap = true)
public class SubscriptionRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Boolean enabled;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode keywords;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode sourceTypes;
    private BigDecimal minScore;
    private Integer suppressWindowHours;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public JsonNode getKeywords() {
        return keywords;
    }

    public void setKeywords(JsonNode keywords) {
        this.keywords = keywords;
    }

    public JsonNode getSourceTypes() {
        return sourceTypes;
    }

    public void setSourceTypes(JsonNode sourceTypes) {
        this.sourceTypes = sourceTypes;
    }

    public BigDecimal getMinScore() {
        return minScore;
    }

    public void setMinScore(BigDecimal minScore) {
        this.minScore = minScore;
    }

    public Integer getSuppressWindowHours() {
        return suppressWindowHours;
    }

    public void setSuppressWindowHours(Integer suppressWindowHours) {
        this.suppressWindowHours = suppressWindowHours;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
