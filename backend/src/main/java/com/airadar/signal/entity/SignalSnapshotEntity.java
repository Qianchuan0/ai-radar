package com.airadar.signal.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@TableName(value = "hot_item_signal_snapshot", autoResultMap = true)
public class SignalSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hotItemId;
    private Long rawItemId;
    private SourceType sourceType;
    private SourceRole sourceRole;
    private Instant observedAt;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode rawMetrics;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode normalizedSignal;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getHotItemId() {
        return hotItemId;
    }

    public void setHotItemId(Long hotItemId) {
        this.hotItemId = hotItemId;
    }

    public Long getRawItemId() {
        return rawItemId;
    }

    public void setRawItemId(Long rawItemId) {
        this.rawItemId = rawItemId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public SourceRole getSourceRole() {
        return sourceRole;
    }

    public void setSourceRole(SourceRole sourceRole) {
        this.sourceRole = sourceRole;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public JsonNode getRawMetrics() {
        return rawMetrics;
    }

    public void setRawMetrics(JsonNode rawMetrics) {
        this.rawMetrics = rawMetrics;
    }

    public JsonNode getNormalizedSignal() {
        return normalizedSignal;
    }

    public void setNormalizedSignal(JsonNode normalizedSignal) {
        this.normalizedSignal = normalizedSignal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
