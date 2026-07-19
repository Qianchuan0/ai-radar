package com.airadar.cluster.feature;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Persisted feature vector for a {@code hot_item}, produced by the Phase 16
 * {@code ItemFeatureExtractor}.
 *
 * <p>The feature row is the deterministic input to V2 candidate retrieval and
 * layered match rules. It is intentionally denormalized away from
 * {@code hot_item} so the V2 pipeline can evolve its feature schema without
 * disturbing V1 code.
 *
 * <p>One row per {@code hot_item_id} (uniquely constrained by migration V8).
 */
@TableName(value = "hot_item_feature", autoResultMap = true)
public class HotItemFeatureEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hotItemId;
    private String normalizedTitle;
    private String canonicalUrl;
    private String publisherDomain;
    private Instant eventTime;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode externalIds;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode entities;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode keywords;
    private String eventType;
    private String featureVersion;
    private Instant createdAt;
    private Instant updatedAt;

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

    public String getNormalizedTitle() {
        return normalizedTitle;
    }

    public void setNormalizedTitle(String normalizedTitle) {
        this.normalizedTitle = normalizedTitle;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = canonicalUrl;
    }

    public String getPublisherDomain() {
        return publisherDomain;
    }

    public void setPublisherDomain(String publisherDomain) {
        this.publisherDomain = publisherDomain;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public JsonNode getExternalIds() {
        return externalIds;
    }

    public void setExternalIds(JsonNode externalIds) {
        this.externalIds = externalIds;
    }

    public JsonNode getEntities() {
        return entities;
    }

    public void setEntities(JsonNode entities) {
        this.entities = entities;
    }

    public JsonNode getKeywords() {
        return keywords;
    }

    public void setKeywords(JsonNode keywords) {
        this.keywords = keywords;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getFeatureVersion() {
        return featureVersion;
    }

    public void setFeatureVersion(String featureVersion) {
        this.featureVersion = featureVersion;
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
