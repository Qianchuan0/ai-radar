package com.airadar.source.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.airadar.source.model.SourceType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@TableName(value = "source_config", autoResultMap = true)
public class SourceConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceCode;
    private SourceType sourceType;
    private String displayName;
    private Boolean enabled;
    private Integer crawlIntervalMinutes;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode configPayload;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getCrawlIntervalMinutes() {
        return crawlIntervalMinutes;
    }

    public void setCrawlIntervalMinutes(Integer crawlIntervalMinutes) {
        this.crawlIntervalMinutes = crawlIntervalMinutes;
    }

    public JsonNode getConfigPayload() {
        return configPayload;
    }

    public void setConfigPayload(JsonNode configPayload) {
        this.configPayload = configPayload;
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
