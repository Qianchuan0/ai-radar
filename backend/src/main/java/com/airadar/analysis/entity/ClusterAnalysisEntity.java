package com.airadar.analysis.entity;

import com.airadar.analysis.model.AnalysisRunStatus;
import com.airadar.analysis.model.AnalysisType;
import com.airadar.common.persistence.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@TableName(value = "cluster_analysis", autoResultMap = true)
public class ClusterAnalysisEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hotClusterId;
    private AnalysisType analysisType;
    private AnalysisRunStatus status;
    private String schemaVersion;
    private String promptVersion;
    private String modelProvider;
    private String modelName;
    private String inputHash;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode resultPayload;
    private String failureCode;
    private String failureMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    private Instant updatedAt;

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

    public AnalysisType getAnalysisType() {
        return analysisType;
    }

    public void setAnalysisType(AnalysisType analysisType) {
        this.analysisType = analysisType;
    }

    public AnalysisRunStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisRunStatus status) {
        this.status = status;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String inputHash) {
        this.inputHash = inputHash;
    }

    public JsonNode getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(JsonNode resultPayload) {
        this.resultPayload = resultPayload;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
