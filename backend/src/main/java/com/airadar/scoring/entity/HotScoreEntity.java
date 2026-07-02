package com.airadar.scoring.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

@TableName(value = "hot_score", autoResultMap = true)
public class HotScoreEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hotClusterId;
    private BigDecimal totalScore;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode scoreComponents;
    private String scoringVersion;
    private Instant calculatedAt;
    private Instant createdAt;

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

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public JsonNode getScoreComponents() {
        return scoreComponents;
    }

    public void setScoreComponents(JsonNode scoreComponents) {
        this.scoreComponents = scoreComponents;
    }

    public String getScoringVersion() {
        return scoringVersion;
    }

    public void setScoringVersion(String scoringVersion) {
        this.scoringVersion = scoringVersion;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
