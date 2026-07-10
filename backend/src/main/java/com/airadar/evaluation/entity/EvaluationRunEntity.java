package com.airadar.evaluation.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.airadar.evaluation.model.EvaluationRunStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@TableName(value = "evaluation_run", autoResultMap = true)
public class EvaluationRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private EvaluationRunStatus status;
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private Integer errorCases;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode metricsPayload;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode errorAnalysisPayload;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public EvaluationRunStatus getStatus() {
        return status;
    }

    public void setStatus(EvaluationRunStatus status) {
        this.status = status;
    }

    public Integer getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(Integer totalCases) {
        this.totalCases = totalCases;
    }

    public Integer getPassedCases() {
        return passedCases;
    }

    public void setPassedCases(Integer passedCases) {
        this.passedCases = passedCases;
    }

    public Integer getFailedCases() {
        return failedCases;
    }

    public void setFailedCases(Integer failedCases) {
        this.failedCases = failedCases;
    }

    public Integer getErrorCases() {
        return errorCases;
    }

    public void setErrorCases(Integer errorCases) {
        this.errorCases = errorCases;
    }

    public JsonNode getMetricsPayload() {
        return metricsPayload;
    }

    public void setMetricsPayload(JsonNode metricsPayload) {
        this.metricsPayload = metricsPayload;
    }

    public JsonNode getErrorAnalysisPayload() {
        return errorAnalysisPayload;
    }

    public void setErrorAnalysisPayload(JsonNode errorAnalysisPayload) {
        this.errorAnalysisPayload = errorAnalysisPayload;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
